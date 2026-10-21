package com.moguru.game.tutorial

import com.moguru.game.model.Board
import com.moguru.game.model.CellType
import com.moguru.game.model.FoodCard
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.persistence.validate
import com.moguru.game.presenter.DigTileChoice
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Checks every lesson the same way: regulation deal, every accepted branch, every refusal. */
class TutorialLessonsTest {
    private val scenarios = TutorialScenarios.all

    private val boardCells: List<Position> = Board().cells.values
        .filter { it.type != CellType.INVALID }
        .map { it.position }

    /** Operations tried at every learner step. The lesson must refuse or accept each one cleanly. */
    private val probes: List<TutorialAction> = buildList {
        boardCells.forEach {
            add(TutorialAction.SelectDigCell(it))
            add(TutorialAction.SelectMoveTarget(it))
            add(TutorialAction.Move(it))
        }
        DigTileChoice.entries.forEach { add(TutorialAction.SelectDigTile(it)) }
        Rotation.entries.forEach { add(TutorialAction.RotateDigTile(it)) }
        listOf(0, 1).forEach {
            add(TutorialAction.SelectCaptureTarget(it))
            add(TutorialAction.SelectRobberyTarget(it))
        }
        addAll(
            listOf(
                TutorialAction.ConfirmDig, TutorialAction.Capture, TutorialAction.RollDice, TutorialAction.ResolveCapture,
                TutorialAction.Eat, TutorialAction.Carry, TutorialAction.Rob, TutorialAction.Skip, TutorialAction.FinishTurn,
            ),
        )
    }

    @Test
    fun `every lesson is listed once and the basic lesson comes first`() {
        assertEquals(TutorialLessonId.entries, scenarios.map { it.lesson })
        assertEquals(TutorialLessonId.BASIC, TutorialScenarios.basic.lesson)
        assertEquals(TutorialLessonId.entries.drop(1), TutorialScenarios.additional.map { it.lesson })
        scenarios.forEach { scenario ->
            assertSame(scenario, TutorialScenarios.of(scenario.lesson))
            assertTrue(scenario.title.isNotBlank() && scenario.summary.isNotBlank())
            assertEquals(scenario.chapterCount, scenario.solution.size)
        }
    }

    @Test
    fun `every lesson starts from a regulation two player game on the learner's turn`() {
        scenarios.forEach { scenario ->
            val start = scenario.newController().exportSnapshot()
            start.validate()
            assertEquals(start, scenario.chapterStartSnapshot(0), "${scenario.lesson}")
            assertEquals(listOf(scenario.learnerPlayerId, scenario.opponentPlayerId), start.engine.players.map { it.id })
            assertEquals(scenario.learnerPlayerId, start.engine.players[start.engine.currentPlayerIndex].id)
            assertEquals(scenario.learnerNest, start.player(scenario.learnerPlayerId).nestPosition)
            val tiles = start.engine.tiles.values + start.engine.tileDrawPile + start.engine.tileDiscardPile +
                listOfNotNull(start.pendingDigDrawnTile)
            assertEquals(HoleTile.createFullSet().groupingBy { it.shape }.eachCount(), tiles.groupingBy { it.shape }.eachCount())
            val food = start.engine.foods.values.flatten() + start.engine.foodStock + start.engine.foodDiscard +
                start.engine.players.flatMap { it.storedFoods + listOfNotNull(it.carriedFood) }
            assertEquals(
                FoodCard.createDeck(includeFrog = false).groupingBy { it.type }.eachCount(),
                food.groupingBy { it.type }.eachCount(),
            )
        }
    }

    @Test
    fun `every lesson completes along its solution with legal opponent moves and no winner`() {
        scenarios.forEach { scenario ->
            val driver = TutorialDriver(TutorialSession(scenario))
            driver.finish(scenario.solution)
            assertEquals(TutorialStatus.COMPLETED, driver.session.status, "${scenario.lesson}")
            assertEquals((0 until scenario.chapterCount).toList(), driver.chapterStarts.keys.toList())
            driver.chapterStarts.forEach { (chapter, start) ->
                assertEquals(scenario.chapterStartSnapshot(chapter), start, "${scenario.lesson} chapter $chapter")
            }
            // Completion holds the game: nothing advances and no operation is accepted afterwards.
            val finished = driver.snapshot()
            assertNull(driver.session.advanceAutomatic())
            probes.forEach { assertRefusedByTutorial(driver.session, it) }
            assertNull(driver.session.nextOpponentAction())
            assertEquals(finished, driver.snapshot())
        }
    }

