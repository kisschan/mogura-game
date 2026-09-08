package com.moguru.game.gui

import com.moguru.game.engine.GameState
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.Player
import com.moguru.game.model.Position
import com.moguru.game.presenter.EAT_ANIMATION_DURATION_MILLIS
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.presenter.TURN_CONSUMPTION_ANIMATION_DURATION_MILLIS
import com.moguru.game.presenter.TurnConsumptionAnimationEvent
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

class TurnConsumptionAnimationTest {
    @Test
    fun `direct finish waits for consumption playback before refreshing next player`() = onEdt {
        val controller = controllerAtEndPhase()
        var time = 0L
        var refreshes = 0
        var inputBlocks = 0
        val board = BoardPanel(controller, GuiAssets(), {}, { time })
        val flow = DesktopActionAnimationFlow(
            controller = controller,
            boardPanel = board,
            refresh = { refreshes++ },
            blockInputs = { inputBlocks++ },
            showFailure = {},
        )

        try {
            board.prepareTurnConsumptionAnimation()
            val result = controller.finishTurn()
            val event = requireNotNull(result.turnConsumptionAnimation)
            assertEquals(13, event.healthBefore)
            assertEquals(12, event.healthAfter)
            flow.handle(result)
            flow.handle(result)

            assertTrue(board.isTurnConsumptionAnimating)
            assertEquals(1, inputBlocks)
            assertEquals(0, refreshes, "duplicate callbacks must not reveal or advance the next-player state")

            time = TURN_CONSUMPTION_ANIMATION_DURATION_MILLIS * 1_000_000L
            board.advanceTurnConsumptionAnimation()
            board.advanceTurnConsumptionAnimation()

            assertFalse(board.isAnimating)
            assertEquals(1, refreshes)
        } finally {
            board.cancelAnimations()
        }
    }

    @Test
    fun `terminal elimination waits for consumption playback before refreshing game result`() = onEdt {
        val controller = controllerAtEndPhase()
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        player.moveTo(Position(0, 0))
        repeat(12) { player.reduceHealth(isOnSurface = false) }
        var time = 0L
        var refreshes = 0
        val board = BoardPanel(controller, GuiAssets(), {}, { time })
        val flow = DesktopActionAnimationFlow(
            controller = controller,
            boardPanel = board,
            refresh = { refreshes++ },
            blockInputs = {},
            showFailure = {},
        )

        try {
            board.prepareTurnConsumptionAnimation()
            val result = controller.finishTurn()
            val event = requireNotNull(result.turnConsumptionAnimation)
            assertEquals(GameState.FINISHED, engine.gameState)
            assertEquals(1, event.healthBefore)
            assertEquals(0, event.healthAfter)

            flow.handle(result)

            assertTrue(board.isTurnConsumptionAnimating)
            assertEquals(0, refreshes, "the terminal result must stay hidden during consumption playback")

            time = TURN_CONSUMPTION_ANIMATION_DURATION_MILLIS * 1_000_000L
            board.advanceTurnConsumptionAnimation()

            assertFalse(board.isAnimating)
            assertEquals(1, refreshes, "the terminal result is refreshed once after playback")
        } finally {
            board.cancelAnimations()
        }
    }

    @Test
    fun `consecutive automatic turns play one consumption event at a time`() = onEdt {
        val controller = MoguraGameController().apply {
            startNewGame(2)
            engine!!.boardState.clear()
        }
        val engine = controller.engine!!
        val firstPlayer = engine.players[0]
        val secondPlayer = engine.players[1]
        var time = 0L
        var refreshes = 0
        var inputBlocks = 0
        val board = BoardPanel(controller, GuiAssets(), {}, { time })
        val flow = DesktopActionAnimationFlow(
            controller = controller,
            boardPanel = board,
            refresh = { refreshes++ },
            blockInputs = { inputBlocks++ },
            showFailure = {},
        )

        try {
            board.prepareTurnConsumptionAnimation()
            val firstResult = requireNotNull(controller.autoAdvanceWhileNoChoice())
            val firstEvent = requireNotNull(firstResult.turnConsumptionAnimation)
            assertEquals(firstPlayer.id, firstEvent.playerId)

            flow.handle(firstResult)

            assertTrue(board.isTurnConsumptionAnimating)
            assertEquals(12, firstPlayer.health)
            assertEquals(13, secondPlayer.health)
            assertEquals(1, inputBlocks)
            assertEquals(0, refreshes)

            time = TURN_CONSUMPTION_ANIMATION_DURATION_MILLIS * 1_000_000L
            board.advanceTurnConsumptionAnimation()

            assertTrue(board.isTurnConsumptionAnimating, "the second event starts only after the first completes")
            assertEquals(12, firstPlayer.health)
            assertEquals(12, secondPlayer.health, "the second player's distinct turn was consumed")
            assertEquals(0, engine.currentPlayerIndex)
            assertEquals(2, inputBlocks)
            assertEquals(0, refreshes, "no next-state refresh occurs between consecutive events")

            board.advanceTurnConsumptionAnimation()
            assertTrue(board.isTurnConsumptionAnimating, "the second event has its own playback interval")
            assertEquals(12, firstPlayer.health)
            assertEquals(12, secondPlayer.health)
            assertEquals(2, inputBlocks)
        } finally {
            board.cancelAnimations()
        }
    }

