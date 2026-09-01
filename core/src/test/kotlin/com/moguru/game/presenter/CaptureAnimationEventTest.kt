package com.moguru.game.presenter

import com.moguru.game.engine.CaptureResult
import com.moguru.game.engine.GameEngine
import com.moguru.game.engine.PlayerConfig
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Board
import com.moguru.game.model.EscapeDirection
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.util.DiceRoller
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CaptureAnimationEventTest {
    @Test
    fun `event is published only after the pending capture is resolved`() {
        val controller = controller()
        controller.startNewGame(2)
        val source = Position(2, 1)
        prepareCapture(controller, source, FoodCard.createDummyCards(FoodType.EARTHWORM).first())

        assertTrue(controller.captureCurrentPosition().success)
        assertNull(controller.playScreenUiState().captureOutcome?.animation)
        assertFalse(controller.resolveCaptureRoll().success)
        assertNull(controller.playScreenUiState().captureOutcome?.animation)
        assertTrue(controller.rollCaptureDice().success)
        assertNull(controller.playScreenUiState().captureOutcome?.animation)
        assertTrue(controller.resolveCaptureRoll().success)

        val event = controller.playScreenUiState().captureOutcome!!.animation!!
        assertEquals(CaptureOutcomeKind.CAPTURED, event.kind)
        assertEquals(source, event.source)
        assertEquals(FoodType.EARTHWORM, event.foodType)
        assertEquals(0, event.foodIndex)
        assertNull(event.destination)
        assertEquals(TurnPhase.DECIDE, controller.engine!!.currentPhase)
    }

    @Test
    fun `capturing a larva creates a success event without rolling dice`() {
        val controller = MoguraGameController(
            diceRoller = object : DiceRoller {
                override fun roll(): Int = error("A larva capture must not roll dice")
            },
            shuffler = FixedShuffler(),
        )
        controller.startNewGame(
            listOf(
                PlayerConfig("モグラ3", Position(0, 1), playerId = 3),
                PlayerConfig("モグラ1", Position(5, 1), playerId = 1),
            ),
        )
        prepareCapture(controller, Position(1, 1), FoodCard.createDummyCards(FoodType.BEETLE_LARVA).first())

        assertTrue(controller.captureCurrentPositionImmediately().success)

        val outcome = controller.playScreenUiState().captureOutcome!!
        val animation = outcome.animation!!
        assertEquals(CaptureOutcomeKind.CAPTURED, animation.kind)
        assertEquals(3, animation.playerId, "Use the selected pawn identity, not the turn index")
        assertEquals(FoodType.BEETLE_LARVA, animation.foodType)
        assertNull(outcome.diceRoll)
        assertNull(animation.destination)
    }

    @Test
    fun `event preserves selected index in a stack after capturing removes the target`() {
        val controller = controller()
        controller.startNewGame(2)
        val source = Position(2, 1)
        val lower = FoodCard.createDummyCards(FoodType.EARTHWORM).first()
        val target = FoodCard.createDummyCards(FoodType.BEETLE_LARVA).first()
        prepareCapture(controller, source, lower, target)
        assertTrue(controller.selectCaptureTarget(1).success)

        assertTrue(controller.captureCurrentPositionImmediately().success)

        val event = controller.playScreenUiState().captureOutcome!!.animation!!
        assertEquals(1, event.foodIndex)
        assertEquals(target.type, event.foodType)
        assertEquals(source, event.source)
        assertEquals(listOf(lower), controller.engine!!.foodsAt(source))
        assertEquals(target.type, controller.pendingFoodDecision!!.type)
    }

    @Test
    fun `escape event identifies the moved card and actual destination in an occupied stack`() {
        val controller = controller(roll = 2)
        controller.startNewGame(2)
        val source = Position(2, 1)
        val destination = Position(2, 2)
        val lower = FoodCard.createDummyCards(FoodType.BEETLE_LARVA).first()
        val target = FoodCard.createDummyCards(FoodType.EARTHWORM).first()
        prepareCapture(controller, source, lower, target)
        val existingDestinationFoods = controller.engine!!.foodsAt(destination)
        assertTrue(controller.selectCaptureTarget(1).success)

        assertTrue(controller.captureCurrentPositionImmediately().success)

        val result = controller.lastCaptureResult as CaptureResult.Escaped
        val event = controller.playScreenUiState().captureOutcome!!.animation!!
        assertEquals(CaptureOutcomeKind.ESCAPED, event.kind)
        assertEquals(result.to, event.destination)
        assertEquals(destination, event.destination)
        assertEquals(source, event.source)
        assertEquals(1, event.foodIndex)
        assertEquals(target.type, event.foodType)
        assertEquals(listOf(lower), controller.engine!!.foodsAt(source))
        assertEquals(existingDestinationFoods + target.copy(isFaceDown = false), controller.engine!!.foodsAt(destination))
    }

    @Test
    fun `blocked escape rolls produce a success animation at edges invalid cells and nests`() {
        val blockedEscapes = listOf(
            Position(1, 0) to EscapeDirection.TOP,
            Position(1, 2) to EscapeDirection.LEFT,
            Position(1, 1) to EscapeDirection.LEFT,
        )
        blockedEscapes.forEach { (source, direction) ->
            val controller = controller(roll = 6)
            controller.startNewGame(2)
            prepareCapture(controller, source, FoodCard(FoodType.EARTHWORM, mapOf(6 to direction)))

            assertTrue(controller.captureCurrentPositionImmediately().success)

            val outcome = controller.playScreenUiState().captureOutcome!!
            val animation = outcome.animation!!
            assertTrue(controller.lastCaptureResult is CaptureResult.Success)
            assertEquals(6, outcome.diceRoll)
            assertEquals(CaptureOutcomeKind.CAPTURED, animation.kind)
            assertEquals(source, animation.source)
            assertNull(animation.destination)
            assertEquals(FoodType.EARTHWORM, controller.pendingFoodDecision!!.type)
        }
    }

    @Test
    fun `repeated reads and rejected duplicate resolution do not create another event`() {
        val controller = controller()
        controller.startNewGame(2)
        prepareCapture(controller, Position(1, 1), FoodCard.createDummyCards(FoodType.BEETLE_LARVA).first())
        assertTrue(controller.captureCurrentPositionImmediately().success)
        val event = controller.playScreenUiState().captureOutcome!!.animation!!
        val boardAfterCapture = controller.engine!!.foodPositions

        repeat(3) {
            assertFalse(controller.resolveCaptureRoll().success)
            assertFalse(controller.captureCurrentPositionImmediately().success)
            assertEquals(event, controller.playScreenUiState().captureOutcome!!.animation)
        }
        assertEquals(boardAfterCapture, controller.engine!!.foodPositions)
        assertTrue(controller.eatPendingFood().success)
        assertNull(controller.playScreenUiState().captureOutcome)
    }

    @Test
    fun `animation ids remain increasing when the same controller starts another game`() {
        val controller = controller()
        var previousId = 0L
        repeat(3) {
            controller.startNewGame(2)
            assertNull(controller.playScreenUiState().captureOutcome)
            prepareCapture(controller, Position(1, 1), FoodCard.createDummyCards(FoodType.BEETLE_LARVA).first())
            assertTrue(controller.captureCurrentPositionImmediately().success)
            val event = controller.playScreenUiState().captureOutcome!!.animation!!
            assertTrue(event.id > previousId)
            previousId = event.id
        }
    }

    @Test
    fun `replenishing food after capture does not replace the event target`() {
        val controller = controller()
        controller.startNewGame(2)
        val engine = controller.engine!!
        Board.HOT_ZONE_POSITIONS.forEach { position ->
            val original = engine.foodsAt(position)
            clearFoodAt(engine, position)
            original.forEach { engine.placeFoodAt(position, it.copy(isFaceDown = false)) }
        }
        val source = Board.HOT_ZONE_POSITIONS.first()
        prepareCapture(controller, source, FoodCard.createDummyCards(FoodType.BEETLE_LARVA).first())

        assertTrue(controller.captureCurrentPositionImmediately().success)

        val event = controller.playScreenUiState().captureOutcome!!.animation!!
        assertEquals(source, event.source)
        assertEquals(FoodType.BEETLE_LARVA, event.foodType)
        assertEquals(0, event.foodIndex)
        assertTrue(engine.foodAt(source)!!.isFaceDown, "The logical board must still replenish immediately")
        assertEquals(FoodType.BEETLE_LARVA, controller.pendingFoodDecision!!.type)
    }

    private fun controller(roll: Int = 6) = MoguraGameController(
        diceRoller = FixedDiceRoller(listOf(roll)),
        shuffler = FixedShuffler(),
    )

    private fun prepareCapture(controller: MoguraGameController, source: Position, vararg foods: FoodCard) {
        val engine = controller.engine!!
        controller.currentPlayer!!.moveTo(source)
        clearFoodAt(engine, source)
        foods.forEach { engine.placeFoodAt(source, it) }
        engine.advancePhase()
        engine.advancePhase()
        assertEquals(TurnPhase.CAPTURE, engine.currentPhase)
    }

    private fun clearFoodAt(engine: GameEngine, source: Position) {
        while (engine.removeFoodAt(source) != null) {
            // Keep setup deterministic even when the position initially has a stack.
        }
    }
}
