package com.moguru.game.gui

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.EscapeDirection
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.presenter.CAPTURE_ESCAPE_DURATION_MILLIS
import com.moguru.game.presenter.CAPTURE_SUCCESS_DURATION_MILLIS
import com.moguru.game.presenter.CaptureOutcomeKind
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

class CaptureAnimationTest {
    @Test
    fun `capture playback blocks board input and finishes only once after the grab`() = onEdt {
        val controller = captureController(FoodCard(FoodType.BEETLE_LARVA, emptyMap()))
        var time = 0L
        var finished = 0
        var clicks = 0
        val board = BoardPanel(controller, GuiAssets(), { clicks++ }, { time })
        board.setSize(1086, 1448)
        try {
            board.prepareCaptureAnimation()
            assertTrue(controller.captureCurrentPositionImmediately().success)
            assertTrue(board.playCaptureAnimation { finished++ })
            assertTrue(board.isCaptureAnimating)

            board.dispatchEvent(MouseEvent(board, MouseEvent.MOUSE_CLICKED, 0, 0, 295, 750, 1, false))
            assertEquals(0, clicks, "clicks must not reach the controller during a resolved capture")
            time = (CAPTURE_SUCCESS_DURATION_MILLIS - 1) * 1_000_000L
            board.advanceCaptureAnimation()
            assertEquals(0, finished)
            assertTrue(board.isCaptureAnimating)

            time = CAPTURE_SUCCESS_DURATION_MILLIS * 1_000_000L
            board.advanceCaptureAnimation()
            board.advanceCaptureAnimation()
            assertEquals(1, finished)
            assertFalse(board.isCaptureAnimating)
            assertEquals(TurnPhase.DECIDE, controller.engine!!.currentPhase)
            assertFalse(board.playCaptureAnimation { finished++ }, "refresh must not replay an old result")
            board.prepareCaptureAnimation()
            assertFalse(board.playCaptureAnimation { finished++ }, "a saved old result is not a new capture")
        } finally {
            board.cancelCaptureAnimation()
        }
    }

