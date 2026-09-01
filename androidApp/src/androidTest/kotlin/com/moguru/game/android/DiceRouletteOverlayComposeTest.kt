package com.moguru.game.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.moguru.game.model.FoodType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DiceRouletteOverlayComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rollButtonAcceptsTapContainingSmallPointerMove() {
        showOverlay()
        composeRule.onNodeWithText(ROLL_BUTTON_TEXT).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithText(ROLL_BUTTON_TEXT).performTouchInput {
            down(center)
            moveBy(Offset(1f, 1f))
            up()
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithText(rouletteStopActionLabel()).assertIsDisplayed()
    }

    @Test
    fun backdropStillBlocksTapFromReachingBackLayer() {
        var backClicks = 0
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(BACK_LAYER_TAG)
                            .clickable { backClicks++ },
                    )
                    DiceRouletteOverlay(
                        foodType = FoodType.EARTHWORM,
                        escapeRolls = listOf(1, 2),
                        targetFace = null,
                        onTap = {},
                        onFinished = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(BACK_LAYER_TAG).performTouchInput {
            click(Offset(8f, 8f))
        }

        composeRule.runOnIdle {
            assertEquals(0, backClicks)
        }
    }

    private fun showOverlay() {
        composeRule.setContent {
            MaterialTheme {
                DiceRouletteOverlay(
                    foodType = FoodType.EARTHWORM,
                    escapeRolls = listOf(1, 2),
                    targetFace = null,
                    onTap = {},
                    onFinished = {},
                )
            }
        }
    }

    private companion object {
        const val ROLL_BUTTON_TEXT = "ダイスを振る"
        const val BACK_LAYER_TAG = "dice-roulette-back-layer"
    }
}
