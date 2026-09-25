package com.moguru.game.presenter

import com.moguru.game.model.Rotation
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.EscapeDirection
import com.moguru.game.model.HoleTile
import com.moguru.game.model.TileShape
import com.moguru.game.model.Position
import com.moguru.game.engine.GameState
import com.moguru.game.engine.TurnPhase
import com.moguru.game.persistence.validate
import com.moguru.game.util.DiceRoller
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import com.moguru.game.util.Shuffler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameSnapshotTest {
    private fun controller() = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())

    @Test
    fun `restoring a pending dig preserves both choices and does not draw or shuffle`() {
        val original = controller()
        original.startNewGame(4)
        original.digAt(original.digTargets().first(), Rotation.DEG_0)
        original.selectPendingDigTile(DigTileChoice.REVEALED)
        original.setPendingDigRotation(Rotation.DEG_90)
        original.selectPendingDigTile(DigTileChoice.DRAWN)
        original.setPendingDigRotation(Rotation.DEG_270)
        val snapshot = original.exportSnapshot()
        val restored = MoguraGameController.fromSnapshot(snapshot,
            object : DiceRoller { override fun roll(): Int = error("restore rolled dice") },
            object : Shuffler { override fun <T> shuffle(list: List<T>): List<T> = error("restore shuffled") })
        assertEquals(snapshot, restored.exportSnapshot())
        assertEquals(original.playScreenUiState(), restored.playScreenUiState())
        original.selectPendingDigTile(DigTileChoice.REVEALED)
        restored.selectPendingDigTile(DigTileChoice.REVEALED)
        assertEquals(Rotation.DEG_90, restored.pendingDigRotation)
        original.confirmPendingDig()
        restored.confirmPendingDig()
        assertEquals(original.exportSnapshot(), restored.exportSnapshot())
    }

    @Test
    fun `snapshot is detached from later play and rejects invalid current player`() {
        val original = controller()
        original.startNewGame(2)
        val before = original.exportSnapshot()
        original.digAt(original.digTargets().first(), Rotation.DEG_0)
        val restored = MoguraGameController.fromSnapshot(before, FixedDiceRoller(listOf(6)), FixedShuffler())
        assertEquals(before, restored.exportSnapshot())
        assertNotEquals(before, original.exportSnapshot())
        assertThrows(IllegalArgumentException::class.java) {
            MoguraGameController.fromSnapshot(before.copy(engine = before.engine.copy(currentPlayerIndex = 99)))
        }
    }

    @Test
    fun `restoration rejects changed escape rules for every food type`() {
        val snapshot = controller().apply { startNewGame(4) }.exportSnapshot()
        for (type in FoodType.entries) {
            val card = FoodCard.createDummyCards(type).first()
            val valid = snapshot.copy(engine = snapshot.engine.copy(foods = emptyMap(), foodStock = FoodCard.createDeck(true)))
            assertDoesNotThrow { MoguraGameController.fromSnapshot(valid) }
            val invalidMaps = listOf(emptyMap(), card.escapeMap + (1 to EscapeDirection.RIGHT))
                .filter { it != card.escapeMap }
            for (escapeMap in invalidMaps) {
                val corrupt = valid.copy(engine = valid.engine.copy(foodStock = valid.engine.foodStock.map {
                    if (it.type == type) it.copy(escapeMap = escapeMap) else it
                }))
                assertThrows(IllegalArgumentException::class.java) { MoguraGameController.fromSnapshot(corrupt) }
            }
        }
    }

    @Test
    fun `restoration rejects extra missing and substituted food cards`() {
        for (count in 2..4) {
            val snapshot = controller().apply { startNewGame(count) }.exportSnapshot()
            val stock = snapshot.engine.foodStock
            val larva = FoodCard.createDummyCards(FoodType.BEETLE_LARVA).first()
            for (corruptStock in listOf(stock + larva, stock.drop(1), stock.drop(1) + larva)) {
                val corrupt = snapshot.copy(engine = snapshot.engine.copy(foodStock = corruptStock))
                assertThrows(IllegalArgumentException::class.java) { MoguraGameController.fromSnapshot(corrupt) }
            }
        }
    }

    @Test
    fun `restoration rejects missing board tiles even when moved into the deck`() {
        val snapshot = controller().apply { startNewGame(2) }.exportSnapshot()
        val removed = snapshot.engine.tiles.entries.first()
        for (drawPile in listOf(snapshot.engine.tileDrawPile, snapshot.engine.tileDrawPile + removed.value)) {
            val corrupt = snapshot.copy(engine = snapshot.engine.copy(
                tiles = snapshot.engine.tiles - removed.key, tileDrawPile = drawPile))
            assertThrows(IllegalArgumentException::class.java) { MoguraGameController.fromSnapshot(corrupt) }
        }
    }

    @Test
    fun `restoration rejects extra missing and substituted hole tiles`() {
        val snapshot = controller().apply { startNewGame(2) }.exportSnapshot()
        val drawPile = snapshot.engine.tileDrawPile
        val cross = HoleTile(TileShape.CROSS)
        for (corruptPile in listOf(drawPile + cross, drawPile.drop(1), drawPile.drop(1) + cross)) {
            val corrupt = snapshot.copy(engine = snapshot.engine.copy(tileDrawPile = corruptPile))
            assertThrows(IllegalArgumentException::class.java) { MoguraGameController.fromSnapshot(corrupt) }
        }
    }

    @Test
    fun `restoration rejects board food placed on a nest`() {
        val snapshot = controller().apply { startNewGame(2) }.exportSnapshot()
        val entry = snapshot.engine.foods.entries.first()
        val corrupt = snapshot.copy(engine = snapshot.engine.copy(
            foods = (snapshot.engine.foods - entry.key) + (snapshot.engine.players[0].nestPosition to entry.value)))
        assertThrows(IllegalArgumentException::class.java) { MoguraGameController.fromSnapshot(corrupt) }
    }

    @Test
    fun `dig phase requires a prepared tile only when digging is possible`() {
        val snapshot = controller().apply { startNewGame(2) }.exportSnapshot()
        val missing = snapshot.copy(
            engine = snapshot.engine.copy(tileDiscardPile = snapshot.engine.tileDiscardPile + snapshot.pendingDigDrawnTile!!),
            pendingDigDrawnTile = null,
        )
        assertThrows(IllegalArgumentException::class.java) { MoguraGameController.fromSnapshot(missing) }
        val noTargets = missing.copy(engine = missing.engine.copy(players = missing.engine.players.mapIndexed { i, p ->
            if (i == 0) p.copy(position = Position(0, 0)) else p
        }))
        val restored = MoguraGameController.fromSnapshot(noTargets)
        assertTrue(restored.digTargets().isEmpty())
        restored.settleAfterRestore()
        assertEquals(TurnPhase.MOVE, restored.engine!!.currentPhase)
    }

    @Test
    fun `every checkpoint in a complete game continues identically after restoration`() {
        for (count in 2..4) {
            val original = controller()
            original.startNewGame(count)
            var steps = 0
            while (original.engine!!.gameState == GameState.PLAYING && steps++ < 1000) {
                val checkpoint = original.exportSnapshot()
                checkpoint.validate()
                val resumed = MoguraGameController.fromSnapshot(checkpoint, FixedDiceRoller(listOf(6)), FixedShuffler())
                assertEquals(checkpoint, resumed.exportSnapshot())
                val action: (MoguraGameController) -> GameActionResult = when {
                    original.pendingCaptureRoll != null -> if (original.pendingCaptureRoll!!.roll == null) {
                        { it.rollCaptureDice() }
                    } else { { it.resolveCaptureRoll() } }
                    original.pendingDigPlacement != null -> { { it.confirmPendingDig() } }
                    else -> when (original.engine!!.currentPhase) {
                        TurnPhase.DIG -> original.digTargets().firstOrNull()?.let { target ->
                            { c: MoguraGameController -> c.digAt(target, Rotation.DEG_0) }
                        } ?: { c: MoguraGameController -> c.skipPhase() }
                        TurnPhase.MOVE -> original.moveTargets().firstOrNull()?.let { target ->
                            { c: MoguraGameController -> c.moveTo(target) }
                        } ?: { c: MoguraGameController -> c.skipPhase() }
                        TurnPhase.CAPTURE -> if (original.canCapture()) { { it.captureCurrentPosition() } }
                            else { { it.skipPhase() } }
                        TurnPhase.DECIDE -> if (original.pendingFoodDecision != null) { { it.eatPendingFood() } }
                            else if (original.playScreenUiState().actionAvailability.canRob) { { it.robSelectedFood() } }
                            else { { it.skipPhase() } }
                        TurnPhase.END -> { { it.finishTurn() } }
                    }
                }
                assertEquals(action(original), action(resumed))
                assertEquals(original.exportSnapshot(), resumed.exportSnapshot())
            }
            assertEquals(GameState.FINISHED, original.engine!!.gameState)
            assertTrue(original.logs.size > 80)
            val finished = original.exportSnapshot()
            assertEquals(finished, MoguraGameController.fromSnapshot(finished).exportSnapshot())
        }
    }

    @Test
    fun `settling a confirmed roll uses that roll once and keeps the food decision`() {
        val source = controller()
        source.startNewGame(2)
        val initial = source.exportSnapshot()
        val position = Position(2, 2)
        val ready = initial.copy(
            engine = initial.engine.copy(
                currentPhase = TurnPhase.CAPTURE,
                tileDiscardPile = initial.engine.tileDiscardPile + listOfNotNull(initial.pendingDigDrawnTile),
                players = initial.engine.players.mapIndexed { i, p -> if (i == 0) p.copy(position = position) else p },
                foods = mapOf(position to listOf(FoodCard.createDummyCards(FoodType.EARTHWORM).first())),
                foodStock = initial.engine.foodStock.drop(1) + initial.engine.foods.values.flatten(),
            ),
            pendingDigDrawnTile = null,
        )
        val original = MoguraGameController.fromSnapshot(ready, FixedDiceRoller(listOf(6)), FixedShuffler())
        original.captureCurrentPosition()
        val unrolled = original.exportSnapshot()
        MoguraGameController.fromSnapshot(unrolled).also {
            it.settleAfterRestore()
            assertEquals(unrolled, it.exportSnapshot())
        }
        original.rollCaptureDice()
        val resumed = MoguraGameController.fromSnapshot(original.exportSnapshot(),
            object : DiceRoller { override fun roll(): Int = error("rerolled confirmed dice") }, FixedShuffler())
        original.resolveCaptureRoll()
        resumed.settleAfterRestore()
        assertEquals(original.exportSnapshot(), resumed.exportSnapshot())
        assertNotNull(resumed.pendingFoodDecision)
        assertEquals(6, resumed.lastDiceRoll)
        val once = resumed.exportSnapshot()
        resumed.settleAfterRestore()
        assertEquals(once, resumed.exportSnapshot())
        assertTrue(resumed.carryPendingFood().success)
        assertNotNull(resumed.currentPlayer!!.carriedFood)
        assertEquals(resumed.exportSnapshot(), MoguraGameController.fromSnapshot(resumed.exportSnapshot()).exportSnapshot())
    }

    @Test
    fun `robbery visit eligibility and nest food rights survive restoration`() {
        val source = controller()
        source.startNewGame(3)
        val initial = source.exportSnapshot()
        val food = FoodCard.createDummyCards(FoodType.CENTIPEDE).first().copy(isFaceDown = false)
        val victimNest = initial.engine.players[1].nestPosition
        val setup = initial.copy(
            engine = initial.engine.copy(
                currentPhase = TurnPhase.DECIDE,
                tileDiscardPile = initial.engine.tileDiscardPile + listOfNotNull(initial.pendingDigDrawnTile),
                foodStock = initial.engine.foodStock.toMutableList().apply { removeAt(indexOfFirst { it.type == food.type }) },
                players = initial.engine.players.mapIndexed { index, player ->
                    when (index) {
                        0 -> player.copy(position = victimNest)
                        1 -> player.copy(position = Position(4, 1), storedFoods = listOf(food))
                        else -> player
                    }
                },
            ),
            pendingDigDrawnTile = null,
            robberyVisits = mapOf(0 to com.moguru.game.persistence.RobberyVisitSnapshot(victimNest, true)),
        )
        val restored = MoguraGameController.fromSnapshot(setup)
        assertTrue(restored.playScreenUiState().actionAvailability.canRob)
        assertTrue(restored.robSelectedFood().success)
        val decision = MoguraGameController.fromSnapshot(restored.exportSnapshot())
        assertEquals(FoodDecisionSource.ROBBERY, decision.pendingFoodDecisionSource)
        assertTrue(decision.carryPendingFood().success)
        assertEquals(3, decision.currentPlayer!!.score)
        assertEquals(victimNest, decision.currentPlayer!!.position)
        assertNull(decision.currentPlayer!!.carriedFood)

        val own = initial.copy(
            engine = initial.engine.copy(currentPhase = TurnPhase.DECIDE,
                tileDiscardPile = initial.engine.tileDiscardPile + listOfNotNull(initial.pendingDigDrawnTile),
                foodStock = initial.engine.foodStock.toMutableList().apply { removeAt(indexOfFirst { it.type == food.type }) },
                players = initial.engine.players.mapIndexed { index, p -> if (index == 0) p.copy(storedFoods = listOf(food)) else p }),
            pendingDigDrawnTile = null, ownNestEatEligiblePlayers = setOf(0),
        )
        val atHome = MoguraGameController.fromSnapshot(own)
        assertTrue(atHome.playScreenUiState().actionAvailability.canEat)
        assertTrue(atHome.eatPendingFood().success)
        assertEquals(0, atHome.currentPlayer!!.score)
        assertEquals(atHome.exportSnapshot(), MoguraGameController.fromSnapshot(atHome.exportSnapshot()).exportSnapshot())
    }
}
