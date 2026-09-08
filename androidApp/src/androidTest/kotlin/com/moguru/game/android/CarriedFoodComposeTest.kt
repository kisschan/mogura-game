package com.moguru.game.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.moguru.game.model.FoodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CarriedFoodComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun carriedFoodIsASquareAtFortyPercentWidthInTheLowerHalfOfTheToken() {
        composeRule.setContent {
            BoardPlayerToken(
                player = token(FoodType.BEETLE_LARVA),
                pieceAlpha = 1f,
                modifier = Modifier.size(100.dp, 120.dp).testTag("test-player-token"),
            )
        }

        val playerBounds = composeRule.onNodeWithTag("test-player-token", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val foodBounds = carriedFood().assertIsDisplayed().fetchSemanticsNode().boundsInRoot

        assertEquals(foodBounds.width, foodBounds.height, 1f)
        assertEquals(playerBounds.width * 0.4f, foodBounds.width, 1f)
        assertEquals(playerBounds.center.x, foodBounds.center.x, 1f)
        assertTrue("The food must stay below the face", foodBounds.top >= playerBounds.center.y)
        assertTrue("The food must stay inside the player outline", foodBounds.bottom < playerBounds.bottom)
        composeRule.onNodeWithContentDescription("カブトムシの幼虫をレンコウ中", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun tokenWithoutCarriedFoodHasNoFoodOverlay() {
        composeRule.setContent {
            BoardPlayerToken(
                player = token(null),
                pieceAlpha = 1f,
                modifier = Modifier.size(100.dp, 120.dp),
            )
        }

        carriedFood().assertDoesNotExist()
    }

    @Test
    fun touchingTheCarriedTileReachesTheUnderlyingBoardAction() {
        var clicks = 0
        composeRule.setContent {
            Box(Modifier.size(100.dp, 120.dp).clickable { clicks++ }) {
                BoardPlayerToken(
                    player = token(FoodType.BEETLE_LARVA),
                    pieceAlpha = 1f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        carriedFood().performTouchInput { click(center) }

        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun changingPieceAlphaAlsoFadesTheCarriedTile() {
        var pieceAlpha by mutableFloatStateOf(1f)
        composeRule.setContent {
            Box(Modifier.size(100.dp, 120.dp).background(Color.White)) {
                BoardPlayerToken(
                    player = token(FoodType.BEETLE_LARVA),
                    pieceAlpha = pieceAlpha,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        val opaque = carriedFood().captureToImage().toPixelMap()
        composeRule.runOnIdle { pieceAlpha = 0.4f }
        val faded = carriedFood().captureToImage().toPixelMap()
        composeRule.runOnIdle { pieceAlpha = 0f }
        val transparent = carriedFood().captureToImage().toPixelMap()
        var changedPixels = 0
        var fadedPixels = 0
        for (y in 0 until opaque.height) {
            for (x in 0 until opaque.width) {
                val original = opaque[x, y]
                val reduced = faded[x, y]
                val hidden = transparent[x, y]
                if (original != reduced) changedPixels++
                if (reduced != hidden) fadedPixels++
                assertEquals("No carried tile may remain at zero piece alpha", 1f, hidden.red, 0.02f)
                assertEquals("No carried tile may remain at zero piece alpha", 1f, hidden.green, 0.02f)
                assertEquals("No carried tile may remain at zero piece alpha", 1f, hidden.blue, 0.02f)
            }
        }
        assertTrue("The carried tile must visibly respond to the transparency setting", changedPixels > 0)
        assertTrue("A partially transparent carried tile must remain visible", fadedPixels > 0)
    }

    private fun carriedFood() = composeRule.onNodeWithTag("carried-food-0", useUnmergedTree = true)

    private fun token(foodType: FoodType?) = AndroidPlayerTokenUiState(
        playerId = 0,
        accessibilityLabel = "モグオの駒",
        isCurrent = true,
        carriedFoodType = foodType,
    )
}
