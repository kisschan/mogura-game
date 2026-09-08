package com.moguru.game.android

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CarriedFoodUiStateTest {
    @Test
    fun `board tokens include carried food for the current player and other players`() {
        val (controller, viewModel) = fixture(playerCount = 3)
        val players = controller.engine!!.players
        players[0].carryFood(food(FoodType.BEETLE_LARVA))
        players[1].carryFood(food(FoodType.EARTHWORM))

        viewModel.selectStartPlayer(0)

        assertEquals(FoodType.BEETLE_LARVA, viewModel.token(players[0].id).carriedFoodType)
        assertEquals(FoodType.EARTHWORM, viewModel.token(players[1].id).carriedFoodType)
        assertNull(viewModel.token(players[2].id).carriedFoodType)
        assertTrue(viewModel.token(players[0].id).isCurrent)
        assertFalse(viewModel.token(players[1].id).isCurrent)
    }

    @Test
    fun `capturing shows no carried tile until carry is chosen and retains it across turn animation`() {
        val (controller, viewModel) = fixture()
        val engine = controller.engine!!
        val playerId = controller.currentPlayer!!.id
        controller.currentPlayer!!.moveTo(CAPTURE_SOURCE)
        while (engine.foodsAt(CAPTURE_SOURCE).isNotEmpty()) engine.removeFoodAt(CAPTURE_SOURCE, 0)
        engine.placeFoodAt(CAPTURE_SOURCE, food(FoodType.BEETLE_LARVA))
        repeat(2) { engine.advancePhase() }
        viewModel.selectStartPlayer(0)

        viewModel.capture()
        viewModel.finishDiceRoulette()
        val capture = requireNotNull(viewModel.uiState.value.captureAnimation)
        assertNull(viewModel.token(playerId).carriedFoodType)
        viewModel.finishCaptureAnimation(capture.event.id)
        assertEquals(TurnPhase.DECIDE, engine.currentPhase)
        assertNull(viewModel.token(playerId).carriedFoodType)

        viewModel.carry()

        val consumption = requireNotNull(viewModel.uiState.value.turnConsumptionAnimation)
        assertEquals(playerId, consumption.playerId)
        assertEquals(FoodType.BEETLE_LARVA, viewModel.token(playerId).carriedFoodType)
        assertTrue(viewModel.token(playerId).isCurrent)
        assertTrue(viewModel.uiState.value.boardState.cells.single { it.position == CAPTURE_SOURCE }.foods.isEmpty())

        viewModel.finishTurnConsumptionAnimation(consumption.id)

        assertEquals(FoodType.BEETLE_LARVA, viewModel.token(playerId).carriedFoodType)
        assertFalse(viewModel.token(playerId).isCurrent)
    }

    @Test
    fun `carried tile follows its player to the destination during the turn transition`() {
        val (controller, viewModel) = fixture()
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val source = Position(1, 1)
        val destination = Position(2, 1)
        player.moveTo(source)
        player.carryFood(food(FoodType.MOLE_CRICKET))
        listOf(source, destination).forEach { position ->
            engine.boardState.placeTile(position, horizontalTile())
            while (engine.foodsAt(position).isNotEmpty()) engine.removeFoodAt(position, 0)
        }
        engine.advancePhase()
        viewModel.selectStartPlayer(0)
        assertTrue(destination in controller.moveTargets())

        viewModel.onCellClicked(destination)

        assertEquals(destination, player.position)
        assertEquals(FoodType.MOLE_CRICKET, viewModel.token(player.id).carriedFoodType)
        assertTrue(viewModel.uiState.value.boardState.cells.single { it.position == source }.players.isEmpty())
        assertEquals(player.id, viewModel.uiState.value.boardState.cells.single { it.position == destination }.players.single().playerId)
        val consumption = requireNotNull(viewModel.uiState.value.turnConsumptionAnimation)
        viewModel.finishTurnConsumptionAnimation(consumption.id)
        assertEquals(FoodType.MOLE_CRICKET, viewModel.token(player.id).carriedFoodType)
    }

    @Test
    fun `returning to the nest removes the carried tile after storing the food`() {
        val (controller, viewModel) = fixture()
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val source = Position(1, 1)
        val carried = food(FoodType.MOLE_CRICKET)
        player.moveTo(source)
        player.carryFood(carried)
        engine.boardState.placeTile(source, horizontalTile())
        engine.advancePhase()
        viewModel.selectStartPlayer(0)
        assertEquals(FoodType.MOLE_CRICKET, viewModel.token(player.id).carriedFoodType)
        assertTrue(player.nestPosition in controller.moveTargets())

        viewModel.onCellClicked(player.nestPosition)

        assertEquals(player.nestPosition, player.position)
        assertNull(player.carriedFood)
        assertEquals(listOf(carried), player.storedFoods)
        assertNull(viewModel.token(player.id).carriedFoodType)
    }

    private fun fixture(playerCount: Int = 2): Pair<MoguraGameController, AndroidGameViewModel> {
        val controller = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(playerCount)
        return controller to viewModel
    }

    private fun AndroidGameViewModel.token(playerId: Int): AndroidPlayerTokenUiState =
        uiState.value.boardState.cells.flatMap { it.players }.single { it.playerId == playerId }

    private fun food(type: FoodType): FoodCard = FoodCard.createDummyCards(type).first()

    private fun horizontalTile(): HoleTile = HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip()

    private companion object {
        val CAPTURE_SOURCE = Position(2, 2)
    }
}