    @Test
    fun `eat recovery chains into consumption without a frame or input gap`() = onEdt {
        val controller = controllerReadyToEat()
        var time = 0L
        var refreshes = 0
        var inputBlocks = 0
        val board = BoardPanel(controller, GuiAssets(), {}, { time })
        val flow = DesktopActionAnimationFlow(
            controller = controller,
            boardPanel = board,
            refresh = { refreshes++ },
            blockInputs = { inputBlocks++ },
            showFailure = {},
        )

        try {
            board.prepareTurnConsumptionAnimation()
            val eatResult = controller.eatPendingFood()
            assertEquals(1, eatResult.eatAnimation?.actualRecovery)
            flow.handle(eatResult)
            assertTrue(board.isEatAnimating)
            assertEquals(1, refreshes, "healed health is rendered at recovery start")

            time = EAT_ANIMATION_DURATION_MILLIS * 1_000_000L
            board.advanceEatAnimation()
            assertFalse(board.isEatAnimating)
            assertTrue(board.isTurnConsumptionAnimating)
            assertTrue(board.isAnimating)
            assertEquals(1, refreshes, "next state remains hidden between the two effects")

            time = (EAT_ANIMATION_DURATION_MILLIS + TURN_CONSUMPTION_ANIMATION_DURATION_MILLIS) * 1_000_000L
            board.advanceTurnConsumptionAnimation()
            assertFalse(board.isAnimating)
            assertEquals(2, refreshes)
            assertTrue(inputBlocks >= 2)
            assertEquals(11, controller.engine!!.players.first { it.id == 0 }.health)
        } finally {
            board.cancelAnimations()
        }
    }

    @Test
    fun `consumption blocks board clicks and stale or cancelled callbacks cannot run`() = onEdt {
        val controller = controllerAtEndPhase()
        var time = 0L
        var finished = 0
        var clicks = 0
        val board = BoardPanel(controller, GuiAssets(), { clicks++ }, { time })
        board.setSize(1086, 1448)

        try {
            board.prepareTurnConsumptionAnimation()
            val event = requireNotNull(controller.finishTurn().turnConsumptionAnimation)
            assertTrue(board.playTurnConsumptionAnimation(event) { finished++ })
            board.dispatchEvent(MouseEvent(board, MouseEvent.MOUSE_CLICKED, 0, 0, 295, 750, 1, false))
            assertEquals(0, clicks)
            board.cancelAnimations()
            time = TURN_CONSUMPTION_ANIMATION_DURATION_MILLIS * 1_000_000L
            board.advanceTurnConsumptionAnimation()
            assertEquals(0, finished)
            assertFalse(board.playTurnConsumptionAnimation(event) { finished++ })
            assertFalse(board.playTurnConsumptionAnimation(event.copy(id = event.id - 1)) { finished++ })
        } finally {
            board.cancelAnimations()
        }
    }

    @Test
    fun `marker interpolation and label use actual consumption`() {
        val meter = Rectangle(0, 0, 1000, 500)
        val underground = event(actual = 1, before = 10, after = 9, surface = false)
        val surface = event(actual = 2, before = 10, after = 8, surface = true)
        val cappedSurface = event(actual = 1, before = 1, after = 0, surface = true)

        assertEquals("-1", turnConsumptionLabel(underground))
        assertEquals("-2", turnConsumptionLabel(surface))
        assertEquals("-1", turnConsumptionLabel(cappedSurface))
        assertEquals(
            hungerMeterMarkerCenter(10, Player.MAX_HEALTH, meter),
            turnConsumptionMarkerCenter(underground, 0f, Player.MAX_HEALTH, meter),
        )
        assertEquals(
            hungerMeterMarkerCenter(9, Player.MAX_HEALTH, meter),
            turnConsumptionMarkerCenter(underground, 1f, Player.MAX_HEALTH, meter),
        )
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MOGURA_TURN_CONSUMPTION_PREVIEW_DIR", matches = ".+")
    fun `render consumption preview frames when explicitly requested`() = onEdt {
        val directory = File(System.getenv("MOGURA_TURN_CONSUMPTION_PREVIEW_DIR"))
        directory.mkdirs()
        val controller = controllerAtEndPhase()
        var time = 0L
        val board = BoardPanel(controller, GuiAssets(), {}, { time })
        board.setSize(1086, 1448)
        try {
            board.prepareTurnConsumptionAnimation()
            val event = requireNotNull(controller.finishTurn().turnConsumptionAnimation)
            assertTrue(board.playTurnConsumptionAnimation(event) {})
            listOf(0L, 100L, 250L, 400L, 499L).forEach { milliseconds ->
                time = milliseconds * 1_000_000L
                val canvas = BufferedImage(board.width, board.height, BufferedImage.TYPE_INT_ARGB)
                val graphics = canvas.createGraphics()
                try {
                    board.paint(graphics)
                } finally {
                    graphics.dispose()
                }
                ImageIO.write(canvas, "png", File(directory, "consumption-$milliseconds.png"))
            }
        } finally {
            board.cancelAnimations()
        }
    }

    private fun controllerAtEndPhase() = MoguraGameController().apply {
        startNewGame(2)
        repeat(3) { engine!!.advancePhase() }
    }

    private fun controllerReadyToEat() = MoguraGameController().apply {
        startNewGame(2)
        currentPlayer!!.reduceHealth(isOnSurface = false)
        currentPlayer!!.reduceHealth(isOnSurface = false)
        currentPlayer!!.moveTo(Position(1, 1))
        engine!!.placeFoodAt(Position(1, 1), FoodCard(FoodType.BEETLE_LARVA, emptyMap()))
        engine!!.advancePhase()
        engine!!.advancePhase()
        check(captureCurrentPositionImmediately().success)
    }

    private fun event(actual: Int, before: Int, after: Int, surface: Boolean) = TurnConsumptionAnimationEvent(
        id = 1,
        playerId = 0,
        requestedConsumption = if (surface) 2 else 1,
        actualConsumption = actual,
        healthBefore = before,
        healthAfter = after,
        isOnSurface = surface,
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
