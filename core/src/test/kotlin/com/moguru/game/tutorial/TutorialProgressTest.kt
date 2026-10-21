package com.moguru.game.tutorial

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TutorialProgressTest {
    private val basic = TutorialScenarios.basic
    private val dice = TutorialScenarios.diceEscape
    private val surface = TutorialScenarios.surfaceHealth

    private fun sample() = TutorialProgress()
        .started(basic, 3)
        .completed(dice)
        .started(surface, 1)

    @Test
    fun `progress is written in a stable line format and read back unchanged`() {
        val progress = sample()
        val text = TutorialProgressCodec.encode(progress)
        assertEquals(
            "moguru-tutorial-progress 1\n" +
                "current SURFACE_HEALTH\n" +
                "lesson BASIC 1 0 3\n" +
                "lesson DICE_ESCAPE 1 1 -\n" +
                "lesson SURFACE_HEALTH 1 0 1\n",
            text,
        )
        assertEquals(progress, TutorialProgressCodec.decode(text))
        assertEquals(TutorialProgress(), TutorialProgressCodec.decode(TutorialProgressCodec.encode(TutorialProgress())))
    }

    @Test
    fun `starting, completing and leaving update only that lesson`() {
        val progress = sample()
        assertEquals(3, progress.resumeChapter(basic))
        assertEquals(1, progress.resumeChapter(surface))
        assertNull(progress.resumeChapter(dice))
        assertTrue(progress.isCompleted(TutorialLessonId.DICE_ESCAPE))
        assertFalse(progress.isCompleted(TutorialLessonId.BASIC))
        assertSame(surface, progress.interrupted())

        val left = progress.left()
        assertNull(left.interrupted(), "leaving on purpose is not an interruption")
        assertEquals(1, left.resumeChapter(surface), "but the chapter is still offered in the list")

        val again = progress.completed(dice).started(dice, 1)
        assertTrue(again.isCompleted(TutorialLessonId.DICE_ESCAPE), "replaying keeps the completion")
        assertEquals(1, again.resumeChapter(dice))

        val done = progress.completed(surface)
        assertNull(done.resumeChapter(surface))
        assertNull(done.current)
    }

    @Test
    fun `progress for another scenario version is not resumed but its completion is kept`() {
        val old = TutorialProgress(
            mapOf(TutorialLessonId.BASIC to TutorialLessonRecord(basic.version + 1, completed = true, resumeChapter = 2)),
            current = TutorialLessonId.BASIC,
        )
        assertNull(old.resumeChapter(basic))
        assertNull(old.interrupted())
        assertTrue(old.isCompleted(TutorialLessonId.BASIC))
        val restarted = old.started(basic, 0)
        assertEquals(TutorialLessonRecord(basic.version, completed = true, resumeChapter = 0), restarted.lessons[TutorialLessonId.BASIC])
    }

    @Test
    fun `a chapter outside the current lesson is not resumed`() {
        val text = "moguru-tutorial-progress 1\ncurrent NEST_EAT\nlesson NEST_EAT 1 0 5\n"
        val progress = TutorialProgressCodec.decode(text)
        assertEquals(5, progress.lessons[TutorialLessonId.NEST_EAT]?.resumeChapter)
        assertNull(progress.resumeChapter(TutorialScenarios.nestEat))
        assertNull(progress.interrupted())
        assertThrows(IllegalArgumentException::class.java) { TutorialProgress().started(TutorialScenarios.nestEat, 5) }
    }

    @Test
    fun `a file from a newer app with unknown lessons still opens`() {
        val text = "moguru-tutorial-progress 1\ncurrent FUTURE_LESSON\nlesson FUTURE_LESSON 3 1 -\nlesson BASIC 1 1 -\n"
        val progress = TutorialProgressCodec.decode(text)
        assertNull(progress.current)
        assertEquals(mapOf(TutorialLessonId.BASIC to TutorialLessonRecord(1, completed = true)), progress.lessons)
    }

    @Test
    fun `another format is unsupported and damaged text is refused`() {
        val unsupported = assertThrows(UnsupportedTutorialProgressException::class.java) {
            TutorialProgressCodec.decode("moguru-tutorial-progress 2\n")
        }
        assertEquals(2, unsupported.format)
        listOf(
            "",
            "not progress",
            "moguru-tutorial-progress x",
            "moguru-tutorial-progress 1\nlesson BASIC 1 2 -",
            "moguru-tutorial-progress 1\nlesson BASIC 0 0 -",
            "moguru-tutorial-progress 1\nlesson BASIC 1 0 -1",
            "moguru-tutorial-progress 1\nlesson BASIC 1 0",
            "moguru-tutorial-progress 1\nlesson BASIC 1 0 -\nlesson BASIC 1 1 -",
            "moguru-tutorial-progress 1\ncurrent BASIC\ncurrent DICE_ESCAPE",
            "moguru-tutorial-progress 1\nsomething else",
        ).forEach { text ->
            assertThrows(IllegalArgumentException::class.java, { TutorialProgressCodec.decode(text) }, text)
        }
    }
}
