package com.moguru.game.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
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
        assertFalse("Guidance and action labels must remain readable", layouts.single().hasVisualOverflow)
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

    private data class Fixture(
        val controller: MoguraGameController,
        val viewModel: AndroidGameViewModel,
    ) {
        fun publishPreparedState() {
            // Publish the deterministic engine fixture without changing its gameplay state.
            viewModel.selectStartPlayer(0)
        }
    }

    private companion object {
        const val VIEWPORT_TAG = "action-guidance-test-viewport"
        val SOURCE = Position(2, 2)
        val LEFT = Position(1, 2)
        val RIGHT = Position(3, 2)
    }
}
