package com.moguru.game.android

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.presenter.CaptureOutcomeKind
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidCaptureAnimationTest {
    @Test
    fun `selected stacked food remains on the display until capture finishes and actions cannot bypass it`() {
        val (controller, viewModel) = fixture()
        prepareCapture(controller, FoodType.BEETLE_LARVA)
        controller.engine!!.placeFoodAt(SOURCE, FoodCard.createDummyCards(FoodType.BEETLE_LARVA).first())
        viewModel.selectCaptureTarget(1)
        viewModel.capture()
        viewModel.finishDiceRoulette()

        val state = viewModel.uiState.value
        val animation = requireNotNull(state.captureAnimation)
        assertEquals(CaptureOutcomeKind.CAPTURED, animation.event.kind)
        assertEquals(1, animation.event.foodIndex)
        assertEquals(2, state.boardState.cells.single { it.position == SOURCE }.foods.size)
        assertEquals(1, controller.engine!!.foodsAt(SOURCE).size)
        assertFalse(isAnimatedCaptureFood(animation, SOURCE, 0))
        assertTrue(isAnimatedCaptureFood(animation, SOURCE, 1))
        assertFalse(isAnimatedCaptureFood(animation, Position(2, 1), 1))
        assertTrue(state.visibleActions.isEmpty())
        val pendingFood = controller.pendingFoodDecision
        val health = controller.currentPlayer!!.health
        val logs = controller.logs

        viewModel.eat()
        viewModel.carry()
        viewModel.capture()
        viewModel.skip()
        viewModel.finishTurn()
        viewModel.onCellClicked(SOURCE)
        viewModel.finishDiceRoulette()
        viewModel.finishCaptureAnimation(animation.event.id + 1)

        assertEquals(pendingFood, controller.pendingFoodDecision)
        assertEquals(logs, controller.logs)
        assertEquals(health, controller.currentPlayer!!.health)
        assertEquals(animation, viewModel.uiState.value.captureAnimation)
        viewModel.finishCaptureAnimation(animation.event.id)
        assertNull(viewModel.uiState.value.captureAnimation)
        assertEquals(1, viewModel.uiState.value.boardState.cells.single { it.position == SOURCE }.foods.size)
        assertEquals(listOf(AndroidVisibleAction.EAT, AndroidVisibleAction.CARRY), viewModel.uiState.value.visibleActions)
        viewModel.finishCaptureAnimation(animation.event.id)
        assertEquals(TurnPhase.DECIDE, controller.engine!!.currentPhase)
    }

    @Test
    fun `escape lands on the appended card and blocks turn completion until playback ends`() {
        val (controller, viewModel) = fixture(1)
        prepareCapture(controller, FoodType.EARTHWORM)
        val destination = Position(2, 1)
        val engine = controller.engine!!
        while (engine.foodsAt(destination).isNotEmpty()) engine.removeFoodAt(destination, 0)
        repeat(2) { engine.placeFoodAt(destination, FoodCard.createDummyCards(FoodType.EARTHWORM).first()) }
        viewModel.capture()
        viewModel.stopDiceRoulette()
        viewModel.finishDiceRoulette()

        val animation = requireNotNull(viewModel.uiState.value.captureAnimation)
        assertEquals(CaptureOutcomeKind.ESCAPED, animation.event.kind)
        assertEquals(destination, animation.event.destination)
        assertEquals(TurnPhase.END, engine.currentPhase)
        val playerId = controller.currentPlayer!!.id
        viewModel.finishTurn()
        assertEquals(playerId, controller.currentPlayer!!.id)
        assertTrue(viewModel.uiState.value.visibleActions.isEmpty())
        assertEquals(1, viewModel.uiState.value.boardState.cells.single { it.position == SOURCE }.foods.size)
        assertEquals(2, viewModel.uiState.value.boardState.cells.single { it.position == destination }.foods.size)
        assertEquals(3, engine.foodsAt(destination).size)
        val geometry = captureAnimationGeometry(animation)
        assertEquals(foodRect(destination, 0.76f, 2, 3), geometry.foodEnd)
        val landing = interpolateCaptureRect(geometry.foodStart, geometry.foodEnd, 1f)
        assertEquals(geometry.foodEnd.left, landing.left, 0.00001f)
        assertEquals(geometry.foodEnd.top, landing.top, 0.00001f)

        viewModel.finishCaptureAnimation(animation.event.id)
        assertEquals(TurnPhase.END, engine.currentPhase)
        assertEquals(playerId, controller.currentPlayer!!.id)
        assertTrue(viewModel.uiState.value.boardState.cells.single { it.position == SOURCE }.foods.isEmpty())
        assertEquals(3, viewModel.uiState.value.boardState.cells.single { it.position == destination }.foods.size)
        viewModel.finishCaptureAnimation(animation.event.id)
        assertEquals(TurnPhase.END, engine.currentPhase)
        viewModel.finishTurn()
        assertNotEquals(playerId, controller.currentPlayer!!.id)
    }

    @Test
    fun `reset cancels playback and stale callbacks cannot complete a later game capture`() {
        val (controller, viewModel) = fixture()
        prepareCapture(controller, FoodType.BEETLE_LARVA)
        viewModel.capture()
        viewModel.finishDiceRoulette()
        val oldId = requireNotNull(viewModel.uiState.value.captureAnimation).event.id

        viewModel.returnToSetup()
        viewModel.finishCaptureAnimation(oldId)
        assertFalse(viewModel.uiState.value.isGameStarted)
        assertNull(viewModel.uiState.value.captureAnimation)
        viewModel.startNewGame(2)
        prepareCapture(controller, FoodType.BEETLE_LARVA)
        viewModel.capture()
        viewModel.finishDiceRoulette()
        val newId = requireNotNull(viewModel.uiState.value.captureAnimation).event.id
        assertNotEquals(oldId, newId)
        viewModel.finishCaptureAnimation(oldId)
        assertEquals(newId, viewModel.uiState.value.captureAnimation?.event?.id)
        viewModel.finishCaptureAnimation(newId)
        viewModel.finishDiceRoulette()
        assertNull(viewModel.uiState.value.captureAnimation)
        assertEquals(TurnPhase.DECIDE, controller.engine!!.currentPhase)
    }

    private fun fixture(roll: Int = 6): Pair<MoguraGameController, AndroidGameViewModel> {
        val controller = MoguraGameController(FixedDiceRoller(listOf(roll)), FixedShuffler())
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        return controller to viewModel
    }

    private fun prepareCapture(controller: MoguraGameController, type: FoodType) {
        val engine = controller.engine!!
        controller.currentPlayer!!.moveTo(SOURCE)
        while (engine.foodsAt(SOURCE).isNotEmpty()) engine.removeFoodAt(SOURCE, 0)
        engine.placeFoodAt(SOURCE, FoodCard.createDummyCards(type).first())
        engine.advancePhase()
        engine.advancePhase()
    }

    private companion object {
        val SOURCE = Position(2, 2)
    }
}
