package com.moguru.game.tutorial

import com.moguru.game.cpu.CpuAction
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Direction
import com.moguru.game.model.Position
import com.moguru.game.presenter.CaptureOutcomeKind

/** Board coordinates as the play screen shows them, for example 「2列2行」. */
internal fun Position.boardLabel(): String = "${col + 1}列${row + 1}行"

internal const val PLACE_FIRST = "向きを決めて「置く」を押そう"

/** Open sides of the tile now previewed for the pending dig, or null without one. */
internal fun TutorialContext.previewSides(): Set<Direction>? {
    val pending = controller.pendingDigPlacement ?: return null
    return engine.boardState.getTile(pending.position)?.openSides
}

/** Whether the previewed tile joins the learner's cell, so the learner could walk onto it. */
internal fun TutorialContext.previewJoinsLearner(): Boolean {
    val pending = controller.pendingDigPlacement ?: return false
    return engine.movementEngine.isConnected(learner.position, pending.position, engine.boardState)
}

/**
 * Dig [cell] and place a tile there. With [sides], 「置く」 is accepted only when the previewed tile
 * opens at least those sides. Choosing and rotating the preview is always free.
 */
internal fun TutorialContext.guidedDig(
    stage: Int,
    cell: Position,
    pickText: String,
    placeText: String,
    sides: Set<Direction>? = null,
    placeRefusal: String = PLACE_FIRST,
    placeControls: Set<TutorialControl> = setOf(TutorialControl.ROTATE, TutorialControl.CONFIRM_DIG),
): TutorialStep? {
    if (!learnersTurn || phase != TurnPhase.DIG) return null
    if (controller.pendingDigPlacement == null) {
        val refusal = "練習では${cell.boardLabel()}を掘ろう"
        return TutorialStep(stage, pickText, setOf(cell)) { action ->
            if (action == TutorialAction.SelectDigCell(cell) && cell in controller.digTargets()) null else refusal
        }
    }
    return TutorialStep(stage, placeText, setOf(cell), placeControls) { action ->
        when (action) {
            is TutorialAction.SelectDigTile, is TutorialAction.RotateDigTile -> null
            TutorialAction.ConfirmDig -> if (sides == null || previewSides()?.containsAll(sides) == true) null else placeRefusal
            else -> PLACE_FIRST
        }
    }
}

/** Select and confirm the move to [target]. */
internal fun TutorialContext.guidedMove(stage: Int, target: Position, text: String): TutorialStep? {
    if (!learnersTurn || phase != TurnPhase.MOVE) return null
    val refusal = "練習では${target.boardLabel()}へ移動しよう"
    return TutorialStep(stage, text, setOf(target), setOf(TutorialControl.MOVE)) { action ->
        val allowed = action == TutorialAction.SelectMoveTarget(target) || action == TutorialAction.Move(target)
        if (allowed && target in controller.moveTargets()) null else refusal
    }
}

/** Stay on the current cell with 「移動しない」. */
internal fun TutorialContext.guidedStay(stage: Int, text: String, refusal: String): TutorialStep? {
    if (!learnersTurn || phase != TurnPhase.MOVE) return null
    return TutorialStep(stage, text, setOf(learner.position), setOf(TutorialControl.SKIP)) { action ->
        if (action == TutorialAction.Skip) null else refusal
    }
}

/**
 * The capture on the learner's cell: start it, then roll when the card needs dice, then resolve.
 * The texts after the start may name the card, because the attempt has turned it over.
 */
