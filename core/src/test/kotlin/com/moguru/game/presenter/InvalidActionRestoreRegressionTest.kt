package com.moguru.game.presenter

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.persistence.RobberyVisitSnapshot
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InvalidActionRestoreRegressionTest {
    private fun controller() = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())

    @Test
    fun `robbery target selection outside the decision phase rejects without mutating state`() {
        val initial = controller().apply { startNewGame(3) }.exportSnapshot()
        val victimNest = initial.engine.players[1].nestPosition
        val food = initial.engine.foodStock.first { it.type == FoodType.CENTIPEDE }
        val eligible = initial.copy(
            engine = initial.engine.copy(
                currentPhase = TurnPhase.DECIDE,
                tileDiscardPile = initial.engine.tileDiscardPile + listOfNotNull(initial.pendingDigDrawnTile),
                foodStock = initial.engine.foodStock.toMutableList().apply { remove(food) },
                players = initial.engine.players.mapIndexed { index, player ->
                    when (index) {
                        0 -> player.copy(position = victimNest)
                        1 -> player.copy(position = Position(4, 1), storedFoods = listOf(food.copy(isFaceDown = false)))
                        else -> player
                    }
                },
            ),
            pendingDigDrawnTile = null,
            robberyVisits = mapOf(0 to RobberyVisitSnapshot(victimNest, true)),
        )
        for (phase in listOf(TurnPhase.MOVE, TurnPhase.CAPTURE, TurnPhase.END)) {
            val snapshot = eligible.copy(engine = eligible.engine.copy(currentPhase = phase))
            val restored = MoguraGameController.fromSnapshot(snapshot)

            assertFalse(restored.selectRobberyTarget(0).success, "phase=$phase")
            assertEquals(snapshot, restored.exportSnapshot(), "phase=$phase")
        }
        val restored = MoguraGameController.fromSnapshot(eligible)
        assertFalse(restored.selectRobberyTarget(-1).success)
        assertFalse(restored.selectRobberyTarget(1).success)
        assertEquals(eligible, restored.exportSnapshot())
        assertTrue(restored.selectRobberyTarget(0).success)
        assertTrue(restored.robSelectedFood().success)
        val afterRobbery = restored.exportSnapshot()
        assertFalse(restored.selectRobberyTarget(0).success)
        assertFalse(restored.robSelectedFood().success)
        assertEquals(afterRobbery, restored.exportSnapshot())
    }

    @Test
    fun `restoration rejects a pending dig preview relocated to a nonadjacent cell`() {
        val live = controller().apply { startNewGame(2) }
        assertTrue(live.digAt(live.digTargets().first(), Rotation.DEG_0).success)
        val valid = live.exportSnapshot()
        assertDoesNotThrow { MoguraGameController.fromSnapshot(valid) }
        val original = valid.pendingDigPlacement!!.position
        val distant = Position(4, 3)
        val corrupt = valid.copy(
            engine = valid.engine.copy(tiles = valid.engine.tiles.toMutableMap().apply {
                this[original] = valid.engine.tiles.getValue(distant)
                this[distant] = valid.engine.tiles.getValue(original)
            }),
            pendingDigPlacement = valid.pendingDigPlacement.copy(position = distant),
        )

        assertThrows(IllegalArgumentException::class.java) { MoguraGameController.fromSnapshot(corrupt) }
        assertEquals(valid, live.exportSnapshot())
    }

    @Test
    fun `restoration rejects a pending dig preview occupied by another active player`() {
        val live = controller().apply { startNewGame(2) }
        assertTrue(live.digAt(live.digTargets().first(), Rotation.DEG_0).success)
        val valid = live.exportSnapshot()
        val target = valid.pendingDigPlacement!!.position
        val corrupt = valid.copy(engine = valid.engine.copy(players = valid.engine.players.mapIndexed { index, player ->
            if (index == 1) player.copy(position = target) else player
        }))

        assertThrows(IllegalArgumentException::class.java) { MoguraGameController.fromSnapshot(corrupt) }
        assertEquals(valid, live.exportSnapshot())
    }
}
