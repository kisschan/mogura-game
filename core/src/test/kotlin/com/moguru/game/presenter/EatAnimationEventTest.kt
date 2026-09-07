package com.moguru.game.presenter

import com.moguru.game.engine.GameEngine
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Player
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EatAnimationEventTest {
    @Test
    fun `captured food returns its actual recovery event and ids keep increasing across games`() {
        val controller = controller()
        val first = captureAndEat(controller, FoodType.MOLE_CRICKET, damage = 5)
        val firstEvent = first.result.eatAnimation!!

        assertEquals(first.player.id, firstEvent.playerId)
        assertEquals(FoodType.MOLE_CRICKET, firstEvent.foodType)
        assertEquals(3, firstEvent.requestedRecovery)
        assertEquals(3, firstEvent.actualRecovery)
        assertEquals(8, firstEvent.healthBefore)
        assertEquals(11, firstEvent.healthAfter)

        val second = captureAndEat(controller, FoodType.BEETLE_LARVA, damage = 1)
        val secondEvent = second.result.eatAnimation!!
        assertTrue(secondEvent.id > firstEvent.id)
        assertEquals(12, secondEvent.healthBefore)
        assertEquals(13, secondEvent.healthAfter)
    }

    @Test
    fun `recovery is capped by max health and full health still emits a zero recovery event`() {
        val controller = controller()
        val capped = captureAndEat(controller, FoodType.FROG, damage = 1).result.eatAnimation!!

        assertEquals(5, capped.requestedRecovery)
        assertEquals(1, capped.actualRecovery)
        assertEquals(12, capped.healthBefore)
        assertEquals(Player.MAX_HEALTH, capped.healthAfter)

        val full = captureAndEat(controller, FoodType.FROG, damage = 0).result.eatAnimation!!
        assertTrue(full.id > capped.id)
        assertEquals(5, full.requestedRecovery)
        assertEquals(0, full.actualRecovery)
        assertEquals(Player.MAX_HEALTH, full.healthBefore)
        assertEquals(Player.MAX_HEALTH, full.healthAfter)
        assertTrue(eatAnimationFrame(full.actualRecovery, 0.4f).icons.isEmpty())
    }

    @Test
    fun `stolen pending food returns an event for the thief`() {
        val controller = controller()
        val (thief, victim) = advanceToRobberyDecision(controller)
        repeat(3) { thief.reduceHealth(isOnSurface = false) }
        val healthBefore = thief.health

        assertTrue(controller.robSelectedFood().success)
        val result = controller.eatPendingFood()
        val event = result.eatAnimation

        assertTrue(result.success)
        assertNotNull(event)
        assertEquals(thief.id, event!!.playerId)
        assertEquals(FoodType.EARTHWORM, event.foodType)
        assertEquals(2, event.requestedRecovery)
        assertEquals(2, event.actualRecovery)
        assertEquals(healthBefore, event.healthBefore)
        assertEquals(healthBefore + 2, event.healthAfter)
        assertTrue(victim.storedFoods.isEmpty())
        assertEquals(0, victim.score)
        assertEquals(0, thief.score)
        assertTrue(thief.storedFoods.isEmpty())
        assertFalse(thief.isCarrying)
        assertEquals(victim.nestPosition, thief.position)
    }

    @Test
    fun `own nest stored food returns the same event contract`() {
        val controller = controller()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        repeat(5) { player.reduceHealth(isOnSurface = false) }
        player.carryFood(FoodCard(FoodType.MOLE_CRICKET, emptyMap(), isFaceDown = false))
        player.storeFood()
        player.moveTo(Position(1, 1))
        engine.boardState.placeTile(
            Position(1, 1),
            HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip(),
        )
        engine.advancePhase()

        assertTrue(controller.moveTo(player.nestPosition).success)
        assertTrue(controller.skipPhase().success)
        val result = controller.eatPendingFood()
        val event = result.eatAnimation

        assertTrue(result.success)
        assertNotNull(event)
        assertEquals(player.id, event!!.playerId)
        assertEquals(FoodType.MOLE_CRICKET, event.foodType)
        assertEquals(3, event.requestedRecovery)
        assertEquals(3, event.actualRecovery)
        assertEquals(8, event.healthBefore)
        assertEquals(11, event.healthAfter)
        assertTrue(player.storedFoods.isEmpty())
        assertEquals(TurnPhase.END, engine.currentPhase)
    }

    private data class EatResult(val result: GameActionResult, val player: Player)

    private fun captureAndEat(
        controller: MoguraGameController,
        foodType: FoodType,
        damage: Int,
    ): EatResult {
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        repeat(damage) { player.reduceHealth(isOnSurface = false) }
        val position = Position(1, 1)
        player.moveTo(position)
        while (engine.removeFoodAt(position) != null) {
            // Keep the setup independent from deck order and board initialization.
        }
        engine.placeFoodAt(position, FoodCard(foodType, emptyMap(), isFaceDown = true))
        engine.advancePhase()
        engine.advancePhase()
        assertTrue(controller.captureCurrentPositionImmediately().success)
        return EatResult(controller.eatPendingFood(), player)
    }

    private fun advanceToRobberyDecision(controller: MoguraGameController): Pair<Player, Player> {
        controller.startNewGame(2)
        val engine = controller.engine!!
        val thief = engine.players[0]
        val victim = engine.players[1]
        victim.carryFood(FoodCard(FoodType.EARTHWORM, emptyMap(), isFaceDown = false))
        victim.storeFood()
        victim.moveTo(Position(1, 1))
        connectLeftNestToRightNest(engine)

        engine.advancePhase()
        assertTrue(controller.moveTo(victim.nestPosition).success)
        assertTrue(controller.skipPhase().success)
        assertEquals(TurnPhase.END, engine.currentPhase)
        assertTrue(controller.finishTurn().success)

        engine.advancePhase()
        assertTrue(controller.finishTurn().success)
        assertEquals(0, engine.currentPlayerIndex)

        engine.advancePhase()
        assertTrue(controller.skipPhase().success)
        assertTrue(controller.skipPhase().success)
        assertEquals(TurnPhase.DECIDE, engine.currentPhase)
        return thief to victim
    }

    private fun connectLeftNestToRightNest(engine: GameEngine) {
        for (col in 1..4) {
            engine.boardState.placeTile(
                Position(col, 1),
                HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip(),
            )
        }
    }

    private fun controller() = MoguraGameController(
        diceRoller = FixedDiceRoller(listOf(6)),
        shuffler = FixedShuffler(),
    )
}
