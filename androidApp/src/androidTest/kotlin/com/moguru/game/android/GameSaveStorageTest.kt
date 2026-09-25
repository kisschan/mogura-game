package com.moguru.game.android

import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class GameSaveStorageTest {
    @Test
    fun interruptedReplacementLeavesPreviousCompleteSaveReadable() {
        val directory = File(InstrumentationRegistry.getInstrumentation().context.cacheDir, "resume-test-" + UUID.randomUUID())
        check(directory.mkdirs())
        try {
            val file = File(directory, "current.json")
            val repository = AtomicGameSaveRepository(file)
            assertNull(repository.load())
            val controller = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
            controller.startNewGame(2)
            val saved = SavedGame(controller.exportSnapshot(), 1, 1000)
            repository.save(saved)
            assertEquals(saved, AtomicGameSaveRepository(file).load())
            // Deliberately close without finishWrite, as the OS would after process death.
            AtomicFile(file).startWrite().use { it.write("{unfinished".toByteArray()) }
            assertEquals(saved, AtomicGameSaveRepository(file).load())
            val next = saved.copy(revision = 2, savedAtMillis = 2000)
            repository.save(next)
            assertEquals(next, AtomicGameSaveRepository(file).load())
            file.writeText("{corrupt")
            try { repository.load(); fail("accepted corrupt save") }
            catch (e: InvalidGameSaveException) { assertEquals(GameSaveLoadIssue.CORRUPT, e.issue) }
            assertEquals("{corrupt", file.readText())
        } finally {
            directory.deleteRecursively()
        }
    }
}
