package com.moguru.game.android

import android.os.SystemClock
import android.view.InputDevice
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
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
    fun initialDigGuidanceExpandsWithoutMovingGameplayAt360By740Viewport() {
        assertInitialDigGuidanceToggle(width = 360, height = 740)
    }

    @Test
    fun initialDigGuidanceExpandsWithoutMovingGameplayAt390By844Viewport() {
        assertInitialDigGuidanceToggle(width = 390, height = 844)
    }

    @Test
    fun initialDigGuidanceExpandsWithoutMovingGameplayAt360By740ViewportAt150PercentFontScale() {
        assertInitialDigGuidanceToggle(width = 360, height = 740, fontScale = 1.5f)
    }

    @Test
    fun initialDigGuidanceExpandsWithoutMovingGameplayAt390By844ViewportAt150PercentFontScale() {
        assertInitialDigGuidanceToggle(width = 390, height = 844, fontScale = 1.5f)
    }

    @Test
    fun logHistoryShowsNewestFirstAndScrollsWithoutTruncationAtCompactLargeTextViewport() {
        val fixture = historyFixture()
        val expectedLogs = fixture.viewModel.uiState.value.logs.asReversed()
        assertEquals(5, expectedLogs.size)
        show(fixture, width = 360, height = 740, fontScale = 1.5f)
        val collapsedBounds = gameplayBounds()

        composeRule.onNodeWithText("履歴").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("log-history-drawer").assertIsDisplayed()
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

        pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("log-history-drawer").assertDoesNotExist()
        assertGameplayBoundsEqual(collapsedBounds, gameplayBounds())
    }

    @Test
    fun stateChangeAutomaticallyCollapsesGuidanceAndRestoresTheLatestEvent() {
        val fixture = initialDigTShapeFixture()
        val digTarget = fixture.controller.digTargets().single()
        show(fixture)

        composeRule.onNodeWithTag("next-action-toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("latest-event", useUnmergedTree = true).assertDoesNotExist()

        composeRule.runOnIdle { fixture.viewModel.onCellClicked(digTarget) }
        composeRule.waitForIdle()

        val updatedState = fixture.viewModel.uiState.value
        assertTrue(updatedState.showDigControls)
        assertNextInstruction(fixture)
        composeRule.onNodeWithTag("latest-event", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals(updatedState.lastMessage ?: updatedState.logs.last())
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
        composeRule.onNodeWithTag("latest-event").assertIsDisplayed().assertTextEquals(oldLog)
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
        composeRule.onNodeWithTag("latest-event").assertIsDisplayed()
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
        composeRule.onNodeWithTag("latest-event").assertIsDisplayed()
        composeRule.onNodeWithText("スキップ").assertDoesNotExist()
        composeRule.onNodeWithText("手番終了", substring = true).assertDoesNotExist()

        val collapsedBounds = gameplayBounds()
        composeRule.onNodeWithTag("next-action-toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("latest-event", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("履歴").assertDoesNotExist()
        assertGameplayBoundsEqual(collapsedBounds, gameplayBounds())

        composeRule.onNodeWithTag("next-action-toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("latest-event", useUnmergedTree = true).assertIsDisplayed()
        assertGameplayBoundsEqual(collapsedBounds, gameplayBounds())
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

    private fun assertInitialDigGuidanceToggle(width: Int, height: Int, fontScale: Float = 1f) {
        val fixture = initialDigTShapeFixture()
        val state = fixture.viewModel.uiState.value
        val instructionText = actionBarInstruction(state)
        val latestEventText = state.logs.last()
        assertEquals("次：掘る場所を選択（山札：T字タイル）", instructionText)
        assertTrue(latestEventText, latestEventText.contains("T字タイル"))
        assertTrue(latestEventText, latestEventText.contains("掘る場所を選んでください"))
        show(fixture, width, height, fontScale)

        val collapsedBounds = gameplayBounds()
        composeRule.onNodeWithTag("next-action-instruction", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals(instructionText)
        composeRule.onNodeWithTag("latest-event", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals(latestEventText)

        composeRule.onNodeWithTag("next-action-toggle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("latest-event", useUnmergedTree = true).assertDoesNotExist()
        val expandedInstruction = composeRule.onNodeWithTag("next-action-instruction", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals(instructionText)
        assertTextDoesNotOverflow(expandedInstruction)
        assertFitsInside(expandedInstruction, composeRule.onNodeWithTag("next-action-toggle"))
        assertGameplayBoundsEqual(collapsedBounds, gameplayBounds())

        composeRule.onNodeWithTag("next-action-toggle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("latest-event", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals(latestEventText)
        assertGameplayBoundsEqual(collapsedBounds, gameplayBounds())
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
    }

    private fun assertTextFits(text: String) {
        val node = composeRule.onNode(
            hasText(text, substring = true) and hasAnyAncestor(hasClickAction()),
            useUnmergedTree = true,
        )
        assertFitsViewport(node)
        assertTextDoesNotOverflow(node)
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

        assertTrue("History must stay above the action bar", drawer.bottom <= actionBar.top)
        assertTrue("History must stay inside the viewport horizontally", drawer.left >= viewport.left)
        assertTrue("History must stay inside the viewport horizontally", drawer.right <= viewport.right)
        assertTrue("History must stay inside the viewport vertically", drawer.top >= viewport.top)
        assertTrue("History must stay inside the viewport vertically", drawer.bottom <= viewport.bottom)
        assertTrue("History height must not exceed 220dp", drawer.height <= 220.5f)
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
