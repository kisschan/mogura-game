package com.moguru.game.cpu

import com.moguru.game.engine.GameState
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.*
import com.moguru.game.persistence.validate
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class StandardCpuStrategyTest {
    private val strategy = StandardCpuStrategy()

    @Test
    fun `observing and choosing do not change the game or consume randomness`() {
        var rolls = 0
        var shuffles = 0
        val controller = MoguraGameController(
            object : DiceRoller { override fun roll(): Int { rolls++; return 6 } },
            object : Shuffler { override fun <T> shuffle(list: List<T>): List<T> { shuffles++; return list } },
        )
        controller.startNewGame(2)
        val before = controller.exportSnapshot()
        val initialShuffles = shuffles

        val observed = controller.cpuObservation()
        repeat(5) { assertEquals(strategy.choose(observed), strategy.choose(controller.cpuObservation())) }

        assertEquals(before, controller.exportSnapshot())
        assertEquals(0, rolls)
        assertEquals(initialShuffles, shuffles)
        assertTrue(observed.tiles.values.all { it.shape == null && it.openSides.isEmpty() })
        assertTrue(observed.foods.values.flatten().all { it.type == null })
        assertEquals(controller.pendingDigDrawnTile!!.shape, observed.drawnTile)
    }

    @Test
    fun `hidden tiles food and deck order cannot influence the observation or decision`() {
        val original = controller()
        original.startNewGame(3)
        val state = original.exportSnapshot()
        val tiles = state.engine.tiles.values.toList().reversed().iterator()
        val firstPosition = state.engine.foods.keys.first()
        val originalFood = state.engine.foods.getValue(firstPosition).first()
        val hiddenStockFood = state.engine.foodStock.last()
        val changed = state.copy(engine = state.engine.copy(
            tiles = state.engine.tiles.mapValues { tiles.next() },
            foods = state.engine.foods + (firstPosition to listOf(hiddenStockFood)),
            foodStock = (state.engine.foodStock.dropLast(1) + originalFood).reversed(),
            tileDrawPile = state.engine.tileDrawPile.reversed(),
        ))
        val other = MoguraGameController.fromSnapshot(changed)

        assertEquals(original.cpuObservation(), other.cpuObservation())
        for (difficulty in CpuDifficulty.entries) {
            val policy = difficulty.createStrategy()
            assertEquals(policy.choose(original.cpuObservation()), policy.choose(other.cpuObservation()))
        }
    }

    @Test
    fun `dig preparation is followed by confirmation and identical preparation is rejected`() {
        val controller = controller()
        controller.startNewGame(2)
        assertTrue(controller.applyCpuAction(strategy.choose(controller.cpuObservation())!!).success)
        assertNotNull(controller.pendingDigPlacement)

        val first = strategy.choose(controller.cpuObservation())!!
        if (first is CpuAction.PrepareDig) {
            assertTrue(controller.applyCpuAction(first).success)
            assertEquals(CpuAction.ConfirmDig, strategy.choose(controller.cpuObservation()))
        } else {
            assertEquals(CpuAction.ConfirmDig, first)
        }
        val same = CpuAction.PrepareDig(controller.pendingDigTileChoice!!, controller.pendingDigRotation!!)
        val checkpoint = controller.exportSnapshot()
        assertFalse(controller.applyCpuAction(same).success)
        assertEquals(checkpoint, controller.exportSnapshot())
        assertTrue(controller.applyCpuAction(CpuAction.ConfirmDig).success)
        assertEquals(TurnPhase.MOVE, controller.engine!!.currentPhase)
    }

    @Test
    fun `pending capture leaves dice and resolution to the presentation runner`() {
        val controller = captureReady(FoodType.EARTHWORM, health = 8)
        val position = controller.currentPlayer!!.position
        assertNull(controller.cpuObservation().foods.getValue(position).single().type)
        assertTrue(controller.applyCpuAction(CpuAction.Capture(0)).success)
        assertEquals(FoodType.EARTHWORM, controller.cpuObservation().foods.getValue(position).single().type)
        assertNull(strategy.choose(controller.cpuObservation()))
        assertFalse(controller.applyCpuAction(CpuAction.Skip).success)
        assertTrue(controller.rollCaptureDice().success)
        assertNull(strategy.choose(controller.cpuObservation()))
    }

    @Test
    fun `CPU application preserves animation events and never automatically consumes the turn`() {
        val controller = captured(FoodType.EARTHWORM, health = 4)
        val player = controller.currentPlayer!!

        val eaten = controller.applyCpuAction(CpuAction.Eat)

        assertTrue(eaten.success)
        assertNotNull(eaten.eatAnimation)
        assertNull(eaten.turnConsumptionAnimation)
        assertEquals(player, controller.currentPlayer)
        assertEquals(6, player.health)
        assertEquals(TurnPhase.END, controller.engine!!.currentPhase)
        val finished = controller.applyCpuAction(CpuAction.FinishTurn)
        assertNotNull(finished.turnConsumptionAnimation)
        assertEquals(5, player.health)
        assertNotEquals(player, controller.currentPlayer)
    }

    @Test
    fun `low health eats a catch while healthy players carry it`() {
        for (health in listOf(1, 4, 5, 13)) {
            val controller = captured(FoodType.MOLE_CRICKET, health)
            assertEquals(if (health <= 4) CpuAction.Eat else CpuAction.Carry,
                strategy.choose(controller.cpuObservation()), "health=$health")
        }
    }

    @Test
    fun `a winning return overrides low health only if both turn costs leave the player alive`() {
        for (health in listOf(2, 3)) {
            val controller = captured(FoodType.CENTIPEDE, health)
            val engine = controller.engine!!
            val player = controller.currentPlayer!!
            player.carryFood(card(FoodType.BEETLE_LARVA))
            player.storeFood()
            engine.boardState.placeTile(Position(1, 1), HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip())

            assertEquals(if (health == 3) CpuAction.Carry else CpuAction.Eat,
                strategy.choose(controller.cpuObservation()))
        }
    }

    @Test
    fun `one health winning robbery eats instead of dying with a winning score`() {
        val controller = robberyReady()
        val thief = controller.currentPlayer!!
        thief.carryFood(card(FoodType.CENTIPEDE))
        thief.storeFood()
        setHealth(thief, 1)
        assertTrue(controller.applyCpuAction(CpuAction.Rob(0)).success)

        assertEquals(CpuAction.Eat, strategy.choose(controller.cpuObservation()))
        assertTrue(controller.applyCpuAction(CpuAction.Eat).success)
        assertTrue(controller.applyCpuAction(CpuAction.FinishTurn).success)
        assertFalse(thief.isEliminated)
        assertEquals(3, thief.score)
    }

    @Test
    fun `a surviving winning player keeps stored points even when eating is still offered`() {
        val source = controller()
        source.startNewGame(2)
        val initial = source.exportSnapshot()
        val storedFoods = initial.engine.foods.values.flatten().map { it.copy(isFaceDown = false) }
        val ready = initial.copy(
            engine = initial.engine.copy(
                currentPhase = TurnPhase.DECIDE,
                players = initial.engine.players.mapIndexed { index, player ->
                    if (index == 0) player.copy(health = 2, storedFoods = storedFoods) else player
                },
                foods = emptyMap(),
                tileDiscardPile = initial.engine.tileDiscardPile + listOfNotNull(initial.pendingDigDrawnTile),
            ),
            pendingDigDrawnTile = null,
            ownNestEatEligiblePlayers = setOf(0),
        )
        val controller = MoguraGameController.fromSnapshot(ready, FixedDiceRoller(listOf(6)), FixedShuffler())
        assertTrue(CpuAction.Eat in controller.cpuObservation().legalActions)

        assertEquals(CpuAction.Skip, strategy.choose(controller.cpuObservation()))
        assertTrue(controller.applyCpuAction(CpuAction.Skip).success)
        val winner = controller.currentPlayer!!
        assertTrue(controller.applyCpuAction(CpuAction.FinishTurn).success)
        assertEquals(1, winner.health)
        assertEquals(4, winner.score)
        assertEquals(winner, controller.engine!!.checkWinCondition())
    }

    @Test
    fun `carrying player returns home and avoids a lethal surface stop`() {
        val controller = controller()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        player.moveTo(Position(1, 1))
        player.carryFood(card(FoodType.EARTHWORM))
        setHealth(player, 2)
        engine.boardState.placeTile(Position(1, 1), HoleTile(TileShape.CROSS).flip())
        engine.advancePhase()

        assertEquals(CpuAction.Move(player.nestPosition), strategy.choose(controller.cpuObservation()))
    }

    @Test
    fun `blocked homecoming selects a foothold from which the entrance can be red dug`() {
        val controller = controller()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val entrance = Position(1, 1)
        val foothold = Position(2, 1)
        player.moveTo(entrance)
        player.carryFood(card(FoodType.MOLE_CRICKET))
        engine.boardState.placeTile(entrance,
            HoleTile(TileShape.L_SHAPE).rotate(Rotation.DEG_180).flip()) // top + right; closed toward home
        engine.boardState.placeTile(foothold,
            HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip())
        engine.advancePhase()

        assertEquals(CpuAction.Move(foothold), strategy.choose(controller.cpuObservation()))
    }

    @Test
    fun `only legal actions are applied and finished games have no CPU command`() {
        val controller = controller()
        assertNull(strategy.choose(controller.cpuObservation()))
        controller.startNewGame(2)
        val before = controller.exportSnapshot()
        assertFalse(controller.applyCpuAction(CpuAction.Move(Position(4, 4))).success)
        assertFalse(controller.applyCpuAction(CpuAction.FinishTurn).success)
        assertFalse(controller.applyCpuAction(CpuAction.RevealDig(Position(0, 0))).success)
        assertEquals(before, controller.exportSnapshot())
    }

    @Test
    fun `two to four CPUs finish seeded games with legal progressing resumable actions`() {
        for (count in 2..4) for (seed in 0..11) {
            val random = Random(seed)
            val controller = MoguraGameController(
                object : DiceRoller { override fun roll(): Int = random.nextInt(1, 7) },
                object : Shuffler { override fun <T> shuffle(list: List<T>): List<T> = list.shuffled(random) },
            )
            controller.startNewGame(count)
            var steps = 0
            while (controller.engine!!.gameState == GameState.PLAYING && steps < 2000) {
                val before = controller.exportSnapshot()
                before.validate()
                val result = when {
                    controller.pendingCaptureRoll != null -> {
                        val pending = controller.pendingCaptureRoll!!
                        if (pending.roll == null && pending.food.escapeMap.isNotEmpty()) controller.rollCaptureDice()
                        else controller.resolveCaptureRoll()
                    }
                    else -> {
                        val observation = controller.cpuObservation()
                        if (observation.phase == TurnPhase.MOVE) {
                            assertEquals(controller.moveTargets(),
                                PublicBoard(observation).reachableIncludingCurrent().toSet() - controller.currentPlayer!!.position)
                        }
                        val action = strategy.choose(observation)
                        assertNotNull(action, "no action: count=$count seed=$seed step=$steps phase=${observation.phase}")
                        assertTrue(action in observation.legalActions)
                        val restored = MoguraGameController.fromSnapshot(before)
                        assertEquals(action, strategy.choose(restored.cpuObservation()))
                        controller.applyCpuAction(action!!)
                    }
                }
                assertTrue(result.success, "count=$count seed=$seed step=$steps: ${result.message}")
                assertNotEquals(before, controller.exportSnapshot(), "no progress: count=$count seed=$seed step=$steps")
                steps++
            }
            assertEquals(GameState.FINISHED, controller.engine!!.gameState,
                "count=$count seed=$seed exceeded $steps steps: ${controller.logs.takeLast(12)}")
            controller.exportSnapshot().validate()
            assertNull(strategy.choose(controller.cpuObservation()))
        }
    }

    private fun controller() = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
    private fun card(type: FoodType) = FoodCard.createDummyCards(type).first().copy(isFaceDown = false)
    private fun setHealth(player: Player, health: Int) { repeat(player.health - health) { player.reduceHealth(false) } }

    private fun captureReady(type: FoodType, health: Int): MoguraGameController = controller().apply {
        startNewGame(2)
        val current = engine!!
        currentPlayer!!.moveTo(Position(1, 1))
        setHealth(currentPlayer!!, health)
        current.placeFoodAt(Position(1, 1), card(type).copy(isFaceDown = true))
        current.advancePhase()
        current.advancePhase()
    }

    private fun captured(type: FoodType, health: Int): MoguraGameController = captureReady(type, health).apply {
        check(captureCurrentPositionImmediately().success)
    }

    private fun robberyReady(): MoguraGameController = controller().apply {
        startNewGame(2)
        val current = engine!!
        val victim = current.players[1]
        victim.carryFood(card(FoodType.BEETLE_LARVA))
        victim.storeFood()
        victim.moveTo(Position(1, 1))
        for (col in 1..4) current.boardState.placeTile(Position(col, 1),
            HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip())
        current.advancePhase()
        check(moveTo(victim.nestPosition).success)
        check(skipPhase().success)
        check(finishTurn().success)
        current.advancePhase()
        check(finishTurn().success)
        current.advancePhase()
        check(skipPhase().success)
        check(skipPhase().success)
    }
}