    @Test
    fun `escape playback keeps the resolved destination and cancellation never advances a new game`() = onEdt {
        val controller = captureController(FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.RIGHT)))
        val destination = Position(2, 1)
        controller.engine!!.placeFoodAt(destination, FoodCard(FoodType.BEETLE_LARVA, emptyMap()))
        var time = 0L
        var finished = 0
        val board = BoardPanel(controller, GuiAssets(), {}, { time })
        try {
            board.prepareCaptureAnimation()
            assertTrue(controller.captureCurrentPositionImmediately().success)
            val event = controller.playScreenUiState().captureOutcome!!.animation!!
            assertEquals(CaptureOutcomeKind.ESCAPED, event.kind)
            assertEquals(destination, event.destination)
            assertEquals(2, controller.engine!!.foodsAt(destination).size)
            assertTrue(board.playCaptureAnimation { finished++ })

            board.cancelCaptureAnimation()
            controller.startNewGame(2)
            time = (CAPTURE_ESCAPE_DURATION_MILLIS + 100) * 1_000_000L
            board.advanceCaptureAnimation()
            assertFalse(board.isCaptureAnimating)
            assertEquals(0, finished)
            assertEquals(TurnPhase.DIG, controller.engine!!.currentPhase)
        } finally {
            board.cancelCaptureAnimation()
        }
    }

    @Test
    fun `capture of a non first stacked food paints through completion without mutating the board`() = onEdt {
        val controller = captureController(FoodCard(FoodType.BEETLE_LARVA, emptyMap()))
        val position = controller.currentPlayer!!.position
        controller.engine!!.placeFoodAt(position, FoodCard(FoodType.MOLE_CRICKET, emptyMap()))
        controller.selectCaptureTarget(1)
        var time = 0L
        val board = BoardPanel(controller, GuiAssets(), {}, { time })
        board.setSize(620, 820)
        val canvas = BufferedImage(620, 820, BufferedImage.TYPE_INT_ARGB)
        val graphics = canvas.createGraphics()
        try {
            board.prepareCaptureAnimation()
            assertTrue(controller.captureCurrentPositionImmediately().success)
            assertTrue(board.playCaptureAnimation {})
            assertEquals(1, controller.playScreenUiState().captureOutcome!!.animation!!.foodIndex)
            listOf(0L, 245L, 300L, 500L, 720L).forEach { milliseconds ->
                time = milliseconds * 1_000_000L
                board.paint(graphics)
            }
            assertEquals(listOf(FoodType.BEETLE_LARVA), controller.engine!!.foodsAt(position).map { it.type })
            assertEquals(FoodType.MOLE_CRICKET, controller.pendingFoodDecision!!.type)
        } finally {
            graphics.dispose()
            board.cancelCaptureAnimation()
        }
    }

    @Test
    fun `animated images restore the caller graphics state and honor alpha`() {
        val canvas = BufferedImage(160, 160, BufferedImage.TYPE_INT_ARGB)
        val sprite = BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB)
        sprite.createGraphics().apply {
            color = Color.RED
            fillRect(0, 0, 20, 20)
            dispose()
        }
        val graphics = canvas.createGraphics()
        try {
            graphics.translate(4, 7)
            graphics.clip = Rectangle(0, 0, 140, 140)
            graphics.composite = AlphaComposite.SrcOver
            val transform = graphics.transform
            val composite = graphics.composite
            val clip = graphics.clip.bounds
            drawCaptureSprite(graphics, sprite, Rectangle(20, 20, 40, 40), rotationDegrees = 12f, alpha = 0.5f)
            assertEquals(transform, graphics.transform)
            assertEquals(composite, graphics.composite)
            assertEquals(clip, graphics.clip.bounds)
            val renderedAlpha = canvas.getRGB(44, 47) ushr 24
            assertTrue(renderedAlpha in 127..129)
        } finally {
            graphics.dispose()
        }
    }

    @Test
    fun `escaped card lands at the final stacked rectangle with no residual hop`() {
        val start = foodCardRect(Rectangle(0, 0, 100, 100), 0.75, stackIndex = 1, stackSize = 2)
        val destination = foodCardRect(Rectangle(100, 100, 100, 100), 0.75, stackIndex = 2, stackSize = 3)
        val frame = com.moguru.game.presenter.captureAnimationFrame(CaptureOutcomeKind.ESCAPED, 1f)
        assertEquals(destination, captureSpriteRect(start, destination, frame.foodTravel, frame.foodScale, frame.foodLift * 100))
    }

    @Test
    @EnabledIfSystemProperty(named = "mogura.capturePreviewDir", matches = ".+")
    fun `render capture preview frames when explicitly requested`() = onEdt {
        val directory = File(System.getProperty("mogura.capturePreviewDir"))
        directory.mkdirs()
        val scenarios = listOf(
            "success" to FoodCard(FoodType.BEETLE_LARVA, emptyMap()),
            "escape" to FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.RIGHT)),
        )
        scenarios.forEach { (name, food) ->
            val controller = captureController(food)
            // Keep another card at both ends to expose stacking and overlay mistakes.
            controller.engine!!.placeFoodAt(Position(1, 1), FoodCard(FoodType.MOLE_CRICKET, emptyMap()))
            controller.engine!!.placeFoodAt(Position(2, 1), FoodCard(FoodType.CENTIPEDE, emptyMap()))
            var time = 0L
            val board = BoardPanel(controller, GuiAssets(), {}, { time })
            board.setSize(1086, 1448)
            try {
                board.prepareCaptureAnimation()
                assertTrue(controller.captureCurrentPositionImmediately().success)
                assertTrue(board.playCaptureAnimation {})
                val moments = if (name == "success") listOf(0L, 270L, 490L, 720L) else listOf(0L, 250L, 430L, 680L)
                moments.forEach { milliseconds ->
                    time = milliseconds * 1_000_000L
                    val canvas = BufferedImage(board.width, board.height, BufferedImage.TYPE_INT_ARGB)
                    val graphics = canvas.createGraphics()
                    try {
                        board.paint(graphics)
                    } finally {
                        graphics.dispose()
                    }
                    ImageIO.write(canvas, "png", File(directory, "$name-$milliseconds.png"))
                }
            } finally {
                board.cancelCaptureAnimation()
            }
        }
    }

    private fun captureController(food: FoodCard): MoguraGameController = MoguraGameController(
        diceRoller = FixedDiceRoller(listOf(1)),
        shuffler = FixedShuffler(),
    ).apply {
        startNewGame(2)
        val position = Position(1, 1)
        currentPlayer!!.moveTo(position)
        engine!!.placeFoodAt(position, food)
        engine!!.advancePhase()
        engine!!.advancePhase()
    }

    private fun onEdt(action: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                action()
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }
}
