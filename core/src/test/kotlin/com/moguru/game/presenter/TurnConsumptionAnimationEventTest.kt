package com.moguru.game.presenter

import com.moguru.game.engine.GameState
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Position
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TurnConsumptionAnimationEventTest {
    @Test
    fun `normal turn returns underground consumption captured before player switch`() {
        val controller = controller()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.advancePhase()

        val result = controller.finishTurn()
        val event = result.turnConsumptionAnimation!!

        assertTrue(result.success)
        assertEquals(player.id, event.playerId)
        assertEquals(1, event.requestedConsumption)
        assertEquals(1, event.actualConsumption)
        assertEquals(13, event.healthBefore)
        assertEquals(12, event.healthAfter)
        assertFalse(event.isOnSurface)
        assertEquals(12, player.health)
        assertEquals(1, engine.currentPlayerIndex)
        assertEquals(TurnPhase.DIG, engine.currentPhase)

        // The result remains about the player whose turn ended, even after the switch.
        assertEquals(player.id, result.turnConsumptionAnimation.playerId)
        assertEquals(12, result.turnConsumptionAnimation.healthAfter)
    }

    @Test
    fun `surface consumption reports requested two but actual one at one health on victory`() {
        val controller = controller()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        player.moveTo(Position(0, 0))
        repeat(12) { player.reduceHealth(isOnSurface = false) }
        engine.advancePhase()

        val result = controller.finishTurn()
        val event = result.turnConsumptionAnimation!!

        assertTrue(result.success)
        assertEquals("ゲーム終了です。", result.message)
        assertEquals(GameState.FINISHED, engine.gameState)
        assertEquals(player.id, event.playerId)
        assertEquals(2, event.requestedConsumption)
        assertEquals(1, event.actualConsumption)
        assertEquals(1, event.healthBefore)
        assertEquals(0, event.healthAfter)
        assertTrue(event.isOnSurface)
    }

    @Test
    fun `draw result still carries the final players consumption`() {
        val controller = controller()
        controller.startNewGame(3)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        repeat(12) { player.reduceHealth(isOnSurface = false) }
        engine.players.drop(1).forEach { other ->
            repeat(13) { other.reduceHealth(isOnSurface = false) }
        }
        engine.advancePhase()

        val result = controller.finishTurn()
        val event = result.turnConsumptionAnimation!!

        assertTrue(result.success)
        assertEquals("ゲーム終了です。", result.message)
        assertEquals(GameState.FINISHED, engine.gameState)
        assertTrue(engine.players.all { it.isEliminated })
        assertEquals(player.id, event.playerId)
        assertEquals(1, event.requestedConsumption)
        assertEquals(1, event.actualConsumption)
        assertEquals(1, event.healthBefore)
        assertEquals(0, event.healthAfter)
        assertFalse(event.isOnSurface)
    }

    @Test
    fun `failed finish has no event and does not consume an animation id`() {
        val controller = controller()
        controller.startNewGame(2)
        val player = controller.currentPlayer!!

        val rejected = controller.finishTurn()

        assertFalse(rejected.success)
        assertNull(rejected.turnConsumptionAnimation)
        assertEquals(13, player.health)

        controller.engine!!.advancePhase()
        val firstSuccessfulEvent = controller.finishTurn().turnConsumptionAnimation!!
        controller.startNewGame(2)
        controller.engine!!.advancePhase()
        val secondSuccessfulEvent = controller.finishTurn().turnConsumptionAnimation!!

        assertEquals(1L, firstSuccessfulEvent.id)
        assertTrue(secondSuccessfulEvent.id > firstSuccessfulEvent.id)
    }

    @Test
    fun `auto advance stops after one consumption event and resumes with the next player`() {
        val controller = controller()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val firstPlayer = engine.players[0]
        val secondPlayer = engine.players[1]
        engine.boardState.clear()

        val firstResult = controller.autoAdvanceWhileNoChoice()
        val firstEvent = firstResult?.turnConsumptionAnimation!!

        assertEquals(firstPlayer.id, firstEvent.playerId)
        assertEquals(12, firstPlayer.health)
        assertEquals(13, secondPlayer.health)
        assertEquals(1, engine.currentPlayerIndex)
        assertEquals(TurnPhase.DIG, engine.currentPhase)

        val secondResult = controller.autoAdvanceWhileNoChoice()
        val secondEvent = secondResult?.turnConsumptionAnimation!!

        assertEquals(secondPlayer.id, secondEvent.playerId)
        assertTrue(secondEvent.id > firstEvent.id)
        assertEquals(12, secondPlayer.health)
        assertEquals(0, engine.currentPlayerIndex)
        assertEquals(TurnPhase.DIG, engine.currentPhase)
    }

    private fun controller() = MoguraGameController(
        diceRoller = FixedDiceRoller(listOf(6)),
        shuffler = FixedShuffler(),
    )
}
