package com.moguru.game.android

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MobileGameplayComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activeGameplayShowsHudBoardAndActionBarInCompactViewport() {
        composeRule.onNodeWithText("ゲームスタート").performClick()

        composeRule.onNodeWithTag("top-hud").assertIsDisplayed()
        composeRule.onNodeWithTag("game-board").assertIsDisplayed()
        composeRule.onNodeWithTag("action-bar").assertIsDisplayed()
    }

    @Test
    fun boardDoesNotRenderVisiblePlayerNamesInActiveGameplay() {
        composeRule.onNodeWithText("ゲームスタート").performClick()

        listOf("モグオ", "モグタ", "モグミ", "モグカ").forEach { name ->
            composeRule.onAllNodesWithText(name, useUnmergedTree = true).assertCountEquals(0)
        }
    }
}
