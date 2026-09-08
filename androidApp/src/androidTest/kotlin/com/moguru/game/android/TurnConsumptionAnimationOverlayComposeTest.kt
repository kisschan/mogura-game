package com.moguru.game.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.moguru.game.model.Position
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TurnConsumptionAnimationOverlayComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completionRunsOnceAndRecompositionDoesNotRestartPlayback() {
        val (_, state) = animationFixture()
        val event = requireNotNull(state.turnConsumptionAnimation)
        val completions = mutableListOf<Long>()
        var boardWidth by mutableFloatStateOf(300f)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(Modifier.size(boardWidth.dp, 400.dp)) {
                TurnConsumptionAnimationOverlay(state, event, boardWidth.dp, 400.dp) {
                    completions += it
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(350)
        composeRule.runOnIdle { assertEquals(emptyList<Long>(), completions) }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.runOnIdle {
            assertEquals(listOf(event.id), completions)
            boardWidth = 301f
        }
        composeRule.mainClock.advanceTimeBy(650)
        composeRule.runOnIdle { assertEquals(listOf(event.id), completions) }
    }

    @Test
    fun regularConsumptionShowsRedLabelMarkerAndAccessibleAnnouncement() {
        val (_, state) = animationFixture()
        val event = requireNotNull(state.turnConsumptionAnimation)
        composeRule.setContent {
            Box(Modifier.size(300.dp, 400.dp)) {
                TurnConsumptionAnimationOverlay(state, event, 300.dp, 400.dp) {}
                TurnConsumptionAnimationInputBlocker(event)
            }
        }

        composeRule.onNodeWithText("-1").assertExists()
        composeRule.onNodeWithTag(TURN_CONSUMPTION_MARKER_TEST_TAG).assertExists()
        composeRule.onNodeWithContentDescription("手番終了で、体力を1消耗しました").assertExists()
    }

    @Test
    fun surfaceConsumptionUsesCompactVisibleLabelAndDetailedAccessibleCopy() {
        val (_, state) = animationFixture(isOnSurface = true)
        val event = requireNotNull(state.turnConsumptionAnimation)
        composeRule.setContent {
            Box(Modifier.size(300.dp, 400.dp)) {
                TurnConsumptionAnimationOverlay(state, event, 300.dp, 400.dp) {}
                TurnConsumptionAnimationInputBlocker(event)
            }
        }

        composeRule.onNodeWithText("-2").assertExists()
        composeRule.onNodeWithContentDescription("地上で手番を終え、体力を2消耗しました").assertExists()
    }

    @Test
    fun playbackConsumesTouchesOnUnderlyingControls() {
        val (_, state) = animationFixture()
        val event = requireNotNull(state.turnConsumptionAnimation)
        var clicks = 0
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag("turn-consumption-back-layer")
                        .clickable { clicks++ },
                )
                TurnConsumptionAnimationInputBlocker(event)
            }
        }

        composeRule.onNodeWithTag("turn-consumption-back-layer")
            .performTouchInput { click(Offset(16f, 16f)) }
        composeRule.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun integratedBoardHidesStaticMarkerWhileAnimatedMarkerIsPresent() {
        val (viewModel, state) = animationFixture()
        val event = requireNotNull(state.turnConsumptionAnimation)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MoguraGameScreen(viewModel = viewModel)
        }

        composeRule.onNodeWithTag(
            "$HUNGER_MARKER_TEST_TAG_PREFIX${event.playerId}",
            useUnmergedTree = true,
        )
            .assertDoesNotExist()
        composeRule.onNodeWithTag(
            TURN_CONSUMPTION_MARKER_TEST_TAG,
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithTag(TURN_CONSUMPTION_INPUT_BLOCKER_TEST_TAG).assertExists()
    }

    private fun animationFixture(
        isOnSurface: Boolean = false,
    ): Pair<AndroidGameViewModel, AndroidGameUiState> {
        val controller = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        if (isOnSurface) controller.currentPlayer!!.moveTo(Position(2, 0))
        repeat(3) { controller.engine!!.advancePhase() }
        viewModel.finishTurn()
        return viewModel to viewModel.uiState.value
    }
}
