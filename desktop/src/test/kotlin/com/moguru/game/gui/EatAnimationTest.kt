package com.moguru.game.gui

import com.moguru.game.model.FoodType
import com.moguru.game.model.Player
import com.moguru.game.model.Position
import com.moguru.game.presenter.EAT_ANIMATION_DURATION_MILLIS
import com.moguru.game.presenter.EatAnimationEvent
import com.moguru.game.presenter.MoguraGameController
import java.awt.Point
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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

class EatAnimationTest {
    @Test
    fun `short wav player loads lazily replays and closes once`() {
        val clip = RecordingShortAudioClip()
        var loadCount = 0
        val player = ShortWavEatRecoverySoundPlayer(
            path = "test-eat.wav",
            loader = ShortAudioClipLoader {
                loadCount++
                clip
            },
        )

        assertEquals(0, loadCount)
        player.play()
        player.play()
        assertEquals(1, loadCount)
        assertEquals(2, clip.playCount)

        player.close()
        player.close()
        player.play()
        assertEquals(1, clip.closeCount)
        assertEquals(2, clip.playCount)
    }

    @Test
    fun `eat playback blocks input plays one sound and finishes exactly once`() = onEdt {
        val controller = eatingController(healthAfter = 11)
        var time = 0L
        var finished = 0
        var clicks = 0
        val sound = RecordingEatRecoverySoundPlayer()
        val board = BoardPanel(controller, GuiAssets(), { clicks++ }, { time }, sound)
        board.setSize(1086, 1448)
        val event = eatEvent(id = 1, healthBefore = 9, healthAfter = 11)

        try {
            assertTrue(board.playEatAnimation(event) { finished++ })
            assertTrue(board.isEatAnimating)
            assertTrue(board.isAnimating)
            assertEquals(1, sound.playCount)

            board.dispatchEvent(MouseEvent(board, MouseEvent.MOUSE_CLICKED, 0, 0, 295, 750, 1, false))
            assertEquals(0, clicks, "clicks must not reach the controller during recovery playback")

            time = (EAT_ANIMATION_DURATION_MILLIS - 1) * 1_000_000L
            board.advanceEatAnimation()
            assertEquals(0, finished)
            assertTrue(board.isEatAnimating)

            time = EAT_ANIMATION_DURATION_MILLIS * 1_000_000L
            board.advanceEatAnimation()
            board.advanceEatAnimation()
            assertEquals(1, finished)
            assertFalse(board.isEatAnimating)
            assertFalse(board.isAnimating)
            assertEquals(1, sound.playCount)
            assertFalse(board.playEatAnimation(event) { finished++ }, "the same event must never replay")
            assertFalse(
                board.playEatAnimation(event.copy(id = 0)) { finished++ },
                "an older event must never replay after a newer recovery",
            )
        } finally {
            board.cancelAnimations()
        }
    }

    @Test
    fun `cancelling recovery cannot finish against a new game`() = onEdt {
        val controller = eatingController(healthAfter = 13)
        var time = 0L
        var finished = 0
        val sound = RecordingEatRecoverySoundPlayer()
        val board = BoardPanel(controller, GuiAssets(), {}, { time }, sound)

        try {
            assertTrue(board.playEatAnimation(eatEvent(id = 2, healthBefore = 13, healthAfter = 13)) { finished++ })
            board.cancelAnimations()
            controller.startNewGame(2)
            time = (EAT_ANIMATION_DURATION_MILLIS + 100) * 1_000_000L
            board.advanceEatAnimation()

            assertFalse(board.isAnimating)
            assertEquals(0, finished)
            assertEquals(1, sound.playCount)
        } finally {
            board.cancelAnimations()
        }
    }

