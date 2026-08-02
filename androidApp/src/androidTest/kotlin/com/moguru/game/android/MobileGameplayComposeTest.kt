package com.moguru.game.android

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    fun activeGameplayKeepsScoreAndDirectHudControlsVisible() {
        composeRule.onNodeWithText("ゲームスタート").performClick()

        composeRule.onNodeWithTag(HUD_SCORE_TEST_TAG)
            .assertIsDisplayed()
            .assertWidthIsAtLeast(HUD_SCORE_MIN_WIDTH)
        composeRule.onNodeWithTag(PLAYER_VISIBILITY_TOGGLE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(GAME_MENU_BUTTON_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun beginnerRulesOpenFromSetupAndReturnWithoutChangingSelections() {
        composeRule.onNodeWithText("3人").performClick()
        composeRule.onNodeWithText("3人").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        )
        composeRule.onNodeWithTag(RULES_SETUP_BUTTON_TEST_TAG).performClick()

        composeRule.onNodeWithTag(RULES_SCREEN_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(RULES_GOAL_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("4点先取").assertIsDisplayed()
        composeRule.onNodeWithTag(RULES_BACK_BUTTON_TEST_TAG).performClick()

        composeRule.onNodeWithText("プレイヤー人数").assertIsDisplayed()
        composeRule.onNodeWithText("3人").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        )
        composeRule.onNodeWithText("ゲームスタート").assertIsDisplayed()
    }

    @Test
    fun beginnerRulesCanScrollThroughTheDetailedSections() {
        composeRule.onNodeWithTag(RULES_SETUP_BUTTON_TEST_TAG).performClick()

        composeRule.onNodeWithText("エサの補充 と ゲーム終了", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("ゲームに戻る")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun audioSettingsOpenFromActiveGameplayHud() {
        composeRule.onNodeWithText("ゲームスタート").performClick()

        composeRule.onNodeWithTag(GAME_MENU_BUTTON_TEST_TAG).performClick()
        composeRule.onNodeWithTag(GAME_MENU_AUDIO_ITEM_TEST_TAG).performClick()

        composeRule.onNodeWithText("音量設定").assertIsDisplayed()
        composeRule.onNodeWithTag(BGM_VOLUME_SLIDER_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SOUND_EFFECT_VOLUME_SLIDER_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun beginnerRulesOpenFromGameplayMenuAndPreserveGameplayUiState() {
        composeRule.onNodeWithText("ゲームスタート").performClick()
        composeRule.onNodeWithTag("game-board").assertIsDisplayed()
        composeRule.onNodeWithTag(PLAYER_VISIBILITY_TOGGLE_TEST_TAG).performClick()
        composeRule.onNodeWithTag(PLAYER_VISIBILITY_TOGGLE_TEST_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "半透明表示"),
        )

        composeRule.onNodeWithTag(GAME_MENU_BUTTON_TEST_TAG).performClick()
        composeRule.onNodeWithTag(GAME_MENU_RULES_ITEM_TEST_TAG).performClick()
        composeRule.onNodeWithTag(RULES_SCREEN_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(RULES_TURN_FLOW_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(RULES_BACK_BUTTON_TEST_TAG).performClick()

        composeRule.onNodeWithTag("top-hud").assertIsDisplayed()
        composeRule.onNodeWithTag("game-board").assertIsDisplayed()
        composeRule.onNodeWithTag("action-bar").assertIsDisplayed()
        composeRule.onNodeWithTag(PLAYER_VISIBILITY_TOGGLE_TEST_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "半透明表示"),
        )
    }

    @Test
    fun newGameRemainsAvailableFromGameplayMenuWithConfirmation() {
        composeRule.onNodeWithText("ゲームスタート").performClick()

        composeRule.onNodeWithTag(GAME_MENU_BUTTON_TEST_TAG).performClick()
        composeRule.onNodeWithTag(GAME_MENU_NEW_GAME_ITEM_TEST_TAG).performClick()

        composeRule.onNodeWithText("設定画面に戻りますか？").assertIsDisplayed()
        composeRule.onNodeWithText("続ける").performClick()
        composeRule.onNodeWithTag("game-board").assertIsDisplayed()
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
