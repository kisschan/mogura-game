package com.moguru.game.tutorial

import com.moguru.game.engine.CaptureResult
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.EscapeDirection
import com.moguru.game.model.FoodType
import com.moguru.game.model.TileShape
import com.moguru.game.presenter.FoodDecisionSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AdditionalLessonsTest {
    @Test
    fun `dice lesson shows an escape along the arrow, then a blocked escape that captures`() {
        val scenario = TutorialScenarios.diceEscape
        val driver = TutorialDriver(TutorialSession(scenario))
        driver.finish(scenario.solution)
        assertTrue(driver.shownGuidance.any { "ダイスの目を決めて" in it.text }, "the fixed dice are explained as a practice aid")

        val rolls = driver.learnerSteps.filter { it.action == TutorialAction.RollDice }
        assertEquals(listOf(2, 2), rolls.map { it.afterAction.pendingCaptureRoll?.roll })
        val (escape, blocked) = driver.learnerSteps.filter { it.action == TutorialAction.ResolveCapture }

        assertEquals(FoodType.EARTHWORM, escape.before.pendingCaptureRoll?.food?.type)
        val escaped = escape.afterAction.lastCaptureResult as CaptureResult.Escaped
        assertEquals(EscapeDirection.BOTTOM, escaped.direction)
        assertEquals(p(2, 4), escaped.to)
        escape.afterAction.engine.foods.getValue(p(2, 4)).single().let {
            assertEquals(FoodType.EARTHWORM, it.type)
            assertFalse(it.isFaceDown, "an escaped card stays face up")
        }
        assertNull(escape.afterAction.pendingDecision)
        assertEquals(TurnPhase.END, escape.afterAction.engine.currentPhase)

        // Below the bottom row is outside the board, so the same roll cannot let it escape.
        val captured = blocked.afterAction.lastCaptureResult as CaptureResult.Success
        assertEquals(2, captured.diceRoll)
        assertEquals(FoodType.EARTHWORM, blocked.afterAction.pendingDecision?.food?.type)
        assertTrue(driver.shownGuidance.any { "盤の外" in it.text })
    }

    @Test
    fun `restarting a dice chapter rolls the same scripted dice again`() {
        val scenario = TutorialScenarios.diceEscape
        val driver = TutorialDriver(TutorialSession(scenario, 1))
        val chapter = scenario.solution[1]
        val untilRoll = chapter.take(chapter.indexOf(TutorialAction.RollDice) + 1)
        driver.play(untilRoll)
        assertEquals(2, driver.snapshot().pendingCaptureRoll?.roll)
        driver.session.restartChapter()
        // The scripted dice hold two rolls; a restart that kept the used one would run out here.
        driver.play(untilRoll)
        assertEquals(2, driver.snapshot().pendingCaptureRoll?.roll)
        driver.finish(listOf(chapter.drop(untilRoll.size)))
        assertEquals(TutorialStatus.COMPLETED, driver.session.status)
    }

    @Test
    fun `surface lesson ends turns underground, on the surface, then underground again`() {
        val scenario = TutorialScenarios.surfaceHealth
        val driver = TutorialDriver(TutorialSession(scenario))
        driver.finish(scenario.solution)
        val consumptions = driver.learnerConsumptions()
        assertEquals(listOf(false to 1, true to 2, false to 1), consumptions.map { it.isOnSurface to it.actualConsumption })
        assertEquals(listOf(13 to 12, 12 to 10, 10 to 9), consumptions.map { it.healthBefore to it.healthAfter })
        assertEquals(p(1, 0), driver.learnerSteps.single { it.chapter == 1 && it.action is TutorialAction.Move }
            .afterAction.player(LEARNER_ID).position)
    }

    @Test
    fun `robbery waits for the next own turn and the returning owner evicts the thief`() {
        val scenario = TutorialScenarios.robberyDefense
        val driver = TutorialDriver(TutorialSession(scenario))
        val opponentNest = requireNotNull(scenario.opponent).nestPosition
        driver.snapshot().player(OPPONENT_ID).let {
            assertEquals(1, it.score, "the opponent stored a larva by legal play")
            assertNotEquals(it.nestPosition, it.position, "and left its nest again")
        }

        driver.play(scenario.solution[0])
        val entering = driver.learnerSteps.last()
        assertEquals(opponentNest, entering.afterAction.player(LEARNER_ID).position)
        assertFalse(entering.robberyOpenAfterAction, "no robbery on the turn the nest was entered")

        val second = scenario.solution[1]
        val untilStay = second.take(second.indexOf(TutorialAction.Skip))
        driver.play(untilStay)
        assertEquals(1, driver.session.chapterIndex)
        assertEquals(1, driver.opponent().score)
        assertTrue(driver.session.controller.hasRobberyOpportunityForCurrentPlayer(), "staying for a turn allows it")
        driver.play(listOf(TutorialAction.Skip))
        driver.reachLearnerAction()
        assertTrue(driver.session.controller.canRobbery())

        driver.finish(listOf(second.drop(untilStay.size + 1)))
        assertEquals(TutorialStatus.COMPLETED, driver.session.status)
        val end = driver.snapshot()
        assertEquals(opponentNest, end.player(OPPONENT_ID).position)
        assertEquals(p(1, 4), end.player(LEARNER_ID).position, "evicted to the fixed cell of that nest")
        assertEquals(0, end.player(OPPONENT_ID).score)
    }

    @Test
    fun `carrying a robbed card stores it at once while the mole stays in the other nest`() {
        val scenario = TutorialScenarios.robberyCarry
        val driver = TutorialDriver(TutorialSession(scenario))
        driver.finish(scenario.solution)
        val carry = driver.learnerSteps.single { it.action == TutorialAction.Carry }
        assertEquals(FoodDecisionSource.ROBBERY, carry.before.pendingDecision?.source)
        carry.afterAction.player(LEARNER_ID).let {
            assertEquals(1, it.score)
            assertNull(it.carriedFood)
            assertEquals(requireNotNull(scenario.opponent).nestPosition, it.position)
        }
        assertEquals(0, carry.afterAction.player(OPPONENT_ID).score)
        assertTrue(driver.shownGuidance.any { "巣まで運ぶ" in it.text }, "it is told apart from a captured card")
    }

    @Test
    fun `eating from the own nest heals but loses that card's point`() {
        val scenario = TutorialScenarios.nestEat
        val driver = TutorialDriver(TutorialSession(scenario))
        assertEquals(FoodType.BEETLE_LARVA, driver.learner().carriedFood?.type)
        driver.finish(scenario.solution)
        val eat = driver.learnerSteps.single { it.action == TutorialAction.Eat }
        assertNull(eat.before.pendingDecision, "this is the nest's stored card, not a fresh capture")
        assertEquals(1, eat.before.player(LEARNER_ID).score)
        assertEquals(0, eat.afterAction.player(LEARNER_ID).score)
        assertEquals(eat.before.player(LEARNER_ID).health + 1, eat.afterAction.player(LEARNER_ID).health)
    }

    @Test
    fun `the basic lesson draws one tile per turn alone and the robbery lessons reuse the discard pile`() {
        val basic = TutorialScenarios.basic
        // 9 at the start and one per chapter, as the learner plays alone: 5 before chapter 5 and 4
        // before chapter 6. Each chapter's own card is already held in pendingDigDrawnTile.
        assertEquals(5, basic.chapterStartSnapshot(4).engine.tileDrawPile.size)
        basic.chapterStartSnapshot(5).let {
            assertEquals(5, it.engine.tileDiscardPile.size)
            assertEquals(4, it.engine.tileDrawPile.size)
            assertEquals(TileShape.L_SHAPE, it.pendingDigDrawnTile?.shape)
        }
        // Two players dig ten times before the first robbery chapter, so the discard pile is drawn again.
        TutorialScenarios.robberyDefense.chapterStartSnapshot(0).let {
            assertTrue(it.engine.tileDiscardPile.isEmpty())
            assertEquals(9, it.engine.tileDrawPile.size)
        }
    }
}
