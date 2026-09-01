package com.moguru.game.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.moguru.game.model.CellType
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.presenter.CaptureAnimationEvent
import com.moguru.game.presenter.CaptureOutcomeKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CaptureAnimationOverlayComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recompositionDoesNotRepeatCompletedCapture() {
        val completions = mutableListOf<Long>()
        val animation = animation(11)
        var alpha by mutableFloatStateOf(1f)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(Modifier.size(300.dp, 400.dp)) {
                CaptureAnimationOverlay(animation, 300.dp, 400.dp, alpha) { completions.add(it) }
            }
        }
        composeRule.mainClock.advanceTimeBy(350)
        composeRule.runOnIdle { assertEquals(emptyList<Long>(), completions) }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.runOnIdle {
            assertEquals(listOf(11L), completions)
            alpha = 0.5f
        }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.runOnIdle { assertEquals(listOf(11L), completions) }
    }

    @Test
    fun replacingAnActiveEventCancelsItsOldCompletion() {
        val completions = mutableListOf<Long>()
        var active by mutableStateOf(animation(21))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(Modifier.size(300.dp, 400.dp)) {
                CaptureAnimationOverlay(active, 300.dp, 400.dp, 1f) { completions.add(it) }
            }
        }
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.runOnIdle { active = animation(22) }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.runOnIdle { assertEquals(listOf(22L), completions) }
    }

    @Test
    fun capturePlaybackConsumesTouchesOnUnderlyingControls() {
        var clicks = 0
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().testTag("capture-back-layer").clickable { clicks++ })
                CaptureAnimationInputBlocker(animation(31).event)
            }
        }
        composeRule.onNodeWithTag("capture-back-layer").performTouchInput { click(Offset(16f, 16f)) }
        composeRule.runOnIdle { assertEquals(0, clicks) }
    }

    private fun animation(id: Long): AndroidCaptureAnimationUiState {
        val source = Position(2, 2)
        val cell = AndroidBoardCellUiState(
            position = source,
            cellType = CellType.HOT_ZONE,
            tile = null,
            foods = listOf(AndroidFoodUiState(FoodType.BEETLE_LARVA, false)),
            players = listOf(AndroidPlayerTokenUiState(0, "モグオの駒", true)),
            highlight = null,
            isCurrentPlayerCell = true,
        )
        return AndroidCaptureAnimationUiState(
            CaptureAnimationEvent(id, CaptureOutcomeKind.CAPTURED, 0, FoodType.BEETLE_LARVA, source, 0, null),
            AndroidBoardUiState(listOf(cell)),
            AndroidBoardUiState(listOf(cell.copy(foods = emptyList()))),
        )
    }
}
