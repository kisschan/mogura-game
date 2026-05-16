package com.moguru.game.gui

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Board
import com.moguru.game.model.Direction
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
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

        assertFalse(controller.canAdvanceFromDigWithoutTargets())
        assertFalse(result.success)
        assertEquals(TurnPhase.DIG, engine.currentPhase)
    }

    @Test
    fun `dig phase advances to move when no face down adjacent tile exists`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        controller.digTargets().forEach { position ->
            val tile = engine.boardState.getTile(position)!!
            engine.boardState.placeTile(position, tile.flip())
        }

        val result = controller.skipPhase()

        assertTrue(controller.digTargets().isEmpty())
        assertTrue(result.success)
        assertEquals(TurnPhase.MOVE, engine.currentPhase)
    }

    @Test
    fun `pending revealed dig tile still cannot be skipped`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val target = controller.digTargets().first()

        val revealResult = controller.revealDigTile(target)
        val skipResult = controller.skipPhase()

        assertTrue(revealResult.success)
        assertFalse(controller.canAdvanceFromDigWithoutTargets())
        assertFalse(skipResult.success)
        assertEquals(TurnPhase.DIG, engine.currentPhase)
        assertEquals(target, controller.pendingDigPlacement?.position)
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
    fun `dig first reveals tile without advancing so player can rotate after seeing it`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val target = Position(1, 1)

        val result = controller.revealDigTile(target)

        assertTrue(result.success)
        assertEquals(TurnPhase.DIG, engine.currentPhase)
        assertEquals(target, controller.pendingDigPlacement?.position)
        assertFalse(engine.boardState.getTile(target)!!.isFaceDown)
        assertTrue(Direction.TOP in engine.boardState.getTile(target)!!.openSides)
        assertTrue(Direction.BOTTOM in engine.boardState.getTile(target)!!.openSides)
    }

    @Test
    fun `revealed dig tile can be rotated before confirming placement`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val target = Position(1, 1)
        controller.revealDigTile(target)

        val rotateResult = controller.setPendingDigRotation(Rotation.DEG_90)
        val confirmResult = controller.confirmPendingDig()

        assertTrue(rotateResult.success)
        assertTrue(confirmResult.success)
        assertEquals(TurnPhase.MOVE, engine.currentPhase)
        assertNull(controller.pendingDigPlacement)
        val tile = engine.boardState.getTile(target)!!
        assertFalse(tile.isFaceDown)
        assertTrue(Direction.LEFT in tile.openSides)
        assertTrue(Direction.RIGHT in tile.openSides)
    }

    @Test
    fun `dig click confirms only the already revealed tile`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val target = Position(1, 1)

        val reveal = controller.digAt(target, Rotation.DEG_0)
        val wrongTarget = controller.digAt(Position(2, 1), Rotation.DEG_90)
        val confirm = controller.digAt(target, Rotation.DEG_90)

        assertTrue(reveal.success)
        assertFalse(wrongTarget.success)
        assertTrue(confirm.success)
        assertEquals(TurnPhase.MOVE, engine.currentPhase)
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
