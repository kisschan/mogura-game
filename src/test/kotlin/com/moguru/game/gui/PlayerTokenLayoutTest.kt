package com.moguru.game.gui

import java.awt.Point
import java.awt.Rectangle
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

    private companion object {
        const val MIN_VISIBLE_TOKEN_CENTER_GAP = 16
    }
}
