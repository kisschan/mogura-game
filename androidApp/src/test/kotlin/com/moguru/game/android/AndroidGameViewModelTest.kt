package com.moguru.game.android

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
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
        // 巣と接続する向き(左右開き)に回転し、移動フェーズで移動先を残す。
        viewModel.selectRotation(Rotation.DEG_90)
        viewModel.onCellClicked(target)

        assertEquals(TurnPhase.MOVE, viewModel.uiState.value.playState.actionAvailability.activePhase)
        assertEquals(
            listOf(AndroidVisibleAction.SKIP, AndroidVisibleAction.END_TURN),
            viewModel.uiState.value.visibleActions,
        )
    }

    @Test
    fun `turn auto ends when no move or capture choice remains after digging`() {
        val viewModel = testViewModel()
        val target = Position(1, 1)
        viewModel.startNewGame(2)

        // 回転なし(上下開き)で掘ると巣と接続せず移動先が無いため、捕獲も無く自動でターンが終わる。
        viewModel.onCellClicked(target)
        viewModel.onCellClicked(target)

        val state = viewModel.uiState.value
        assertEquals(TurnPhase.DIG, state.playState.actionAvailability.activePhase)
        assertEquals(1, state.playState.currentPlayer.playerId)
    }

    @Test
    fun `failed board tap at end phase does not auto advance the turn`() {
        val controller = testController()
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val engine = controller.engine!!
        engine.advancePhase()
        engine.advancePhase()
        engine.advancePhase()
        assertEquals(TurnPhase.END, engine.currentPhase)
        assertEquals(0, engine.currentPlayerIndex)

        viewModel.onCellClicked(Position(0, 1))

        val state = viewModel.uiState.value
        assertEquals(TurnPhase.END, state.playState.actionAvailability.activePhase)
        assertEquals(0, state.playState.currentPlayer.playerId)
        assertEquals(0, engine.currentPlayerIndex)
    }

    @Test
    fun `current hunger marker is exposed last when player health overlaps`() {
        val viewModel = testViewModel()
        viewModel.startNewGame(4)

        val markers = viewModel.uiState.value.hungerMarkers

        assertEquals(4, markers.size)
        assertEquals(0, markers.last().playerId)
        assertTrue(markers.last().isCurrent)
    }

    @Test
    fun `beetle larva food resource uses beetle larva asset`() {
        assertEquals(R.drawable.food_beetle_larva, foodRes(FoodType.BEETLE_LARVA))
    }

    @Test
    fun `capture with escape dice runs the roulette flow`() {
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

        var state = viewModel.uiState.value.playState
        assertTrue(state.diceRouletteActive, "捕獲でルーレットが始まるべき")
        assertNull(state.diceRouletteResult)
        assertEquals(FoodType.EARTHWORM, state.diceRouletteFood, "カード公開用にエサ種別を見せるべき")
        assertEquals(listOf(1, 2), state.diceRouletteEscapeRolls, "逃走目を見せるべき")
        assertEquals(TurnPhase.CAPTURE, state.actionAvailability.activePhase)
        assertTrue(viewModel.uiState.value.visibleActions.isEmpty(), "ルーレット中は操作ボタンを隠す")

        viewModel.stopDiceRoulette()

        state = viewModel.uiState.value.playState
        assertTrue(state.diceRouletteActive, "着地演出中はオーバーレイ表示が続く")
        assertEquals(6, state.diceRouletteResult)
        assertEquals(TurnPhase.CAPTURE, state.actionAvailability.activePhase)

        viewModel.finishDiceRoulette()

        state = viewModel.uiState.value.playState
        assertFalse(state.diceRouletteActive)
        assertEquals(6, state.lastDiceRoll)
        assertEquals(TurnPhase.DECIDE, state.actionAvailability.activePhase)
        assertEquals(
            listOf(AndroidVisibleAction.EAT, AndroidVisibleAction.CARRY),
            viewModel.uiState.value.visibleActions,
        )
    }

    @Test
    fun `capture without escape dice reveals the card then resolves without spinning`() {
        val controller = testController()
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.placeFoodAt(
            player.position,
            FoodCard(FoodType.BEETLE_LARVA, emptyMap()),
        )
        engine.advancePhase()
        engine.advancePhase()

        viewModel.capture()

        var state = viewModel.uiState.value.playState
        assertTrue(state.diceRouletteActive, "逃走なしエサでもカード公開を見せるべき")
        assertEquals(FoodType.BEETLE_LARVA, state.diceRouletteFood)
        assertTrue(state.diceRouletteEscapeRolls.isEmpty(), "逃走目なしとして公開するべき")
        assertEquals(TurnPhase.CAPTURE, state.actionAvailability.activePhase)

        viewModel.finishDiceRoulette()

        state = viewModel.uiState.value.playState
        assertFalse(state.diceRouletteActive)
        assertEquals(TurnPhase.DECIDE, state.actionAvailability.activePhase)
        assertEquals(
            listOf(AndroidVisibleAction.EAT, AndroidVisibleAction.CARRY),
            viewModel.uiState.value.visibleActions,
        )
    }

    @Test
    fun `repeated roulette taps are ignored`() {
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
        viewModel.stopDiceRoulette()

        viewModel.stopDiceRoulette()

        val state = viewModel.uiState.value.playState
        assertTrue(state.diceRouletteActive)
        assertEquals(6, state.diceRouletteResult)
        assertEquals(TurnPhase.CAPTURE, state.actionAvailability.activePhase)

        viewModel.finishDiceRoulette()
        viewModel.finishDiceRoulette()

        assertEquals(TurnPhase.DECIDE, viewModel.uiState.value.playState.actionAvailability.activePhase)
    }

    private fun testViewModel(): AndroidGameViewModel =
        AndroidGameViewModel(testController())

    private fun testController(): MoguraGameController =
        MoguraGameController(
            diceRoller = FixedDiceRoller(listOf(6)),
            shuffler = FixedShuffler(),
        )
}
