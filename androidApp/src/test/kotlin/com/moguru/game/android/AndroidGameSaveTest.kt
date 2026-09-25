package com.moguru.game.android

import com.moguru.game.model.*
import com.moguru.game.engine.GameState
import com.moguru.game.engine.TurnPhase
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.DiceRoller
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AndroidGameSaveTest {
    private class QueuedIo : GameSaveIo {
        val pending = ArrayDeque<() -> Unit>()
        override fun <T> execute(task: () -> T, complete: (Result<T>) -> Unit) {
            pending.addLast { complete(runCatching(task)) }
        }
        fun finish() = pending.removeFirst().invoke()
    }
    private class Store : GameSaveRepository {
        var saved: SavedGame? = null
        var fail = false
        var loadFailure: Exception? = null
        override fun load(): SavedGame? { loadFailure?.let { throw it }; return saved }
        override fun save(game: SavedGame) {
            if (fail) throw java.io.IOException("test")
            saved = game
        }
    }
    private fun controller() = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())

    @Test
    fun `results appear only after save and retry does not repeat the action`() {
        val io = QueuedIo()
        val store = Store()
        val controller = controller()
        val vm = AndroidGameViewModel(controller, store, io)
        io.finish() // load
        vm.startSelectedGame()
        assertFalse(vm.uiState.value.isGameStarted)
        assertTrue(vm.uiState.value.persistence.busy)
        io.finish()
        val opening = requireNotNull(store.saved)
        val target = controller.digTargets().first()
        store.fail = true
        vm.onCellClicked(target)
        io.finish()
        val afterAction = controller.exportSnapshot()
        assertTrue(vm.uiState.value.persistence.saveFailed)
        assertEquals(opening, store.saved)
        vm.confirmDigPlacement() // blocked while save failed
        assertEquals(afterAction, controller.exportSnapshot())
        store.fail = false
        vm.retrySave()
        io.finish()
        assertEquals(afterAction, store.saved?.game)
        assertEquals(afterAction, controller.exportSnapshot())
        assertFalse(vm.uiState.value.persistence.saveFailed)
        assertTrue(vm.uiState.value.showDigControls)
    }

    @Test
    fun `cold launch offers saved game and new game needs overwrite confirmation`() {
        val store = Store()
        val original = AndroidGameViewModel(controller(), store)
        original.startSelectedGame()
        val saved = requireNotNull(store.saved)
        val restarted = AndroidGameViewModel(controller(), store)
        assertFalse(restarted.uiState.value.isGameStarted)
        assertTrue(restarted.uiState.value.persistence.showEntry)
        restarted.chooseNewGame()
        restarted.startSelectedGame()
        assertTrue(restarted.uiState.value.persistence.confirmOverwrite)
        assertEquals(saved, store.saved)
        restarted.cancelOverwrite()
        restarted.resumeSavedGame()
        assertTrue(restarted.uiState.value.isGameStarted)
        assertEquals(saved.game, store.saved?.game)
        restarted.returnToSetup()
        assertTrue(restarted.uiState.value.persistence.showEntry)
    }

    @Test
    fun `pending rotation and full history survive a new ViewModel`() {
        val store = Store()
        val controller = controller()
        val vm = AndroidGameViewModel(controller, store)
        vm.startSelectedGame()
        vm.onCellClicked(controller.digTargets().first())
        vm.selectRotation(Rotation.DEG_270)
        val saved = requireNotNull(store.saved)
        val resumed = AndroidGameViewModel(controller(), store)
        resumed.resumeSavedGame()
        assertEquals(Rotation.DEG_270, resumed.uiState.value.playState.selectedRotation)
        assertEquals(saved.game.logs, resumed.uiState.value.logs)
        assertEquals(saved.game, store.saved?.game)
    }

    @Test
    fun `capture meal and consumption checkpoints resume without duplicate effects`() {
        val source = controller()
        source.startNewGame(2)
        val initial = source.exportSnapshot()
        val position = Position(2, 2)
        val ready = initial.copy(
            engine = initial.engine.copy(currentPhase = TurnPhase.CAPTURE,
                players = initial.engine.players.mapIndexed { i, p ->
                    if (i == 0) p.copy(position = position, health = 5) else p
                },
                foods = mapOf(position to listOf(FoodCard.createDummyCards(FoodType.EARTHWORM).first()))),
            pendingDigDrawnTile = null,
        )
        val store = Store().apply { saved = SavedGame(ready, 1, 1) }
        val vm = AndroidGameViewModel(controller(), store)
        vm.resumeSavedGame()
        vm.capture()
        vm.stopDiceRoulette()
        val rolled = requireNotNull(store.saved)
        vm.finishDiceRoulette()
        val captured = requireNotNull(store.saved)
        vm.finishCaptureAnimation(vm.uiState.value.captureAnimation!!.event.id)
        val afterCapture = requireNotNull(store.saved).game
        for (checkpoint in listOf(rolled, captured)) {
            val restartedStore = Store().apply { saved = checkpoint }
            val restarted = AndroidGameViewModel(controller(), restartedStore)
            restarted.resumeSavedGame()
            assertEquals(afterCapture, restartedStore.saved!!.game)
            assertNull(restarted.uiState.value.captureAnimation)
        }
        vm.eat()
        val eaten = requireNotNull(store.saved)
        vm.finishEatAnimation(vm.uiState.value.eatAnimation!!.id)
        val consumed = requireNotNull(store.saved)
        vm.finishTurnConsumptionAnimation(vm.uiState.value.turnConsumptionAnimation!!.id)
        val afterMeal = requireNotNull(store.saved).game
        assertEquals(6, afterMeal.engine.players[0].health)
        for (checkpoint in listOf(eaten, consumed)) {
            val restartedStore = Store().apply { saved = checkpoint }
            val restarted = AndroidGameViewModel(controller(), restartedStore)
            restarted.resumeSavedGame()
            assertEquals(afterMeal, restartedStore.saved!!.game)
            assertNull(restarted.uiState.value.eatAnimation)
            assertNull(restarted.uiState.value.turnConsumptionAnimation)
        }
    }

    @Test
    fun `move selection and piece transparency survive cold launch`() {
        val source = controller()
        source.startNewGame(2)
        val initial = source.exportSnapshot()
        val ready = initial.copy(engine = initial.engine.copy(currentPhase = TurnPhase.MOVE,
            tiles = initial.engine.tiles.mapValues { HoleTile(TileShape.CROSS).flip() }),
            pendingDigDrawnTile = null)
        val target = MoguraGameController.fromSnapshot(ready).moveTargets().last()
        val store = Store().apply { saved = SavedGame(ready, 1, 1) }
        val vm = AndroidGameViewModel(controller(), store)
        vm.resumeSavedGame()
        vm.selectMoveTarget(target)
        vm.setBoardPiecesTransparent(true)
        val restarted = AndroidGameViewModel(controller(), store)
        restarted.resumeSavedGame()
        assertEquals(target, restarted.uiState.value.selectedMovePosition)
        assertTrue(restarted.uiState.value.boardPiecesTransparent)
        assertEquals(initial.engine.players[0].position, store.saved!!.game.engine.players[0].position)
    }

    @Test
    fun `next players move phase starts at the first target instead of the previous selection`() {
        val initial = controller().apply { startNewGame(2) }.exportSnapshot()
        val ready = initial.copy(engine = initial.engine.copy(currentPhase = TurnPhase.MOVE,
            tiles = initial.engine.tiles.mapValues { HoleTile(TileShape.CROSS).flip() }),
            pendingDigDrawnTile = null)
        val store = Store().apply { saved = SavedGame(ready, 1, 1) }
        val vm = AndroidGameViewModel(controller(), store)
        vm.resumeSavedGame()
        val oldTarget = Position(2, 2)
        vm.selectMoveTarget(oldTarget)
        assertEquals(oldTarget, vm.uiState.value.selectedMovePosition)
        vm.skip()
        vm.finishTurnConsumptionAnimation(vm.uiState.value.turnConsumptionAnimation!!.id)
        val nextPlayer = store.saved!!.game.engine.currentPlayerIndex
        assertNotEquals(ready.engine.currentPlayerIndex, nextPlayer)
        val digTarget = MoguraGameController.fromSnapshot(store.saved!!.game).digTargets().first()
        vm.onCellClicked(digTarget)
        vm.selectDigChoice(com.moguru.game.presenter.DigTileChoice.REVEALED)
        vm.confirmDigPlacement()
        assertEquals(TurnPhase.MOVE, store.saved!!.game.engine.currentPhase)
        val targets = MoguraGameController.fromSnapshot(store.saved!!.game).moveTargets()
            .sortedWith(compareBy<Position> { it.row }.thenBy { it.col })
        assertTrue(oldTarget in targets)
        assertNotEquals(oldTarget, targets.first())
        assertEquals(targets.first(), vm.uiState.value.selectedMovePosition)
        assertEquals(targets.first(), store.saved!!.selectedMovePosition)
    }

    @Test
    fun `new games reset piece transparency in both UI and saved state`() {
        for (useSetup in listOf(true, false)) {
            val store = Store()
            val vm = AndroidGameViewModel(controller(), store)
            vm.startSelectedGame()
            vm.setBoardPiecesTransparent(true)
            assertTrue(store.saved!!.boardPiecesTransparent)
            vm.returnToSetup()
            if (useSetup) {
                vm.chooseNewGame()
                vm.startSelectedGame()
                vm.confirmNewGame()
            } else {
                vm.startNewGame(2)
            }
            assertFalse(vm.uiState.value.boardPiecesTransparent)
            assertFalse(store.saved!!.boardPiecesTransparent)
        }
    }

    @Test
    fun `failed replacement retains old save and retries the same new game`() {
        val store = Store()
        val vm = AndroidGameViewModel(controller(), store)
        vm.startSelectedGame()
        val old = requireNotNull(store.saved)
        vm.returnToSetup()
        vm.chooseNewGame()
        vm.selectPlayerCount(4)
        vm.startSelectedGame()
        store.fail = true
        vm.confirmNewGame()
        assertEquals(old, store.saved)
        assertFalse(vm.uiState.value.isGameStarted)
        assertTrue(vm.uiState.value.persistence.saveFailed)
        store.fail = false
        vm.retrySave()
        assertEquals(4, store.saved!!.game.engine.players.size)
        assertEquals(old.revision + 1, store.saved!!.revision)
    }

    @Test
    fun `unreadable save needs explicit confirmation before replacement`() {
        val store = Store().apply { loadFailure = InvalidGameSaveException(GameSaveLoadIssue.UNSUPPORTED) }
        val vm = AndroidGameViewModel(controller(), store)
        assertEquals(GameSaveLoadIssue.UNSUPPORTED, vm.uiState.value.persistence.loadIssue)
        vm.chooseNewGame()
        vm.startSelectedGame()
        assertTrue(vm.uiState.value.persistence.confirmOverwrite)
        assertNull(store.saved)
        vm.cancelOverwrite()
        assertNull(store.saved)
        vm.startSelectedGame()
        vm.confirmNewGame()
        assertNotNull(store.saved)
        assertNull(vm.uiState.value.persistence.loadIssue)
    }

    @Test
    fun `failed dice save retries the exact roll before showing it`() {
        var rolls = 0
        val dice = object : DiceRoller {
            override fun roll(): Int { rolls++; return 6 }
        }
        val source = controller().apply { startNewGame(2) }.exportSnapshot()
        val position = Position(2, 2)
        val ready = source.copy(
            engine = source.engine.copy(currentPhase = TurnPhase.CAPTURE,
                players = source.engine.players.mapIndexed { i, p -> if (i == 0) p.copy(position = position) else p },
                foods = mapOf(position to listOf(FoodCard.createDummyCards(FoodType.EARTHWORM).first()))),
            pendingDigDrawnTile = null,
        )
        val store = Store().apply { saved = SavedGame(ready, 1, 1) }
        val vm = AndroidGameViewModel(MoguraGameController(dice, FixedShuffler()), store)
        vm.resumeSavedGame()
        vm.capture()
        store.fail = true
        vm.stopDiceRoulette()
        assertEquals(1, rolls)
        assertNull(store.saved!!.game.pendingCaptureRoll!!.roll)
        assertTrue(vm.uiState.value.persistence.saveFailed)
        vm.stopDiceRoulette()
        vm.finishDiceRoulette()
        assertEquals(1, rolls)
        store.fail = false
        vm.retrySave()
        assertEquals(1, rolls)
        assertEquals(6, store.saved!!.game.pendingCaptureRoll!!.roll)
        assertFalse(vm.uiState.value.persistence.saveFailed)
    }

    @Test
    fun `finished result stays available after dismissing it and relaunching`() {
        val source = controller().apply { startNewGame(2) }.exportSnapshot()
        val finished = source.copy(
            engine = source.engine.copy(gameState = GameState.FINISHED, currentPhase = TurnPhase.END,
                players = source.engine.players.mapIndexed { i, p -> if (i == 1) p.copy(health = 0) else p }),
            pendingDigDrawnTile = null,
        )
        val store = Store().apply { saved = SavedGame(finished, 1, 1) }
        val vm = AndroidGameViewModel(controller(), store)
        assertTrue(vm.uiState.value.persistence.finished)
        vm.resumeSavedGame()
        assertTrue(vm.uiState.value.showGameResultOverlay)
        assertEquals(finished.engine.players[0].id, vm.uiState.value.gameResult?.winnerPlayerId)
        vm.dismissGameResultOverlay()
        assertFalse(vm.uiState.value.showGameResultOverlay)
        vm.returnToSetup()
        val restarted = AndroidGameViewModel(controller(), store)
        restarted.resumeSavedGame()
        assertTrue(restarted.uiState.value.showGameResultOverlay)
        assertEquals(finished, store.saved!!.game)
    }
}
