package com.moguru.game.android

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.presenter.DigTileChoice
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import com.moguru.game.util.Shuffler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActionGuidanceComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun moveGuidanceAndButtonsFitIn360By740Viewport() {
        assertMoveGuidanceFitsViewport(width = 360, height = 740)
    }

    @Test
    fun moveGuidanceAndButtonsFitIn390By844Viewport() {
        assertMoveGuidanceFitsViewport(width = 390, height = 844)
    }

    @Test
    fun moveGuidanceAndButtonsFitIn360By740ViewportAt120PercentFontScale() {
        assertMoveGuidanceFitsViewport(width = 360, height = 740, fontScale = 1.2f)
    }

    @Test
    fun moveGuidanceAndButtonsFitIn360By740ViewportAt150PercentFontScale() {
        assertMoveGuidanceFitsViewport(width = 360, height = 740, fontScale = 1.5f)
    }

    @Test
    fun initialDigGuidanceAndLatestEventAreReadableAt360By740Viewport() {
        assertInitialDigGuidanceIsReadable(width = 360, height = 740)
    }

    @Test
    fun initialDigGuidanceAndLatestEventAreReadableAt390By844Viewport() {
        assertInitialDigGuidanceIsReadable(width = 390, height = 844)
    }

    @Test
    fun initialDigGuidanceAndLatestEventAreReadableAt360By740ViewportAt150PercentFontScale() {
        assertInitialDigGuidanceIsReadable(width = 360, height = 740, fontScale = 1.5f)
    }

    @Test
    fun initialDigGuidanceAndLatestEventAreReadableAt390By844ViewportAt150PercentFontScale() {
        assertInitialDigGuidanceIsReadable(width = 390, height = 844, fontScale = 1.5f)
    }

    @Test
    fun digPlacementGuidanceEventAndCandidateLabelsAreReadableAt360By740Viewport() {
        assertDigPlacementIsReadable(width = 360, height = 740)
    }

    @Test
    fun digPlacementGuidanceEventAndCandidateLabelsAreReadableAt390By844Viewport() {
        assertDigPlacementIsReadable(width = 390, height = 844)
    }

    @Test
    fun digPlacementGuidanceEventAndCandidateLabelsAreReadableAt360By740ViewportAt150PercentFontScale() {
        assertDigPlacementIsReadable(width = 360, height = 740, fontScale = 1.5f)
    }

    @Test
    fun digPlacementGuidanceEventAndCandidateLabelsAreReadableAt390By844ViewportAt150PercentFontScale() {
        assertDigPlacementIsReadable(width = 390, height = 844, fontScale = 1.5f)
    }

    @Test
    fun boardStaysDirectlyBelowHudThroughDigAndNextTurnAt360By740Viewport() {
        assertBoardStaysAnchoredThroughDigAndNextTurn(width = 360, height = 740)
    }

    @Test
    fun boardStaysDirectlyBelowHudThroughDigAndNextTurnAt390By844Viewport() {
        assertBoardStaysAnchoredThroughDigAndNextTurn(width = 390, height = 844)
    }

    @Test
    fun boardStaysDirectlyBelowHudThroughDigAndNextTurnAt360By740ViewportAt150PercentFontScale() {
        assertBoardStaysAnchoredThroughDigAndNextTurn(width = 360, height = 740, fontScale = 1.5f)
    }

    @Test
    fun boardStaysDirectlyBelowHudThroughDigAndNextTurnAt390By844ViewportAt150PercentFontScale() {
        assertBoardStaysAnchoredThroughDigAndNextTurn(width = 390, height = 844, fontScale = 1.5f)
    }

    @Test
    fun boardStaysDirectlyBelowHudThroughCaptureAndItsAnimationAt360By740ViewportAt150PercentFontScale() {
        assertBoardStaysAnchoredThroughCapture(width = 360, height = 740, fontScale = 1.5f)
    }

    @Test
    fun boardStaysDirectlyBelowHudThroughCaptureAndItsAnimationAt390By844ViewportAt150PercentFontScale() {
        assertBoardStaysAnchoredThroughCapture(width = 390, height = 844, fontScale = 1.5f)
    }

    @Test
    fun logHistoryShowsNewestFirstAndScrollsWithoutTruncationAtCompactLargeTextViewport() {
        val fixture = historyFixture()
        val expectedLogs = fixture.viewModel.uiState.value.logs.asReversed()
        assertTrue("History must include events older than the latest five", expectedLogs.size > 5)
        assertEquals(fixture.controller.logs.asReversed(), expectedLogs)
        show(fixture, width = 360, height = 740, fontScale = 1.5f)
        val collapsedBounds = gameplayBounds()

        composeRule.onNodeWithText("履歴").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("log-history-drawer").assertIsDisplayed()
        composeRule.onNodeWithTag("log-history-count", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("履歴（${expectedLogs.size}件）")
        assertGameplayBoundsEqual(collapsedBounds, gameplayBounds())
        assertHistoryPopupPlacement()
        val historyList = composeRule.onNodeWithTag("log-history-list")
            .assertIsDisplayed()

        expectedLogs.forEachIndexed { index, log ->
            historyList.performScrollToIndex(index)
            val entry = composeRule.onNodeWithTag("log-history-entry-$index", useUnmergedTree = true)
                .assertIsDisplayed()
                .assertTextEquals(log)
            assertTextDoesNotOverflow(entry)
            assertFitsInside(entry, historyList)
        }

        composeRule.onNodeWithText("閉じる").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("log-history-drawer").assertDoesNotExist()
        assertGameplayBoundsEqual(collapsedBounds, gameplayBounds())
    }

    @Test
    fun outsideTapDismissesLogHistoryWithoutClickingThroughToItsButton() {
        val fixture = historyFixture()
        show(fixture)
        val logsBeforeTap = fixture.viewModel.uiState.value.logs
        val historyButtonBounds = composeRule.onNodeWithText("履歴")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsOnScreen()

        composeRule.onNodeWithText("履歴").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("log-history-drawer").assertIsDisplayed()

        tapScreen(historyButtonBounds.center)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("log-history-drawer").assertDoesNotExist()
        composeRule.onNodeWithText("履歴").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(logsBeforeTap, fixture.viewModel.uiState.value.logs) }
    }

    @Test
    fun logHistoryDismissesWithBackWithoutChangingGameplayBounds() {
        val fixture = historyFixture()
        show(fixture)
        val collapsedBounds = gameplayBounds()

        composeRule.onNodeWithText("履歴").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("log-history-drawer").assertIsDisplayed()

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("log-history-drawer").assertDoesNotExist()
        assertGameplayBoundsEqual(collapsedBounds, gameplayBounds())
    }

    @Test
    fun stateChangeKeepsTheFullInstructionAndLatestEventVisibleTogether() {
        val fixture = initialDigTShapeFixture()
        val digTarget = fixture.controller.digTargets().single()
        show(fixture)

        assertNextInstruction(fixture)
        assertLatestEventIsReadable(fixture)

        composeRule.runOnIdle { fixture.viewModel.onCellClicked(digTarget) }
        composeRule.waitForIdle()

        val updatedState = fixture.viewModel.uiState.value
        assertTrue(updatedState.showDigControls)
        assertNextInstruction(fixture)
        assertLatestEventIsReadable(fixture)
        composeRule.onNodeWithTag("next-action-toggle").assertDoesNotExist()
        composeRule.onNodeWithText("履歴").assertIsDisplayed()
    }

    @Test
    fun currentInstructionIsSeparateFromTheOldLogAndHistoryRemainsAvailable() {
        val fixture = moveFixture()
        val logs = fixture.viewModel.uiState.value.logs
        val oldLog = logs.last()
        val expectedInstruction = actionBarInstruction(fixture.viewModel.uiState.value)
        assertNotEquals(oldLog, expectedInstruction)
        show(fixture)

        assertNextInstruction(fixture)
        composeRule.onNodeWithTag("latest-event", useUnmergedTree = true).assertIsDisplayed().assertTextEquals(oldLog)
        composeRule.onNodeWithText("履歴").performClick()
        composeRule.onNodeWithTag("log-history-drawer").assertIsDisplayed()
        composeRule.onAllNodesWithText(oldLog).assertCountEquals(2)
        composeRule.runOnIdle { assertEquals(logs, fixture.viewModel.uiState.value.logs) }
        composeRule.onNodeWithText("閉じる").performClick()
        assertNextInstruction(fixture)
    }

    @Test
    fun captureGuidanceShowsDistinctSkipAndEndTurnActions() {
        val fixture = moveFixture()
        fixture.controller.currentPlayer!!.moveTo(RIGHT)
        fixture.controller.engine!!.advancePhase()
        fixture.publishPreparedState()
        assertEquals(TurnPhase.CAPTURE, fixture.controller.engine!!.currentPhase)
        show(fixture)

        assertNextInstruction(fixture)
        assertTextFits("捕獲しない")
        assertTextFits("手番終了")
        assertTextFits("残りを省略")
        composeRule.onNodeWithText("スキップ").assertDoesNotExist()
        composeRule.onNodeWithText("移動しない").assertDoesNotExist()
    }

    @Test
    fun endPhaseOffersOnlyEndTurnWithoutADuplicateSkipAction() {
        val fixture = newFixture()
        repeat(3) { fixture.controller.engine!!.advancePhase() }
        fixture.publishPreparedState()
        assertEquals(TurnPhase.END, fixture.controller.engine!!.currentPhase)
        show(fixture)

        assertNextInstruction(fixture)
        assertTextFits("手番終了")
        composeRule.onAllNodes(hasText("手番終了", substring = true) and hasClickAction()).assertCountEquals(1)
        composeRule.onNodeWithTag("primary-action").assertIsDisplayed()
        listOf("スキップ", "移動しない", "捕獲しない", "移動へ進む", "残りを省略").forEach { label ->
            composeRule.onNodeWithText(label, substring = true).assertDoesNotExist()
        }
        composeRule.runOnIdle {
            assertEquals(listOf(AndroidVisibleAction.END_TURN), fixture.viewModel.uiState.value.visibleActions)
        }
    }

    @Test
    fun pendingDigPlacementKeepsTheNextInstructionAndPlacementButtonVisible() {
        val fixture = newFixture()
        fixture.viewModel.onCellClicked(fixture.controller.digTargets().single())
        assertTrue(fixture.viewModel.uiState.value.showDigControls)
        show(fixture)

        assertNextInstruction(fixture)
        assertTextFits("置く")
        composeRule.onNodeWithTag("latest-event", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun capturedFoodKeepsDecisionGuidanceVisibleBesideTheResult() {
        val fixture = moveFixture()
        fixture.controller.currentPlayer!!.moveTo(RIGHT)
        fixture.controller.engine!!.advancePhase()
        assertTrue(fixture.controller.captureCurrentPositionImmediately().success)
        fixture.publishPreparedState()
        assertEquals(TurnPhase.DECIDE, fixture.controller.engine!!.currentPhase)
        show(fixture)

        assertNextInstruction(fixture)
        assertTextFits("タベる")
        assertTextFits("レンコウ")
        composeRule.onNodeWithTag("latest-event", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("スキップ").assertDoesNotExist()
        composeRule.onNodeWithText("手番終了", substring = true).assertDoesNotExist()

        val latestEvent = composeRule.onNodeWithTag("latest-event", useUnmergedTree = true)
        assertTextDoesNotOverflow(latestEvent)
        assertFitsInside(latestEvent, composeRule.onNodeWithTag("action-bar"))
        assertFitsInsideClickTarget(latestEvent)
        composeRule.onNodeWithText("履歴").assertIsDisplayed()
        composeRule.onNodeWithTag("next-action-toggle").assertDoesNotExist()
    }

    private fun assertBoardStaysAnchoredThroughDigAndNextTurn(width: Int, height: Int, fontScale: Float = 1f) {
        val fixture = initialDigTShapeFixture()
        val initialPlayerId = fixture.controller.currentPlayer!!.id
        show(fixture, width, height, fontScale)
        val initialBoard = boardBounds()
        assertBoardIsUnchangedBelowHud(initialBoard)
        assertEquals(TurnPhase.DIG, fixture.controller.engine!!.currentPhase)
        composeRule.mainClock.autoAdvance = false
        try {
            updateGameplay {
                fixture.viewModel.onCellClicked(fixture.controller.digTargets().single())
            }
            assertTrue(fixture.viewModel.uiState.value.showDigControls)
            assertBoardIsUnchangedBelowHud(initialBoard)
            assertNextInstruction(fixture)
            assertLatestEventIsReadable(fixture)

            updateGameplay { fixture.viewModel.selectRotation(Rotation.DEG_90) }
            assertEquals(Rotation.DEG_90, fixture.viewModel.uiState.value.playState.selectedRotation)
            assertBoardIsUnchangedBelowHud(initialBoard)

            updateGameplay { fixture.viewModel.selectDigChoice(DigTileChoice.DRAWN) }
            assertEquals(DigTileChoice.DRAWN, fixture.controller.pendingDigTileChoice)
            assertBoardIsUnchangedBelowHud(initialBoard)

            updateGameplay { fixture.viewModel.selectRotation(Rotation.DEG_180) }
            assertEquals(Rotation.DEG_180, fixture.viewModel.uiState.value.playState.selectedRotation)
            assertBoardIsUnchangedBelowHud(initialBoard)

            updateGameplay { fixture.viewModel.selectDigChoice(DigTileChoice.REVEALED) }
            assertEquals(DigTileChoice.REVEALED, fixture.controller.pendingDigTileChoice)
            assertBoardIsUnchangedBelowHud(initialBoard)

            updateGameplay { fixture.viewModel.selectDigChoice(DigTileChoice.DRAWN) }
            assertEquals(DigTileChoice.DRAWN, fixture.controller.pendingDigTileChoice)
            assertBoardIsUnchangedBelowHud(initialBoard)

            updateGameplay { fixture.viewModel.selectRotation(Rotation.DEG_0) }
            assertBoardIsUnchangedBelowHud(initialBoard)
            updateGameplay { fixture.viewModel.confirmDigPlacement() }
            assertEquals(TurnPhase.MOVE, fixture.controller.engine!!.currentPhase)
            assertFalse(fixture.viewModel.uiState.value.showDigControls)
            assertBoardIsUnchangedBelowHud(initialBoard)
            assertNextInstruction(fixture)
            assertLatestEventIsReadable(fixture)

            // Publish END before its action runs so automatic phase skipping cannot hide this layout.
            updateGameplay {
                repeat(2) { fixture.controller.engine!!.advancePhase() }
                fixture.publishPreparedState()
            }
            assertEquals(TurnPhase.END, fixture.controller.engine!!.currentPhase)
            assertBoardIsUnchangedBelowHud(initialBoard)
            assertNextInstruction(fixture)
            assertTextFits("手番終了")

            updateGameplay { fixture.viewModel.finishTurn() }
            val consumption = requireNotNull(fixture.viewModel.uiState.value.turnConsumptionAnimation)
            assertBoardIsUnchangedBelowHud(initialBoard)
            composeRule.mainClock.advanceTimeBy(150)
            composeRule.waitForIdle()
            assertBoardIsUnchangedBelowHud(initialBoard)

            updateGameplay { fixture.viewModel.finishTurnConsumptionAnimation(consumption.id) }
            assertEquals(null, fixture.viewModel.uiState.value.turnConsumptionAnimation)
            assertNotEquals(initialPlayerId, fixture.controller.currentPlayer!!.id)
            assertEquals(TurnPhase.DIG, fixture.controller.engine!!.currentPhase)
            assertBoardIsUnchangedBelowHud(initialBoard)
            assertNextInstruction(fixture)
            assertLatestEventIsReadable(fixture)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    private fun assertBoardStaysAnchoredThroughCapture(width: Int, height: Int, fontScale: Float) {
        val fixture = moveFixture()
        val engine = fixture.controller.engine!!
        while (engine.foodsAt(RIGHT).isNotEmpty()) engine.removeFoodAt(RIGHT, 0)
        engine.placeFoodAt(RIGHT, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
        fixture.publishPreparedState()
        show(fixture, width, height, fontScale)
        val initialBoard = boardBounds()
        assertBoardIsUnchangedBelowHud(initialBoard)
        composeRule.mainClock.autoAdvance = false
        try {
            updateGameplay { fixture.viewModel.onCellClicked(RIGHT) }
            assertEquals(TurnPhase.CAPTURE, engine.currentPhase)
            assertBoardIsUnchangedBelowHud(initialBoard)

            updateGameplay { fixture.viewModel.capture() }
            assertTrue(fixture.viewModel.uiState.value.playState.diceRouletteActive)
            assertBoardIsUnchangedBelowHud(initialBoard)

            updateGameplay { fixture.viewModel.stopDiceRoulette() }
            assertEquals(6, fixture.viewModel.uiState.value.playState.diceRouletteResult)
            assertBoardIsUnchangedBelowHud(initialBoard)

            updateGameplay { fixture.viewModel.finishDiceRoulette() }
            val animation = requireNotNull(fixture.viewModel.uiState.value.captureAnimation)
            assertBoardIsUnchangedBelowHud(initialBoard)
            composeRule.mainClock.advanceTimeBy(150)
            composeRule.waitForIdle()
            assertBoardIsUnchangedBelowHud(initialBoard)

            updateGameplay { fixture.viewModel.finishCaptureAnimation(animation.event.id) }
            assertEquals(null, fixture.viewModel.uiState.value.captureAnimation)
            assertEquals(TurnPhase.DECIDE, engine.currentPhase)
            assertBoardIsUnchangedBelowHud(initialBoard)
            assertNextInstruction(fixture)
            assertTextFits("タベる")
            assertTextFits("レンコウ")
            val result = composeRule.onNodeWithTag("latest-event", useUnmergedTree = true)
            assertTextDoesNotOverflow(result)
            assertFitsViewport(result)
            assertFitsInsideClickTarget(result)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    private fun updateGameplay(action: () -> Unit) {
        composeRule.runOnIdle(action)
        composeRule.mainClock.advanceTimeBy(32)
        composeRule.waitForIdle()
    }

    private fun boardBounds(): Rect = composeRule.onNodeWithTag("game-board")
        .assertIsDisplayed()
        .fetchSemanticsNode().boundsInRoot

    private fun assertBoardIsUnchangedBelowHud(expected: Rect) {
        val actual = boardBounds()
        val hud = composeRule.onNodeWithTag("top-hud").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertRectEquals("board across gameplay changes", expected, actual)
        assertEquals("The board must touch the bottom of the HUD", hud.bottom, actual.top, 0.5f)
        assertFitsViewport(composeRule.onNodeWithTag("game-board"))
    }

    private fun assertMoveGuidanceFitsViewport(width: Int, height: Int, fontScale: Float = 1f) {
        val fixture = moveFixture()
        show(fixture, width, height, fontScale)

        assertNextInstruction(fixture)
        assertTextFits("次の移動先")
        assertTextFits("このマスへ移動\n2列3行")
        assertTextFits("移動しない")
        assertTextFits("手番終了")
        assertTextFits("残りを省略")
        composeRule.onNodeWithText("スキップ").assertDoesNotExist()
        composeRule.onNodeWithText("ターン終了").assertDoesNotExist()
    }

    private fun assertInitialDigGuidanceIsReadable(width: Int, height: Int, fontScale: Float = 1f) {
        val fixture = initialDigTShapeFixture()
        val state = fixture.viewModel.uiState.value
        val instructionText = actionBarInstruction(state)
        val latestEventText = state.logs.last()
        assertEquals("次：掘る場所を選択（山札：T字タイル）", instructionText)
        assertTrue(latestEventText, latestEventText.contains("T字タイル"))
        assertTrue(latestEventText, latestEventText.contains("掘る場所を選んでください"))
        show(fixture, width, height, fontScale)

        assertNextInstruction(fixture)
        assertLatestEventIsReadable(fixture)
        composeRule.onNodeWithTag("next-action-toggle").assertDoesNotExist()
        composeRule.onNodeWithText("履歴").assertIsDisplayed()
    }

    private fun assertDigPlacementIsReadable(width: Int, height: Int, fontScale: Float = 1f) {
        val fixture = initialDigTShapeFixture()
        fixture.viewModel.onCellClicked(fixture.controller.digTargets().single())
        assertTrue(fixture.viewModel.uiState.value.showDigControls)
        show(fixture, width, height, fontScale)

        assertNextInstruction(fixture)
        assertLatestEventIsReadable(fixture)
        listOf("めくり", "山札", "置く").forEach(::assertTextFits)
        composeRule.onNodeWithText("履歴").assertIsDisplayed()
        composeRule.onNodeWithTag("next-action-toggle").assertDoesNotExist()
    }

    @Test
    fun tappingTheLatestEventOpensHistoryAndLeavesTheFullEventVisibleWhenClosed() {
        val fixture = historyFixture()
        show(fixture, width = 360, height = 740, fontScale = 1.5f)
        val boundsBeforeHistory = gameplayBounds()
        val logsBeforeHistory = fixture.viewModel.uiState.value.logs

        composeRule.onNodeWithTag("latest-event", useUnmergedTree = true).performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("log-history-drawer").assertIsDisplayed()
        assertHistoryPopupPlacement()
        assertGameplayBoundsEqual(boundsBeforeHistory, gameplayBounds())

        composeRule.onNodeWithText("閉じる").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("log-history-drawer").assertDoesNotExist()
        assertNextInstruction(fixture)
        assertLatestEventIsReadable(fixture)
        assertGameplayBoundsEqual(boundsBeforeHistory, gameplayBounds())
        composeRule.runOnIdle { assertEquals(logsBeforeHistory, fixture.viewModel.uiState.value.logs) }
    }

    private fun show(
        fixture: Fixture,
        width: Int = 360,
        height: Int = 740,
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                Box(Modifier.requiredSize(width.dp, height.dp).testTag(VIEWPORT_TAG)) {
                    MoguraGameScreen(viewModel = fixture.viewModel)
                }
            }
        }
        val bounds = viewportBounds()
        assertEquals(width.toFloat(), bounds.width, 0.5f)
        assertEquals(height.toFloat(), bounds.height, 0.5f)
        listOf("top-hud", "game-board", "action-bar").forEach { tag ->
            assertFitsViewport(composeRule.onNodeWithTag(tag))
        }
    }

    private fun assertNextInstruction(fixture: Fixture) {
        val expected = actionBarInstruction(fixture.viewModel.uiState.value)
        assertTrue(expected.isNotBlank())
        val instruction = composeRule.onNodeWithTag("next-action-instruction", useUnmergedTree = true)
        instruction.assertTextEquals(expected)
        assertFitsViewport(instruction)
        assertTextDoesNotOverflow(instruction)
        assertFitsInside(instruction, composeRule.onNodeWithTag("action-bar"))
    }

    private fun assertLatestEventIsReadable(fixture: Fixture) {
        val state = fixture.viewModel.uiState.value
        val latestEvent = composeRule.onNodeWithTag("latest-event", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals(state.lastMessage ?: state.logs.last())
        assertFitsViewport(latestEvent)
        assertTextDoesNotOverflow(latestEvent)
        assertFitsInside(latestEvent, composeRule.onNodeWithTag("action-bar"))
        assertFitsInsideClickTarget(latestEvent)
    }

    private fun assertTextFits(text: String) {
        val node = composeRule.onNode(
            hasText(text) and hasAnyAncestor(hasClickAction()),
            useUnmergedTree = true,
        )
        assertFitsViewport(node)
        assertTextDoesNotOverflow(node)
        assertFitsInsideClickTarget(node)
    }

    private fun assertFitsViewport(node: SemanticsNodeInteraction) {
        val viewport = viewportBounds()
        val bounds = node.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("The control must fit horizontally", bounds.left >= viewport.left && bounds.right <= viewport.right)
        assertTrue("The control must fit vertically", bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
    }

    private fun assertTextDoesNotOverflow(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayouts ->
            assertTrue(getLayouts(layouts))
        }
        assertEquals(1, layouts.size)
        val layout = layouts.single()
        assertFalse("Guidance and action labels must remain readable", layout.hasVisualOverflow)
        repeat(layout.lineCount) { line ->
            assertFalse("Guidance, logs, and action labels must not be ellipsized", layout.isLineEllipsized(line))
        }
    }

    private fun assertFitsInside(node: SemanticsNodeInteraction, container: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val containerBounds = container.fetchSemanticsNode().boundsInRoot
        assertBoundsInside(bounds, containerBounds)
    }

    private fun assertFitsInsideClickTarget(node: SemanticsNodeInteraction) {
        val textNode = node.fetchSemanticsNode()
        val clickTarget = generateSequence(textNode.parent) { it.parent }
            .firstOrNull { it.config.contains(SemanticsActions.OnClick) }
        assertTrue("Readable text must belong to a click target", clickTarget != null)
        assertBoundsInside(textNode.boundsInRoot, clickTarget!!.boundsInRoot)
    }

    private fun assertBoundsInside(bounds: Rect, containerBounds: Rect) {
        assertTrue(
            "The content must fit horizontally inside its container",
            bounds.left >= containerBounds.left && bounds.right <= containerBounds.right,
        )
        assertTrue(
            "The content must fit vertically inside its container",
            bounds.top >= containerBounds.top && bounds.bottom <= containerBounds.bottom,
        )
    }

    private fun gameplayBounds(): GameplayBounds = GameplayBounds(
        board = composeRule.onNodeWithTag("game-board").assertIsDisplayed().fetchSemanticsNode().boundsInRoot,
        actionBar = composeRule.onNodeWithTag("action-bar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot,
    )

    private fun assertGameplayBoundsEqual(expected: GameplayBounds, actual: GameplayBounds) {
        assertRectEquals("game board", expected.board, actual.board)
        assertRectEquals("action bar", expected.actionBar, actual.actionBar)
    }

    private fun assertHistoryPopupPlacement() {
        val drawer = composeRule.onNodeWithTag("log-history-drawer")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsOnScreen()
        val actionBar = composeRule.onNodeWithTag("action-bar")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsOnScreen()
        val viewport = composeRule.onNodeWithTag(VIEWPORT_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsOnScreen()
        val board = composeRule.onNodeWithTag("board-viewport")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsOnScreen()

        assertTrue("History must stay above the action bar", drawer.bottom <= actionBar.top)
        assertTrue("History must stay inside the viewport horizontally", drawer.left >= viewport.left)
        assertTrue("History must stay inside the viewport horizontally", drawer.right <= viewport.right)
        assertTrue("History must stay inside the viewport vertically", drawer.top >= viewport.top)
        assertTrue("History must stay inside the viewport vertically", drawer.bottom <= viewport.bottom)
        assertTrue("History must stay below the HUD in the board area", drawer.top >= board.top - 0.5f)
        assertTrue("History height must not exceed 420dp", drawer.height <= 420.5f)
    }

    private fun assertRectEquals(label: String, expected: Rect, actual: Rect) {
        assertEquals("$label left", expected.left, actual.left, 0.5f)
        assertEquals("$label top", expected.top, actual.top, 0.5f)
        assertEquals("$label right", expected.right, actual.right, 0.5f)
        assertEquals("$label bottom", expected.bottom, actual.bottom, 0.5f)
    }

    private fun androidx.compose.ui.semantics.SemanticsNode.boundsOnScreen(): Rect {
        val topLeft = positionOnScreen
        return Rect(
            left = topLeft.x,
            top = topLeft.y,
            right = topLeft.x + size.width,
            bottom = topLeft.y + size.height,
        )
    }

    private fun tapScreen(position: Offset) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            position.x,
            position.y,
            0,
        ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        val up = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_UP,
            position.x,
            position.y,
            0,
        ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        try {
            instrumentation.sendPointerSync(down)
            instrumentation.sendPointerSync(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun viewportBounds(): Rect = composeRule.onNodeWithTag(VIEWPORT_TAG)
        .assertIsDisplayed()
        .fetchSemanticsNode().boundsInRoot

    private fun newFixture(): Fixture {
        val controller = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        return Fixture(controller, viewModel)
    }

    private fun moveFixture(): Fixture {
        val fixture = newFixture()
        val engine = fixture.controller.engine!!
        engine.boardState.clear()
        fixture.controller.currentPlayer!!.moveTo(SOURCE)
        val horizontalTile = HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip()
        listOf(LEFT, SOURCE, RIGHT).forEach { position ->
            engine.boardState.placeTile(position, horizontalTile)
            while (engine.foodsAt(position).isNotEmpty()) engine.removeFoodAt(position, 0)
        }
        listOf(LEFT, RIGHT).forEach { position ->
            engine.placeFoodAt(position, FoodCard.createDummyCards(FoodType.BEETLE_LARVA).first())
        }
        engine.advancePhase()
        assertEquals(setOf(LEFT, RIGHT), fixture.controller.moveTargets())
        fixture.publishPreparedState()
        return fixture
    }

    private fun initialDigTShapeFixture(): Fixture {
        val controller = MoguraGameController(FixedDiceRoller(listOf(6)), TShapeFirstDrawShuffler)
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        assertEquals(TileShape.T_SHAPE, controller.pendingDigDrawnTile?.shape)
        return Fixture(controller, viewModel)
    }

    private fun historyFixture(): Fixture {
        val fixture = initialDigTShapeFixture()
        fixture.viewModel.onCellClicked(fixture.controller.digTargets().single())
        assertTrue(fixture.viewModel.uiState.value.showDigControls)
        fixture.viewModel.selectDigChoice(DigTileChoice.DRAWN)
        fixture.viewModel.confirmDigPlacement()
        assertEquals(TurnPhase.MOVE, fixture.controller.engine!!.currentPhase)
        assertTrue(fixture.controller.finishTurn().success)
        fixture.publishPreparedState()
        return fixture
    }

    private data class GameplayBounds(
        val board: Rect,
        val actionBar: Rect,
    )

    private data class Fixture(
        val controller: MoguraGameController,
        val viewModel: AndroidGameViewModel,
    ) {
        fun publishPreparedState() {
            // Publish the deterministic engine fixture without changing its gameplay state.
            viewModel.selectStartPlayer(0)
        }
    }

    private object TShapeFirstDrawShuffler : Shuffler {
        override fun <T> shuffle(list: List<T>): List<T> {
            val reordered = list.toMutableList()
            val tShapeIndex = reordered.indexOfFirst { item ->
                item is HoleTile && item.shape == TileShape.T_SHAPE
            }
            if (tShapeIndex >= 0 && reordered.size > FIRST_DRAW_TILE_INDEX) {
                val firstDrawTile = reordered[FIRST_DRAW_TILE_INDEX]
                reordered[FIRST_DRAW_TILE_INDEX] = reordered[tShapeIndex]
                reordered[tShapeIndex] = firstDrawTile
            }
            return reordered
        }
    }

    private companion object {
        const val VIEWPORT_TAG = "action-guidance-test-viewport"
        // Game setup places the first 16 shuffled tiles on the board; the next tile starts the draw pile.
        const val FIRST_DRAW_TILE_INDEX = 16
        val SOURCE = Position(2, 2)
        val LEFT = Position(1, 2)
        val RIGHT = Position(3, 2)
    }
}
