package com.moguru.game.presenter

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Player

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
