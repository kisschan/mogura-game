package com.moguru.game.gui

import java.awt.Color
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HungerMeterTest {

    private val meterRect = Rectangle(0, 0, 1000, 500)
    private val boardRect = Rectangle(0, 0, 1086, 1448)

    @Test
    fun `full health starts at upper left of meter route`() {
        assertEquals(Point(130, 120), hungerMeterMarkerCenter(13, 13, meterRect))
    }

    @Test
    fun `zero health ends at lower left of meter route`() {
        assertEquals(Point(130, 380), hungerMeterMarkerCenter(0, 13, meterRect))
    }

    @Test
    fun `middle health moves along top and right side before lower path`() {
        assertEquals(Point(532, 120), hungerMeterMarkerCenter(10, 13, meterRect))
        assertEquals(Point(870, 317), hungerMeterMarkerCenter(6, 13, meterRect))
        assertEquals(Point(398, 380), hungerMeterMarkerCenter(2, 13, meterRect))
    }

    @Test
    fun `health values are clamped to valid meter range`() {
        assertEquals(Point(130, 120), hungerMeterMarkerCenter(99, 13, meterRect))
        assertEquals(Point(130, 380), hungerMeterMarkerCenter(-5, 13, meterRect))
    }

    @Test
    fun `single board hunger meter is placed above playable grid`() {
        val rect = boardHungerMeterRect(boardRect)

        assertTrue(rect.y + rect.height < 488)
        assertTrue(rect.width > rect.height)
    }

    @Test
    fun `board hunger meter rect does not depend on player count`() {
        val rect = boardHungerMeterRect(boardRect)

        assertEquals(rect, boardHungerMeterRect(boardRect))
    }

    @Test
    fun `same health markers are offset from each other`() {
        val centers = hungerMeterMarkerCenters(
            healths = listOf(13, 13, 13, 13),
            maxHealth = 13,
            rect = meterRect,
            markerSize = 80,
        )

        assertEquals(4, centers.distinct().size)
        assertTrue(centers.all { center ->
            center.x in meterRect.x..(meterRect.x + meterRect.width) &&
                center.y in meterRect.y..(meterRect.y + meterRect.height)
        })
    }

    @Test
    fun `white pixels in meter image become transparent`() {
        val image = BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, Color.WHITE.rgb)
        image.setRGB(1, 0, Color(80, 48, 24).rgb)

        val transparent = makeWhiteTransparent(image)

        assertEquals(0, transparent.getRGB(0, 0) ushr 24)
        assertEquals(255, transparent.getRGB(1, 0) ushr 24)
    }
}
