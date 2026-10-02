package com.moguru.game.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.moguru.game.model.CellType
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.presenter.CaptureAnimationEvent
import com.moguru.game.presenter.CaptureOutcomeKind
import com.moguru.game.presenter.DigTileChoice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReducedMotionComposeTest {
    @get:Rule
    val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0.5f
    })

    @Test
    fun reducedRotationAppliesTheSelectedAngleImmediately() {
        var rotation by mutableStateOf(Rotation.DEG_270)
        var displayed = 0f
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            displayed = rememberDigPreviewRotation(Position(2, 2), TileShape.L_SHAPE,
                DigTileChoice.REVEALED, 1, rotation).value
        }
        composeRule.runOnIdle { rotation = Rotation.DEG_0 }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertEquals(0f, displayed, 0.01f) }
    }

    @Test
    fun reducedMoveSelectionRemainsVisibleAndStatic() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(Modifier.size(100.dp).background(Color.White).testTag("static-move-target")) {
                SelectedMoveTargetIndicator(Position(2, 2), Modifier.size(100.dp))
            }
        }
        composeRule.mainClock.advanceTimeBy(100)
        val before = composeRule.onNodeWithTag("static-move-target").captureToImage().toPixelMap()
        composeRule.mainClock.advanceTimeBy(250)
        val after = composeRule.onNodeWithTag("static-move-target").captureToImage().toPixelMap()
        var coloredPixels = 0
        for (y in 0 until before.height) {
            for (x in 0 until before.width) {
                assertEquals(before[x, y], after[x, y])
                if (after[x, y] != Color.White) coloredPixels++
            }
        }
        org.junit.Assert.assertTrue(coloredPixels > 0)
    }

    @Test
    fun reducedMotionKeepsTheUnresolvedDiceStaticAndStillAcceptsStop() {
        var stops = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                DiceRouletteOverlay(FoodType.EARTHWORM, listOf(1, 2), null, { stops++ }, {})
            }
        }
        composeRule.onNodeWithText("ダイスを振る").performClick()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithContentDescription("ダイスの目 1").assertIsDisplayed()
        composeRule.onNodeWithText(rouletteStopActionLabel()).performClick()
        composeRule.runOnIdle { assertEquals(1, stops) }
    }

    @Test
    fun reducedMotionShowsTheConfirmedFaceWithoutDeceleration() {
        var completions = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                DiceRouletteOverlay(FoodType.EARTHWORM, listOf(1, 2), 6, {}, { completions++ })
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithContentDescription("ダイスの目 6").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.runOnIdle { assertEquals(1, completions) }
    }

    @Test
    fun reducedCaptureCompletesOnceWithoutWaitingForMovement() {
        val source = Position(2, 2)
        val cell = AndroidBoardCellUiState(source, CellType.HOT_ZONE, null,
            listOf(AndroidFoodUiState(FoodType.BEETLE_LARVA, false)),
            listOf(AndroidPlayerTokenUiState(0, "モグオの駒", true)), null)
        val animation = AndroidCaptureAnimationUiState(
            CaptureAnimationEvent(11, CaptureOutcomeKind.CAPTURED, 0, FoodType.BEETLE_LARVA, source, 0, null),
            AndroidBoardUiState(listOf(cell)), AndroidBoardUiState(listOf(cell.copy(foods = emptyList()))),
        )
        val completions = mutableListOf<Long>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(Modifier.size(300.dp, 400.dp)) {
                CaptureAnimationOverlay(animation, 300.dp, 400.dp, 1f) { completions.add(it) }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertEquals(listOf(11L), completions) }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.runOnIdle { assertEquals(listOf(11L), completions) }
    }
}
