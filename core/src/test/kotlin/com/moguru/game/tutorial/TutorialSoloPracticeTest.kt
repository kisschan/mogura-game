package com.moguru.game.tutorial

import com.moguru.game.engine.PlayerConfig
import com.moguru.game.persistence.validate
import com.moguru.game.persistence.validatePractice
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** The four lessons played alone, and the practice-only start and restore that allow a single player. */
class TutorialSoloPracticeTest {
    private val solo = listOf(
        TutorialLessonId.BASIC, TutorialLessonId.DICE_ESCAPE, TutorialLessonId.SURFACE_HEALTH, TutorialLessonId.NEST_EAT,
    )
    private val withOpponent = listOf(TutorialLessonId.ROBBERY_DEFENSE, TutorialLessonId.ROBBERY_CARRY)

    private fun controller() = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())

    private fun learnerAlone() = listOf(
        PlayerConfig(MoguraGameController.playerNameForId(LEARNER_ID), LEARNER_NEST, playerId = LEARNER_ID),
    )

    @Test
    fun `four lessons are played alone and both robbery lessons keep their scripted opponent`() {
        assertEquals(TutorialLessonId.entries.toSet(), (solo + withOpponent).toSet())
        solo.map(TutorialScenarios::of).forEach { scenario ->
            assertTrue(scenario.solo, "${scenario.lesson}")
            assertNull(scenario.opponentPlayerId, "${scenario.lesson}")
            assertEquals(2, scenario.version, "${scenario.lesson}: chapters saved for two players must not resume")
            for (chapter in 0 until scenario.chapterCount) {
                assertEquals(
                    listOf(scenario.learnerPlayerId),
                    scenario.chapterStartSnapshot(chapter).engine.players.map { it.id },
                    "${scenario.lesson} chapter $chapter",
                )
            }
        }
        withOpponent.map(TutorialScenarios::of).forEach { scenario ->
            assertFalse(scenario.solo, "${scenario.lesson}")
            assertEquals(OPPONENT_ID, scenario.opponentPlayerId)
            assertEquals(1, scenario.version, "${scenario.lesson} did not change")
            assertEquals(listOf(LEARNER_ID, OPPONENT_ID), scenario.chapterStartSnapshot(0).engine.players.map { it.id })
        }
    }

    @Test
    fun `a match still needs two to four players while only practice may start alone`() {
        val match = controller()
        assertThrows(IllegalArgumentException::class.java) { match.startNewGame(1) }
        assertThrows(IllegalArgumentException::class.java) { match.startNewGame(5) }
        assertThrows(IllegalArgumentException::class.java) { match.startNewGame(learnerAlone()) }
        assertNull(match.engine, "a refused start leaves no game")

        val practice = controller()
        assertThrows(IllegalArgumentException::class.java) { practice.startPracticeGame(emptyList()) }
        assertTrue(practice.startPracticeGame(learnerAlone()).success)
        assertEquals(listOf(LEARNER_ID), practice.engine?.players?.map { it.id })
        assertEquals(LEARNER_ID, practice.currentPlayer?.id)

        // With two to four players a practice game starts exactly like a match.
        val two = controller().apply { startNewGame(2) }
        val practiceTwo = controller().apply { startPracticeGame(MoguraGameController.defaultConfigs(2)) }
        assertEquals(two.exportSnapshot(), practiceTwo.exportSnapshot())
    }

    @Test
    fun `a single player snapshot is refused as a save and restored only as practice`() {
        val single = TutorialScenarios.basic.chapterStartSnapshot(0)
        assertEquals(1, single.engine.players.size)
        assertThrows(IllegalArgumentException::class.java) { single.validate() }
        assertThrows(IllegalArgumentException::class.java) { MoguraGameController.fromSnapshot(single) }

        single.validatePractice()
        val restored = MoguraGameController.fromPracticeSnapshot(single, FixedDiceRoller(listOf(6)), FixedShuffler())
        assertEquals(single, restored.exportSnapshot())
    }

    @Test
    fun `restoring practice still refuses every other kind of damage`() {
        val single = TutorialScenarios.basic.chapterStartSnapshot(0)
        val learner = single.engine.players.single()
        listOf(
            single.copy(engine = single.engine.copy(players = emptyList())),
            single.copy(engine = single.engine.copy(players = List(5) { learner })),
            single.copy(engine = single.engine.copy(currentPlayerIndex = 1)),
            single.copy(engine = single.engine.copy(players = listOf(learner.copy(health = 99)))),
            single.copy(engine = single.engine.copy(tileDrawPile = single.engine.tileDrawPile.drop(1))),
            single.copy(turnConsumptionAnimationId = -1),
        ).forEach { corrupt ->
            assertThrows(IllegalArgumentException::class.java) { corrupt.validatePractice() }
            assertThrows(IllegalArgumentException::class.java) {
                MoguraGameController.fromPracticeSnapshot(corrupt, FixedDiceRoller(listOf(6)), FixedShuffler())
            }
        }
    }

    @Test
    fun `alone the next turn opens the next chapter only after the turn end has been explained`() {
        val scenario = TutorialScenarios.basic
        val driver = TutorialDriver(TutorialSession(scenario))
        val session = driver.session
        driver.play(BasicLesson.chapter0)
        // The learner's next turn has already begun, but it does not belong to chapter 2 yet.
        assertEquals(0, session.chapterIndex)
        assertEquals(TutorialStatus.AWAITING_ACKNOWLEDGEMENT, session.status)
        assertEquals("手番が終わり、体力が1減ったよ", session.guidance.text)
        assertEquals(LEARNER_ID, session.controller.currentPlayer?.id)
        assertEquals(12, driver.learner().health)
        assertRefusedByTutorial(session, BasicLesson.chapter1.first())
        assertNull(session.nextOpponentAction())

        driver.acknowledge()
        assertEquals("練習は君1人。次も君の番だよ", session.guidance.text)
        driver.acknowledge()
        assertEquals(1, session.chapterIndex)
        assertEquals(TutorialStatus.LEARNER_ACTION, session.status)
        assertEquals(scenario.chapterStartSnapshot(1), driver.snapshot())

        // Without a turn end explanation the next turn opens the next chapter at once.
        driver.play(BasicLesson.chapter1)
        assertEquals(2, session.chapterIndex)
        assertEquals(TutorialStatus.LEARNER_ACTION, session.status)
        assertEquals(scenario.chapterStartSnapshot(2), driver.snapshot())
    }

    @Test
    fun `a restart after the turn end returns to the chapter start and counts its turn again`() {
        val scenario = TutorialScenarios.surfaceHealth
        val driver = TutorialDriver(TutorialSession(scenario, 1))
        val session = driver.session
        driver.play(scenario.solution[1])
        assertEquals(1, session.chapterIndex, "the surface turn is over but its explanation is unread")
        assertEquals(TutorialStatus.AWAITING_ACKNOWLEDGEMENT, session.status)

        session.restartChapter()
        assertEquals(1, session.chapterIndex)
        assertEquals(scenario.chapterStartSnapshot(1), driver.snapshot())
        driver.reachLearnerAction()
        assertEquals(1, session.chapterIndex, "the restarted turn has not ended yet")

        driver.finish(scenario.solution.drop(1))
        assertEquals(TutorialStatus.COMPLETED, session.status)
        // The abandoned attempt ended one surface turn before the restart.
        assertEquals(
            listOf(true to 2, true to 2, false to 1),
            driver.learnerConsumptions().map { it.isOnSurface to it.actualConsumption },
        )
    }

    @Test
    fun `progress saved for the two player versions restarts those lessons but keeps their completion`() {
        val old = TutorialProgress(
            lessons = mapOf(
                TutorialLessonId.BASIC to TutorialLessonRecord(1, completed = true, resumeChapter = 3),
                TutorialLessonId.DICE_ESCAPE to TutorialLessonRecord(1, completed = false, resumeChapter = 1),
                TutorialLessonId.SURFACE_HEALTH to TutorialLessonRecord(1, completed = true),
                TutorialLessonId.NEST_EAT to TutorialLessonRecord(1, completed = false, resumeChapter = 0),
                TutorialLessonId.ROBBERY_DEFENSE to TutorialLessonRecord(1, completed = false, resumeChapter = 1),
            ),
            current = TutorialLessonId.DICE_ESCAPE,
        )
        assertEquals(old, TutorialProgressCodec.decode(TutorialProgressCodec.encode(old)), "old records still read")
        solo.forEach { assertNull(old.resumeChapter(TutorialScenarios.of(it)), "$it resumed a two-player chapter") }
        assertNull(old.interrupted(), "an interrupted two-player chapter is not offered")
        assertTrue(old.isCompleted(TutorialLessonId.BASIC))
        assertTrue(old.isCompleted(TutorialLessonId.SURFACE_HEALTH))
        assertFalse(old.isCompleted(TutorialLessonId.DICE_ESCAPE))
        // The robbery lessons did not change, so their chapter still resumes.
        assertEquals(1, old.resumeChapter(TutorialScenarios.robberyDefense))
        assertSame(TutorialScenarios.robberyDefense, old.copy(current = TutorialLessonId.ROBBERY_DEFENSE).interrupted())

        val restarted = old.started(TutorialScenarios.basic, 0)
        assertEquals(TutorialLessonRecord(2, completed = true, resumeChapter = 0), restarted.lessons[TutorialLessonId.BASIC])
    }
}
