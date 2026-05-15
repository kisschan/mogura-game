package com.moguru.game.gui

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Board
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MoguraGameControllerTest {

    @Test
    fun `new game creates requested players`() {
        val controller = testController()

        controller.startNewGame(3)

        val engine = controller.engine!!
        assertEquals(3, engine.players.size)
        assertEquals(listOf("モグオ", "モグタ", "モグミ"), engine.players.map { it.name })
        assertEquals(TurnPhase.DIG, engine.currentPhase)
    }

    @Test
    fun `capture success enters decide phase without auto carrying`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val food = FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true)
        engine.placeFoodAt(player.position, food)
        engine.advancePhase()
        engine.advancePhase()

        val result = controller.captureCurrentPosition()

        assertTrue(result.success)
        assertNull(engine.foodPositions[player.position])
        assertFalse(player.isCarrying)
        assertEquals(FoodType.BEETLE_LARVA, controller.pendingFoodDecision?.type)
        assertEquals(TurnPhase.DECIDE, engine.currentPhase)
    }

    @Test
    fun `eat captured food heals without scoring`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        repeat(5) { player.reduceHealth(isOnSurface = false) }
        engine.placeFoodAt(player.position, FoodCard(FoodType.MOLE_CRICKET, emptyMap(), isFaceDown = true))
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPosition()

        val result = controller.eatPendingFood()

        assertTrue(result.success)
        assertEquals(11, player.health)
        assertFalse(player.isCarrying)
        assertEquals(0, player.score)
        assertEquals(TurnPhase.END, engine.currentPhase)
    }

    @Test
    fun `carry captured food starts carrying and ends decision`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.placeFoodAt(player.position, FoodCard(FoodType.EARTHWORM, emptyMap(), isFaceDown = true))
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPosition()

        val result = controller.carryPendingFood()

        assertTrue(result.success)
        assertEquals(FoodType.EARTHWORM, player.carriedFood?.type)
        assertNull(controller.pendingFoodDecision)
        assertEquals(TurnPhase.END, engine.currentPhase)
    }

    @Test
    fun `food carried to own nest is stored without healing when homecoming resolves`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        repeat(3) { player.reduceHealth(isOnSurface = false) }
        player.carryFood(FoodCard(FoodType.MOLE_CRICKET, emptyMap(), isFaceDown = false))

        controller.resolveCurrentPlayerPositionEffects()

        assertFalse(player.isCarrying)
        assertEquals(1, player.storedFoods.size)
        assertEquals(10, player.health)
        assertEquals(2, player.score)
        assertEquals(0, engine.currentPlayerIndex)
        assertEquals(TurnPhase.DIG, engine.currentPhase)
    }

    @Test
    fun `dig phase cannot be skipped`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!

        val result = controller.skipPhase()

        assertFalse(result.success)
        assertEquals(TurnPhase.DIG, engine.currentPhase)
    }

    @Test
    fun `turn cannot end before mandatory dig`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!

        val result = controller.finishTurn()

        assertFalse(result.success)
        assertEquals(TurnPhase.DIG, engine.currentPhase)
        assertEquals(0, engine.currentPlayerIndex)
    }

    @Test
    fun `pending captured food decision cannot be skipped by ending turn`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.placeFoodAt(player.position, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true))
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPosition()

        val result = controller.finishTurn()

        assertFalse(result.success)
        assertEquals(TurnPhase.DECIDE, engine.currentPhase)
        assertEquals(FoodType.BEETLE_LARVA, controller.pendingFoodDecision?.type)
    }

    @Test
    fun `food replenishes immediately when no face down food remains in hot zone`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val target = Board.HOT_ZONE_POSITIONS.first()
        player.moveTo(target)

        Board.HOT_ZONE_POSITIONS.forEach { position ->
            val food = engine.foodPositions[position]
            if (food != null) {
                engine.placeFoodAt(position, food.copy(isFaceDown = false))
            }
        }
        engine.placeFoodAt(target, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true))
        engine.advancePhase()
        engine.advancePhase()

        val result = controller.captureCurrentPosition()

        assertTrue(result.success)
        Board.HOT_ZONE_POSITIONS.forEach { position ->
            val food = engine.foodPositions[position]
            assertTrue(food != null, "ホットゾーン $position にエサが補充されるべき")
            assertTrue(food!!.isFaceDown, "補充後のエサは裏向きであるべき")
        }
    }

    @Test
    fun `robbed food enters decision without auto carrying`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val thief = engine.players[0]
        val victim = engine.players[1]
        victim.carryFood(FoodCard(FoodType.EARTHWORM, emptyMap(), isFaceDown = false))
        victim.storeFood()
        victim.moveTo(Position(1, 1))
        thief.moveTo(victim.nestPosition)

        val needsDecision = controller.resolveCurrentPlayerPositionEffects()

        assertTrue(needsDecision)
        assertFalse(thief.isCarrying)
        assertEquals(FoodType.EARTHWORM, controller.pendingFoodDecision?.type)
        assertEquals(0, victim.score)
    }

    @Test
    fun `finish turn advances to next active player`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        engine.advancePhase()

        val result = controller.finishTurn()

        assertTrue(result.success)
        assertEquals(1, engine.currentPlayerIndex)
        assertEquals(TurnPhase.DIG, engine.currentPhase)
    }

    private fun testController(): MoguraGameController =
        MoguraGameController(
            diceRoller = FixedDiceRoller(listOf(6)),
            shuffler = FixedShuffler(),
        )
}