    @Test
    fun `every chapter can be rebuilt and restarted from its start`() {
        scenarios.forEach { scenario ->
            for (chapter in 0 until scenario.chapterCount) {
                val expected = scenario.chapterStartSnapshot(chapter)
                expected.validate()
                val driver = TutorialDriver(TutorialSession(scenario, chapter))
                assertEquals(expected, driver.snapshot(), "${scenario.lesson} chapter $chapter")
                // Leave the chapter half done, then restart it.
                val half = scenario.solution[chapter].take((scenario.solution[chapter].size + 1) / 2)
                driver.play(half)
                val generation = driver.session.generation
                driver.session.restartChapter()
                assertEquals(chapter, driver.session.chapterIndex)
                assertEquals(generation + 1, driver.session.generation)
                assertEquals(expected, driver.snapshot(), "${scenario.lesson} chapter $chapter restart")
                driver.finish(scenario.solution.drop(chapter))
                assertEquals(TutorialStatus.COMPLETED, driver.session.status)
            }
        }
    }

    @Test
    fun `every branch the lessons accept completes the lesson`() {
        val expected = mapOf(
            TutorialLessonId.BASIC to listOf(2, 1, 1, 2, 8, 18),
            TutorialLessonId.DICE_ESCAPE to listOf(2, 16),
            TutorialLessonId.SURFACE_HEALTH to listOf(1, 8, 5),
            TutorialLessonId.ROBBERY_DEFENSE to listOf(8, 2),
            TutorialLessonId.ROBBERY_CARRY to listOf(8, 1),
            TutorialLessonId.NEST_EAT to listOf(8),
        )
        scenarios.forEach { scenario ->
            val counts = (0 until scenario.chapterCount).map { chapter ->
                val branches = branches(scenario, chapter)
                branches.forEach { branch ->
                    val driver = TutorialDriver(TutorialSession(scenario, chapter))
                    driver.finish(listOf(branch) + scenario.solution.drop(chapter + 1))
                    assertEquals(TutorialStatus.COMPLETED, driver.session.status, "${scenario.lesson} $branch")
                }
                branches.size
            }
            assertEquals(expected[scenario.lesson], counts, "${scenario.lesson}")
        }
    }

    @Test
    fun `refused operations never reach the game in any lesson`() {
        var refusedLegalMoves = 0
        scenarios.forEach { scenario ->
            val driver = TutorialDriver(TutorialSession(scenario))
            scenario.solution.flatten().forEach { action ->
                driver.reachLearnerAction()
                val session = driver.session
                probes.filter { session.check(it) != null }.forEach { probe ->
                    if (isLegalForTheGame(session, probe)) refusedLegalMoves++
                    assertRefusedByTutorial(session, probe)
                }
                assertNull(session.check(action), "${scenario.lesson}: the solution step $action must be accepted")
                driver.perform(action)
            }
        }
        // The lessons hold back moves the game itself would allow, not just illegal ones.
        assertTrue(refusedLegalMoves > 0)
    }

    @Test
    fun `basic lesson refuses a legal dig away from the guided cell`() {
        val driver = TutorialDriver(TutorialSession(TutorialScenarios.basic, 2))
        driver.reachLearnerAction()
        val other = p(1, 1)
        assertTrue(other in driver.session.controller.digTargets(), "the game would allow it")
        assertRefusedByTutorial(driver.session, TutorialAction.SelectDigCell(other))
    }

    @Test
    fun `every text the lessons show fits one instruction line page`() {
        val shown = mutableSetOf<String>()
        scenarios.forEach { scenario ->
            scenario.chapters.forEach { chapter -> chapter.explanations.forEach { shown += it.pages } }
            shown += scenario.completionText
            val driver = TutorialDriver(TutorialSession(scenario))
            scenario.solution.flatten().forEach { action ->
                driver.reachLearnerAction()
                probes.mapNotNullTo(shown) { driver.session.check(it) }
                driver.perform(action)
            }
            driver.finish(emptyList())
            driver.shownGuidance.forEach { guidance ->
                shown += guidance.text
                guidance.notice?.let { shown += it }
            }
        }
        shown.forEach { text ->
            assertTrue(text.isNotBlank())
            assertTrue(text.length <= TUTORIAL_PAGE_MAX_CHARS, "too long for the instruction line: $text")
        }
    }

