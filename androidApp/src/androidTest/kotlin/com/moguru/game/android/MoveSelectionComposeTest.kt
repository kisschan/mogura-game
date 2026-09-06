package com.moguru.game.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MoveSelectionComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun boardTapAndRepeatedTapOnlySelectTheDestination() {
        val fixture = moveFixture()
        val before = fixture.snapshot()
        show(fixture)

        assertSelectedDestination(LEFT)
        boardCell(RIGHT).assert(
            SemanticsMatcher("移動先の選択操作を読み上げる") { node ->
                SemanticsActions.OnClick in node.config &&
                    node.config[SemanticsActions.OnClick].label == "移動先に選択"
            },
        )
        repeat(2) {
            boardCell(RIGHT).performClick()

            assertSelectedDestination(RIGHT)
            composeRule.runOnIdle { assertEquals(before, fixture.snapshot()) }
        }
    }

    @Test
    fun nextDestinationCyclesFromTheBoardSelectionAndWrapsWithoutMoving() {
        val fixture = moveFixture()
        val before = fixture.snapshot()
        show(fixture)

        composeRule.onNodeWithText("次の移動先", substring = true).performClick()
        assertSelectedDestination(RIGHT)
        composeRule.runOnIdle { assertEquals(before, fixture.snapshot()) }

        composeRule.onNodeWithText("次の移動先", substring = true).performClick()
        assertSelectedDestination(LEFT)
        composeRule.runOnIdle { assertEquals(before, fixture.snapshot()) }

        boardCell(RIGHT).performClick()
        composeRule.onNodeWithText("次の移動先", substring = true).performClick()
        assertSelectedDestination(LEFT)
        composeRule.runOnIdle { assertEquals(before, fixture.snapshot()) }
    }

    @Test
    fun confirmMovesToTheSelectedCellAndBoardCaptureStillStartsImmediately() {
        val fixture = moveFixture()
        val before = fixture.snapshot()
        show(fixture)
        boardCell(RIGHT).performClick()

        composeRule.onNodeWithTag("primary-action").performClick()

        composeRule.runOnIdle {
            assertEquals(
                before.copy(
                    positions = listOf(RIGHT, before.positions[1]),
                    phase = TurnPhase.CAPTURE,
                ),
                fixture.snapshot(),
            )
            assertFalse(fixture.viewModel.uiState.value.playState.diceRouletteActive)
        }
        composeRule.onNodeWithText("次の移動先", substring = true).assertDoesNotExist()
        boardCell(RIGHT).performClick()
        composeRule.runOnIdle {
            assertTrue(fixture.viewModel.uiState.value.playState.diceRouletteActive)
            assertEquals(RIGHT, fixture.controller.currentPlayer!!.position)
            assertEquals(before.health, fixture.snapshot().health)
        }
    }

    @Test
    fun singleDestinationIsSelectedAndStillRequiresTheConfirmButton() {
        val fixture = moveFixture(targets = listOf(RIGHT))
        val before = fixture.snapshot()
        show(fixture)

        assertSelectedDestination(RIGHT)
        composeRule.onNodeWithText("次の移動先", substring = true).assertDoesNotExist()
        boardCell(RIGHT).performClick()
        composeRule.runOnIdle { assertEquals(before, fixture.snapshot()) }
        composeRule.onNodeWithTag("primary-action").performClick()
        composeRule.runOnIdle {
            assertEquals(RIGHT, fixture.controller.currentPlayer!!.position)
            assertEquals(TurnPhase.CAPTURE, fixture.controller.engine!!.currentPhase)
        }
    }

    @Test
    fun changingTheCurrentPlayerResetsTheSelectionToTheFirstDestination() {
        val fixture = moveFixture()
        show(fixture)
        boardCell(RIGHT).performClick()
        assertSelectedDestination(RIGHT)

        composeRule.runOnIdle {
            val engine = fixture.controller.engine!!
            // Preserve the same candidates while changing the player/phase identity.
            engine.players[0].moveTo(engine.players[0].nestPosition)
            repeat(3) { engine.advancePhase() }
            assertEquals(1, engine.currentPlayerIndex)
            engine.players[1].moveTo(SOURCE)
            engine.advancePhase()
            fixture.publishPreparedState()
        }

        assertSelectedDestination(LEFT)
        composeRule.runOnIdle {
            assertEquals(SOURCE, fixture.controller.currentPlayer!!.position)
            assertEquals(TurnPhase.MOVE, fixture.controller.engine!!.currentPhase)
        }
    }

    @Test
    fun changingTheCandidatesResetsTheSelectionAndHandlesZeroCandidates() {
        val fixture = moveFixture()
        show(fixture)
        boardCell(RIGHT).performClick()
        assertSelectedDestination(RIGHT)

        composeRule.runOnIdle {
            fixture.controller.engine!!.boardState.placeTile(RIGHT, HoleTile(TileShape.STRAIGHT))
            fixture.publishPreparedState()
        }
        assertSelectedDestination(LEFT)
        composeRule.onNodeWithText("次の移動先", substring = true).assertDoesNotExist()

        composeRule.runOnIdle {
            fixture.controller.engine!!.boardState.placeTile(LEFT, HoleTile(TileShape.STRAIGHT))
            fixture.publishPreparedState()
            assertTrue(fixture.controller.moveTargets().isEmpty())
        }
        composeRule.onNodeWithText("このマスへ移動", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("次の移動先", substring = true).assertDoesNotExist()
    }

    @Test
    fun boardDigStillRevealsATileAndNoMoveCandidatesStillAutoAdvance() {
        val controller = testController()
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val target = controller.digTargets().single()
        val fixture = Fixture(controller, viewModel)
        val before = fixture.snapshot()
        show(fixture)

        boardCell(target).performClick()

        composeRule.runOnIdle {
            assertNotNull(controller.pendingDigPlacement)
            assertFalse(controller.engine!!.boardState.getTile(target)!!.isFaceDown)
            assertEquals(before, fixture.snapshot())
        }
        composeRule.onNodeWithText("置く").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val state = viewModel.uiState.value
            state.turnConsumptionAnimation == null &&
                state.playState.currentPlayer.playerId == 1 &&
                state.playState.actionAvailability.activePhase == TurnPhase.DIG
        }
        composeRule.runOnIdle {
            assertEquals(1, controller.engine!!.currentPlayerIndex)
            assertEquals(TurnPhase.DIG, controller.engine!!.currentPhase)
            assertEquals(before.health[0] - 1, controller.engine!!.players[0].health)
        }
        composeRule.onNodeWithText("このマスへ移動", substring = true).assertDoesNotExist()
    }

    @Test
    fun moveControlsAndSelectedCoordinatesFitIn360By740Viewport() {
        assertMoveLayoutFitsViewport(width = 360, height = 740)
    }

    @Test
    fun moveControlsAndSelectedCoordinatesFitIn390By844Viewport() {
        assertMoveLayoutFitsViewport(width = 390, height = 844)
    }

    private fun assertMoveLayoutFitsViewport(width: Int, height: Int) {
        val fixture = moveFixture()
        composeRule.setContent {
            // Force the requested logical viewport instead of inheriting the device's dp size.
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
                Box(Modifier.requiredSize(width.dp, height.dp).testTag("move-test-viewport")) {
                    MoguraGameScreen(viewModel = fixture.viewModel)
                }
            }
        }

        val viewport = composeRule.onNodeWithTag("move-test-viewport")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertEquals(width.toFloat(), viewport.width, 0.5f)
        assertEquals(height.toFloat(), viewport.height, 0.5f)
        listOf("game-board", "action-bar", "primary-action").forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue("$tag must fit horizontally", bounds.left >= viewport.left && bounds.right <= viewport.right)
            assertTrue("$tag must fit vertically", bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
        }
        assertSelectedDestination(LEFT)

        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("このマスへ移動\n2列3行", useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayouts ->
                assertTrue(getLayouts(layouts))
            }
        assertEquals(1, layouts.size)
        assertFalse("The move action and its coordinates must not be truncated", layouts.single().hasVisualOverflow)
        assertEquals(2, layouts.single().lineCount)
    }

    private fun show(fixture: Fixture) {
        composeRule.setContent { MoguraGameScreen(viewModel = fixture.viewModel) }
        composeRule.onNodeWithTag("game-board").assertIsDisplayed()
        composeRule.onNodeWithTag("action-bar").assertIsDisplayed()
    }

    private fun assertSelectedDestination(position: Position) {
        boardCell(position).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        )
        composeRule.onNodeWithText(
            "このマスへ移動\n${position.col + 1}列${position.row + 1}行",
        ).assertIsDisplayed()
    }

    private fun boardCell(position: Position) = composeRule.onNodeWithContentDescription(
        "マス ${position.col + 1},${position.row + 1}、",
        substring = true,
    )

    private fun moveFixture(targets: List<Position> = listOf(LEFT, RIGHT)): Fixture {
        val controller = testController()
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val engine = controller.engine!!
        engine.boardState.clear()
        controller.currentPlayer!!.moveTo(SOURCE)
        val horizontalTile = HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip()
        (targets + SOURCE).forEach { position ->
            engine.boardState.placeTile(position, horizontalTile)
            while (engine.foodsAt(position).isNotEmpty()) engine.removeFoodAt(position, 0)
        }
        targets.forEach { position ->
            engine.placeFoodAt(position, FoodCard.createDummyCards(FoodType.BEETLE_LARVA).first())
        }
        engine.advancePhase()
        assertEquals(targets.toSet(), controller.moveTargets())
        return Fixture(controller, viewModel).also { it.publishPreparedState() }
    }

    private fun testController() = MoguraGameController(
        FixedDiceRoller(listOf(6)),
        FixedShuffler(),
    )

    private data class Fixture(
        val controller: MoguraGameController,
        val viewModel: AndroidGameViewModel,
    ) {
        fun publishPreparedState() {
            // Refresh through a public setter; setup selection does not mutate the live game.
            viewModel.selectStartPlayer(0)
        }

        fun snapshot(): GameSnapshot {
            val engine = controller.engine!!
            return GameSnapshot(
                positions = engine.players.map { it.position },
                health = engine.players.map { it.health },
                playerIndex = engine.currentPlayerIndex,
                phase = engine.currentPhase,
            )
        }
    }

    private data class GameSnapshot(
        val positions: List<Position>,
        val health: List<Int>,
        val playerIndex: Int,
        val phase: TurnPhase,
    )

    private companion object {
        val SOURCE = Position(2, 2)
        val LEFT = Position(1, 2)
        val RIGHT = Position(3, 2)
    }
}
