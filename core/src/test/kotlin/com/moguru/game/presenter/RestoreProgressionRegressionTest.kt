package com.moguru.game.presenter

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.persistence.GameSnapshot
import com.moguru.game.util.DiceRoller
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RestoreProgressionRegressionTest {
    private fun captureReady(): GameSnapshot {
        val initial = MoguraGameController(FixedDiceRoller(listOf(1)), FixedShuffler())
            .apply { startNewGame(2) }.exportSnapshot()
        val food = initial.engine.foodStock.first { it.type == FoodType.EARTHWORM }
        val position = Position(2, 2)
        return initial.copy(
            engine = initial.engine.copy(
                currentPhase = TurnPhase.CAPTURE,
                players = initial.engine.players.mapIndexed { index, player ->
                    if (index == 0) player.copy(position = position) else player
                },
                tileDiscardPile = initial.engine.tileDiscardPile + listOfNotNull(initial.pendingDigDrawnTile),
                foods = mapOf(position to listOf(food)),
                foodStock = initial.engine.foodStock.toMutableList().apply { remove(food) } +
                    initial.engine.foods.values.flatten(),
            ),
            pendingDigDrawnTile = null,
        )
    }

    private fun resumeWithoutReroll(snapshot: GameSnapshot) = MoguraGameController.fromSnapshot(
        snapshot,
        object : DiceRoller { override fun roll(): Int = error("A saved roll must not be rerolled") },
        FixedShuffler(),
    )

    @Test
    fun `restoring an applied escape settles the turn once without replaying animation`() {
        val live = MoguraGameController.fromSnapshot(captureReady(), FixedDiceRoller(listOf(1)), FixedShuffler())
        assertTrue(live.captureCurrentPositionImmediately().success)
        val applied = live.exportSnapshot()
        assertEquals(TurnPhase.END, applied.engine.currentPhase)
        assertEquals(CaptureOutcomeKind.ESCAPED, applied.captureOutcome?.kind)

        val restored = resumeWithoutReroll(applied)
        restored.settleAfterRestore()

        assertEquals(1, restored.engine!!.currentPlayerIndex)
        assertEquals(12, restored.engine!!.players[0].health)
        assertEquals(TurnPhase.DIG, restored.engine!!.currentPhase)
        assertNull(restored.playScreenUiState().captureOutcome)
        assertEquals(1, restored.lastDiceRoll)
        assertEquals(applied.engine.foods, restored.exportSnapshot().engine.foods)
        assertEquals(applied.captureAnimationId, restored.exportSnapshot().captureAnimationId)
        assertTrue(restored.logs.take(applied.logs.size) == applied.logs)
        val settled = restored.exportSnapshot()
        restored.settleAfterRestore()
        assertEquals(settled, restored.exportSnapshot())
        assertEquals(settled, resumeWithoutReroll(settled).exportSnapshot())
    }

    @Test
    fun `restoring a confirmed escape roll resolves it and settles the deferred turn`() {
        val live = MoguraGameController.fromSnapshot(captureReady(), FixedDiceRoller(listOf(1)), FixedShuffler())
        assertTrue(live.captureCurrentPosition().success)
        assertTrue(live.rollCaptureDice().success)
        val confirmed = live.exportSnapshot()

        assertTrue(live.resolveCaptureRoll().success)
        assertTrue(live.finishTurn().success)
        val expected = live.exportSnapshot()
        val restored = resumeWithoutReroll(confirmed)
        restored.settleAfterRestore()

        assertEquals(expected, restored.exportSnapshot())
        assertNull(restored.pendingCaptureRoll)
        assertNull(restored.playScreenUiState().captureOutcome)
        restored.settleAfterRestore()
        assertEquals(expected, restored.exportSnapshot())
    }

    @Test
    fun `restoring an unconfirmed roll preserves the player choice and all state`() {
        val live = MoguraGameController.fromSnapshot(captureReady(), FixedDiceRoller(listOf(1)), FixedShuffler())
        assertTrue(live.captureCurrentPosition().success)
        val unconfirmed = live.exportSnapshot()
        val restored = resumeWithoutReroll(unconfirmed)

        restored.settleAfterRestore()

        assertEquals(unconfirmed, restored.exportSnapshot())
        assertEquals(13, restored.currentPlayer!!.health)
        assertNull(restored.pendingCaptureRoll!!.roll)
    }
}
