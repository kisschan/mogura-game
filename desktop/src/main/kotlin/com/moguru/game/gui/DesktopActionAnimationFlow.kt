package com.moguru.game.gui

import com.moguru.game.presenter.GameActionResult
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.presenter.TurnConsumptionAnimationEvent

/** Sequences resolved action effects while keeping controller auto-advance behind each effect. */
internal class DesktopActionAnimationFlow(
    private val controller: MoguraGameController,
    private val boardPanel: BoardPanel,
    private val refresh: () -> Unit,
    private val blockInputs: () -> Unit,
    private val showFailure: (String) -> Unit,
) {
    fun handle(result: GameActionResult) {
        // Swing normally prevents a second action while an effect is playing. Keep the
        // sequencing layer safe as well: a duplicated callback must not auto-advance
        // the controller behind the animation that is already on screen.
        if (boardPanel.isAnimating) return

        if (!result.success) {
            boardPanel.clearPreparedCaptureAnimation()
            boardPanel.clearPreparedTurnConsumptionAnimation()
            showFailure(result.message)
            refresh()
            return
        }

        val eatEvent = result.eatAnimation
        if (eatEvent != null) {
            if (boardPanel.playEatAnimation(eatEvent, ::advanceUntilChoiceOrAnimation)) {
                blockInputs()
                refresh()
                return
            }
            advanceUntilChoiceOrAnimation()
            return
        }

        val consumptionEvent = result.turnConsumptionAnimation
        if (consumptionEvent != null) {
            if (startTurnConsumption(consumptionEvent)) return
            advanceUntilChoiceOrAnimation()
            return
        }

        if (boardPanel.playCaptureAnimation(::advanceUntilChoiceOrAnimation)) {
            blockInputs()
            refresh()
            return
        }

        advanceUntilChoiceOrAnimation()
    }

    private fun startTurnConsumption(event: TurnConsumptionAnimationEvent): Boolean {
        val playing = boardPanel.playTurnConsumptionAnimation(event, ::advanceUntilChoiceOrAnimation)
        if (playing) blockInputs()
        return playing
    }

    private fun advanceUntilChoiceOrAnimation() {
        while (true) {
            boardPanel.prepareTurnConsumptionAnimation()
            val result = controller.autoAdvanceWhileNoChoice()
            val consumptionEvent = result?.turnConsumptionAnimation
            if (consumptionEvent == null) {
                boardPanel.clearPreparedTurnConsumptionAnimation()
                refresh()
                return
            }
            if (startTurnConsumption(consumptionEvent)) return
            // A stale or cancelled event must not strand auto-advance at an END phase.
        }
    }
}
