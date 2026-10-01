package com.moguru.game.gui

import com.moguru.game.engine.PlayerConfig
import com.moguru.game.presenter.MoguraGameController

internal data class DesktopGameSetup(val players: List<PlayerConfig>, val startPlayerIndex: Int)

/** Collects a setup without changing the running game until all dialogs are confirmed. */
internal fun collectDesktopGameSetup(
    select: (message: String, title: String, choices: Array<String>) -> String?,
): DesktopGameSetup? {
    val choices = arrayOf("2", "3", "4")
    val choice = select(
        "プレイヤー人数を選んでください",
        "新しいゲーム",
        choices,
    ) ?: return null
    val playerCount = choice.toInt()
    val remainingMoles = MoguraGameController.moleOptions.toMutableList()
    val remainingNests = MoguraGameController.nestPositions.toMutableList()
    val configs = mutableListOf<PlayerConfig>()

    repeat(playerCount) { index ->
        val moleLabels = remainingMoles.map { it.name }.toTypedArray()
        val moleChoice = select(
            "P${index + 1} のモグラを選んでください",
            "モグラ選択",
            moleLabels,
        ) ?: return null
        val mole = remainingMoles.removeAt(moleLabels.indexOf(moleChoice).coerceAtLeast(0))

        val nestLabels = remainingNests.map(::nestChoiceLabel).toTypedArray()
        val nestChoice = select(
            "P${index + 1} の巣を選んでください",
            "巣選択",
            nestLabels,
        ) ?: return null
        val nest = remainingNests.removeAt(nestLabels.indexOf(nestChoice).coerceAtLeast(0))
        configs.add(PlayerConfig(mole.name, nest, playerId = mole.playerId))
    }

    val startLabels = configs.mapIndexed { index, config ->
        "P${index + 1}: ${config.name}"
    }.toTypedArray()
    val startChoice = select(
        "先手プレイヤーを選んでください",
        "先手選択",
        startLabels,
    ) ?: return null
    val startPlayerIndex = startLabels.indexOf(startChoice).takeIf { it >= 0 } ?: 0

    return DesktopGameSetup(configs, startPlayerIndex)
}
