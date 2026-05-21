package com.moguru.game.gui

import java.awt.Point
import java.awt.Rectangle
import kotlin.math.roundToInt

fun hungerMeterMarkerCenter(health: Int, maxHealth: Int, rect: Rectangle): Point {
    require(maxHealth > 0) { "maxHealth must be greater than 0" }

    val clampedHealth = health.coerceIn(0, maxHealth)
    val progress = (maxHealth - clampedHealth).toDouble() / maxHealth
    val left = rect.x + (rect.width * HUNGER_METER_LEFT_RATIO).roundToInt()
    val right = rect.x + (rect.width * HUNGER_METER_RIGHT_RATIO).roundToInt()
    val top = rect.y + (rect.height * HUNGER_METER_TOP_RATIO).roundToInt()
    val bottom = rect.y + (rect.height * HUNGER_METER_BOTTOM_RATIO).roundToInt()
    val topLength = (right - left).toDouble()
    val sideLength = (bottom - top).toDouble()
    val bottomLength = topLength
    val routeLength = topLength + sideLength + bottomLength
    val distance = progress * routeLength

    return when {
        distance <= topLength -> Point((left + distance).roundToInt(), top)
        distance <= topLength + sideLength -> Point(right, (top + distance - topLength).roundToInt())
        else -> Point((right - (distance - topLength - sideLength)).roundToInt(), bottom)
    }
}

fun hungerMeterMarkerCenters(
    healths: List<Int>,
    maxHealth: Int,
    rect: Rectangle,
): List<Point> {
    return healths.map { health -> hungerMeterMarkerCenter(health, maxHealth, rect) }
}

fun hungerMeterMarkerRects(centers: List<Point>, markerSize: Int): List<Rectangle> {
    val seen = mutableMapOf<Point, Int>()
    return centers.map { center ->
        val overlapIndex = seen.getOrDefault(center, 0)
        seen[center] = overlapIndex + 1
        markerRect(offsetMarkerCenter(center, overlapIndex, markerSize), markerSize)
    }
}

private fun markerRect(center: Point, markerSize: Int): Rectangle =
    Rectangle(center.x - markerSize / 2, center.y - markerSize / 2, markerSize, markerSize)

private fun offsetMarkerCenter(base: Point, overlapIndex: Int, markerSize: Int): Point {
    if (overlapIndex == 0) return base

    val step = (markerSize * HUNGER_MARKER_OVERLAP_STEP_RATIO).roundToInt().coerceAtLeast(4)
    val offsetX = when (overlapIndex) {
        1, 3 -> step
        else -> 0
    }
    val offsetY = when (overlapIndex) {
        2, 3 -> step
        else -> 0
    }
    return Point(base.x + offsetX, base.y + offsetY)
}

fun boardHungerMeterRect(boardRect: Rectangle): Rectangle {
    val width = (boardRect.width * HUNGER_BOARD_WIDTH_RATIO).roundToInt()
    val height = (width / HUNGER_METER_ASPECT).roundToInt()
    val x = boardRect.x + (boardRect.width - width) / 2
    val y = boardRect.y + (boardRect.height * HUNGER_BOARD_TOP_RATIO).roundToInt()
    return Rectangle(x, y, width, height)
}

private const val HUNGER_METER_LEFT_RATIO = 0.13
private const val HUNGER_METER_RIGHT_RATIO = 0.87
private const val HUNGER_METER_TOP_RATIO = 0.24
private const val HUNGER_METER_BOTTOM_RATIO = 0.76
private const val HUNGER_METER_ASPECT = 1536.0 / 762.0
private const val HUNGER_BOARD_WIDTH_RATIO = 0.72
private const val HUNGER_BOARD_TOP_RATIO = 0.018
private const val HUNGER_MARKER_OVERLAP_STEP_RATIO = 0.24
