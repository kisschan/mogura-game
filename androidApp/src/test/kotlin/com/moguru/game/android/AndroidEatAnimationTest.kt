package com.moguru.game.android

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidEatAnimationTest {
    @Test
    fun `successful eat pauses auto advance blocks actions and completes exactly once`() {
        val (controller, viewModel) = fixture()
        repeat(4) { controller.currentPlayer!!.reduceHealth(isOnSurface = false) }
        prepareCapturedFood(controller, FoodType.MOLE_CRICKET)

        viewModel.eat()

        val animation = requireNotNull(viewModel.uiState.value.eatAnimation)
        assertEquals(3, animation.requestedRecovery)
        assertEquals(3, animation.actualRecovery)
        assertEquals(9, animation.healthBefore)
        assertEquals(12, animation.healthAfter)
        assertEquals(0, eatAnimationGeometry(viewModel.uiState.value, animation).markerIndex)
        assertEquals(TurnPhase.END, controller.engine!!.currentPhase)
        assertTrue(viewModel.uiState.value.visibleActions.isEmpty())
        assertEquals(12, viewModel.uiState.value.hungerMarkers.single { it.playerId == animation.playerId }.health)
        val playerId = controller.currentPlayer!!.id
        val logs = controller.logs

        viewModel.eat()
        viewModel.carry()
        viewModel.capture()
        viewModel.skip()
        viewModel.finishTurn()
        viewModel.onCellClicked(SOURCE)
        viewModel.finishDiceRoulette()
        viewModel.finishEatAnimation(animation.id + 1)

        assertEquals(animation, viewModel.uiState.value.eatAnimation)
        assertEquals(playerId, controller.currentPlayer!!.id)
        assertEquals(TurnPhase.END, controller.engine!!.currentPhase)
        assertEquals(logs, controller.logs)

        viewModel.finishEatAnimation(animation.id)

        assertNull(viewModel.uiState.value.eatAnimation)
        val consumption = requireNotNull(viewModel.uiState.value.turnConsumptionAnimation)
        assertEquals(animation.playerId, consumption.playerId)
        assertEquals(animation.healthAfter, consumption.healthBefore)
        assertEquals(1, consumption.actualConsumption)
        assertEquals(11, consumption.healthAfter)
        assertNotEquals(playerId, controller.currentPlayer!!.id)
        assertEquals(TurnPhase.DIG, controller.engine!!.currentPhase)
        val completedPlayerId = controller.currentPlayer!!.id
        val completedLogs = controller.logs
        viewModel.finishEatAnimation(animation.id)
        viewModel.finishTurnConsumptionAnimation(consumption.id + 1)
        viewModel.finishTurn()
        assertEquals(consumption, viewModel.uiState.value.turnConsumptionAnimation)
        assertEquals(completedPlayerId, controller.currentPlayer!!.id)
        assertEquals(completedLogs, controller.logs)

        viewModel.finishTurnConsumptionAnimation(consumption.id)

        assertNull(viewModel.uiState.value.turnConsumptionAnimation)
        assertEquals(completedPlayerId, controller.currentPlayer!!.id)
        val logsAfterCompletion = controller.logs
        viewModel.finishTurnConsumptionAnimation(consumption.id)
        assertEquals(logsAfterCompletion, controller.logs)
    }

    @Test
    fun `full health meal exposes zero recovery and the full label`() {
        val (controller, viewModel) = fixture()
        prepareCapturedFood(controller, FoodType.FROG)

        viewModel.eat()

        val animation = requireNotNull(viewModel.uiState.value.eatAnimation)
        assertEquals(5, animation.requestedRecovery)
        assertEquals(0, animation.actualRecovery)
        assertEquals(13, animation.healthBefore)
        assertEquals(13, animation.healthAfter)
        assertEquals("満腹", recoveryLabel(animation.actualRecovery))
        assertEquals("+3", recoveryLabel(3))
        assertTrue(recoveryAnnouncement(animation).contains("満腹"))
    }

    @Test
    fun `reset clears playback and a stale restored callback cannot finish a later meal`() {
        val (controller, viewModel) = fixture()
        controller.currentPlayer!!.reduceHealth(isOnSurface = false)
        prepareCapturedFood(controller, FoodType.BEETLE_LARVA)
        viewModel.eat()
        val oldEvent = requireNotNull(viewModel.uiState.value.eatAnimation)

        // Re-reading state models recreation with the same ViewModel: the event remains stable.
        assertEquals(oldEvent, viewModel.uiState.value.eatAnimation)
        viewModel.returnToSetup()
        viewModel.finishEatAnimation(oldEvent.id)
        assertFalse(viewModel.uiState.value.isGameStarted)
        assertNull(viewModel.uiState.value.eatAnimation)

        viewModel.startNewGame(2)
        controller.currentPlayer!!.reduceHealth(isOnSurface = false)
        prepareCapturedFood(controller, FoodType.BEETLE_LARVA)
        viewModel.eat()
        val newEvent = requireNotNull(viewModel.uiState.value.eatAnimation)
        assertNotEquals(oldEvent.id, newEvent.id)

        viewModel.finishEatAnimation(oldEvent.id)
        assertEquals(newEvent, viewModel.uiState.value.eatAnimation)
        viewModel.finishEatAnimation(newEvent.id)
        assertNull(viewModel.uiState.value.eatAnimation)
        assertTrue(viewModel.uiState.value.turnConsumptionAnimation != null)
    }

    private fun fixture(): Pair<MoguraGameController, AndroidGameViewModel> {
        val controller = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        return controller to viewModel
    }

    private fun prepareCapturedFood(controller: MoguraGameController, type: FoodType) {
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        player.moveTo(SOURCE)
        while (engine.foodsAt(SOURCE).isNotEmpty()) engine.removeFoodAt(SOURCE, 0)
        engine.placeFoodAt(SOURCE, FoodCard.createDummyCards(type).first())
        engine.advancePhase()
        engine.advancePhase()
        assertTrue(controller.captureCurrentPositionImmediately().success)
        assertEquals(TurnPhase.DECIDE, engine.currentPhase)
    }

    private companion object {
        val SOURCE = Position(2, 2)
    }
}
