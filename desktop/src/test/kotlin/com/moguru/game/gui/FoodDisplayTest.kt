package com.moguru.game.gui

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodType
import java.awt.Point
import java.awt.Rectangle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FoodDisplayTest {

    @Test
    fun `food card is small while route visibility matters`() {
        assertEquals(0.42, foodCardScaleForPhase(TurnPhase.DIG))
        assertEquals(0.42, foodCardScaleForPhase(TurnPhase.MOVE))
    }

    @Test
    fun `food card keeps large display outside dig and move`() {
        assertEquals(0.75, foodCardScaleForPhase(TurnPhase.CAPTURE))
        assertEquals(0.75, foodCardScaleForPhase(TurnPhase.DECIDE))
        assertEquals(0.75, foodCardScaleForPhase(TurnPhase.END))
    }

    @Test
    fun `beetle larva food image uses beetle larva asset`() {
        val path = foodImagePath(FoodType.BEETLE_LARVA)

        assertEquals("assets/images/foods/food_beetle_larva.png", path)
        assertFalse(path.contains("dango", ignoreCase = true))
    }

    @Test
    fun `food preview is larger than the board cell`() {
        val cell = Rectangle(100, 100, 80, 80)
        val board = Rectangle(0, 0, 400, 400)

        val preview = foodPreviewRect(cell, board)

        assertTrue(preview.width > cell.width)
        assertTrue(preview.height > cell.height)
    }

    @Test
    fun `food preview stays inside board image`() {
        val cell = Rectangle(330, 330, 60, 60)
        val board = Rectangle(0, 0, 400, 400)

        val preview = foodPreviewRect(cell, board)

        assertTrue(board.contains(preview))
    }

    @Test
    fun `food card hover area is only the drawn compact food card`() {
        val cell = Rectangle(10, 20, 100, 80)

        val card = foodCardRect(cell, foodCardScaleForPhase(TurnPhase.DIG))

        assertEquals(Rectangle(73, 63, 34, 34), card)
        assertTrue(card.contains(Point(90, 80)))
        assertTrue(cell.contains(Point(20, 30)))
        assertFalse(card.contains(Point(20, 30)))
    }
}
