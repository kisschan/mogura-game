package com.moguru.game.gui

import java.awt.Rectangle
import java.awt.image.BufferedImage

fun currentPlayerPortraitDrawSize(
    panelSize: Int = CURRENT_PLAYER_PORTRAIT_PANEL_SIZE,
    inset: Int = CURRENT_PLAYER_PORTRAIT_INSET,
): Int = (panelSize - inset * 2).coerceAtLeast(1)

fun visibleImageBounds(image: BufferedImage, alphaThreshold: Int = 16): Rectangle {
    var minX = image.width
    var minY = image.height
    var maxX = -1
    var maxY = -1

    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            val alpha = image.getRGB(x, y) ushr 24
            if (alpha > alphaThreshold) {
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }
    }

    return if (maxX < minX || maxY < minY) {
        Rectangle(0, 0, image.width, image.height)
    } else {
        Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }
}

const val CURRENT_PLAYER_PANEL_HEIGHT = 136
const val CURRENT_PLAYER_PORTRAIT_PANEL_SIZE = 112
const val CURRENT_PLAYER_PORTRAIT_INSET = 6
