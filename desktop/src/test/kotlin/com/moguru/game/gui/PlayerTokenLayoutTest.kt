package com.moguru.game.gui

import com.moguru.game.engine.TurnPhase
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import com.moguru.game.presenter.CaptureOutcomeDisplay
import com.moguru.game.presenter.CaptureOutcomeKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerTokenLayoutTest {

    private val sourceBoardCell = Rectangle(48, 488, 165, 175)

    @Test
    fun `stacked player tokens keep distinct positions at large scale`() {
        val tokenRects = playerTokenRects(sourceBoardCell, 4)

        assertEquals(4, tokenRects.distinct().size)
    }

    @Test
    fun `stacked player tokens stay within the source cell`() {
        val tokenRects = playerTokenRects(sourceBoardCell, 4)

        assertTrue(
            tokenRects.all(sourceBoardCell::contains),
            "stacked token rects should stay inside $sourceBoardCell but were $tokenRects",
        )
    }

    @Test
    fun `stacked player token centers are separated enough to identify each player`() {
        val centers = playerTokenRects(sourceBoardCell, 4).map { rect ->
            Point(rect.centerX.toInt(), rect.centerY.toInt())
        }

        centers.forEachIndexed { index, center ->
            centers.drop(index + 1).forEach { other ->
                val dx = kotlin.math.abs(center.x - other.x)
                val dy = kotlin.math.abs(center.y - other.y)
                assertTrue(
                    dx >= MIN_VISIBLE_TOKEN_CENTER_GAP || dy >= MIN_VISIBLE_TOKEN_CENTER_GAP,
                    "token centers should not visually collapse: $centers",
                )
            }
        }
    }

    @Test
    fun `name badge occupies only a shallow bottom band of the token`() {
        val token = Rectangle(30, 40, 72, 72)

        val badge = playerNameBadgeRect(token, "モグミ")

        assertTrue(badge.height <= token.height / 4)
        assertTrue(token.contains(badge))
    }

    @Test
    fun `long desktop player names are elided without exceeding the badge width`() {
        val token = Rectangle(30, 40, 72, 72)

        val label = playerNameBadgeLabel("モグミスーパー", token.width)

        assertTrue(label.endsWith("…"))
        assertTrue(label.length < "モグミスーパー".length)
    }

    @Test
    fun `desktop player name badge text fits with actual font metrics`() {
        val token = Rectangle(30, 40, 72, 72)
        val image = BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()

        try {
            val label = playerNameBadgeLabelForMetrics(graphics, "モグミスーパー", token.width)
            val badge = playerNameBadgeRectForMetrics(graphics, token, label)

            assertTrue(graphics.fontMetrics.stringWidth(label) + 10 <= badge.width)
            assertTrue(token.contains(badge))
        } finally {
            graphics.dispose()
        }
    }

    @Test
    fun `desktop board paint order keeps food below players and current outline`() {
        assertTrue(boardPaintLayerOrder.indexOf(BoardPaintLayer.FOOD) < boardPaintLayerOrder.indexOf(BoardPaintLayer.PLAYERS))
        assertTrue(boardPaintLayerOrder.indexOf(BoardPaintLayer.PLAYERS) < boardPaintLayerOrder.indexOf(BoardPaintLayer.CURRENT_PLAYER_OUTLINE))
        assertEquals(boardPaintLayerOrder, boardPaintRenderPlan())
    }

    @Test
    fun `desktop highlight fill alpha is fixed for visibility`() {
        assertEquals(52, BOARD_HIGHLIGHT_FILL_ALPHA)
    }

    @Test
    fun `desktop status shows structured capture outcome before phase help`() {
        val outcome = CaptureOutcomeDisplay(
            kind = CaptureOutcomeKind.ESCAPED,
            diceRoll = 1,
            message = "ムカデ はダイス 1 で 右上に逃げました。",
        )

        assertEquals("ダイス: 1　ムカデ はダイス 1 で 右上に逃げました。", desktopCaptureOutcomeStatus(outcome))
        assertEquals(desktopCaptureOutcomeStatus(outcome), desktopGuideStatusText(outcome, TurnPhase.DIG))
    }

    @Test
    fun `desktop compact controls keep accessible hit area and focus visibility`() {
        assertTrue(DESKTOP_ROTATION_BUTTON_SIZE.width >= 40)
        assertTrue(DESKTOP_ROTATION_BUTTON_SIZE.height >= 40)
        assertTrue(DESKTOP_SHOW_BUTTON_FOCUS)
    }

    private companion object {
        const val MIN_VISIBLE_TOKEN_CENTER_GAP = 16
    }
}
