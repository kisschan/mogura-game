package com.moguru.game.gui

import com.moguru.game.engine.TurnPhase
import java.awt.Rectangle
import kotlin.math.min
import kotlin.math.roundToInt

fun foodCardScaleForPhase(phase: TurnPhase): Double = when (phase) {
    TurnPhase.DIG,
    TurnPhase.MOVE,
    -> FOOD_CARD_COMPACT_SCALE

    TurnPhase.CAPTURE,
    TurnPhase.DECIDE,
    TurnPhase.END,
    -> FOOD_CARD_LARGE_SCALE
}

fun foodPreviewRect(cellRect: Rectangle, imageRect: Rectangle): Rectangle {
    val base = min(cellRect.width, cellRect.height)
    val maxSize = min(imageRect.width, imageRect.height)
    val size = (base * FOOD_PREVIEW_SCALE).roundToInt()
        .coerceAtLeast(base + 1)
        .coerceAtMost(maxSize)
    val gap = (base * FOOD_PREVIEW_GAP_SCALE).roundToInt().coerceAtLeast(6)
    val imageRight = imageRect.x + imageRect.width
    val imageBottom = imageRect.y + imageRect.height

    val preferredRightX = cellRect.x + cellRect.width + gap
    val preferredLeftX = cellRect.x - size - gap
    val x = if (preferredRightX + size <= imageRight) {
        preferredRightX
    } else {
        preferredLeftX
    }.coerceIn(imageRect.x, imageRight - size)

    val y = (cellRect.y + (cellRect.height - size) / 2)
        .coerceIn(imageRect.y, imageBottom - size)

    return Rectangle(x, y, size, size)
}

fun foodCardRect(cellRect: Rectangle, scale: Double, padding: Int = FOOD_CARD_PADDING): Rectangle {
    val size = (min(cellRect.width, cellRect.height) * scale).roundToInt()
    return Rectangle(
        cellRect.x + cellRect.width - size - padding,
        cellRect.y + cellRect.height - size - padding,
        size,
        size,
    )
}

const val FOOD_CARD_PADDING = 3

private const val FOOD_CARD_COMPACT_SCALE = 0.42
private const val FOOD_CARD_LARGE_SCALE = 0.75
private const val FOOD_PREVIEW_SCALE = 1.35
private const val FOOD_PREVIEW_GAP_SCALE = 0.12
