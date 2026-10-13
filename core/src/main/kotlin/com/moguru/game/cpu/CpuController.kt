package com.moguru.game.cpu

import com.moguru.game.engine.GameState
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Rotation
import com.moguru.game.presenter.*

/** Build a CPU view without advancing rules, drawing a card, rolling, or editing a preview. */
fun MoguraGameController.cpuObservation(): CpuObservation {
    val current = engine
    val ui = playScreenUiState()
    val pending = pendingDigPlacement
    val capture = pendingCaptureRoll
    val playing = current?.gameState == GameState.PLAYING
    val actions = buildList {
        if (!playing || capture != null) return@buildList
        when (current.currentPhase) {
            TurnPhase.DIG -> if (pending != null) {
                val choices = buildList {
                    if (pending.revealedTile != null) add(DigTileChoice.REVEALED)
                    if (pending.drawnTile != null) add(DigTileChoice.DRAWN)
                }
                for (choice in choices) for (rotation in Rotation.entries) {
                    if (choice != pendingDigTileChoice || rotation != pendingDigRotation) {
                        add(CpuAction.PrepareDig(choice, rotation))
                    }
                }
                add(CpuAction.ConfirmDig)
            } else {
                digTargets().sortedWith(compareBy({ it.row }, { it.col })).forEach { add(CpuAction.RevealDig(it)) }
                if (canAdvanceFromDigWithoutTargets()) add(CpuAction.Skip)
            }
            TurnPhase.MOVE -> {
                moveTargets().sortedWith(compareBy({ it.row }, { it.col })).forEach { add(CpuAction.Move(it)) }
                if (ui.actionAvailability.canSkip) add(CpuAction.Skip)
            }
            TurnPhase.CAPTURE -> {
                if (ui.actionAvailability.canCapture) ui.captureTargets.forEach { add(CpuAction.Capture(it.index)) }
                if (ui.actionAvailability.canSkip) add(CpuAction.Skip)
            }
            TurnPhase.DECIDE -> {
                if (ui.actionAvailability.canRob) ui.robberyTargets.forEach { add(CpuAction.Rob(it.index)) }
                if (ui.actionAvailability.canEat) add(CpuAction.Eat)
                if (ui.actionAvailability.canCarry) add(CpuAction.Carry)
                if (ui.actionAvailability.canSkip) add(CpuAction.Skip)
            }
            TurnPhase.END -> if (ui.actionAvailability.canEndTurn) add(CpuAction.FinishTurn)
        }
    }
    return CpuObservation(
        gameState = current?.gameState ?: GameState.SETUP,
        phase = current?.currentPhase,
        currentPlayerId = currentPlayer?.id,
        players = current?.players.orEmpty().map { player ->
            CpuPlayer(player.id, player.position, player.nestPosition, player.health,
                player.carriedFood?.type, player.storedFoods.map { it.type })
        },
        tiles = current?.board?.cells?.keys.orEmpty().mapNotNull { position ->
            current?.boardState?.getTile(position)?.let { tile ->
                position to if (tile.isFaceDown) CpuTile(null) else CpuTile(tile.shape, tile.openSides.toSet())
            }
        }.toMap(),
        foods = current?.foodPositions.orEmpty().mapValues { (position, foods) ->
            foods.mapIndexed { index, food ->
                val revealedByCapture = capture?.position == position && capture.foodIndex == index
                CpuFood(food.type.takeIf { !food.isFaceDown || revealedByCapture })
            }
        },
        // These cards are public even though the model's stored card may still have isFaceDown=true.
        drawnTile = pendingDigDrawnTile?.shape,
        pendingDig = pending?.let {
            CpuDigPlacement(it.position, buildMap {
                it.revealedTile?.let { tile -> put(DigTileChoice.REVEALED, tile.shape) }
                it.drawnTile?.let { tile -> put(DigTileChoice.DRAWN, tile.shape) }
            }, pendingDigTileChoice ?: DigTileChoice.REVEALED, pendingDigRotation ?: Rotation.DEG_0)
        },
        pendingFood = pendingFoodDecision?.type,
        pendingFoodSource = pendingFoodDecisionSource,
        waitingForCapture = capture != null,
        canRobAfterStaying = playing && hasRobberyOpportunityForCurrentPlayer(),
        canEatStoredFoodAfterSkipping = playing && hasOwnNestStoredFoodOpportunityForCurrentPlayer(),
        legalActions = actions,
    )
}

/** Revalidate, then use the same rules operations as human input. Never run automatic progression here. */
fun MoguraGameController.applyCpuAction(action: CpuAction): GameActionResult {
    if (action !in cpuObservation().legalActions) {
        return GameActionResult(false, "現在の状態ではそのCPU操作を実行できません。")
    }
    return when (action) {
        is CpuAction.RevealDig -> revealDigTile(action.position)
        is CpuAction.PrepareDig -> {
            var result = GameActionResult(false, "タイルの選択と向きは変更されていません。")
            if (pendingDigTileChoice != action.choice) {
                result = selectPendingDigTile(action.choice)
                if (!result.success) return result
            }
            if (pendingDigRotation != action.rotation) result = setPendingDigRotation(action.rotation)
            result
        }
        CpuAction.ConfirmDig -> confirmPendingDig()
        is CpuAction.Move -> moveTo(action.position)
        is CpuAction.Capture -> {
            val selected = selectCaptureTarget(action.foodIndex)
            if (selected.success) captureCurrentPosition() else selected
        }
        is CpuAction.Rob -> {
            val selected = selectRobberyTarget(action.foodIndex)
            if (selected.success) robSelectedFood() else selected
        }
        CpuAction.Eat -> eatPendingFood()
        CpuAction.Carry -> carryPendingFood()
        CpuAction.Skip -> skipPhase()
        CpuAction.FinishTurn -> finishTurn()
    }
}
