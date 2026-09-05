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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.presenter.EatAnimationEvent
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EatAnimationOverlayComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completionRunsOnceAndRecompositionDoesNotRestartPlayback() {
        val state = animationState(actualRecovery = 3)
        val event = requireNotNull(state.eatAnimation)
        val completions = mutableListOf<Long>()
        var alpha by mutableFloatStateOf(1f)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(Modifier.size(300.dp, 400.dp)) {
                EatAnimationOverlay(state, event, 300.dp, 400.dp, alpha) { completions += it }
            }
        }

        composeRule.mainClock.advanceTimeBy(500)
        composeRule.runOnIdle { assertEquals(emptyList<Long>(), completions) }
        composeRule.mainClock.advanceTimeBy(450)
        composeRule.runOnIdle {
            assertEquals(listOf(event.id), completions)
            alpha = 0.5f
        }
        composeRule.mainClock.advanceTimeBy(900)
        composeRule.runOnIdle { assertEquals(listOf(event.id), completions) }
    }

    @Test
    fun overlayShowsOneProgrammaticIconPerActualRecoveryAndGreenLabelText() {
        val state = animationState(actualRecovery = 3)
        val event = requireNotNull(state.eatAnimation)
        composeRule.setContent {
            Box(Modifier.size(300.dp, 400.dp)) {
                EatAnimationOverlay(state, event, 300.dp, 400.dp, 1f) {}
            }
        }

        composeRule.onNodeWithTag("eat-animation-icon-0").assertExists()
        composeRule.onNodeWithTag("eat-animation-icon-1").assertExists()
        composeRule.onNodeWithTag("eat-animation-icon-2").assertExists()
        composeRule.onNodeWithTag("eat-animation-icon-3").assertDoesNotExist()
        composeRule.onNodeWithText("+3").assertExists()
        composeRule.onNodeWithTag("eat-animated-player").assertExists()
        composeRule.onNodeWithTag("eat-animated-hunger-marker").assertExists()
    }

    @Test
    fun zeroRecoveryUsesFullLabelAndNoIcons() {
        val state = animationState(actualRecovery = 0)
        val event = requireNotNull(state.eatAnimation)
        composeRule.setContent {
            Box(Modifier.size(300.dp, 400.dp)) {
                EatAnimationOverlay(state, event, 300.dp, 400.dp, 1f) {}
            }
        }

        composeRule.onNodeWithText("満腹").assertExists()
        composeRule.onNodeWithTag("eat-animation-icon-0").assertDoesNotExist()
    }

    @Test
    fun playbackConsumesTouchesOnUnderlyingControls() {
        var clicks = 0
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().testTag("eat-back-layer").clickable { clicks++ })
                EatAnimationInputBlocker(event(id = 31L, actualRecovery = 2))
            }
        }

        composeRule.onNodeWithTag("eat-back-layer").performTouchInput { click(Offset(16f, 16f)) }
        composeRule.runOnIdle { assertEquals(0, clicks) }
    }

    private fun animationState(actualRecovery: Int): AndroidGameUiState {
        val controller = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val player = controller.currentPlayer!!
        val foodType = if (actualRecovery == 0) FoodType.FROG else FoodType.MOLE_CRICKET
        if (actualRecovery > 0) repeat(4) { player.reduceHealth(isOnSurface = false) }
        val engine = controller.engine!!
        player.moveTo(SOURCE)
        while (engine.foodsAt(SOURCE).isNotEmpty()) engine.removeFoodAt(SOURCE, 0)
        engine.placeFoodAt(SOURCE, FoodCard.createDummyCards(foodType).first())
        engine.advancePhase()
        engine.advancePhase()
        check(controller.captureCurrentPositionImmediately().success)
        check(engine.currentPhase == TurnPhase.DECIDE)
        viewModel.eat()
        return viewModel.uiState.value
    }

    private fun event(id: Long, actualRecovery: Int): EatAnimationEvent =
        EatAnimationEvent(
            id = id,
            playerId = 0,
            foodType = FoodType.EARTHWORM,
            requestedRecovery = 2,
            actualRecovery = actualRecovery,
            healthBefore = 8,
            healthAfter = 8 + actualRecovery,
        )

    private companion object {
        val SOURCE = Position(2, 2)
    }
}
