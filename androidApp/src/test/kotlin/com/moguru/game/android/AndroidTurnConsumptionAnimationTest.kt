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

class AndroidTurnConsumptionAnimationTest {
    @Test
    fun `direct finish holds one event blocks actions and ignores stale completion ids`() {
        val (controller, viewModel) = fixture()
        val playerId = controller.currentPlayer!!.id
        val displayBefore = viewModel.uiState.value
        val layoutIndexBefore = displayBefore.hungerMarkers
            .single { it.playerId == playerId }
            .layoutIndex
        repeat(3) { controller.engine!!.advancePhase() }

        viewModel.finishTurn()

        val event = requireNotNull(viewModel.uiState.value.turnConsumptionAnimation)
        assertEquals(playerId, event.playerId)
        assertEquals(1, event.requestedConsumption)
        assertEquals(1, event.actualConsumption)
        assertEquals(13, event.healthBefore)
        assertEquals(12, event.healthAfter)
        assertFalse(event.isOnSurface)
        val animatingState = viewModel.uiState.value
        assertEquals(layoutIndexBefore, animatingState.hungerMarkers.single { it.playerId == playerId }.layoutIndex)
        assertEquals(displayBefore.playState, animatingState.playState)
        assertEquals(displayBefore.boardState, animatingState.boardState)
        assertEquals(displayBefore.hungerMarkers, animatingState.hungerMarkers)
        assertEquals(displayBefore.logs, animatingState.logs)
        assertEquals(displayBefore.lastMessage, animatingState.lastMessage)
        assertNull(animatingState.gameResult)
        assertEquals(playerId, animatingState.playState.currentPlayer.playerId)
        assertTrue(animatingState.visibleActions.isEmpty())
        assertEquals("-1", turnConsumptionLabel(event))
        val nextPlayerId = controller.currentPlayer!!.id
        val logs = controller.logs

        viewModel.finishTurn()
        viewModel.skip()
        viewModel.onCellClicked(Position(1, 1))
        viewModel.finishTurnConsumptionAnimation(event.id + 1)

        assertEquals(event, viewModel.uiState.value.turnConsumptionAnimation)
        assertEquals(nextPlayerId, controller.currentPlayer!!.id)
        assertEquals(logs, controller.logs)

        viewModel.finishTurnConsumptionAnimation(event.id)

        assertNull(viewModel.uiState.value.turnConsumptionAnimation)
        assertEquals(nextPlayerId, controller.currentPlayer!!.id)
        assertEquals(nextPlayerId, viewModel.uiState.value.playState.currentPlayer.playerId)
        assertEquals(event.healthAfter, viewModel.uiState.value.hungerMarkers.single { it.playerId == playerId }.health)
        val logsAfterCompletion = controller.logs
        viewModel.finishTurnConsumptionAnimation(event.id)
        assertEquals(logsAfterCompletion, controller.logs)
    }

    @Test
    fun `surface finish uses actual two point loss with compact visible label`() {
        val (controller, viewModel) = fixture()
        controller.currentPlayer!!.moveTo(Position(2, 0))
        repeat(3) { controller.engine!!.advancePhase() }

        viewModel.finishTurn()

        val state = viewModel.uiState.value
        val event = requireNotNull(state.turnConsumptionAnimation)
        assertTrue(event.isOnSurface)
        assertEquals(2, event.requestedConsumption)
        assertEquals(2, event.actualConsumption)
        assertEquals(13, event.healthBefore)
        assertEquals(11, event.healthAfter)
        assertEquals("-2", turnConsumptionLabel(event))
        assertEquals("地上で手番を終え、体力を2消耗しました", turnConsumptionAnnouncement(event))
        val geometry = turnConsumptionAnimationGeometry(state, event)
        assertEquals(
            state.hungerMarkers.single { it.playerId == event.playerId }.layoutIndex,
            geometry.layoutIndex,
        )
        assertEquals(hungerMarkerRect(event.healthBefore, geometry.layoutIndex), geometry.markerStart)
        assertEquals(hungerMarkerRect(event.healthAfter, geometry.layoutIndex), geometry.markerEnd)
        assertNotEquals(geometry.markerStart, geometry.markerEnd)
    }