internal fun TutorialContext.guidedCapture(
    stage: Int,
    startText: String,
    rollText: String,
    revealedText: String,
    landedText: String = "出目を確かめよう",
): TutorialStep? {
    if (!learnersTurn || phase != TurnPhase.CAPTURE) return null
    val cell = setOf(learner.position)
    val pending = controller.pendingCaptureRoll
    if (pending == null) {
        if (!controller.canCapture()) return null
        val foods = engine.foodsAt(learner.position).indices
        return TutorialStep(stage, startText, cell, setOf(TutorialControl.CAPTURE)) { action ->
            val allowed = action == TutorialAction.Capture ||
                (action is TutorialAction.SelectCaptureTarget && action.index in foods)
            if (allowed) null else "このマスのエサを「捕獲」しよう"
        }
    }
    return when {
        pending.food.escapeMap.isNotEmpty() && pending.roll == null ->
            TutorialStep(stage, rollText, cell, setOf(TutorialControl.ROLL_DICE)) { action ->
                if (action == TutorialAction.RollDice) null else "ダイスを振って判定しよう"
            }
        pending.food.escapeMap.isEmpty() ->
            TutorialStep(stage, revealedText, cell, setOf(TutorialControl.RESOLVE_CAPTURE)) { action ->
                if (action == TutorialAction.ResolveCapture) null else "「捕獲する」で確定しよう"
            }
        else -> TutorialStep(stage, landedText, cell, setOf(TutorialControl.RESOLVE_CAPTURE)) { action ->
            if (action == TutorialAction.ResolveCapture) null else "出目を確かめよう"
        }
    }
}

/** The choice after a capture or robbery. */
internal fun TutorialContext.guidedDecision(
    stage: Int,
    text: String,
    eat: Boolean,
    carry: Boolean,
    refusal: String,
): TutorialStep? {
    if (!learnersTurn || phase != TurnPhase.DECIDE || controller.pendingFoodDecision == null) return null
    val controls = buildSet {
        if (eat) add(TutorialControl.EAT)
        if (carry) add(TutorialControl.CARRY)
    }
    return TutorialStep(stage, text, emptySet(), controls) { action ->
        val allowed = (eat && action == TutorialAction.Eat) || (carry && action == TutorialAction.Carry)
        if (allowed) null else refusal
    }
}

/** A robbery in the opponent's nest: choose a stored card and rob it. */
internal fun TutorialContext.guidedRobbery(stage: Int, text: String): TutorialStep? {
    if (!learnersTurn || phase != TurnPhase.DECIDE || !controller.canRobbery()) return null
    val foods = opponent.storedFoods.indices
    return TutorialStep(stage, text, setOf(learner.position), setOf(TutorialControl.ROB)) { action ->
        val allowed = action == TutorialAction.Rob ||
            (action is TutorialAction.SelectRobberyTarget && action.index in foods)
        if (allowed) null else "エサを選んで「強奪」しよう"
    }
}

/** End the turn with 「手番終了」 while nothing else is guided. */
internal fun TutorialContext.guidedFinish(stage: Int, text: String, refusal: String, phases: Set<TurnPhase>): TutorialStep? {
    if (!learnersTurn || phase !in phases || controller.pendingFoodDecision != null) return null
    return TutorialStep(stage, text, emptySet(), setOf(TutorialControl.FINISH_TURN)) { action ->
        if (action == TutorialAction.FinishTurn) null else refusal
    }
}

/** Whether the last capture of this chapter let the card escape. */
internal fun TutorialContext.lastCaptureEscaped(): Boolean =
    controller.playScreenUiState().captureOutcome?.kind == CaptureOutcomeKind.ESCAPED

/**
 * An opponent that digs [cell] every turn, keeps the turned-over tile as it lies, and then
 * stays where it is, unless [move] names a destination for this moment.
 */
internal fun digAndStay(
    cell: Position,
    move: (TutorialContext) -> Position? = { null },
): (TutorialContext) -> CpuAction? = { context ->
    when (context.phase) {
        TurnPhase.DIG -> if (context.controller.pendingDigPlacement == null) CpuAction.RevealDig(cell) else CpuAction.ConfirmDig
        TurnPhase.MOVE -> move(context)?.let { CpuAction.Move(it) } ?: CpuAction.Skip
        TurnPhase.CAPTURE, TurnPhase.DECIDE -> CpuAction.Skip
        TurnPhase.END -> CpuAction.FinishTurn
    }
}
