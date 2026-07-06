package com.moguru.game.android

import com.moguru.game.engine.GameEngine
import com.moguru.game.engine.GameState
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Direction
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Player
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.presenter.FoodDecisionSource
import com.moguru.game.presenter.CaptureOutcomeKind
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
        assertNull(state.gameResult)
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
    fun `setup selections choose moles nests and start player`() {
        val controller = testController()
        val viewModel = AndroidGameViewModel(controller)

        viewModel.selectPlayerCount(3)
        viewModel.selectPlayerMole(0, 3)
        viewModel.selectPlayerNest(0, Position(5, 4))
        viewModel.selectStartPlayer(2)
        viewModel.startSelectedGame()

        val engine = controller.engine!!
        assertEquals(3, viewModel.uiState.value.selectedPlayerCount)
        assertEquals(listOf(3, 1, 2), engine.players.map { it.id })
        assertEquals(listOf(Position(5, 4), Position(5, 1), Position(0, 4)), engine.players.map { it.nestPosition })
        assertEquals(2, engine.currentPlayerIndex)
        assertEquals("モグミ", controller.currentPlayer?.name)
    }

    @Test
    fun `setup swaps duplicate mole and nest selections`() {
        val viewModel = testViewModel()

        viewModel.selectPlayerMole(1, 0)
        viewModel.selectPlayerNest(1, Position(0, 1))

        val state = viewModel.uiState.value
        assertEquals(listOf(1, 0), state.setupPlayers.map { it.playerId })
        assertEquals(listOf(Position(5, 1), Position(0, 1)), state.setupPlayers.map { it.nestPosition })
        assertTrue(state.canStartGame)
    }

    @Test
    fun `four player setup can swap occupied mole and nest choices`() {
        val viewModel = testViewModel()

        viewModel.selectPlayerCount(4)
        viewModel.selectPlayerMole(0, 3)
        viewModel.selectPlayerNest(0, Position(5, 4))

        val state = viewModel.uiState.value
        assertEquals(listOf(3, 1, 2, 0), state.setupPlayers.map { it.playerId })
        assertEquals(
            listOf(Position(5, 4), Position(5, 1), Position(0, 4), Position(0, 1)),
            state.setupPlayers.map { it.nestPosition },
        )
        assertTrue(state.canStartGame)
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
    fun `pending dig placement can be confirmed without tapping the board again`() {
        val viewModel = testViewModel()
        val target = Position(1, 1)
        viewModel.startNewGame(2)

        viewModel.onCellClicked(target)
        viewModel.selectRotation(Rotation.DEG_90)
        viewModel.confirmDigPlacement()

        val state = viewModel.uiState.value
        assertEquals(TurnPhase.MOVE, state.playState.actionAvailability.activePhase)
        assertFalse(state.showDigControls)
        assertEquals("タイルを置きました。", state.lastMessage)
    }

    @Test
    fun `candidate outside the active board choices gives feedback without advancing`() {
        val viewModel = testViewModel()
        viewModel.startNewGame(2)

        viewModel.onCellClicked(Position(5, 4))

        val state = viewModel.uiState.value
        assertEquals(TurnPhase.DIG, state.playState.actionAvailability.activePhase)
        assertEquals(0, state.playState.currentPlayer.playerId)
        assertEquals("現在のプレイヤーに隣接する掘れる穴タイルを選んでください。", state.lastMessage)
    }

    @Test
    fun `robbery action appears on next own turn in opponent nest`() {
        val controller = testController()
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val engine = controller.engine!!
        val thief = engine.players[0]
        val victim = engine.players[1]
        victim.carryFood(FoodCard(FoodType.EARTHWORM, emptyMap(), isFaceDown = false))
        victim.storeFood()
        victim.moveTo(Position(1, 1))
        connectLeftNestToRightNest(engine)

        engine.advancePhase()
        viewModel.onCellClicked(victim.nestPosition)
        engine.advancePhase()
        viewModel.finishTurn()
        engine.advancePhase()
        viewModel.skip()

        var state = viewModel.uiState.value
        assertEquals(TurnPhase.DECIDE, state.playState.actionAvailability.activePhase)
        assertEquals(listOf(AndroidVisibleAction.ROB), state.visibleActions)
        assertEquals(listOf(FoodType.EARTHWORM), state.playState.robberyTargets.map { it.type })

        viewModel.rob()

        state = viewModel.uiState.value
        assertEquals(FoodDecisionSource.ROBBERY, state.playState.pendingDecisionSource)
        assertEquals(
            listOf(AndroidVisibleAction.EAT, AndroidVisibleAction.CARRY),
            state.visibleActions,
        )

        viewModel.carry()

        assertTrue(thief.isCarrying)
        assertEquals(0, thief.score)
        assertTrue(victim.storedFoods.isEmpty())
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
    fun `board state exposes current cell and connection edges without visible player names`() {
        val viewModel = testViewModel()
        val target = Position(1, 1)
        viewModel.startNewGame(2)

        viewModel.onCellClicked(target)
        viewModel.selectRotation(Rotation.DEG_90)
        viewModel.confirmDigPlacement()

        val boardCells = viewModel.uiState.value.boardState.cells
        val currentCell = boardCells.single { it.isCurrentPlayerCell }
        assertTrue(currentCell.players.single().accessibilityLabel.contains("モグオ"))
        assertTrue(currentCell.players.single().accessibilityLabel.contains("現在の手番"))

        val targetCell = boardCells.single { it.position == target }
        assertEquals(setOf(Direction.LEFT, Direction.RIGHT), targetCell.tile?.openSides)
        assertTrue(targetCell.connectionEdges.any { it.direction == Direction.LEFT })
        assertTrue(targetCell.connectionEdges.any { it.direction == Direction.RIGHT })
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
        placeFoodForCapture(engine, player, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
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
        assertEquals(CaptureOutcomeKind.CAPTURED, state.captureOutcome?.kind)
        assertTrue(
            resultBannerText(state.captureOutcome)
                ?.contains("ダイス: 6") == true,
        )
        assertEquals(
            listOf(AndroidVisibleAction.EAT, AndroidVisibleAction.CARRY),
            viewModel.uiState.value.visibleActions,
        )

        viewModel.eat()

        assertNull(viewModel.uiState.value.playState.captureOutcome)
    }

    @Test
    fun `escaped capture result remains visible after auto advance logs`() {
        val controller = MoguraGameController(
            diceRoller = FixedDiceRoller(listOf(1)),
            shuffler = FixedShuffler(),
        )
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        placeFoodForCapture(engine, player, FoodCard.createDummyCards(FoodType.CENTIPEDE).first())
        engine.advancePhase()
        engine.advancePhase()

        viewModel.capture()
        viewModel.stopDiceRoulette()
        viewModel.finishDiceRoulette()

        val state = viewModel.uiState.value.playState
        val outcome = state.captureOutcome
        val text = resultBannerText(outcome)
        assertEquals(1, state.lastDiceRoll)
        assertEquals(CaptureOutcomeKind.ESCAPED, outcome?.kind)
        assertEquals(1, outcome?.diceRoll)
        assertTrue(text?.contains("ダイス: 1") == true)
        assertTrue(text?.contains("逃げました") == true)

        viewModel.finishTurn()

        assertNull(viewModel.uiState.value.playState.captureOutcome)
    }

    @Test
    fun `capture without escape dice reveals the card then resolves without spinning`() {
        val controller = testController()
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        placeFoodForCapture(engine, player, FoodCard(FoodType.BEETLE_LARVA, emptyMap()))
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
        assertTrue(
            resultBannerText(state.captureOutcome)
                ?.startsWith("逃走なし") == true,
        )
        assertEquals(
            listOf(AndroidVisibleAction.EAT, AndroidVisibleAction.CARRY),
            viewModel.uiState.value.visibleActions,
        )

        viewModel.carry()

        assertNull(viewModel.uiState.value.playState.captureOutcome)
    }

    @Test
    fun `capture reveal guidance does not depend on tap wording`() {
        val controller = testController()
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        placeFoodForCapture(engine, player, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
        engine.advancePhase()
        engine.advancePhase()

        viewModel.capture()

        assertFalse(
            viewModel.uiState.value.logs.any { it.contains("タップ") || it.contains("クリック") },
            "shared capture guidance should stay neutral across Android and Desktop",
        )
    }

    @Test
    fun `capture reveal guidance without escape dice does not depend on tap wording`() {
        val controller = testController()
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        placeFoodForCapture(engine, player, FoodCard(FoodType.BEETLE_LARVA, emptyMap()))
        engine.advancePhase()
        engine.advancePhase()

        viewModel.capture()

        assertFalse(
            viewModel.uiState.value.logs.any { it.contains("タップ") || it.contains("クリック") },
            "no-escape capture guidance should stay neutral across Android and Desktop",
        )
    }

    @Test
    fun `stacked capture target selection is reflected in roulette state`() {
        val controller = testController()
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val target = Position(1, 1)
        player.moveTo(target)
        engine.placeFoodAt(target, FoodCard(FoodType.BEETLE_LARVA, emptyMap()))
        engine.placeFoodAt(target, FoodCard(FoodType.MOLE_CRICKET, emptyMap()))
        engine.advancePhase()
        engine.advancePhase()

        viewModel.selectCaptureTarget(1)

        var state = viewModel.uiState.value.playState
        assertEquals(listOf(FoodType.BEETLE_LARVA, FoodType.MOLE_CRICKET), state.captureTargets.map { it.type })
        assertTrue(state.captureTargets[1].selected)

        viewModel.capture()

        state = viewModel.uiState.value.playState
        assertTrue(state.diceRouletteActive)
        assertEquals(FoodType.MOLE_CRICKET, state.diceRouletteFood)

        viewModel.finishDiceRoulette()

        assertEquals(FoodType.MOLE_CRICKET, controller.pendingFoodDecision?.type)
        assertEquals(listOf(FoodType.BEETLE_LARVA), engine.foodsAt(target).map { it.type })
    }

    @Test
    fun `repeated roulette taps are ignored`() {
        val controller = testController()
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        placeFoodForCapture(engine, player, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
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

    @Test
    fun `score win exposes winner and every player result`() {
        val controller = testController()
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val engine = controller.engine!!
        val winner = engine.players[0]

        winner.carryFood(FoodCard(FoodType.FROG, emptyMap(), isFaceDown = false))
        winner.storeFood()
        repeat(3) { engine.advancePhase() }

        viewModel.finishTurn()

        val state = viewModel.uiState.value
        val result = state.gameResult!!
        assertEquals(GameState.FINISHED, engine.gameState)
        assertEquals("ゲーム終了です。", state.lastMessage)
        assertTrue(state.showGameResultOverlay)
        assertEquals(winner.id, result.winnerPlayerId)
        assertEquals("モグオ", result.winnerName)
        assertEquals(listOf(0, 1), result.players.map { it.playerId })
        assertEquals(4, result.players.single { it.playerId == 0 }.score)
        assertEquals(Player.MAX_HEALTH - 1, result.players.single { it.playerId == 0 }.health)
        assertTrue(result.players.single { it.playerId == 0 }.isWinner)
        assertFalse(result.players.single { it.playerId == 1 }.isWinner)
        assertTrue(state.visibleActions.isEmpty())
        assertFalse(state.showDigControls)

        viewModel.dismissGameResultOverlay()

        val dismissedState = viewModel.uiState.value
        assertEquals(result, dismissedState.gameResult)
        assertFalse(dismissedState.showGameResultOverlay)
    }

    @Test
    fun `elimination win exposes eliminated player result`() {
        val controller = testController()
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val engine = controller.engine!!
        val eliminated = engine.players[1]
        repeat(Player.MAX_HEALTH) { eliminated.reduceHealth(isOnSurface = false) }
        repeat(3) { engine.advancePhase() }

        viewModel.finishTurn()

        val result = viewModel.uiState.value.gameResult!!
        val winnerResult = result.players.single { it.playerId == 0 }
        val eliminatedResult = result.players.single { it.playerId == 1 }
        assertEquals(GameState.FINISHED, engine.gameState)
        assertEquals("モグオ", result.winnerName)
        assertTrue(winnerResult.isWinner)
        assertEquals(0, eliminatedResult.health)
        assertTrue(eliminatedResult.isEliminated)
        assertFalse(eliminatedResult.isWinner)
    }

    private fun testViewModel(): AndroidGameViewModel =
        AndroidGameViewModel(testController())

    private fun placeFoodForCapture(
        engine: GameEngine,
        player: Player,
        food: FoodCard,
    ) {
        player.moveTo(Position(1, 1))
        engine.placeFoodAt(player.position, food)
    }

    private fun connectLeftNestToRightNest(engine: GameEngine) {
        for (col in 1..4) {
            engine.boardState.placeTile(
                Position(col, 1),
                HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip(),
            )
        }
    }

    private fun testController(): MoguraGameController =
        MoguraGameController(
            diceRoller = FixedDiceRoller(listOf(6)),
            shuffler = FixedShuffler(),
        )
}
