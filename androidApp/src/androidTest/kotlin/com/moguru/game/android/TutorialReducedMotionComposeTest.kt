package com.moguru.game.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.moguru.game.tutorial.TutorialLessonId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Practice with system animations reduced: playback finishes by itself, once, and nothing moves. */
class TutorialReducedMotionComposeTest {
    @get:Rule
    val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0.5f
    })

    @Test
    fun practiceDiceAndCaptureFinishWithoutMotionAndTheMenuStaysReachable() {
        val practice = PracticeDriver(TutorialLessonId.DICE_ESCAPE)
        practice.runUntil { it.playState.diceRouletteResult != null }
        val generation = practice.state.playbackGeneration
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                Box(Modifier.requiredSize(360.dp, 740.dp)) {
                    MoguraGameScreen(viewModel = practice.vm)
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(FRAME_STEP_MILLIS)
        val board = boardBounds()
        // The fixed face is shown at once, without the decelerating spin.
        composeRule.onNodeWithContentDescription("ダイスの目 2").assertIsDisplayed()
        composeRule.onNodeWithTag(GAME_MENU_BUTTON_TEST_TAG).assertHasClickAction()

        // After the result pause the screen itself reports the landing, and the capture snaps to its end.
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle {
            val after = practice.state
            assertFalse(after.playState.diceRouletteActive)
            assertNull(after.captureAnimation)
            assertEquals(generation, after.playbackGeneration)
        }
        assertEquals(board, boardBounds())

        composeRule.onNodeWithTag(GAME_MENU_BUTTON_TEST_TAG).assertHasClickAction().performClick()
        composeRule.mainClock.advanceTimeBy(MENU_SETTLE_MILLIS)
        composeRule.onNodeWithTag(GAME_MENU_RESTART_CHAPTER_ITEM_TEST_TAG).performClick()
        composeRule.mainClock.advanceTimeBy(MENU_SETTLE_MILLIS)
        composeRule.runOnIdle {
            assertEquals(1, practice.state.tutorial?.chapter)
            assertFalse(practice.state.playState.diceRouletteActive)
            assertEquals(0, practice.store.saves)
        }
        assertEquals(board, boardBounds())
        composeRule.mainClock.autoAdvance = true
    }

    private fun boardBounds(): Rect =
        composeRule.onNodeWithTag("game-board", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private companion object {
        const val MENU_SETTLE_MILLIS = 64L
        const val FRAME_STEP_MILLIS = 32L
    }
}
