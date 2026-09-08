package com.moguru.game.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Direction
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DigRotationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rotationButtonTurnsClockwiseAndWrapsWithoutConfirmingPlacement() {
        val fixture = pendingDigFixture()
        val before = fixture.snapshot()
        show(fixture)
        assertRotationCopy(degrees = 0)

        val expectedRotations = listOf(
            Rotation.DEG_90 to setOf(Direction.TOP, Direction.LEFT),
            Rotation.DEG_180 to setOf(Direction.TOP, Direction.RIGHT),
            Rotation.DEG_270 to setOf(Direction.RIGHT, Direction.BOTTOM),
            Rotation.DEG_0 to setOf(Direction.LEFT, Direction.BOTTOM),
        )
        expectedRotations.forEach { (rotation, openSides) ->
            rotationButton().performClick()

            assertRotationCopy(degrees = rotation.steps * 90)
            composeRule.runOnIdle {
                assertEquals(rotation, fixture.viewModel.uiState.value.playState.selectedRotation)
                assertEquals(rotation, fixture.controller.pendingDigRotation)
                assertEquals(openSides, fixture.controller.engine!!.boardState.getTile(fixture.target)!!.openSides)
                assertNotNull(fixture.controller.pendingDigPlacement)
                assertEquals(before, fixture.snapshot())
            }
            composeRule.onNodeWithText("置く").assertIsDisplayed()
        }
    }

    @Test
    fun placementButtonCommitsTheClockwisePreviewAndAdvancesToMove() {
        val fixture = pendingDigFixture()
        val before = fixture.snapshot()
        show(fixture)
        rotationButton().performClick()

        composeRule.runOnIdle {
            assertNotNull(fixture.controller.pendingDigPlacement)
            assertEquals(before, fixture.snapshot())
        }
        composeRule.onNodeWithText("置く").performClick()

        composeRule.runOnIdle {
            val engine = fixture.controller.engine!!
            val placedTile = engine.boardState.getTile(fixture.target)!!
            assertNull(fixture.controller.pendingDigPlacement)
            assertEquals(TileShape.L_SHAPE, placedTile.shape)
            assertFalse(placedTile.isFaceDown)
            assertEquals(setOf(Direction.TOP, Direction.LEFT), placedTile.openSides)
            assertEquals(before.copy(phase = TurnPhase.MOVE), fixture.snapshot())
        }
        rotationButton().assertDoesNotExist()
        composeRule.onNodeWithText("置く").assertDoesNotExist()
    }

    @Test
    fun rotationDirectionAndCurrentAngleFitIn360By740Viewport() {
        assertRotationLayoutFitsViewport(fontScale = 1f)
    }

    @Test
    fun rotationDirectionAndCurrentAngleFitIn360By740ViewportAt150PercentFontScale() {
        assertRotationLayoutFitsViewport(fontScale = 1.5f)
    }

    private fun assertRotationLayoutFitsViewport(fontScale: Float) {
        val fixture = pendingDigFixture()
        show(fixture, fontScale)
        val viewport = composeRule.onNodeWithTag(VIEWPORT_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(360f, viewport.width, 0.5f)
        assertEquals(740f, viewport.height, 0.5f)

        listOf(0, 90, 180, 270).forEachIndexed { index, degrees ->
            if (index > 0) rotationButton().performClick()
            assertRotationCopy(degrees)
            val buttonBounds = rotationButton().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(buttonBounds.left >= viewport.left && buttonBounds.right <= viewport.right)
            assertTrue(buttonBounds.top >= viewport.top && buttonBounds.bottom <= viewport.bottom)
            listOf("右に90°", "${degrees}°").forEach { label ->
                val text = rotationText(label)
                val textBounds = text.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue("$label must fit inside the button horizontally", textBounds.left >= buttonBounds.left && textBounds.right <= buttonBounds.right)
                assertTrue("$label must fit inside the button vertically", textBounds.top >= buttonBounds.top && textBounds.bottom <= buttonBounds.bottom)
                assertTextDoesNotOverflow(text)
            }
        }
    }

    private fun assertRotationCopy(degrees: Int) {
        rotationButton()
            .assertHasClickAction()
            .assertContentDescriptionEquals("右に90度回転、現在の向き ${degrees}度")
        rotationText("↻").assertIsDisplayed()
        rotationText("${degrees}°").assertIsDisplayed()
        rotationText("右に90°").assertIsDisplayed()
        composeRule.onNodeWithText("↺", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun rotationButton() = composeRule.onNodeWithTag(ROTATION_BUTTON_TAG, useUnmergedTree = true)

    private fun rotationText(text: String) = composeRule.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag(ROTATION_BUTTON_TAG)),
        useUnmergedTree = true,
    )

    private fun assertTextDoesNotOverflow(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayouts ->
            assertTrue(getLayouts(layouts))
        }
        assertEquals(1, layouts.size)
        assertFalse("The direction and current angle must remain readable", layouts.single().hasVisualOverflow)
    }

    private fun show(fixture: Fixture, fontScale: Float = 1f) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                Box(Modifier.requiredSize(360.dp, 740.dp).testTag(VIEWPORT_TAG)) {
                    MoguraGameScreen(viewModel = fixture.viewModel)
                }
            }
        }
        composeRule.onNodeWithTag("game-board").assertIsDisplayed()
        composeRule.onNodeWithTag("action-bar").assertIsDisplayed()
    }

    private fun pendingDigFixture(): Fixture {
        val controller = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val target = controller.digTargets().single()
        // An asymmetric tile makes clockwise and counterclockwise rotation distinguishable.
        controller.engine!!.boardState.placeTile(target, HoleTile(TileShape.L_SHAPE))
        viewModel.onCellClicked(target)
        assertNotNull(controller.pendingDigPlacement)
        assertEquals(Rotation.DEG_0, viewModel.uiState.value.playState.selectedRotation)
        return Fixture(controller, viewModel, target)
    }

    private data class Fixture(
        val controller: MoguraGameController,
        val viewModel: AndroidGameViewModel,
        val target: Position,
    ) {
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
        const val ROTATION_BUTTON_TAG = "dig-rotation-button"
        const val VIEWPORT_TAG = "dig-rotation-test-viewport"
    }
}
