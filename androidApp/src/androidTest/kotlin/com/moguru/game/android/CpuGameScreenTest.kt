package com.moguru.game.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.moguru.game.cpu.CpuAction
import com.moguru.game.cpu.CpuStrategy
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CpuGameScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val boardClickTargets = hasClickAction() and hasAnyAncestor(hasTestTag("game-board"))

    private fun controller() = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())

    private fun captureReadySave(type: FoodType): SavedGame {
        val initial = controller().apply { startNewGame(2) }.exportSnapshot()
        val position = Position(2, 2)
        val pool = initial.engine.foods.values.flatten() + initial.engine.foodStock
        val target = pool.first { it.type == type }
        val ready = initial.copy(
            engine = initial.engine.copy(
                currentPhase = TurnPhase.CAPTURE,
                tileDiscardPile = initial.engine.tileDiscardPile + listOfNotNull(initial.pendingDigDrawnTile),
                players = initial.engine.players.mapIndexed { index, player ->
                    if (index == 0) player.copy(position = position) else player
                },
                foods = mapOf(position to listOf(target)),
                foodStock = pool - target,
            ),
            pendingDigDrawnTile = null,
        )
        return SavedGame(ready, 1, 1, cpuPlayerIds = setOf(0))
    }

    @Test
    fun setupOffersHumanOrCpuPerSeatAndKeepsTheKindOnTheSeat() {
        val vm = AndroidGameViewModel(controller())
        rule.setContent { MoguraGameScreen(viewModel = vm) }

        rule.onNodeWithTag(setupSeatKindTestTag(0, isCpu = false)).performScrollTo().assertIsSelected()
        rule.onNodeWithTag(setupSeatKindTestTag(1, isCpu = true)).performScrollTo().performClick()

        rule.onNodeWithTag(setupSeatKindTestTag(1, isCpu = true)).assertIsSelected()
        rule.onNodeWithTag(setupSeatKindTestTag(1, isCpu = false)).assertIsNotSelected()
        rule.onNodeWithTag(setupSeatKindTestTag(0, isCpu = true)).assertIsNotSelected()

        rule.runOnUiThread { vm.selectPlayerMole(0, 1) }
        rule.onNodeWithTag(setupSeatKindTestTag(1, isCpu = true)).assertIsSelected()
        rule.onNodeWithTag(setupSeatKindTestTag(0, isCpu = false)).assertIsSelected()

        rule.onNodeWithText("3人").performScrollTo().performClick()
        for (seat in 0..2) {
            rule.onNodeWithTag(setupSeatKindTestTag(seat, isCpu = false)).assertIsSelected()
        }
    }

    @Test
    fun cpuTurnShowsCpuGuidanceWithoutHumanControlsAndTheHumanTurnKeepsThem() {
        val game = controller()
        val scheduler = ManualCpuStepScheduler()
        val vm = AndroidGameViewModel(
            game,
            cpuStrategy = CpuStrategy { CpuAction.RevealDig(game.digTargets().first()) },
            cpuScheduler = scheduler,
        )
        rule.setContent { MoguraGameScreen(viewModel = vm) }
        rule.runOnUiThread {
            vm.setCpuHostResumed(true)
            vm.selectPlayerKind(1, true)
            vm.startSelectedGame()
        }

        // Human turn: the usual board targets are offered and nothing is labelled CPU.
        assertTrue(rule.onAllNodes(boardClickTargets).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithTag(HUD_CPU_BADGE_TEST_TAG).assertCountEquals(0)

        rule.runOnUiThread {
            vm.onCellClicked(game.digTargets().first())
            vm.confirmDigPlacement()
            if (vm.uiState.value.turnConsumptionAnimation == null) vm.finishTurn()
        }
        rule.waitUntil(15_000) {
            vm.uiState.value.cpuTurn && vm.uiState.value.turnConsumptionAnimation == null
        }

        rule.onNodeWithTag("next-action-instruction").assertTextContains("CPU", substring = true)
        rule.onNodeWithTag(HUD_CPU_BADGE_TEST_TAG).assertExists()
        rule.onAllNodes(boardClickTargets).assertCountEquals(0)
        rule.onAllNodesWithTag("primary-action").assertCountEquals(0)

        // The CPU's dig is revealed on the board, still without rotate or place buttons.
        rule.runOnUiThread { scheduler.advanceBy(CPU_STEP_DELAY_MILLIS) }
        rule.waitUntil(5_000) { vm.uiState.value.digPreviewPosition != null }
        rule.onAllNodesWithTag("dig-rotation-button").assertCountEquals(0)
        rule.onAllNodes(boardClickTargets).assertCountEquals(0)
        assertNotNull(game.pendingDigPlacement)
    }

    @Test
    fun cpuRouletteOffersNoButtonsAndResolvesOnceAfterItsLanding() {
        val store = MemoryGameSaveRepository().apply { save(captureReadySave(FoodType.EARTHWORM)) }
        val scheduler = ManualCpuStepScheduler()
        val script = ArrayDeque<CpuAction>(listOf(CpuAction.Capture(0)))
        val vm = AndroidGameViewModel(
            controller(), store,
            cpuStrategy = CpuStrategy { script.removeFirstOrNull() },
            cpuScheduler = scheduler,
        )
        rule.setContent { MoguraGameScreen(viewModel = vm) }
        rule.runOnUiThread {
            vm.setCpuHostResumed(true)
            vm.resumeSavedGame()
        }
        rule.waitForIdle()
        rule.runOnUiThread { scheduler.advanceBy(CPU_STEP_DELAY_MILLIS) }

        rule.onNodeWithTag(DICE_ROULETTE_CPU_STATUS_TEST_TAG).assertExists()
        rule.onAllNodesWithText(roulettePrimaryActionLabel(listOf(1, 2))).assertCountEquals(0)
        rule.onAllNodesWithText(rouletteStopActionLabel()).assertCountEquals(0)

        // Spin and roll back to back: only the timer may start the dice and fix the face.
        rule.runOnUiThread {
            scheduler.advanceBy(CPU_CAPTURE_STAGE_MILLIS)
            scheduler.advanceBy(CPU_CAPTURE_STAGE_MILLIS)
        }
        assertEquals(6, vm.uiState.value.playState.diceRouletteResult)
        assertEquals(0, scheduler.pendingCount)

        rule.waitUntil(20_000) {
            val state = vm.uiState.value
            state.playState.captureOutcome != null && state.captureAnimation == null
        }
        assertEquals(false, vm.uiState.value.playState.diceRouletteActive)
        assertEquals(1, scheduler.pendingCount)
    }

    @Test
    fun boardAndHudStayPutBetweenHumanAndCpuTurns() {
        val game = controller()
        val vm = AndroidGameViewModel(game, cpuStrategy = CpuStrategy { null }, cpuScheduler = ManualCpuStepScheduler())
        rule.setContent {
            Box(Modifier.requiredSize(360.dp, 740.dp)) { MoguraGameScreen(viewModel = vm) }
        }
        rule.runOnUiThread {
            vm.setCpuHostResumed(true)
            vm.selectPlayerKind(1, true)
            vm.startSelectedGame()
        }
        val humanBoard = rule.onNodeWithTag("game-board").getUnclippedBoundsInRoot()
        val humanHud = rule.onNodeWithTag("top-hud").getUnclippedBoundsInRoot()

        rule.runOnUiThread {
            vm.onCellClicked(game.digTargets().first())
            vm.confirmDigPlacement()
            if (vm.uiState.value.turnConsumptionAnimation == null) vm.finishTurn()
        }
        rule.waitUntil(15_000) {
            vm.uiState.value.cpuTurn && vm.uiState.value.turnConsumptionAnimation == null
        }

        assertEquals(humanBoard, rule.onNodeWithTag("game-board").getUnclippedBoundsInRoot())
        assertEquals(humanHud, rule.onNodeWithTag("top-hud").getUnclippedBoundsInRoot())
        rule.onNodeWithTag("top-hud").assertHeightIsEqualTo(MOBILE_PLAY_HUD_HEIGHT)
        val shell = rule.onNodeWithTag("gameplay-shell").getUnclippedBoundsInRoot()
        val actionBar = rule.onNodeWithTag("action-bar").getUnclippedBoundsInRoot()
        assertTrue(actionBar.bottom <= shell.bottom)
        rule.onNodeWithTag("next-action-instruction").assertTextContains("CPU", substring = true)
    }
}
