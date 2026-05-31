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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidGameViewModelTest {
    @Test
    fun `starts on setup screen before a game is created`() {
        val viewModel = testViewModel()

        val state = viewModel.uiState.value

        assertFalse(state.isGameStarted)
        assertEquals(2, state.selectedPlayerCount)
        assertNull(state.playState.actionAvailability.activePhase)
        assertTrue(state.boardState.cells.isEmpty())
        assertTrue(state.visibleActions.isEmpty())
        assertFalse(state.showDigControls)
    }

    @Test
    fun `selecting player count then starting game creates the board`() {
        val viewModel = testViewModel()

        viewModel.selectPlayerCount(4)
        viewModel.startSelectedGame()

        val state = viewModel.uiState.value
        assertTrue(state.isGameStarted)
        assertEquals(4, state.selectedPlayerCount)
        assertEquals(TurnPhase.DIG, state.playState.actionAvailability.activePhase)
        assertTrue(state.boardState.cells.isNotEmpty())
    }

    @Test
    fun `dig controls only appear while a dig placement is pending`() {
        val viewModel = testViewModel()
        viewModel.startNewGame(2)

        assertFalse(viewModel.uiState.value.showDigControls)

        viewModel.onCellClicked(Position(1, 1))

        assertEquals(TurnPhase.DIG, viewModel.uiState.value.playState.actionAvailability.activePhase)
        assertTrue(viewModel.uiState.value.showDigControls)
    }

    @Test
    fun `only currently available actions are exposed`() {
        val viewModel = testViewModel()
        val target = Position(1, 1)
        viewModel.startNewGame(2)

        assertTrue(viewModel.uiState.value.visibleActions.isEmpty())

        viewModel.onCellClicked(target)
        viewModel.onCellClicked(target)

        assertEquals(TurnPhase.MOVE, viewModel.uiState.value.playState.actionAvailability.activePhase)
        assertEquals(
            listOf(AndroidVisibleAction.SKIP, AndroidVisibleAction.END_TURN),
            viewModel.uiState.value.visibleActions,
        )
    }

    @Test
    fun `capture action updates last dice roll`() {
        val controller = testController()
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.placeFoodAt(
            player.position,
            FoodCard.createDummyCards(FoodType.EARTHWORM).first(),
        )
        engine.advancePhase()
        engine.advancePhase()

        viewModel.capture()

        assertEquals(6, viewModel.uiState.value.playState.lastDiceRoll)
        assertEquals(TurnPhase.DECIDE, viewModel.uiState.value.playState.actionAvailability.activePhase)
        assertEquals(
            listOf(AndroidVisibleAction.EAT, AndroidVisibleAction.CARRY),
            viewModel.uiState.value.visibleActions,
        )
    }

    private fun testViewModel(): AndroidGameViewModel =
        AndroidGameViewModel(testController())

    private fun testController(): MoguraGameController =
        MoguraGameController(
            diceRoller = FixedDiceRoller(listOf(6)),
            shuffler = FixedShuffler(),
        )
}
