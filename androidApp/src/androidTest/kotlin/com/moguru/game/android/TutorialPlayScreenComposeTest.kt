package com.moguru.game.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.tutorial.TutorialLessonId
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Practice on the real play screen (requirements 16.1, 16.7): the HUD menu's restart and exit stay
 * reachable over dice and playback, the board matches a match's, and entry and completion work.
 */
class TutorialPlayScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private enum class Playback(val blockerTag: String) {
        CAPTURE("capture-animation-input-blocker"),
        MEAL(EAT_ANIMATION_INPUT_BLOCKER_TEST_TAG),
        TURN_CONSUMPTION(TURN_CONSUMPTION_INPUT_BLOCKER_TEST_TAG);

        fun shown(state: AndroidGameUiState): Boolean = when (this) {
            CAPTURE -> state.captureAnimation != null
            MEAL -> state.eatAnimation != null
            TURN_CONSUMPTION -> state.turnConsumptionAnimation != null
        }
    }

    // ---- The HUD menu over the dice overlay ----

    @Test
    fun practiceMenuRestartsTheChapterFromTheDiceOverlay() {
        val practice = PracticeDriver(TutorialLessonId.DICE_ESCAPE)
        practice.runUntil { it.playState.diceRouletteActive }
        show(practice.vm, width = 360, height = 740, fontScale = 1.5f)

        // The overlay still offers its own control, below the uncovered HUD.
        val roll = composeRule.onNodeWithText(roulettePrimaryActionLabel(listOf(1, 2))).assertIsDisplayed()
        assertTrue(roll.fetchSemanticsNode().boundsInRoot.top >= bounds("top-hud").bottom)
        assertFitsViewport(roll)

        openPracticeMenu()
        composeRule.onNodeWithTag(GAME_MENU_RESTART_CHAPTER_ITEM_TEST_TAG).performClick()
        composeRule.runOnIdle {
            assertFalse(practice.state.playState.diceRouletteActive)
            assertEquals(1, practice.state.tutorial?.chapter)
            assertEquals(0, practice.store.saves)
        }
    }

    @Test
    fun practiceMenuLeavesPracticeFromTheDiceOverlay() {
        val practice = PracticeDriver(TutorialLessonId.DICE_ESCAPE)
        practice.runUntil { it.playState.diceRouletteActive }
        show(practice.vm, width = 390, height = 844, fontScale = 1.2f)

        openPracticeMenu()
        composeRule.onNodeWithTag(GAME_MENU_EXIT_PRACTICE_ITEM_TEST_TAG).performClick()
        composeRule.runOnIdle {
            assertNull(practice.state.tutorial)
            assertFalse(practice.state.isGameStarted)
            assertEquals(0, practice.store.saves)
        }
        composeRule.onNodeWithTag(TUTORIAL_SETUP_BUTTON_TEST_TAG).assertExists()
    }

    @Test
    fun matchDiceOverlayStillBlocksTheHudMenu() {
        val store = MemoryGameSaveRepository().apply { save(captureReadySave()) }
        val vm = AndroidGameViewModel(MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler()), store)
        vm.resumeSavedGame()
        vm.capture()
        assertTrue(vm.uiState.value.playState.diceRouletteActive)
        show(vm)

        composeRule.onNodeWithTag(GAME_MENU_BUTTON_TEST_TAG).performClick()
        composeRule.onAllNodesWithTag(GAME_MENU_RULES_ITEM_TEST_TAG).assertCountEquals(0)
        composeRule.runOnIdle { assertTrue(vm.uiState.value.playState.diceRouletteActive) }
    }

    // ---- The HUD menu during capture, meal and turn-consumption playback ----

    @Test
    fun practiceMenuRestartsTheChapterDuringTheCapturePlayback() {
        assertPracticeMenuReachableDuring(Playback.CAPTURE, GAME_MENU_RESTART_CHAPTER_ITEM_TEST_TAG, 360, 740, 1.5f)
    }

    @Test
    fun practiceMenuLeavesPracticeDuringTheMealPlayback() {
        assertPracticeMenuReachableDuring(Playback.MEAL, GAME_MENU_EXIT_PRACTICE_ITEM_TEST_TAG, 390, 844, 1.2f)
    }

    @Test
    fun practiceMenuRestartsTheChapterDuringTheTurnConsumptionPlayback() {
        assertPracticeMenuReachableDuring(Playback.TURN_CONSUMPTION, GAME_MENU_RESTART_CHAPTER_ITEM_TEST_TAG, 360, 740, 1f)
    }

    private fun assertPracticeMenuReachableDuring(
        playback: Playback,
        menuItem: String,
        width: Int,
        height: Int,
        fontScale: Float,
    ) {
        // Chapter 3 of the basic lesson captures a larva, eats it and ends the turn.
        val practice = PracticeDriver(TutorialLessonId.BASIC, startChapter = 2)
        practice.runUntil { playback.shown(it) }
        val generation = practice.state.playbackGeneration
        composeRule.mainClock.autoAdvance = false
        try {
            show(practice.vm, width, height, fontScale)
            composeRule.mainClock.advanceTimeByFrame()

            // Screen readers reach the HUD menu; the board and controls stay hidden as in a match.
            composeRule.onNodeWithTag(playback.blockerTag).assertExists()
            composeRule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("action-bar"))).assertCountEquals(0)
            composeRule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("board-viewport"))).assertCountEquals(0)
            assertBoardTouchesHud()

            // Touch reaches it as well: the playback layer starts below the HUD.
            openPracticeMenu()
            composeRule.onNodeWithTag(menuItem).performClick()
            composeRule.mainClock.advanceTimeBy(MENU_SETTLE_MILLIS)
            composeRule.runOnIdle {
                val after = practice.state
                assertFalse("$playback is still shown", playback.shown(after))
                assertTrue(after.playbackGeneration > generation)
                if (menuItem == GAME_MENU_EXIT_PRACTICE_ITEM_TEST_TAG) {
                    assertNull(after.tutorial)
                } else {
                    assertEquals(3, after.tutorial?.chapter)
                }
                assertEquals(0, practice.store.saves)
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    // ---- The board stays where a match puts it, at each viewport and text size ----

    @Test
    fun practiceLayoutMatchesTheMatchAt360x740() = assertPracticeLayout(360, 740, 1f)

    @Test
    fun practiceLayoutMatchesTheMatchAt360x740With120PercentText() = assertPracticeLayout(360, 740, 1.2f)

    @Test
    fun practiceLayoutMatchesTheMatchAt360x740With150PercentText() = assertPracticeLayout(360, 740, 1.5f)

    @Test
    fun practiceLayoutMatchesTheMatchAt390x844() = assertPracticeLayout(390, 844, 1f)

    @Test
    fun practiceLayoutMatchesTheMatchAt390x844With120PercentText() = assertPracticeLayout(390, 844, 1.2f)

    @Test
    fun practiceLayoutMatchesTheMatchAt390x844With150PercentText() = assertPracticeLayout(390, 844, 1.5f)

    private fun assertPracticeLayout(width: Int, height: Int, fontScale: Float) {
        val match = AndroidGameViewModel(MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler()))
            .apply { startNewGame(2) }
        val practice = PracticeDriver(TutorialLessonId.BASIC)
        val screen = show(match, width, height, fontScale)
        val matchHud = bounds("top-hud")
        val matchBoard = bounds("game-board")

        composeRule.runOnIdle { screen.value = practice.vm }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TUTORIAL_NEXT_TEST_TAG).assertIsDisplayed()
        assertRectEquals("HUD", matchHud, bounds("top-hud"))
        assertRectEquals("board", matchBoard, bounds("game-board"))
        listOf("top-hud", "game-board", "action-bar", TUTORIAL_NEXT_TEST_TAG, GAME_MENU_BUTTON_TEST_TAG).forEach {
            assertFitsViewport(composeRule.onNodeWithTag(it, useUnmergedTree = true))
        }
        assertTextDoesNotOverflow(composeRule.onNodeWithTag("next-action-instruction", useUnmergedTree = true))

        // Through the first chapter's pages, dig, move, playback and opponent turn, nothing moves
        // and the menu stays reachable.
        composeRule.mainClock.autoAdvance = false
        try {
            var steps = 0
            while (practice.state.tutorial?.chapter == 1) {
                assertTrue("practice stopped in chapter 1", composeRule.runOnIdle { practice.step() })
                composeRule.mainClock.advanceTimeBy(FRAME_STEP_MILLIS)
                composeRule.waitForIdle()
                assertRectEquals("board", matchBoard, bounds("game-board"))
                assertRectEquals("HUD", matchHud, bounds("top-hud"))
                assertFitsViewport(composeRule.onNodeWithTag("action-bar", useUnmergedTree = true))
                // Read from the merged tree, as screen readers do.
                composeRule.onNodeWithTag(GAME_MENU_BUTTON_TEST_TAG).assertHasClickAction()
                assertTrue("chapter 1 did not end", ++steps < 200)
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    // ---- Entry, 「次へ」 and completion ----

    @Test
    fun practiceOpensFromSetupAndLeavesTheMatchSaveAlone() {
        val store = CountingGameSaveRepository()
        val vm = AndroidGameViewModel(saveRepository = store, cpuScheduler = ManualCpuStepScheduler())
        vm.setCpuHostResumed(true)
        show(vm)

        composeRule.onNodeWithTag(TUTORIAL_SETUP_BUTTON_TEST_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(TUTORIAL_MENU_TEST_TAG).assertIsDisplayed()
        TutorialLessonId.entries.forEach {
            composeRule.onNodeWithTag(tutorialLessonStartTestTag(it)).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithTag(tutorialLessonStartTestTag(TutorialLessonId.SURFACE_HEALTH)).performScrollTo().performClick()
        composeRule.onNodeWithTag("game-board").assertIsDisplayed()
        composeRule.onNodeWithTag(TUTORIAL_NEXT_TEST_TAG).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(2, vm.uiState.value.tutorial?.page) }

        openPracticeMenu()
        composeRule.onNodeWithTag(GAME_MENU_EXIT_PRACTICE_ITEM_TEST_TAG).performClick()
        composeRule.onNodeWithTag(TUTORIAL_SETUP_BUTTON_TEST_TAG).assertExists()
        composeRule.runOnIdle {
            assertNull(vm.uiState.value.tutorial)
            assertEquals(1, store.loads)
            assertEquals(0, store.saves)
        }
    }

    @Test
    fun nextLeftOnScreenAfterTheLessonChangedDoesNotAdvanceTheNewLesson() {
        val practice = PracticeDriver(TutorialLessonId.BASIC)
        composeRule.mainClock.autoAdvance = false
        try {
            show(practice.vm)
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithTag(TUTORIAL_NEXT_TEST_TAG).assertIsDisplayed()

            // No frame runs, so the button still carries the previous lesson's page when it is tapped.
            composeRule.runOnIdle { practice.vm.startTutorial(TutorialLessonId.SURFACE_HEALTH, fromBeginning = true) }
            val replaced = practice.state.tutorial
            composeRule.onNodeWithTag(TUTORIAL_NEXT_TEST_TAG).performClick()
            composeRule.runOnIdle { assertEquals(replaced, practice.state.tutorial) }

            composeRule.mainClock.advanceTimeBy(FRAME_STEP_MILLIS)
            composeRule.onNodeWithTag(TUTORIAL_NEXT_TEST_TAG).performClick()
            composeRule.runOnIdle { assertEquals(2, practice.state.tutorial?.page) }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun completionOffersRetryLessonsAndSetupWithoutTheMatchResult() {
        val practice = PracticeDriver(TutorialLessonId.SURFACE_HEALTH)
        practice.runUntil { it.tutorialCompletion != null }
        show(practice.vm, fontScale = 1.5f)

        composeRule.onNodeWithTag(TUTORIAL_COMPLETE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("レッスンクリア！").assertIsDisplayed()
        listOf(TUTORIAL_COMPLETE_RETRY_TEST_TAG, TUTORIAL_COMPLETE_LESSONS_TEST_TAG, TUTORIAL_COMPLETE_SETUP_TEST_TAG).forEach {
            composeRule.onNodeWithTag(it).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
        composeRule.runOnIdle {
            assertNull(practice.state.gameResult)
            assertFalse(practice.state.showGameResultOverlay)
        }

        composeRule.onNodeWithTag(TUTORIAL_COMPLETE_LESSONS_TEST_TAG).performClick()
        composeRule.onNodeWithTag(TUTORIAL_MENU_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("クリア済み", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, practice.store.saves) }
    }

    // ---- Log history ----

    @Test
    fun practiceHistoryCopiesEveryEntryWithoutMovingThePracticeOn() {
        // Chapter 3 starts after replaying chapters 1 and 2, so its history already holds their events.
        val practice = PracticeDriver(TutorialLessonId.BASIC, startChapter = 2)
        val clipboard = RecordingClipboard()
        show(practice.vm, width = 360, height = 740, fontScale = 1.5f, clipboard = clipboard)

        composeRule.onNodeWithText("履歴").performClick()
        composeRule.onNodeWithTag("log-history-drawer").assertIsDisplayed()
        val before = composeRule.runOnIdle { practice.state }
        assertTrue(before.logs.isNotEmpty())
        composeRule.onNodeWithTag(LOG_HISTORY_COPY_TEST_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LOG_HISTORY_COPY_STATUS_TEST_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("全${before.logs.size}件をコピーしました")
        composeRule.onNodeWithTag("log-history-drawer").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(before.logs, clipboard.writtenTexts.single().split("\n"))
            assertEquals(0, clipboard.reads)
            assertEquals(before, practice.state)
            assertEquals(0, practice.store.saves)
        }
    }

    // ---- Helpers ----

    private fun show(
        vm: AndroidGameViewModel,
        width: Int = 360,
        height: Int = 740,
        fontScale: Float = 1f,
        clipboard: RecordingClipboard = RecordingClipboard(),
    ): MutableState<AndroidGameViewModel> {
        val screen = mutableStateOf(vm)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = fontScale),
                LocalClipboard provides clipboard,
            ) {
                Box(Modifier.requiredSize(width.dp, height.dp).testTag(VIEWPORT_TAG)) {
                    MoguraGameScreen(viewModel = screen.value)
                }
            }
        }
        return screen
    }

    /** Opens the HUD menu by touch and checks the practice items are offered instead of 「新規ゲーム」. */
    private fun openPracticeMenu() {
        composeRule.onNodeWithTag(GAME_MENU_BUTTON_TEST_TAG)
            .assertHasClickAction()
            .assert(hasContentDescription("ゲームメニュー"))
            .performClick()
        if (!composeRule.mainClock.autoAdvance) composeRule.mainClock.advanceTimeBy(MENU_SETTLE_MILLIS)
        composeRule.onNodeWithTag(GAME_MENU_PRACTICE_INFO_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(GAME_MENU_RESTART_CHAPTER_ITEM_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(GAME_MENU_EXIT_PRACTICE_ITEM_TEST_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTag(GAME_MENU_NEW_GAME_ITEM_TEST_TAG).assertCountEquals(0)
    }

    /** Bounds from the unmerged tree, which still holds the board while playback hides it from screen readers. */
    private fun bounds(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun assertBoardTouchesHud() {
        assertEquals(bounds("top-hud").bottom, bounds("game-board").top, 0.5f)
    }

    private fun assertFitsViewport(node: SemanticsNodeInteraction) {
        val viewport = bounds(VIEWPORT_TAG)
        val bounds = node.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("must fit horizontally", bounds.left >= viewport.left - 0.5f && bounds.right <= viewport.right + 0.5f)
        assertTrue("must fit vertically", bounds.top >= viewport.top - 0.5f && bounds.bottom <= viewport.bottom + 0.5f)
    }

    private fun assertTextDoesNotOverflow(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayouts ->
            assertTrue(getLayouts(layouts))
        }
        val layout = layouts.single()
        assertFalse("practice guidance must remain readable", layout.hasVisualOverflow)
        repeat(layout.lineCount) { line -> assertFalse(layout.isLineEllipsized(line)) }
    }

    private fun assertRectEquals(label: String, expected: Rect, actual: Rect) {
        assertEquals("$label left", expected.left, actual.left, 0.5f)
        assertEquals("$label top", expected.top, actual.top, 0.5f)
        assertEquals("$label right", expected.right, actual.right, 0.5f)
        assertEquals("$label bottom", expected.bottom, actual.bottom, 0.5f)
    }

    private fun captureReadySave(): SavedGame {
        val initial = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
            .apply { startNewGame(2) }.exportSnapshot()
        val position = Position(2, 2)
        val pool = initial.engine.foods.values.flatten() + initial.engine.foodStock
        val target = pool.first { it.type == FoodType.EARTHWORM }
        val ready = initial.copy(
            engine = initial.engine.copy(
                currentPhase = TurnPhase.CAPTURE,
                tileDiscardPile = initial.engine.tileDiscardPile + listOfNotNull(initial.pendingDigDrawnTile),
                players = initial.engine.players.mapIndexed { index, player ->
                    if (index == 0) player.copy(position = position) else player
                },
                foods = mapOf(position to listOf(target)),
                foodStock = pool - target,
            ),
            pendingDigDrawnTile = null,
        )
        return SavedGame(ready, 1, 1)
    }

    private companion object {
        const val VIEWPORT_TAG = "tutorial-play-screen-test-viewport"
        const val MENU_SETTLE_MILLIS = 64L
        const val FRAME_STEP_MILLIS = 32L
    }
}