    @Test
    fun `stages and explanation pages are numbered for the screen`() {
        scenarios.forEach { scenario ->
            val driver = TutorialDriver(TutorialSession(scenario))
            driver.finish(scenario.solution)
            driver.shownGuidance.forEach { guidance ->
                assertTrue(guidance.stage in 1..scenario.stageCount, "${scenario.lesson} ${guidance.text}")
                if (guidance.page != null) {
                    assertTrue(guidance.page!! in 1..guidance.pageCount!!)
                    assertTrue(TutorialControl.ACKNOWLEDGE in guidance.controls)
                }
            }
        }
        assertEquals(7, TutorialScenarios.basic.stageCount)
        assertEquals((1..7).toSet(), TutorialDriver(TutorialSession(TutorialScenarios.basic)).run {
            finish(TutorialScenarios.basic.solution)
            shownGuidance.map { it.stage }.toSet()
        })
    }

    @Test
    fun `a stale explanation serial tells a repeated confirmation apart`() {
        val session = TutorialSession(TutorialScenarios.basic)
        val first = session.explanationSerial
        assertEquals(1, session.guidance.page)
        assertTrue(session.acknowledge())
        assertTrue(session.explanationSerial > first)
        assertEquals(2, session.guidance.page)
    }

    /**
     * Whether the game would accept [probe] right now, judged on a copy of the session's game with
     * its own dice, so the copy can never use up one of the lesson's scripted rolls.
     */
    private fun isLegalForTheGame(session: TutorialSession, probe: TutorialAction): Boolean {
        if (probe is TutorialAction.SelectMoveTarget) return false
        val copy = MoguraGameController.fromSnapshot(session.controller.exportSnapshot(), FixedDiceRoller(listOf(6)), FixedShuffler())
        return copy.runTutorialAction(probe)?.success == true
    }

    /**
     * Every way to play [chapter] that its lesson accepts. At each choice in the solution (the tile
     * and rotation placed, the move target, the meal decision) the session is asked which options
     * it accepts, and the rest of the chapter follows the solution.
     */
    private fun branches(scenario: TutorialScenario, chapter: Int): List<List<TutorialAction>> {
        val result = mutableListOf<List<TutorialAction>>()
        fun at(prefix: List<TutorialAction>): TutorialSession {
            val driver = TutorialDriver(TutorialSession(scenario, chapter))
            driver.play(prefix)
            driver.reachLearnerAction()
            return driver.session
        }
        fun walk(prefix: List<TutorialAction>, rest: List<TutorialAction>) {
            if (rest.isEmpty()) {
                result += prefix
                return
            }
            when (val next = rest.first()) {
                is TutorialAction.SelectDigTile, is TutorialAction.RotateDigTile, TutorialAction.ConfirmDig -> {
                    val after = rest.drop(rest.indexOf(TutorialAction.ConfirmDig) + 1)
                    for (choice in DigTileChoice.entries) for (rotation in Rotation.entries) {
                        val option = listOf(TutorialAction.SelectDigTile(choice), TutorialAction.RotateDigTile(rotation))
                        val session = at(prefix)
                        if (option.all { session.perform(it).accepted } && session.check(TutorialAction.ConfirmDig) == null) {
                            walk(prefix + option + TutorialAction.ConfirmDig, after)
                        }
                    }
                }
                is TutorialAction.SelectMoveTarget -> {
                    val session = at(prefix)
                    session.controller.moveTargets()
                        .sortedWith(compareBy({ it.row }, { it.col }))
                        .filter {
                            session.check(TutorialAction.SelectMoveTarget(it)) == null &&
                                session.check(TutorialAction.Move(it)) == null
                        }
                        .forEach { walk(prefix + TutorialAction.SelectMoveTarget(it) + TutorialAction.Move(it), rest.drop(2)) }
                }
                TutorialAction.Eat, TutorialAction.Carry -> {
                    val session = at(prefix)
                    listOf(TutorialAction.Eat, TutorialAction.Carry)
                        .filter { session.check(it) == null }
                        .forEach { walk(prefix + it, rest.drop(1)) }
                }
                else -> walk(prefix + next, rest.drop(1))
            }
        }
        walk(emptyList(), scenario.solution[chapter])
        return result
    }
}
