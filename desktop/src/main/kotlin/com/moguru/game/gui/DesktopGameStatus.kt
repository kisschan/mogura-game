package com.moguru.game.gui

import com.moguru.game.engine.TurnPhase
import com.moguru.game.engine.GameState
import com.moguru.game.presenter.*

internal fun desktopCurrentPlayerDisplay(controller: MoguraGameController): CurrentPlayerDisplay {
    val current = controller.engine
    if (current?.gameState != GameState.FINISHED) return controller.playScreenUiState().currentPlayer
    val winner = current.checkWinCondition()
    return currentPlayerDisplay(winner, null).copy(
        titleText = winner?.let { "${it.name} の勝利" } ?: "ゲーム終了",
        phaseText = "ゲーム終了",
    )
}

internal fun desktopGameStatusText(controller: MoguraGameController): String {
    val current = controller.engine
    if (current?.gameState == GameState.FINISHED) {
        val winner = current.checkWinCondition()
        return if (winner != null) {
            "${winner.name} の勝利です（${winner.score}点）。新しいゲームを開始できます。"
        } else {
            "全員脱落でドローです。新しいゲームを開始できます。"
        }
    }
    val uiState = controller.playScreenUiState()
    val actions = uiState.actionAvailability
    val canAdvanceFromDig = controller.canAdvanceFromDigWithoutTargets()
    val preparedDigShape = controller.pendingDigDrawnTile?.shape
    return uiState.captureOutcome?.let(::desktopCaptureOutcomeStatus)
        ?: controller.pendingFoodDecision?.let { food ->
            val prefix = if (uiState.pendingDecisionSource == FoodDecisionSource.ROBBERY) "強奪した " else ""
            "${prefix}${food.type.displayName()} を食べるか、巣へ持ち帰るか選んでください。"
        } ?: if (canAdvanceFromDig) {
        "掘れる穴タイルがありません。移動へ進んでください。"
    } else if (actions.canRob) {
        "強奪するエサを選んでください。"
    } else if (controller.pendingDigPlacement != null) {
        val pending = controller.pendingDigPlacement!!
        val selected = controller.pendingDigTileChoice?.label() ?: DigTileChoice.REVEALED.label()
        val drawn = pending.drawnTile?.shape?.displayName() ?: "なし"
        desktopPendingDigStatus(selected, drawn)
    } else if (actions.activePhase == TurnPhase.DIG && preparedDigShape != null) {
        "山札: ${preparedDigShape.displayName()}。確認してから掘る場所を選んでください。"
    } else {
        desktopPhaseHelp(current?.currentPhase)
    }
}
