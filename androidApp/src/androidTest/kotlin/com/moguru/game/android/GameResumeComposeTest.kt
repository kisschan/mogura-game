package com.moguru.game.android

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GameResumeComposeTest {
    @get:Rule val composeRule = createComposeRule()
    private fun vm(store: GameSaveRepository) =
        AndroidGameViewModel(MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler()), store)

    @Test
    fun coldLaunchOffersResumeAndCancellingOverwriteKeepsPreviousGame() {
        val store = MemoryGameSaveRepository()
        val first = vm(store).apply { startSelectedGame() }
        val checkpoint = store.load()!!.game
        val current = mutableStateOf(vm(store))
        composeRule.setContent { MoguraGameScreen(viewModel = current.value) }
        composeRule.onNodeWithText("続きから").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("game-board").assertIsDisplayed()
        composeRule.runOnIdle { current.value.returnToSetup() }
        composeRule.onNodeWithText("新しく始める").performClick()
        composeRule.onNodeWithText("ゲームスタート").performClick()
        composeRule.onNodeWithText("前のゲームの保存データに上書きします。").assertIsDisplayed()
        composeRule.onNodeWithText("戻る", useUnmergedTree = true).performClick()
        composeRule.runOnIdle { assertEquals(checkpoint, store.load()!!.game) }
        composeRule.onNodeWithText("前のゲームに戻る").performClick()
        composeRule.onNodeWithText("続きから").assertIsDisplayed()
        assertFalse(first.uiState.value.persistence.saveFailed)
    }

    @Test
    fun failedSaveBlocksPlayUntilRetrySucceeds() {
        var fail = true
        val delegate = MemoryGameSaveRepository()
        val store = object : GameSaveRepository {
            override fun load() = delegate.load()
            override fun save(game: SavedGame) {
                if (fail) throw java.io.IOException("injected")
                delegate.save(game)
            }
        }
        val viewModel = vm(store)
        composeRule.setContent { MoguraGameScreen(viewModel = viewModel) }
        composeRule.onNodeWithText("ゲームスタート").performClick()
        composeRule.onNodeWithText("保存できませんでした").assertIsDisplayed()
        composeRule.runOnIdle { fail = false }
        composeRule.onNodeWithText("再試行").performClick()
        composeRule.onNodeWithTag("game-board").assertIsDisplayed()
        composeRule.runOnIdle { assertNotNull(delegate.load()) }
    }
}
