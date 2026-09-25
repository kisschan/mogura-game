package com.moguru.game.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import com.moguru.game.model.Position
import com.moguru.game.persistence.GameSnapshot
import com.moguru.game.presenter.MoguraGameController
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

data class SavedGame(
    val game: GameSnapshot,
    val revision: Long,
    val savedAtMillis: Long,
    val selectedMovePosition: Position? = null,
    val boardPiecesTransparent: Boolean = false,
    val lastMessage: String? = null,
)

interface GameSaveRepository {
    fun load(): SavedGame?
    /** Return only when this exact save has been committed. */
    fun save(game: SavedGame)
}

interface GameSaveIo : AutoCloseable {
    fun <T> execute(task: () -> T, complete: (Result<T>) -> Unit)
    override fun close() {}
}

/** Deterministic default for previews and JVM tests. Production injects disk storage. */
object ImmediateGameSaveIo : GameSaveIo {
    override fun <T> execute(task: () -> T, complete: (Result<T>) -> Unit) = complete(runCatching(task))
}

class MemoryGameSaveRepository : GameSaveRepository {
    private var value: SavedGame? = null
    override fun load() = value
    override fun save(game: SavedGame) { value = game }
}

enum class GameSaveLoadIssue { IO, CORRUPT, UNSUPPORTED }
class InvalidGameSaveException(val issue: GameSaveLoadIssue, cause: Throwable? = null) : IOException(cause)

data class GamePersistenceUiState(
    val busy: Boolean = false,
    val saveFailed: Boolean = false,
    val loadIssue: GameSaveLoadIssue? = null,
    val hasSavedGame: Boolean = false,
    val showEntry: Boolean = false,
    val confirmOverwrite: Boolean = false,
    val finished: Boolean = false,
    val playerCount: Int = 0,
    val currentPlayerName: String = "",
)

internal class AndroidGameSaveIo : GameSaveIo {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    override fun <T> execute(task: () -> T, complete: (Result<T>) -> Unit) {
        executor.execute {
            val result = runCatching(task)
            main.post { if (!closed) complete(result) }
        }
    }
    override fun close() { closed = true }
    companion object {
        // Old ViewModels finish their queued commit before a new ViewModel loads.
        private val executor = Executors.newSingleThreadExecutor()
    }
}

/** All instances use the same lock, including a replacement Activity during teardown. */
internal class AtomicGameSaveRepository(file: File) : GameSaveRepository {
    private val atomic = AtomicFile(file)
    override fun load(): SavedGame? = synchronized(lock) {
        // openRead also recovers a backup left by older AtomicFile implementations.
        val bytes = try {
            atomic.openRead().use { it.readBytes() }
        } catch (e: java.io.FileNotFoundException) {
            if (!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) return@synchronized null
            throw e
        }
        GameSaveCodec.decode(bytes.toString(Charsets.UTF_8))
    }

    override fun save(game: SavedGame): Unit = synchronized(lock) {
        MoguraGameController.fromSnapshot(game.game)
        val bytes = GameSaveCodec.encode(game).toByteArray(Charsets.UTF_8)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            // Surface sync errors rather than relying on AtomicFile's logging alone.
            stream.fd.sync()
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
        // Some platform versions log rename failures. Verify the committed payload.
        if (!atomic.openRead().use { it.readBytes() }.contentEquals(bytes)) {
            throw IOException("Game save commit could not be verified")
        }
    }

    companion object {
        private val lock = Any()
    }
}

internal fun defaultGameSaveRepository(context: Context): GameSaveRepository =
    AtomicGameSaveRepository(File(context.applicationContext.filesDir, "game-save/current.json"))

/** Composition root; instrumentation can supply an isolated store before launching the Activity. */
internal object GameSaveDependencies {
    var repositoryFactory: (Context) -> GameSaveRepository = ::defaultGameSaveRepository
    var ioFactory: () -> GameSaveIo = ::AndroidGameSaveIo
}