    @Test
    fun `eat recovery completes before its automatic turn consumption starts`() {
        val (controller, viewModel) = fixture()
        repeat(4) { controller.currentPlayer!!.reduceHealth(isOnSurface = false) }
        prepareCapturedFood(controller, FoodType.MOLE_CRICKET)

        viewModel.eat()

        val greenState = viewModel.uiState.value
        val eat = requireNotNull(greenState.eatAnimation)
        assertNull(greenState.turnConsumptionAnimation)
        viewModel.finishEatAnimation(eat.id)

        val redState = viewModel.uiState.value
        val consumption = requireNotNull(redState.turnConsumptionAnimation)
        assertNull(redState.eatAnimation)
        assertEquals(eat.healthAfter, consumption.healthBefore)
        assertEquals(11, consumption.healthAfter)
        assertEquals(greenState.playState, redState.playState)
        assertEquals(greenState.boardState, redState.boardState)
        assertEquals(greenState.hungerMarkers, redState.hungerMarkers)
        assertEquals(greenState.logs, redState.logs)
        assertEquals(greenState.lastMessage, redState.lastMessage)
        assertEquals(
            consumption.healthBefore,
            redState.hungerMarkers.single { it.playerId == consumption.playerId }.health,
        )
        assertEquals(consumption.playerId, redState.playState.currentPlayer.playerId)
        val nextPlayerId = controller.currentPlayer!!.id
        viewModel.finishEatAnimation(eat.id)
        assertEquals(consumption, viewModel.uiState.value.turnConsumptionAnimation)

        viewModel.finishTurnConsumptionAnimation(consumption.id)

        val completedState = viewModel.uiState.value
        assertNull(completedState.turnConsumptionAnimation)
        assertEquals(nextPlayerId, completedState.playState.currentPlayer.playerId)
        assertEquals(
            consumption.healthAfter,
            completedState.hungerMarkers.single { it.playerId == consumption.playerId }.health,
        )
    }

    @Test
    fun `reset clears a retained event and its stale callback cannot affect a new game`() {
        val (controller, viewModel) = fixture()
        repeat(3) { controller.engine!!.advancePhase() }
        viewModel.finishTurn()
        val oldEvent = requireNotNull(viewModel.uiState.value.turnConsumptionAnimation)

        assertEquals(oldEvent, viewModel.uiState.value.turnConsumptionAnimation)
        viewModel.returnToSetup()
        viewModel.finishTurnConsumptionAnimation(oldEvent.id)

        assertFalse(viewModel.uiState.value.isGameStarted)
        assertNull(viewModel.uiState.value.turnConsumptionAnimation)

        viewModel.startNewGame(2)
        repeat(3) { controller.engine!!.advancePhase() }
        viewModel.finishTurn()
        val newEvent = requireNotNull(viewModel.uiState.value.turnConsumptionAnimation)
        val newDisplay = viewModel.uiState.value
        assertNotEquals(oldEvent.id, newEvent.id)

        viewModel.finishTurnConsumptionAnimation(oldEvent.id)

        assertEquals(newEvent, viewModel.uiState.value.turnConsumptionAnimation)
        assertEquals(newDisplay, viewModel.uiState.value)
        assertEquals(TurnPhase.DIG, controller.engine!!.currentPhase)
    }

    @Test
    fun `chained auto consumption freezes the next players fresh live state`() {
        val (controller, viewModel) = fixture()
        val engine = controller.engine!!
        engine.boardState.clear()
        repeat(3) { engine.advancePhase() }
        viewModel.finishTurn()
        val first = requireNotNull(viewModel.uiState.value.turnConsumptionAnimation)

        viewModel.finishTurnConsumptionAnimation(first.id)

        val secondState = viewModel.uiState.value
        val second = requireNotNull(secondState.turnConsumptionAnimation)
        assertNotEquals(first.id, second.id)
        assertEquals(engine.players[1].id, second.playerId)
        assertEquals(engine.players[1].id, secondState.playState.currentPlayer.playerId)
        assertEquals(engine.players[0].id, controller.currentPlayer!!.id)
        assertEquals(TurnPhase.DIG, secondState.playState.actionAvailability.activePhase)
        assertEquals(
            second.healthBefore,
            secondState.hungerMarkers.single { it.playerId == second.playerId }.health,
        )
        assertEquals(
            engine.players.indexOfFirst { it.id == second.playerId },
            secondState.hungerMarkers.single { it.playerId == second.playerId }.layoutIndex,
        )
        assertFalse(secondState.logs.any { it.contains("${engine.players[1].name} の番を終了しました") })

        viewModel.finishTurnConsumptionAnimation(first.id)

        assertEquals(secondState, viewModel.uiState.value)

        viewModel.returnToSetup()
        assertNull(viewModel.uiState.value.turnConsumptionAnimation)
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
