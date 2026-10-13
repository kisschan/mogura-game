package com.moguru.game.android

import com.moguru.game.cpu.CpuAction
import com.moguru.game.cpu.CpuObservation
import com.moguru.game.cpu.CpuStrategy
import com.moguru.game.cpu.cpuObservation
import com.moguru.game.engine.GameState
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodType
import com.moguru.game.model.Player
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.persistence.GameSnapshot
import com.moguru.game.presenter.DigTileChoice
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.DiceRoller
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import com.moguru.game.util.Shuffler
import com.moguru.game.persistence.validate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AndroidCpuGameTest {
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
        override fun load(): SavedGame? = saved
        override fun save(game: SavedGame) {
            if (fail) throw java.io.IOException("test")
            saved = game
        }
    }
    private class CountingDice(private val face: Int = 6) : DiceRoller {
        var rolls = 0
        override fun roll(): Int { rolls++; return face }
    }
    private class Scripted(private val decide: () -> CpuAction?) : CpuStrategy {
        var calls = 0
        override fun choose(observation: CpuObservation): CpuAction? { calls++; return decide() }
    }
    /** Keeps cancelled reservations, so a timer that fires anyway can be simulated. */
    private class LeakyScheduler : CpuStepScheduler {
        val tasks = mutableListOf<() -> Unit>()
        override fun schedule(delayMillis: Long, task: () -> Unit): CpuStepCancellation {
            tasks.add(task)
            return CpuStepCancellation {}
        }
    }
    private class Harness(
        val vm: AndroidGameViewModel,
        val store: Store,
        val scheduler: ManualCpuStepScheduler,
        val strategy: Scripted,
    ) {
        val state get() = vm.uiState.value
        val saved get() = requireNotNull(store.saved)

        /** Advances timers and reports finished playbacks, as the screen would, until [condition] holds. */
        fun runUntil(condition: (AndroidGameUiState) -> Boolean) {
            repeat(600) {
                val now = state
                if (condition(now)) return
                val consumption = now.turnConsumptionAnimation
                val eat = now.eatAnimation
                val capture = now.captureAnimation
                when {
                    consumption != null -> vm.finishTurnConsumptionAnimation(consumption.id)
                    eat != null -> vm.finishEatAnimation(eat.id)
                    capture != null -> vm.finishCaptureAnimation(capture.event.id)
                    now.playState.diceRouletteResult != null -> vm.finishDiceRoulette()
                    else -> {
                        assertEquals(1, scheduler.pendingCount, "the CPU must have a step reserved")
                        scheduler.advanceBy(CPU_CAPTURE_STAGE_MILLIS)
                    }
                }
            }
            fail<Unit>("the game did not reach the expected state")
        }
    }

    private fun controller() = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())

    private fun AndroidGameViewModel.activateCpuHost() {
        setCpuHostResumed(true)
        setCpuPlayScreenClear(true)
    }

    /** Starts from setup with the given seats CPU-operated. */
    private fun cpuGame(
        game: MoguraGameController,
        cpuSeats: Set<Int>,
        playerCount: Int = 2,
        decide: () -> CpuAction?,
    ): Harness {
        val store = Store()
        val scheduler = ManualCpuStepScheduler()
        val strategy = Scripted(decide)
        val vm = AndroidGameViewModel(game, store, cpuStrategy = strategy, cpuScheduler = scheduler)
        vm.activateCpuHost()
        vm.selectPlayerCount(playerCount)
        cpuSeats.forEach { vm.selectPlayerKind(it, true) }
        vm.startSelectedGame()
        return Harness(vm, store, scheduler, strategy)
    }

    /** Resumes a saved position. The live controller is restored inside the ViewModel. */
    private fun resumedCpuGame(
        snapshot: GameSnapshot,
        cpuIds: Set<Int>,
        dice: DiceRoller = FixedDiceRoller(listOf(6)),
        decide: () -> CpuAction?,
    ): Harness {
        val store = Store().apply { saved = SavedGame(snapshot, 1, 1, cpuPlayerIds = cpuIds) }
        val scheduler = ManualCpuStepScheduler()
        val strategy = Scripted(decide)
        val vm = AndroidGameViewModel(
            MoguraGameController(dice, FixedShuffler()), store,
            cpuStrategy = strategy, cpuScheduler = scheduler,
        )
        vm.activateCpuHost()
        vm.resumeSavedGame()
        return Harness(vm, store, scheduler, strategy)
    }

    private fun script(vararg actions: CpuAction): () -> CpuAction? {
        val remaining = ArrayDeque(actions.toList())
        return { remaining.removeFirstOrNull() }
    }

    /** Digs the first target as revealed, then ends the turn wherever that leaves the mole. */
    private fun digAndEndTurn(game: MoguraGameController): CpuAction = when {
        game.pendingDigPlacement != null -> CpuAction.ConfirmDig
        game.engine!!.currentPhase == TurnPhase.DIG -> CpuAction.RevealDig(game.digTargets().first())
        CpuAction.Skip in game.cpuObservation().legalActions -> CpuAction.Skip
        else -> CpuAction.FinishTurn
    }

    private fun humanDigsAndEndsTurn(vm: AndroidGameViewModel, game: MoguraGameController) {
        vm.onCellClicked(game.digTargets().first())
        vm.confirmDigPlacement()
        if (vm.uiState.value.turnConsumptionAnimation == null) vm.finishTurn()
    }

    private fun moveReady(): GameSnapshot {
        val initial = controller().apply { startNewGame(2) }.exportSnapshot()
        return initial.copy(
            engine = initial.engine.copy(
                currentPhase = TurnPhase.MOVE,
                tileDiscardPile = initial.engine.tileDiscardPile + listOfNotNull(initial.pendingDigDrawnTile),
                tiles = initial.engine.tiles.mapValues { it.value.rotate(Rotation.DEG_90).flip() },
            ),
            pendingDigDrawnTile = null,
        )
    }

    private fun sortedMoveTargets(snapshot: GameSnapshot): List<Position> =
        MoguraGameController.fromSnapshot(snapshot).moveTargets()
            .sortedWith(compareBy<Position> { it.row }.thenBy { it.col })

    /** Player 0 stands on a single food card of [type], ready to capture it. */
    private fun captureReady(type: FoodType, health: Int = 5): GameSnapshot {
        val initial = controller().apply { startNewGame(2) }.exportSnapshot()
        val position = Position(2, 2)
        val pool = initial.engine.foods.values.flatten() + initial.engine.foodStock
        val target = pool.first { it.type == type }
        return initial.copy(
            engine = initial.engine.copy(
                currentPhase = TurnPhase.CAPTURE,
                tileDiscardPile = initial.engine.tileDiscardPile + listOfNotNull(initial.pendingDigDrawnTile),
                players = initial.engine.players.mapIndexed { index, player ->
                    if (index == 0) player.copy(position = position, health = health) else player
                },
                foods = mapOf(position to listOf(target)),
                foodStock = pool - target,
            ),
            pendingDigDrawnTile = null,
        )
    }

    @Test
    fun `seat kinds default to human stay with the seat and reset with the player count`() {
        val vm = AndroidGameViewModel(controller())
        assertTrue(vm.uiState.value.setupPlayers.none { it.isCpu })

        vm.selectPlayerCount(3)
        vm.selectPlayerKind(1, true)
        vm.selectPlayerMole(0, 1)
        vm.selectPlayerNest(0, Position(5, 1))

        val setup = vm.uiState.value.setupPlayers
        assertEquals(listOf(1, 0, 2), setup.map { it.playerId })
        assertEquals(listOf(false, true, false), setup.map { it.isCpu })
        assertTrue(vm.uiState.value.canStartGame)

        vm.selectPlayerCount(4)
        assertTrue(vm.uiState.value.setupPlayers.none { it.isCpu })
        vm.selectPlayerKind(9, true)
        assertTrue(vm.uiState.value.setupPlayers.none { it.isCpu })
    }

    @Test
    fun `starting turns cpu seats into player ids and a non zero cpu can move first`() {
        val game = controller()
        val store = Store()
        val scheduler = ManualCpuStepScheduler()
        val strategy = Scripted { CpuAction.RevealDig(game.digTargets().first()) }
        val vm = AndroidGameViewModel(game, store, cpuStrategy = strategy, cpuScheduler = scheduler)
        vm.activateCpuHost()
        vm.selectPlayerCount(3)
        vm.selectPlayerMole(2, 3)
        vm.selectPlayerKind(2, true)
        vm.selectStartPlayer(2)

        vm.startSelectedGame()

        assertEquals(setOf(3), store.saved!!.cpuPlayerIds)
        assertEquals(3, game.currentPlayer!!.id)
        assertTrue(vm.uiState.value.cpuTurn)
        assertTrue(vm.uiState.value.visibleActions.isEmpty())
        scheduler.advanceBy(CPU_STEP_DELAY_MILLIS - 1)
        assertEquals(0, strategy.calls)
        scheduler.advanceBy(1)
        assertEquals(1, strategy.calls)
        assertNotNull(store.saved!!.game.pendingDigPlacement)
        assertNull(vm.uiState.value.cpuFault)
    }

    @Test
    fun `the direct new game entry point is all human`() {
        val store = Store()
        val scheduler = ManualCpuStepScheduler()
        val vm = AndroidGameViewModel(controller(), store, cpuScheduler = scheduler)
        vm.activateCpuHost()
        vm.selectPlayerKind(0, true)

        vm.startNewGame(2)

        assertTrue(store.saved!!.cpuPlayerIds.isEmpty())
        assertFalse(vm.uiState.value.cpuTurn)
        assertEquals(0, scheduler.pendingCount)
        assertTrue(vm.uiState.value.setupPlayers.none { it.isCpu })
    }

    @Test
    fun `a cpu game replaces the previous save only once its own save succeeds`() {
        val store = Store()
        val scheduler = ManualCpuStepScheduler()
        val vm = AndroidGameViewModel(controller(), store, cpuScheduler = scheduler)
        vm.activateCpuHost()
        vm.startSelectedGame()
        val old = requireNotNull(store.saved)
        vm.returnToSetup()
        vm.chooseNewGame()
        vm.selectPlayerKind(0, true)
        vm.startSelectedGame()
        store.fail = true

        vm.confirmNewGame()

        assertEquals(old, store.saved)
        assertFalse(vm.uiState.value.isGameStarted)
        assertTrue(vm.uiState.value.persistence.saveFailed)
        assertEquals(0, scheduler.pendingCount)

        store.fail = false
        vm.retrySave()

        // The operators are part of the save even when the opening position itself is unchanged.
        assertEquals(setOf(0), store.saved!!.cpuPlayerIds)
        assertEquals(old.revision + 1, store.saved!!.revision)
        assertTrue(vm.uiState.value.cpuTurn)
        assertEquals(1, scheduler.pendingCount)
    }

    @Test
    fun `resuming restores the cpu operators by player id`() {
        val snapshot = controller().apply { startNewGame(3) }.exportSnapshot()
        val h = resumedCpuGame(snapshot, setOf(2)) { null }

        assertEquals(listOf(false, false, true), h.state.setupPlayers.map { it.isCpu })
        assertFalse(h.state.cpuTurn)
        assertEquals(0, h.scheduler.pendingCount)
        assertEquals(setOf(2), h.saved.cpuPlayerIds)

        val invalid = Store().apply { saved = SavedGame(snapshot, 1, 1, cpuPlayerIds = setOf(3)) }
        val rejected = AndroidGameViewModel(controller(), invalid)
        assertEquals(GameSaveLoadIssue.CORRUPT, rejected.uiState.value.persistence.loadIssue)
    }

    @Test
    fun `dig reveal and rotation are shown one step ahead of the placement`() {
        val game = controller()
        val h = cpuGame(game, cpuSeats = setOf(0)) {
            when {
                game.engine!!.currentPhase != TurnPhase.DIG -> CpuAction.FinishTurn
                game.pendingDigPlacement == null -> CpuAction.RevealDig(Position(1, 1))
                game.pendingDigRotation != Rotation.DEG_90 ->
                    CpuAction.PrepareDig(DigTileChoice.REVEALED, Rotation.DEG_90)
                else -> CpuAction.ConfirmDig
            }
        }

        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)
        assertEquals(Position(1, 1), h.state.digPreviewPosition)
        assertFalse(h.state.showDigControls)
        assertNotNull(h.saved.game.pendingDigPlacement)

        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)
        assertEquals(Rotation.DEG_90, h.state.playState.selectedRotation)
        assertEquals(Rotation.DEG_90, h.saved.game.pendingDigRotation)
        assertEquals(TurnPhase.DIG, h.saved.game.engine.currentPhase)

        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)
        assertEquals(TurnPhase.MOVE, h.saved.game.engine.currentPhase)
        assertNull(h.state.digPreviewPosition)
        assertNull(h.state.cpuFault)
    }

    @Test
    fun `move target is previewed and saved a full step before the move is confirmed`() {
        val ready = moveReady()
        val targets = sortedMoveTargets(ready)
        assertTrue(targets.size > 1)
        val target = targets.last()
        val start = ready.engine.players[0].position
        val h = resumedCpuGame(ready, setOf(0)) { CpuAction.Move(target) }

        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)

        assertEquals(target, h.state.selectedMovePosition)
        assertEquals(target, h.saved.selectedMovePosition)
        assertEquals(start, h.saved.game.engine.players[0].position)
        val previewRevision = h.saved.revision

        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS - 1)
        assertEquals(start, h.saved.game.engine.players[0].position)
        h.scheduler.advanceBy(1)

        assertEquals(target, h.saved.game.engine.players[0].position)
        assertEquals(previewRevision + 1, h.saved.revision)
        // The shown preview is confirmed as is, not decided and saved a second time.
        assertEquals(1, h.strategy.calls)
        assertNull(h.state.cpuFault)
    }

    @Test
    fun `the default move selection still waits a full step without another save`() {
        val ready = moveReady()
        val target = sortedMoveTargets(ready).first()
        val start = ready.engine.players[0].position
        val h = resumedCpuGame(ready, setOf(0)) { CpuAction.Move(target) }
        val resumedRevision = h.saved.revision
        assertEquals(target, h.state.selectedMovePosition)

        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)
        assertEquals(resumedRevision, h.saved.revision)
        assertEquals(start, h.saved.game.engine.players[0].position)

        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS - 1)
        assertEquals(start, h.saved.game.engine.players[0].position)
        h.scheduler.advanceBy(1)
        assertEquals(target, h.saved.game.engine.players[0].position)
    }

    @Test
    fun `human input is ignored on a cpu turn and works again on the human turn`() {
        val game = controller()
        val h = cpuGame(game, cpuSeats = setOf(0)) { digAndEndTurn(game) }
        val before = game.exportSnapshot()
        val savedBefore = h.saved
        val target = game.digTargets().first()

        h.vm.onCellClicked(target)
        h.vm.selectMoveTarget(target)
        h.vm.selectDigChoice(DigTileChoice.DRAWN)
        h.vm.selectRotation(Rotation.DEG_180)
        h.vm.confirmDigPlacement()
        h.vm.selectCaptureTarget(0)
        h.vm.selectRobberyTarget(0)
        h.vm.capture()
        h.vm.rob()
        h.vm.stopDiceRoulette()
        h.vm.finishDiceRoulette()
        h.vm.eat()
        h.vm.carry()
        h.vm.skip()
        h.vm.finishTurn()

        assertEquals(before, game.exportSnapshot())
        assertEquals(savedBefore, h.saved)
        assertTrue(h.state.cpuTurn)
        assertTrue(h.state.visibleActions.isEmpty())
        assertFalse(h.state.showDigControls)

        h.runUntil { !it.cpuTurn && it.turnConsumptionAnimation == null }

        assertEquals(1, game.currentPlayer!!.id)
        assertEquals(0, h.scheduler.pendingCount)
        val calls = h.strategy.calls
        h.scheduler.advanceBy(60_000)
        assertEquals(calls, h.strategy.calls)
        h.vm.onCellClicked(game.digTargets().first())
        assertTrue(h.state.showDigControls)
    }

    @Test
    fun `cpu label follows the frozen display while the driver follows the live turn`() {
        val game = controller()
        val h = cpuGame(game, cpuSeats = setOf(1)) { digAndEndTurn(game) }
        assertFalse(h.state.cpuTurn)
        assertEquals(0, h.scheduler.pendingCount)

        humanDigsAndEndsTurn(h.vm, game)

        val humanEnd = requireNotNull(h.state.turnConsumptionAnimation)
        assertEquals(1, game.currentPlayer!!.id)
        assertEquals(0, h.state.playState.currentPlayer.playerId)
        assertFalse(h.state.cpuTurn)
        assertEquals(0, h.scheduler.pendingCount)

        h.vm.finishTurnConsumptionAnimation(humanEnd.id)

        assertTrue(h.state.cpuTurn)
        assertEquals(1, h.scheduler.pendingCount)

        h.runUntil { it.turnConsumptionAnimation != null }

        val cpuEnd = requireNotNull(h.state.turnConsumptionAnimation)
        assertEquals(0, game.currentPlayer!!.id)
        assertEquals(1, h.state.playState.currentPlayer.playerId)
        assertTrue(h.state.cpuTurn)

        h.vm.finishTurnConsumptionAnimation(cpuEnd.id)

        assertFalse(h.state.cpuTurn)
        assertEquals(0, h.scheduler.pendingCount)
    }

    @Test
    fun `cpu capture reveals spins rolls once and resolves through the landing callback`() {
        val dice = CountingDice()
        val h = resumedCpuGame(
            captureReady(FoodType.EARTHWORM), setOf(0), dice, script(CpuAction.Capture(0), CpuAction.Eat),
        )

        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)
        assertTrue(h.state.playState.diceRouletteActive)
        assertFalse(h.state.cpuRouletteSpinning)
        h.vm.stopDiceRoulette()
        h.vm.finishDiceRoulette()
        assertEquals(0, dice.rolls)

        h.scheduler.advanceBy(CPU_CAPTURE_STAGE_MILLIS - 1)
        assertFalse(h.state.cpuRouletteSpinning)
        h.scheduler.advanceBy(1)
        assertTrue(h.state.cpuRouletteSpinning)
        assertEquals(0, dice.rolls)

        h.scheduler.advanceBy(CPU_CAPTURE_STAGE_MILLIS - 1)
        assertEquals(0, dice.rolls)
        h.scheduler.advanceBy(1)
        assertEquals(1, dice.rolls)
        assertEquals(6, h.state.playState.diceRouletteResult)
        assertEquals(6, h.saved.game.pendingCaptureRoll!!.roll)

        // The landing belongs to the overlay; no timer may resolve or re-roll it.
        assertEquals(0, h.scheduler.pendingCount)
        h.scheduler.advanceBy(60_000)
        assertNotNull(h.saved.game.pendingCaptureRoll)

        h.vm.finishDiceRoulette()
        h.vm.finishDiceRoulette()

        val capture = requireNotNull(h.state.captureAnimation)
        assertNull(h.saved.game.pendingCaptureRoll)
        assertEquals(1, dice.rolls)
        assertFalse(h.state.cpuRouletteSpinning)
        assertEquals(0, h.scheduler.pendingCount)

        h.vm.finishCaptureAnimation(capture.event.id)
        assertNotNull(h.saved.game.pendingDecision)
        assertEquals(1, h.scheduler.pendingCount)
    }

    @Test
    fun `cpu larva capture resolves after the reveal without rolling`() {
        val dice = CountingDice()
        val h = resumedCpuGame(
            captureReady(FoodType.BEETLE_LARVA), setOf(0), dice, script(CpuAction.Capture(0)),
        )

        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)
        assertTrue(h.state.playState.diceRouletteActive)
        assertTrue(h.state.playState.diceRouletteEscapeRolls.isEmpty())
        // The confirm button belongs to a human; a tap on a CPU turn must not resolve it early.
        h.vm.finishDiceRoulette()
        assertNotNull(h.saved.game.pendingCaptureRoll)

        h.scheduler.advanceBy(CPU_CAPTURE_STAGE_MILLIS - 1)
        assertNotNull(h.saved.game.pendingCaptureRoll)
        h.scheduler.advanceBy(1)

        assertNotNull(h.state.captureAnimation)
        assertNull(h.saved.game.pendingCaptureRoll)
        assertNotNull(h.saved.game.pendingDecision)
        assertEquals(0, dice.rolls)
        assertFalse(h.state.cpuRouletteSpinning)
    }

    @Test
    fun `saved cpu captures resume without repeating the roll`() {
        val dice = CountingDice()
        val h = resumedCpuGame(captureReady(FoodType.EARTHWORM), setOf(0), dice, script(CpuAction.Capture(0)))
        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)
        val revealed = h.saved
        h.scheduler.advanceBy(CPU_CAPTURE_STAGE_MILLIS)
        h.scheduler.advanceBy(CPU_CAPTURE_STAGE_MILLIS)
        val rolled = h.saved
        assertEquals(6, rolled.game.pendingCaptureRoll!!.roll)

        // Unrolled: the reveal is shown again and the roll happens exactly once.
        val unrolledDice = CountingDice()
        val unrolled = resumedCpuGame(revealed.game, setOf(0), unrolledDice) { null }
        assertTrue(unrolled.state.playState.diceRouletteActive)
        assertNull(unrolled.state.playState.diceRouletteResult)
        assertFalse(unrolled.state.cpuRouletteSpinning)
        unrolled.scheduler.advanceBy(CPU_CAPTURE_STAGE_MILLIS)
        assertTrue(unrolled.state.cpuRouletteSpinning)
        unrolled.scheduler.advanceBy(CPU_CAPTURE_STAGE_MILLIS)
        assertEquals(1, unrolledDice.rolls)
        assertEquals(0, unrolled.strategy.calls)

        // Rolled: the saved face settles the capture, with no roll and no playback.
        val settledDice = CountingDice(face = 1)
        val settled = resumedCpuGame(rolled.game, setOf(0), settledDice, script(CpuAction.Carry))
        assertEquals(0, settledDice.rolls)
        assertNull(settled.saved.game.pendingCaptureRoll)
        assertNull(settled.state.captureAnimation)
        assertEquals(6, settled.saved.game.lastDiceRoll)
        assertNotNull(settled.saved.game.pendingDecision)
        assertEquals(1, settled.scheduler.pendingCount)
    }

    @Test
    fun `meal and turn consumption chain one playback at a time before the next player`() {
        val h = resumedCpuGame(
            captureReady(FoodType.EARTHWORM), setOf(0), CountingDice(), script(CpuAction.Capture(0), CpuAction.Eat),
        )
        h.runUntil { it.eatAnimation != null }

        val eat = requireNotNull(h.state.eatAnimation)
        assertEquals(0, h.scheduler.pendingCount)
        assertEquals(7, h.saved.game.engine.players[0].health)
        h.vm.finishEatAnimation(eat.id + 1)
        assertEquals(eat, h.state.eatAnimation)

        h.vm.finishEatAnimation(eat.id)
        h.vm.finishEatAnimation(eat.id)

        val consumption = requireNotNull(h.state.turnConsumptionAnimation)
        assertNull(h.state.eatAnimation)
        assertEquals(0, h.scheduler.pendingCount)
        assertEquals(6, h.saved.game.engine.players[0].health)

        h.vm.finishTurnConsumptionAnimation(consumption.id)
        h.vm.finishTurnConsumptionAnimation(consumption.id)

        assertNull(h.state.turnConsumptionAnimation)
        assertEquals(1, h.saved.game.engine.currentPlayerIndex)
        assertEquals(6, h.saved.game.engine.players[0].health)
        assertFalse(h.state.cpuTurn)
        assertEquals(0, h.scheduler.pendingCount)
        assertEquals(2, h.strategy.calls)
    }

    @Test
    fun `background and covering layers cancel the reserved step and returning waits in full again`() {
        val game = controller()
        val h = cpuGame(game, cpuSeats = setOf(0)) { CpuAction.RevealDig(game.digTargets().first()) }
        assertEquals(1, h.scheduler.pendingCount)

        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS - 1)
        h.vm.setCpuHostResumed(false)
        assertEquals(0, h.scheduler.pendingCount)
        h.scheduler.advanceBy(60_000)
        assertEquals(0, h.strategy.calls)

        h.vm.setCpuHostResumed(true)
        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS - 1)
        assertEquals(0, h.strategy.calls)

        // Rules, audio settings, history or the menu opened over the board.
        h.vm.setCpuPlayScreenClear(false)
        h.scheduler.advanceBy(60_000)
        assertEquals(0, h.strategy.calls)

        h.vm.setCpuPlayScreenClear(true)
        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS - 1)
        assertEquals(0, h.strategy.calls)
        h.scheduler.advanceBy(1)
        assertEquals(1, h.strategy.calls)
    }

    @Test
    fun `a completion that arrives while paused is held and applied exactly once on return`() {
        val h = resumedCpuGame(
            captureReady(FoodType.EARTHWORM), setOf(0), CountingDice(), script(CpuAction.Capture(0), CpuAction.Eat),
        )
        h.runUntil { it.eatAnimation != null }
        val eat = requireNotNull(h.state.eatAnimation)

        h.vm.setCpuHostResumed(false)
        h.vm.finishEatAnimation(eat.id + 1)
        h.vm.finishEatAnimation(eat.id)
        h.vm.finishEatAnimation(eat.id)

        // Nothing advances unseen: the meal is still showing and the turn is not consumed.
        assertEquals(eat, h.state.eatAnimation)
        assertNull(h.state.turnConsumptionAnimation)
        assertEquals(7, h.saved.game.engine.players[0].health)

        h.vm.setCpuHostResumed(true)

        val consumption = requireNotNull(h.state.turnConsumptionAnimation)
        assertNull(h.state.eatAnimation)
        assertEquals(6, h.saved.game.engine.players[0].health)

        // The held callback was used up: pausing again must not consume the turn twice.
        h.vm.setCpuHostResumed(false)
        h.vm.setCpuHostResumed(true)
        assertEquals(consumption, h.state.turnConsumptionAnimation)
        assertEquals(6, h.saved.game.engine.players[0].health)
        assertEquals(1, h.saved.game.engine.currentPlayerIndex)
    }

    @Test
    fun `leaving the game discards a held completion instead of applying it later`() {
        val h = resumedCpuGame(
            captureReady(FoodType.EARTHWORM), setOf(0), CountingDice(), script(CpuAction.Capture(0), CpuAction.Eat),
        )
        h.runUntil { it.eatAnimation != null }
        h.vm.setCpuHostResumed(false)
        h.vm.finishEatAnimation(requireNotNull(h.state.eatAnimation).id)

        h.vm.returnToSetup()
        h.vm.setCpuHostResumed(true)

        assertFalse(h.state.isGameStarted)
        assertEquals(7, h.saved.game.engine.players[0].health)
        assertEquals(0, h.scheduler.pendingCount)

        // Resuming settles the saved meal once, without replaying or double counting it.
        h.vm.resumeSavedGame()
        assertNull(h.state.eatAnimation)
        assertNull(h.state.turnConsumptionAnimation)
        assertEquals(6, h.saved.game.engine.players[0].health)
        assertEquals(1, h.saved.game.engine.currentPlayerIndex)
    }

    @Test
    fun `cpu steps wait for their save and a landing that races a display save is not lost`() {
        val io = QueuedIo()
        val store = Store().apply {
            saved = SavedGame(captureReady(FoodType.EARTHWORM), 1, 1, cpuPlayerIds = setOf(0))
        }
        val scheduler = ManualCpuStepScheduler()
        val vm = AndroidGameViewModel(
            MoguraGameController(CountingDice(), FixedShuffler()), store, io,
            Scripted(script(CpuAction.Capture(0))), scheduler,
        )
        io.finish() // load
        vm.activateCpuHost()
        vm.resumeSavedGame()

        scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)
        // The reveal exists only in the pending save: not shown yet, and nothing reserved.
        assertFalse(vm.uiState.value.playState.diceRouletteActive)
        assertEquals(0, scheduler.pendingCount)
        io.finish()
        assertTrue(vm.uiState.value.playState.diceRouletteActive)
        assertEquals(1, scheduler.pendingCount)

        scheduler.advanceBy(CPU_CAPTURE_STAGE_MILLIS)
        assertTrue(io.pending.isEmpty())
        scheduler.advanceBy(CPU_CAPTURE_STAGE_MILLIS)
        assertNull(vm.uiState.value.playState.diceRouletteResult)
        io.finish()
        assertEquals(6, vm.uiState.value.playState.diceRouletteResult)

        vm.setBoardPiecesTransparent(true)
        vm.finishDiceRoulette()
        assertNull(vm.uiState.value.captureAnimation)
        assertNotNull(store.saved!!.game.pendingCaptureRoll)

        io.finish() // the display-only save lands and releases the held landing
        assertTrue(store.saved!!.boardPiecesTransparent)
        io.finish() // the resolved capture
        assertNotNull(vm.uiState.value.captureAnimation)
        assertNull(store.saved!!.game.pendingCaptureRoll)
        assertTrue(io.pending.isEmpty())
    }

    @Test
    fun `a failed cpu save pauses play and retrying saves the same action without deciding again`() {
        val game = controller()
        val h = cpuGame(game, cpuSeats = setOf(0)) { CpuAction.RevealDig(game.digTargets().first()) }
        val opening = h.saved
        h.store.fail = true

        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)

        val afterAction = game.exportSnapshot()
        assertTrue(h.state.persistence.saveFailed)
        assertNull(h.state.digPreviewPosition)
        assertEquals(opening, h.saved)
        assertEquals(0, h.scheduler.pendingCount)
        h.scheduler.advanceBy(60_000)
        assertEquals(1, h.strategy.calls)

        h.store.fail = false
        h.vm.retrySave()

        assertEquals(afterAction, h.saved.game)
        assertEquals(afterAction, game.exportSnapshot())
        assertEquals(1, h.strategy.calls)
        assertNotNull(h.state.digPreviewPosition)
        assertEquals(1, h.scheduler.pendingCount)
    }

    @Test
    fun `display only changes on a cpu turn do not stall the next step`() {
        val game = controller()
        val h = cpuGame(game, cpuSeats = setOf(0)) { CpuAction.RevealDig(game.digTargets().first()) }
        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS - 1)

        h.vm.setBoardPiecesTransparent(true)

        assertTrue(h.saved.boardPiecesTransparent)
        assertEquals(1, h.scheduler.pendingCount)
        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)
        assertEquals(1, h.strategy.calls)
    }

    @Test
    fun `stale superseded and duplicate timer callbacks never act`() {
        val game = controller()
        val scheduler = LeakyScheduler()
        val strategy = Scripted { CpuAction.RevealDig(game.digTargets().first()) }
        val vm = AndroidGameViewModel(game, Store(), cpuStrategy = strategy, cpuScheduler = scheduler)
        vm.activateCpuHost()
        vm.selectPlayerKind(0, true)
        vm.startSelectedGame()
        val first = scheduler.tasks.single()

        vm.setCpuHostResumed(false)
        first()
        assertEquals(0, strategy.calls)

        vm.setCpuHostResumed(true)
        val second = scheduler.tasks.last()
        first()
        assertEquals(0, strategy.calls)

        second()
        second()
        assertEquals(1, strategy.calls)

        val third = scheduler.tasks.last()
        vm.returnToSetup()
        third()
        assertEquals(1, strategy.calls)
    }

    @Test
    fun `a repeated dig preview is rejected and stops with a bounded fault`() {
        val game = controller()
        val h = cpuGame(game, cpuSeats = setOf(0)) {
            if (game.pendingDigPlacement == null) {
                CpuAction.RevealDig(game.digTargets().first())
            } else {
                CpuAction.PrepareDig(game.pendingDigTileChoice!!, game.pendingDigRotation!!)
            }
        }
        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)

        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)

        assertEquals(AndroidCpuFault.ILLEGAL_ACTION, h.state.cpuFault)
        assertEquals(2, h.strategy.calls)
        assertEquals(0, h.scheduler.pendingCount)
        h.scheduler.advanceBy(60_000)
        assertEquals(2, h.strategy.calls)

        // The turn is not handed to a human.
        assertTrue(h.state.cpuTurn)
        assertTrue(h.state.visibleActions.isEmpty())
        h.vm.confirmDigPlacement()
        assertNotNull(game.pendingDigPlacement)

        h.vm.retryCpuStep()
        assertNull(h.state.cpuFault)
        h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)
        assertEquals(3, h.strategy.calls)
        assertEquals(AndroidCpuFault.ILLEGAL_ACTION, h.state.cpuFault)

        h.vm.returnToSetup()
        assertFalse(h.state.isGameStarted)
        assertNull(h.state.cpuFault)
        assertEquals(0, h.scheduler.pendingCount)
    }

    @Test
    fun `illegal actions missing decisions and strategy errors each stop without changing the game`() {
        fun assertFault(expected: AndroidCpuFault, decide: () -> CpuAction?) {
            val game = controller()
            val h = cpuGame(game, cpuSeats = setOf(0), decide = decide)
            val before = game.exportSnapshot()

            h.scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)

            assertEquals(expected, h.state.cpuFault)
            assertEquals(before, game.exportSnapshot())
            assertEquals(0, h.scheduler.pendingCount)
            h.scheduler.advanceBy(60_000)
            assertEquals(1, h.strategy.calls)
        }
        assertFault(AndroidCpuFault.ILLEGAL_ACTION) { CpuAction.Eat }
        assertFault(AndroidCpuFault.ILLEGAL_ACTION) { CpuAction.Move(Position(5, 4)) }
        assertFault(AndroidCpuFault.NO_DECISION) { null }
        assertFault(AndroidCpuFault.NO_DECISION) { error("strategy bug") }
    }

    @Test
    fun `an all cpu game plays itself to the result and then stops`() {
        val game = controller()
        val h = cpuGame(game, cpuSeats = setOf(0, 1)) { digAndEndTurn(game) }
        assertEquals(setOf(0, 1), h.saved.cpuPlayerIds)

        h.runUntil { it.gameResult != null }

        assertEquals(GameState.FINISHED, h.saved.game.engine.gameState)
        assertNull(h.state.cpuFault)
        assertFalse(h.state.cpuTurn)
        assertEquals(0, h.scheduler.pendingCount)
        val calls = h.strategy.calls
        h.scheduler.advanceBy(60_000)
        assertEquals(calls, h.strategy.calls)
    }

    @Test
    fun `cpu players keep playing after the only human is eliminated`() {
        val game = controller()
        val h = cpuGame(game, cpuSeats = setOf(1, 2), playerCount = 3) { digAndEndTurn(game) }
        val human = game.engine!!.players[0]
        repeat(Player.MAX_HEALTH - 1) { human.reduceHealth(isOnSurface = false) }

        humanDigsAndEndsTurn(h.vm, game)
        h.runUntil { it.turnConsumptionAnimation == null && it.cpuTurn }

        assertTrue(human.isEliminated)
        assertNull(h.state.gameResult)
        val turns = mutableListOf<Int>()
        repeat(4) {
            turns.add(game.currentPlayer!!.id)
            val current = game.currentPlayer!!.id
            h.runUntil { it.turnConsumptionAnimation == null && game.currentPlayer!!.id != current }
        }
        assertEquals(listOf(1, 2, 1, 2), turns)
        assertNull(h.state.cpuFault)
        assertEquals(1, h.scheduler.pendingCount)
    }

    @Test
    fun `a finished game never reserves a cpu step`() {
        val source = controller().apply { startNewGame(2) }.exportSnapshot()
        val finished = source.copy(
            engine = source.engine.copy(
                gameState = GameState.FINISHED, currentPhase = TurnPhase.END,
                tileDiscardPile = source.engine.tileDiscardPile + listOfNotNull(source.pendingDigDrawnTile),
                players = source.engine.players.mapIndexed { i, p -> if (i == 1) p.copy(health = 0) else p },
            ),
            pendingDigDrawnTile = null,
        )

        val h = resumedCpuGame(finished, setOf(0, 1)) { CpuAction.FinishTurn }

        assertNotNull(h.state.gameResult)
        assertFalse(h.state.cpuTurn)
        assertEquals(0, h.scheduler.pendingCount)
        h.scheduler.advanceBy(60_000)
        assertEquals(0, h.strategy.calls)
    }

    @Test
    fun `standard cpu games finish through Android saves and reject earlier dice callbacks`() {
        var staleLandingsChecked = 0
        for (count in 2..4) for (seed in 0..3) {
            val random = Random(seed)
            val game = MoguraGameController(
                object : DiceRoller { override fun roll() = random.nextInt(1, 7) },
                object : Shuffler { override fun <T> shuffle(list: List<T>) = list.shuffled(random) },
            )
            val store = Store()
            val scheduler = ManualCpuStepScheduler()
            val vm = AndroidGameViewModel(game, store, cpuScheduler = scheduler)
            vm.activateCpuHost()
            vm.selectPlayerCount(count)
            repeat(count) { vm.selectPlayerKind(it, true) }
            vm.startSelectedGame()
            var previousLanding: (() -> Unit)? = null
            var steps = 0
            while (vm.uiState.value.gameResult == null && steps++ < 3_000) {
                val state = vm.uiState.value
                assertNull(state.cpuFault, "count=$count seed=$seed step=$steps")
                requireNotNull(store.saved).game.validate()
                when {
                    state.turnConsumptionAnimation != null ->
                        vm.finishTurnConsumptionAnimation(state.turnConsumptionAnimation.id, state.playbackGeneration)
                    state.eatAnimation != null -> vm.finishEatAnimation(state.eatAnimation.id, state.playbackGeneration)
                    state.captureAnimation != null ->
                        vm.finishCaptureAnimation(state.captureAnimation.event.id, state.playbackGeneration)
                    state.playState.diceRouletteResult != null -> {
                        previousLanding?.let { stale ->
                            val before = store.saved
                            stale()
                            assertEquals(before, store.saved, "an old landing must not resolve another capture")
                            assertEquals(state, vm.uiState.value)
                            staleLandingsChecked++
                        }
                        val landing = { vm.finishDiceRoulette(state.playbackGeneration, state.diceRouletteEventId) }
                        landing()
                        previousLanding = landing
                    }
                    else -> {
                        assertEquals(1, scheduler.pendingCount, "count=$count seed=$seed step=$steps")
                        scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)
                    }
                }
            }
            assertNotNull(vm.uiState.value.gameResult, "count=$count seed=$seed did not finish")
            assertEquals(GameState.FINISHED, requireNotNull(store.saved).game.engine.gameState)
            assertEquals(0, scheduler.pendingCount)
        }
        assertTrue(staleLandingsChecked > 0)
    }
}
