package com.moguru.game.gui

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Player
import java.awt.Rectangle
import java.awt.image.BufferedImage

data class CurrentPlayerDisplay(
    val playerId: Int?,
    val titleText: String,
    val phaseText: String,
    val healthText: String,
    val scoreText: String,
    val carriedFoodText: String,
)

fun currentPlayerDisplay(player: Player?, phase: TurnPhase?): CurrentPlayerDisplay =
    CurrentPlayerDisplay(
        playerId = player?.id,
        titleText = player?.let { "${it.name} の番" } ?: "-",
        phaseText = "フェーズ: ${phase?.displayName() ?: "-"}",
        healthText = player?.let { "体力: ${it.health}/${Player.MAX_HEALTH}" } ?: "体力: -",
        scoreText = player?.let { "点: ${it.score}" } ?: "点: -",
        carriedFoodText = "所持: ${player?.carriedFood?.type?.displayName() ?: "-"}",
    )

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
