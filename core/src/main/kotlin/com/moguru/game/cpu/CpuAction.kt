package com.moguru.game.cpu

import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.presenter.DigTileChoice

/** One rules operation. Animation, scheduling and saving belong to the host UI. */
sealed interface CpuAction {
    data class RevealDig(val position: Position) : CpuAction
    data class PrepareDig(val choice: DigTileChoice, val rotation: Rotation) : CpuAction
    data object ConfirmDig : CpuAction
    data class Move(val position: Position) : CpuAction
    data class Capture(val foodIndex: Int) : CpuAction
    data class Rob(val foodIndex: Int) : CpuAction
    data object Eat : CpuAction
    data object Carry : CpuAction
    data object Skip : CpuAction
    data object FinishTurn : CpuAction
}

/** A strategy can inspect only the public view, and must not execute game operations. */
fun interface CpuStrategy {
    fun choose(observation: CpuObservation): CpuAction?
}
