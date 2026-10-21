package com.moguru.game.android

import android.content.Context
import android.util.AtomicFile
import com.moguru.game.tutorial.TutorialProgress
import com.moguru.game.tutorial.TutorialProgressCodec
import java.io.File
import java.io.IOException

/** Learning progress only. It is a separate file from the match save and never touches it. */
interface TutorialProgressRepository {
    /** Returns empty progress when nothing was saved yet. */
    fun load(): TutorialProgress
    /** Return only when this exact progress has been committed. */
    fun save(progress: TutorialProgress)
}

class MemoryTutorialProgressRepository(private var value: TutorialProgress = TutorialProgress()) : TutorialProgressRepository {
    override fun load() = value
    override fun save(progress: TutorialProgress) { value = progress }
}

/** All instances share one lock, like the match save. */
internal class AtomicTutorialProgressRepository(file: File) : TutorialProgressRepository {
    private val atomic = AtomicFile(file)

    override fun load(): TutorialProgress = synchronized(lock) {
        val bytes = try {
            atomic.openRead().use { it.readBytes() }
        } catch (e: java.io.FileNotFoundException) {
            if (!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) return@synchronized TutorialProgress()
            throw e
        }
        TutorialProgressCodec.decode(bytes.toString(Charsets.UTF_8))
    }

    override fun save(progress: TutorialProgress): Unit = synchronized(lock) {
        val bytes = TutorialProgressCodec.encode(progress).toByteArray(Charsets.UTF_8)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            stream.fd.sync()
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
        if (!atomic.openRead().use { it.readBytes() }.contentEquals(bytes)) {
            throw IOException("Tutorial progress commit could not be verified")
        }
    }

    companion object {
        private val lock = Any()
    }
}

internal fun defaultTutorialProgressRepository(context: Context): TutorialProgressRepository =
    AtomicTutorialProgressRepository(File(context.applicationContext.filesDir, "tutorial/progress.txt"))

/** Composition root; instrumentation can supply an isolated store before launching the Activity. */
internal object TutorialProgressDependencies {
    var repositoryFactory: (Context) -> TutorialProgressRepository = ::defaultTutorialProgressRepository
}
