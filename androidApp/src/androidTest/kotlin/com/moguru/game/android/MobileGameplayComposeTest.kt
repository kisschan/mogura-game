package com.moguru.game.android

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import org.junit.Rule
import org.junit.Test

class MobileGameplayComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun audioSettingsOpenFromSetupAndKeepAdjustedVolumes() {
        composeRule.onNodeWithTag(AUDIO_SETTINGS_BUTTON_TEST_TAG).performClick()

        composeRule.onNodeWithText("音量設定").assertIsDisplayed()
        composeRule.onNodeWithTag(BGM_VOLUME_SLIDER_TEST_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(0.7f)
            }
        composeRule.onNodeWithTag(SOUND_EFFECT_VOLUME_SLIDER_TEST_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(0.8f)
            }
        composeRule.onNodeWithText("閉じる").performClick()

        composeRule.onNodeWithTag(AUDIO_SETTINGS_BUTTON_TEST_TAG).performClick()
        composeRule.onNodeWithTag(BGM_VOLUME_SLIDER_TEST_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "70%"),
        )
        composeRule.onNodeWithTag(SOUND_EFFECT_VOLUME_SLIDER_TEST_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "80%"),
        )
    }

    @Test
    fun activeGameplayShowsHudBoardAndActionBarInCompactViewport() {
        composeRule.onNodeWithText("ゲームスタート").performClick()

        composeRule.onNodeWithTag("top-hud").assertIsDisplayed()
        composeRule.onNodeWithTag("game-board").assertIsDisplayed()
        composeRule.onNodeWithTag("action-bar").assertIsDisplayed()
    }

    @Test
    fun audioSettingsOpenFromActiveGameplayHud() {
        composeRule.onNodeWithText("ゲームスタート").performClick()

        composeRule.onNodeWithTag(AUDIO_SETTINGS_BUTTON_TEST_TAG).performClick()

        composeRule.onNodeWithText("音量設定").assertIsDisplayed()
        composeRule.onNodeWithTag(BGM_VOLUME_SLIDER_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SOUND_EFFECT_VOLUME_SLIDER_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun playerVisibilityToggleSwitchesBetweenNormalAndTransparentBoardModes() {
        composeRule.onNodeWithText("ゲームスタート").performClick()

        composeRule.onNodeWithTag(PLAYER_VISIBILITY_TOGGLE_TEST_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "通常表示"),
        )
        composeRule.onNodeWithTag(PLAYER_VISIBILITY_TOGGLE_TEST_TAG).performClick()
        composeRule.onNodeWithTag(PLAYER_VISIBILITY_TOGGLE_TEST_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "半透明表示"),
        )
        composeRule.onNodeWithTag(PLAYER_VISIBILITY_TOGGLE_TEST_TAG).performClick()
        composeRule.onNodeWithTag(PLAYER_VISIBILITY_TOGGLE_TEST_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "通常表示"),
        )
    }

    @Test
    fun boardDoesNotRenderVisiblePlayerNamesInActiveGameplay() {
        composeRule.onNodeWithText("ゲームスタート").performClick()

        listOf("モグオ", "モグタ", "モグミ", "モグカ").forEach { name ->
            composeRule.onAllNodesWithText(name, useUnmergedTree = true).assertCountEquals(0)
        }
    }
}
