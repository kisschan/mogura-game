package com.moguru.game.android

import com.moguru.game.cpu.CpuDifficulty
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AndroidCpuDifficultyTest {
    @Test fun `resumed driver uses saved difficulty instead of always using normal`() {
        val initial = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
            .apply { startNewGame(2) }.exportSnapshot()
        val pool = initial.engine.foods.values.flatten() + initial.engine.foodStock
        val food = pool.first { it.type == FoodType.BEETLE_LARVA }
        val ready = initial.copy(
            engine = initial.engine.copy(
                currentPhase = TurnPhase.CAPTURE,
                tileDiscardPile = initial.engine.tileDiscardPile + listOfNotNull(initial.pendingDigDrawnTile),
                players = initial.engine.players.mapIndexed { index, p ->
                    if (index == 0) p.copy(position = Position(2, 2), health = 5) else p
                },
                foods = mapOf(Position(2, 2) to listOf(food)), foodStock = pool - food,
            ), pendingDigDrawnTile = null,
        )
        val game = MoguraGameController.fromSnapshot(ready, FixedDiceRoller(listOf(6)), FixedShuffler())
        check(game.captureCurrentPositionImmediately().success)
        for (difficulty in listOf(CpuDifficulty.NORMAL, CpuDifficulty.HARD)) {
            val store = MemoryGameSaveRepository()
            store.save(SavedGame(game.exportSnapshot(), 1, 1, cpuPlayerIds = setOf(0), cpuDifficulties = mapOf(0 to difficulty)))
            val scheduler = ManualCpuStepScheduler()
            val vm = AndroidGameViewModel(saveRepository = store, cpuScheduler = scheduler)
            vm.resumeSavedGame()
            vm.setCpuHostResumed(true)
            vm.setCpuPlayScreenClear(true)
            scheduler.advanceBy(CPU_STEP_DELAY_MILLIS)
            val player = store.load()!!.game.engine.players[0]
            assertNull(vm.uiState.value.cpuFault)
            if (difficulty == CpuDifficulty.HARD) {
                assertTrue(player.health > 5)
                assertNull(player.carriedFood)
            } else assertEquals(food.type, player.carriedFood?.type)
        }
    }

    @Test fun `difficulty stays on seat and survives save and resume by player id`() {
        val store = MemoryGameSaveRepository()
        val vm = AndroidGameViewModel(saveRepository = store)
        vm.selectPlayerKind(0, true)
        vm.selectCpuDifficulty(0, CpuDifficulty.HARD)
        vm.selectPlayerMole(0, 1)
        assertEquals(CpuDifficulty.HARD, vm.uiState.value.setupPlayers[0].cpuDifficulty)
        vm.selectPlayerKind(0, false)
        vm.selectPlayerKind(0, true)
        assertEquals(CpuDifficulty.HARD, vm.uiState.value.setupPlayers[0].cpuDifficulty)
        vm.startSelectedGame()
        val saved = store.load()!!
        assertEquals(mapOf(1 to CpuDifficulty.HARD), saved.cpuDifficulties)
        val resumed = AndroidGameViewModel(saveRepository = store)
        resumed.resumeSavedGame()
        assertEquals(CpuDifficulty.HARD, resumed.uiState.value.setupPlayers.first { it.playerId == 1 }.cpuDifficulty)
        resumed.selectCpuDifficulty(0, CpuDifficulty.EASY)
        assertEquals(saved.cpuDifficulties, store.load()!!.cpuDifficulties)
    }

    @Test fun `count changes reset difficulty and human slots ignore difficulty changes`() {
        val vm = AndroidGameViewModel()
        vm.selectCpuDifficulty(0, CpuDifficulty.HARD)
        assertEquals(CpuDifficulty.NORMAL, vm.uiState.value.setupPlayers[0].cpuDifficulty)
        vm.selectPlayerKind(0, true)
        vm.selectCpuDifficulty(0, CpuDifficulty.HARD)
        vm.selectPlayerCount(3)
        assertTrue(vm.uiState.value.setupPlayers.all { !it.isCpu && it.cpuDifficulty == CpuDifficulty.NORMAL })
    }
}