    @Test
    fun `recovery marker follows the meter route instead of cutting across its corner`() {
        val meter = Rectangle(0, 0, 1000, 500)
        val start = hungerMeterMarkerCenter(6, Player.MAX_HEALTH, meter)
        val end = hungerMeterMarkerCenter(11, Player.MAX_HEALTH, meter)
        val midpoint = hungerMeterRecoveryCenter(6, 11, 0.5f, Player.MAX_HEALTH, meter)

        assertEquals(start, hungerMeterRecoveryCenter(6, 11, 0f, Player.MAX_HEALTH, meter))
        assertEquals(end, hungerMeterRecoveryCenter(6, 11, 1f, Player.MAX_HEALTH, meter))
        assertTrue(
            midpoint.x == hungerMeterMarkerCenter(8, Player.MAX_HEALTH, meter).x ||
                midpoint.y == hungerMeterMarkerCenter(8, Player.MAX_HEALTH, meter).y,
            "the interpolated marker must remain on one segment of the U-shaped route",
        )
    }

    @Test
    fun `flying recovery icon interpolates centers and lifts above the direct path`() {
        val start = Point(100, 300)
        val destination = Point(500, 100)
        val rect = eatRecoveryIconRect(start, destination, travel = 0.5f, scale = 1f, lift = 0.2f, iconSize = 40)

        assertEquals(300, rect.centerX.toInt())
        assertTrue(rect.centerY < 200, "positive lift should arc the icon above its direct path")
        assertEquals(40, rect.width)
        assertEquals(40, rect.height)
    }

    @Test
    fun `recovery label anchor stays inside the board near the top meter`() {
        val board = Rectangle(0, 0, 1086, 1448)
        val fontSize = 52f
        val destination = Point(680, 70)
        val anchor = recoveryLabelAnchor(destination, board, fontSize, avoidanceDistance = 90)

        assertTrue(anchor.x >= fontSize)
        assertTrue(anchor.x <= board.width - fontSize)
        assertTrue(anchor.y >= fontSize * 2f)
        assertTrue(anchor.x < destination.x, "a right-half marker should place its label to the left")
        // The renderer keeps its small upward drift below the meter marker without clipping.
        assertTrue(anchor.y - fontSize * 1.10f > board.y)
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MOGURA_EAT_PREVIEW_DIR", matches = ".+")
    fun `render eat preview frames when explicitly requested`() = onEdt {
        val directory = File(System.getenv("MOGURA_EAT_PREVIEW_DIR"))
        directory.mkdirs()
        val controller = eatingController(healthAfter = 11)
        var time = 0L
        val board = BoardPanel(controller, GuiAssets(), {}, { time })
        board.setSize(1086, 1448)
        try {
            assertTrue(board.playEatAnimation(eatEvent(id = 20, healthBefore = 9, healthAfter = 11)) {})
            listOf(0L, 120L, 250L, 400L, 600L, 799L).forEach { milliseconds ->
                time = milliseconds * 1_000_000L
                val canvas = BufferedImage(board.width, board.height, BufferedImage.TYPE_INT_ARGB)
                val graphics = canvas.createGraphics()
                try {
                    board.paint(graphics)
                } finally {
                    graphics.dispose()
                }
                ImageIO.write(canvas, "png", File(directory, "eat-$milliseconds.png"))
            }
        } finally {
            board.cancelAnimations()
        }
    }

    private fun eatingController(healthAfter: Int): MoguraGameController = MoguraGameController().apply {
        startNewGame(2)
        currentPlayer!!.moveTo(Position(1, 1))
        repeat(Player.MAX_HEALTH - healthAfter) { currentPlayer!!.reduceHealth(isOnSurface = false) }
    }

    private fun eatEvent(id: Long, healthBefore: Int, healthAfter: Int) = EatAnimationEvent(
        id = id,
        playerId = 0,
        foodType = FoodType.EARTHWORM,
        requestedRecovery = 2,
        actualRecovery = healthAfter - healthBefore,
        healthBefore = healthBefore,
        healthAfter = healthAfter,
    )

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

private class RecordingEatRecoverySoundPlayer : EatRecoverySoundPlayer {
    var playCount = 0

    override fun play() {
        playCount++
    }
}

private class RecordingShortAudioClip : ShortAudioClip {
    var playCount = 0
    var closeCount = 0

    override fun playFromStart() {
        playCount++
    }

    override fun close() {
        closeCount++
    }
}
