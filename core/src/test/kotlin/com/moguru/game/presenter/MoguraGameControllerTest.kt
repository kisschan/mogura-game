package com.moguru.game.presenter

import com.moguru.game.engine.GameState
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Board
import com.moguru.game.model.Direction
import com.moguru.game.model.EscapeDirection
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MoguraGameControllerTest {

    @Test
    fun `new game creates requested players`() {
        val controller = testController()

        controller.startNewGame(3)

        val engine = controller.engine!!
        assertEquals(3, engine.players.size)
        assertEquals(listOf("モグオ", "モグタ", "モグミ"), engine.players.map { it.name })
        assertEquals(TurnPhase.DIG, engine.currentPhase)
    }

    @Test
    fun `public display labels stay readable Japanese`() {
        val controller = testController()

        val result = controller.startNewGame(2)

        assertEquals("めくったタイル", DigTileChoice.REVEALED.label())
        assertEquals("山札タイル", DigTileChoice.DRAWN.label())
        assertEquals("90°", Rotation.DEG_90.label())
        assertEquals("掘る", TurnPhase.DIG.displayName())
        assertEquals("モグオ", MoguraGameController.defaultConfigs(2).first().name)
        assertEquals("新しいゲームを開始しました。", result.message)
        assertTrue(controller.logs.any { it.contains("モグオ") })
    }

    @Test
    fun `public deck summary exposes only pile counts`() {
        val controller = testController()

        controller.startNewGame(2)

        assertEquals(
            PublicDeckSummary(
                tileDrawCount = 10,
                tileDiscardCount = 0,
                foodDrawCount = 8,
                foodDiscardCount = 0,
            ),
            controller.publicDeckSummary(),
        )
    }

    @Test
    fun `play screen ui state exposes initial public state`() {
        val controller = testController()

        controller.startNewGame(2)

        val state = controller.playScreenUiState()
        assertEquals(0, state.currentPlayer.playerId)
        assertEquals(
            PublicDeckSummary(
                tileDrawCount = 10,
                tileDiscardCount = 0,
                foodDrawCount = 8,
                foodDiscardCount = 0,
            ),
            state.deckSummary,
        )
        assertEquals(DigTileChoice.entries.toList(), state.digCandidates.map { it.choice })
        assertEquals(listOf<TileShape?>(null, null), state.digCandidates.map { it.shape })
        assertTrue(state.digCandidates.none { it.enabled })
        assertEquals(Rotation.DEG_0, state.selectedRotation)
        assertNull(state.lastDiceRoll)
        assertFalse(state.diceRouletteActive)
        assertNull(state.diceRouletteFood)
        assertTrue(state.diceRouletteEscapeRolls.isEmpty())
        assertEquals(TurnPhase.DIG, state.actionAvailability.activePhase)
        assertFalse(state.actionAvailability.canCapture)
        assertFalse(state.actionAvailability.canEat)
        assertFalse(state.actionAvailability.canCarry)
        assertFalse(state.actionAvailability.canSkip)
        assertFalse(state.actionAvailability.canEndTurn)
    }

    @Test
    fun `capture success enters decide phase without auto carrying`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val food = FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true)
        engine.placeFoodAt(player.position, food)
        engine.advancePhase()
        engine.advancePhase()

        val reveal = controller.captureCurrentPosition()

        assertTrue(reveal.success)
        assertNotNull(controller.pendingCaptureRoll, "逃走なしエサでもカード公開を経由するべき")
        assertTrue(controller.playScreenUiState().diceRouletteEscapeRolls.isEmpty())
        assertEquals(FoodType.BEETLE_LARVA, controller.playScreenUiState().diceRouletteFood)
        assertEquals(TurnPhase.CAPTURE, engine.currentPhase)

        val result = controller.resolveCaptureRoll()

        assertTrue(result.success, "逃走なしエサはダイスを振らずに解決できるべき")
        assertNull(engine.foodPositions[player.position])
        assertFalse(player.isCarrying)
        assertEquals(FoodType.BEETLE_LARVA, controller.pendingFoodDecision?.type)
        assertEquals(TurnPhase.DECIDE, engine.currentPhase)
    }

    @Test
    fun `last dice roll clears when a later capture needs no dice`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.placeFoodAt(player.position, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPositionImmediately()
        assertEquals(6, controller.lastDiceRoll, "ダイス捕獲では出目が残るべき")

        controller.eatPendingFood()
        engine.advancePhase()
        val nextPlayer = controller.currentPlayer!!
        engine.placeFoodAt(nextPlayer.position, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true))
        engine.advancePhase()
        engine.advancePhase()

        val result = controller.captureCurrentPositionImmediately()

        assertTrue(result.success)
        assertNull(controller.lastDiceRoll, "ダイスを使わない捕獲では古い出目をクリアするべき")
        assertNull(controller.playScreenUiState().lastDiceRoll)
    }

    @Test
    fun `capture immediately resolves the whole flow for plain uis`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.placeFoodAt(player.position, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
        engine.advancePhase()
        engine.advancePhase()

        val result = controller.captureCurrentPositionImmediately()

        assertTrue(result.success)
        assertNull(controller.pendingCaptureRoll)
        assertEquals(6, controller.lastDiceRoll)
        assertEquals(TurnPhase.DECIDE, engine.currentPhase)
        assertEquals(FoodType.EARTHWORM, controller.pendingFoodDecision?.type)
    }

    @Test
    fun `capture success with dice roll keeps last dice roll visible`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val food = FoodCard.createDummyCards(FoodType.EARTHWORM).first()
        engine.placeFoodAt(player.position, food)
        engine.advancePhase()
        engine.advancePhase()

        assertTrue(controller.captureCurrentPosition().success)
        assertTrue(controller.rollCaptureDice().success)
        val result = controller.resolveCaptureRoll()

        assertTrue(result.success)
        assertEquals(TurnPhase.DECIDE, engine.currentPhase)
        assertEquals(6, controller.lastDiceRoll)
    }

    @Test
    fun `capture with escape dice enters roulette pending without resolving`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val food = FoodCard.createDummyCards(FoodType.EARTHWORM).first()
        engine.placeFoodAt(player.position, food)
        engine.advancePhase()
        engine.advancePhase()

        val result = controller.captureCurrentPosition()

        assertTrue(result.success)
        assertNotNull(controller.pendingCaptureRoll)
        assertNull(controller.pendingCaptureRoll!!.roll)
        assertEquals(TurnPhase.CAPTURE, engine.currentPhase)
        assertNotNull(engine.foodPositions[player.position], "解決前にエサが消えてはならない")
        assertNull(controller.pendingFoodDecision)
        assertNull(controller.lastCaptureResult)

        val state = controller.playScreenUiState()
        assertTrue(state.diceRouletteActive)
        assertNull(state.diceRouletteResult)
        assertEquals(FoodType.EARTHWORM, state.diceRouletteFood, "カード公開用にエサ種別を見せるべき")
        assertEquals(listOf(1, 2), state.diceRouletteEscapeRolls, "逃走目を昇順で見せるべき")
        assertFalse(state.actionAvailability.canCapture)
        assertFalse(state.actionAvailability.canSkip)
        assertFalse(state.actionAvailability.canEndTurn)
    }

    @Test
    fun `roll capture dice fixes the roll without resolving capture`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.placeFoodAt(player.position, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPosition()

        val result = controller.rollCaptureDice()

        assertTrue(result.success)
        assertEquals(6, controller.lastDiceRoll)
        assertEquals(6, controller.pendingCaptureRoll?.roll)
        assertEquals(TurnPhase.CAPTURE, engine.currentPhase)
        assertNotNull(engine.foodPositions[player.position], "出目確定だけではエサは動かない")
        assertNull(controller.pendingFoodDecision)

        val state = controller.playScreenUiState()
        assertTrue(state.diceRouletteActive)
        assertEquals(6, state.diceRouletteResult)
    }

    @Test
    fun `resolve capture roll success enters decide phase`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.placeFoodAt(player.position, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPosition()
        controller.rollCaptureDice()

        val result = controller.resolveCaptureRoll()

        assertTrue(result.success)
        assertEquals(FoodType.EARTHWORM, controller.pendingFoodDecision?.type)
        assertEquals(TurnPhase.DECIDE, engine.currentPhase)
        assertNull(controller.pendingCaptureRoll)
        assertFalse(controller.playScreenUiState().diceRouletteActive)
    }

    @Test
    fun `resolve capture roll escape moves food and skips decide`() {
        val controller = MoguraGameController(
            diceRoller = FixedDiceRoller(listOf(1)),
            shuffler = FixedShuffler(),
        )
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val foodPos = Position(2, 2)
        val escapeTo = Position(3, 2)
        player.moveTo(foodPos)
        engine.placeFoodAt(foodPos, FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.RIGHT)))
        engine.removeFoodAt(escapeTo)
        engine.boardState.placeTile(foodPos, HoleTile(TileShape.CROSS, setOf(Direction.RIGHT), isFaceDown = false))
        engine.boardState.placeTile(escapeTo, HoleTile(TileShape.CROSS, setOf(Direction.LEFT), isFaceDown = false))
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPosition()
        controller.rollCaptureDice()

        val result = controller.resolveCaptureRoll()

        assertTrue(result.success)
        assertNull(engine.foodPositions[foodPos], "逃走したエサは元の位置から消えるべき")
        assertNotNull(engine.foodPositions[escapeTo], "エサは逃走先へ移動するべき")
        assertNull(controller.pendingFoodDecision)
        assertNull(controller.pendingCaptureRoll)
        assertEquals(TurnPhase.END, engine.currentPhase)
        assertTrue(controller.logs.any { it.contains("逃げました") })
    }

    @Test
    fun `capture is rejected while roulette pending`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.placeFoodAt(player.position, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPosition()

        val result = controller.captureCurrentPosition()

        assertFalse(result.success)
        assertNull(controller.pendingCaptureRoll!!.roll, "二度目の捕獲で状態が壊れてはならない")
    }

    @Test
    fun `skip and finish turn are rejected while roulette pending`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.placeFoodAt(player.position, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPosition()

        assertFalse(controller.skipPhase().success)
        assertFalse(controller.finishTurn().success)
        assertEquals(TurnPhase.CAPTURE, engine.currentPhase)
        assertNotNull(controller.pendingCaptureRoll)
    }

    @Test
    fun `auto advance stops while roulette pending`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.placeFoodAt(player.position, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPosition()

        val result = controller.autoAdvanceWhileNoChoice()

        assertNull(result)
        assertEquals(TurnPhase.CAPTURE, engine.currentPhase)
        assertNotNull(controller.pendingCaptureRoll)
    }

    @Test
    fun `roll capture dice fails when not pending or already rolled`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!

        assertFalse(controller.rollCaptureDice().success, "ルーレット待ちでなければ振れない")

        engine.placeFoodAt(player.position, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPosition()
        assertTrue(controller.rollCaptureDice().success)

        assertFalse(controller.rollCaptureDice().success, "出目確定後は二度振れない")
        assertEquals(6, controller.pendingCaptureRoll?.roll)
    }

    @Test
    fun `resolve capture roll fails before dice is rolled`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!

        assertFalse(controller.resolveCaptureRoll().success, "ルーレット待ちでなければ解決できない")

        engine.placeFoodAt(player.position, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPosition()

        assertFalse(controller.resolveCaptureRoll().success, "出目確定前は解決できない")
        assertEquals(TurnPhase.CAPTURE, engine.currentPhase)
    }

    @Test
    fun `start new game clears roulette pending`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.placeFoodAt(player.position, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPosition()

        controller.startNewGame(2)

        assertNull(controller.pendingCaptureRoll)
        assertFalse(controller.playScreenUiState().diceRouletteActive)
    }

    @Test
    fun `play screen ui state reports action availability for capture decision`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.placeFoodAt(player.position, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true))

        val initialActions = controller.playScreenUiState().actionAvailability
        assertEquals(TurnPhase.DIG, initialActions.activePhase)
        assertFalse(initialActions.canCapture)
        assertFalse(initialActions.canEat)
        assertFalse(initialActions.canCarry)
        assertFalse(initialActions.canSkip)
        assertFalse(initialActions.canEndTurn)

        engine.advancePhase()
        engine.advancePhase()
        val captureActions = controller.playScreenUiState().actionAvailability
        assertEquals(TurnPhase.CAPTURE, captureActions.activePhase)
        assertTrue(captureActions.canCapture)
        assertFalse(captureActions.canEat)
        assertFalse(captureActions.canCarry)
        assertTrue(captureActions.canSkip)
        assertTrue(captureActions.canEndTurn)

        controller.captureCurrentPositionImmediately()

        val decideActions = controller.playScreenUiState().actionAvailability
        assertEquals(TurnPhase.DECIDE, decideActions.activePhase)
        assertFalse(decideActions.canCapture)
        assertTrue(decideActions.canEat)
        assertTrue(decideActions.canCarry)
        assertFalse(decideActions.canSkip)
        assertFalse(decideActions.canEndTurn)
    }

    @Test
    fun `eat captured food heals without scoring`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        repeat(5) { player.reduceHealth(isOnSurface = false) }
        engine.placeFoodAt(player.position, FoodCard(FoodType.MOLE_CRICKET, emptyMap(), isFaceDown = true))
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPositionImmediately()

        val result = controller.eatPendingFood()

        assertTrue(result.success)
        assertEquals(11, player.health)
        assertFalse(player.isCarrying)
        assertEquals(0, player.score)
        assertEquals(TurnPhase.END, engine.currentPhase)
    }

    @Test
    fun `carry captured food starts carrying and ends decision`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.placeFoodAt(player.position, FoodCard(FoodType.EARTHWORM, emptyMap(), isFaceDown = true))
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPositionImmediately()

        val result = controller.carryPendingFood()

        assertTrue(result.success)
        assertEquals(FoodType.EARTHWORM, player.carriedFood?.type)
        assertNull(controller.pendingFoodDecision)
        assertEquals(TurnPhase.END, engine.currentPhase)
    }

    @Test
    fun `food carried to own nest is stored without healing when homecoming resolves`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        repeat(3) { player.reduceHealth(isOnSurface = false) }
        player.carryFood(FoodCard(FoodType.MOLE_CRICKET, emptyMap(), isFaceDown = false))

        controller.resolveCurrentPlayerPositionEffects()

        assertFalse(player.isCarrying)
        assertEquals(1, player.storedFoods.size)
        assertEquals(10, player.health)
        assertEquals(2, player.score)
        assertEquals(0, engine.currentPlayerIndex)
        assertEquals(TurnPhase.DIG, engine.currentPhase)
    }

    @Test
    fun `dig phase cannot be skipped`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!

        val result = controller.skipPhase()

        assertFalse(controller.canAdvanceFromDigWithoutTargets())
        assertFalse(result.success)
        assertEquals(TurnPhase.DIG, engine.currentPhase)
    }

    @Test
    fun `dig phase advances to move when no face down adjacent tile exists`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        controller.digTargets().forEach { position ->
            val tile = engine.boardState.getTile(position)!!
            engine.boardState.placeTile(position, tile.flip())
        }

        val result = controller.skipPhase()

        assertTrue(controller.digTargets().isEmpty())
        assertTrue(result.success)
        assertEquals(TurnPhase.MOVE, engine.currentPhase)
    }

    @Test
    fun `pending revealed dig tile still cannot be skipped`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val target = controller.digTargets().first()

        val revealResult = controller.revealDigTile(target)
        val skipResult = controller.skipPhase()

        assertTrue(revealResult.success)
        assertFalse(controller.canAdvanceFromDigWithoutTargets())
        assertFalse(skipResult.success)
        assertEquals(TurnPhase.DIG, engine.currentPhase)
        assertEquals(target, controller.pendingDigPlacement?.position)
    }

    @Test
    fun `dig targets only include face down tiles along current tile open sides`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val currentPosition = Position(3, 1)
        val left = Position(2, 1)
        val right = Position(4, 1)
        val down = Position(3, 2)
        player.moveTo(currentPosition)
        engine.boardState.placeTile(
            currentPosition,
            HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip(),
        )
        listOf(left, right, down).forEach { position ->
            engine.boardState.placeTile(position, HoleTile(TileShape.CROSS))
        }

        val targets = controller.digTargets().toSet()

        assertEquals(setOf(left, right), targets)
    }

    @Test
    fun `vertical straight tile can dig only top and bottom face down tiles`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val currentPosition = Position(3, 2)
        val top = Position(3, 1)
        val bottom = Position(3, 3)
        val left = Position(2, 2)
        val right = Position(4, 2)
        player.moveTo(currentPosition)
        engine.boardState.placeTile(currentPosition, HoleTile(TileShape.STRAIGHT).flip())
        listOf(top, bottom, left, right).forEach { position ->
            engine.boardState.placeTile(position, HoleTile(TileShape.CROSS))
        }

        val targets = controller.digTargets().toSet()

        assertEquals(setOf(top, bottom), targets)
    }

    @Test
    fun `nest can dig adjacent face down tiles without a current hole tile`() {
        val controller = testController()
        controller.startNewGame(2)

        assertEquals(setOf(Position(1, 1)), controller.digTargets().toSet())
    }

    @Test
    fun `turn cannot end before mandatory dig`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!

        val result = controller.finishTurn()

        assertFalse(result.success)
        assertEquals(TurnPhase.DIG, engine.currentPhase)
        assertEquals(0, engine.currentPlayerIndex)
    }

    @Test
    fun `dig first reveals tile without advancing so player can rotate after seeing it`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val target = Position(1, 1)

        val result = controller.revealDigTile(target)

        assertTrue(result.success)
        assertEquals(TurnPhase.DIG, engine.currentPhase)
        assertEquals(target, controller.pendingDigPlacement?.position)
        assertFalse(engine.boardState.getTile(target)!!.isFaceDown)
        assertTrue(Direction.TOP in engine.boardState.getTile(target)!!.openSides)
        assertTrue(Direction.BOTTOM in engine.boardState.getTile(target)!!.openSides)
    }

    @Test
    fun `revealed dig tile can be rotated before confirming placement`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val target = Position(1, 1)
        controller.revealDigTile(target)

        val rotateResult = controller.setPendingDigRotation(Rotation.DEG_90)
        val confirmResult = controller.confirmPendingDig()

        assertTrue(rotateResult.success)
        assertTrue(confirmResult.success)
        assertEquals(TurnPhase.MOVE, engine.currentPhase)
        assertNull(controller.pendingDigPlacement)
        val tile = engine.boardState.getTile(target)!!
        assertFalse(tile.isFaceDown)
        assertTrue(Direction.LEFT in tile.openSides)
        assertTrue(Direction.RIGHT in tile.openSides)
    }

    @Test
    fun `dig defaults to revealed tile and discards drawn tile`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val target = Position(1, 1)

        val revealResult = controller.revealDigTile(target)
        assertEquals(DigTileChoice.REVEALED, controller.pendingDigTileChoice)
        val confirmResult = controller.confirmPendingDig()

        assertTrue(revealResult.success)
        assertTrue(confirmResult.success)
        assertEquals(TileShape.STRAIGHT, engine.boardState.getTile(target)!!.shape)
        assertEquals(listOf(TileShape.L_SHAPE), engine.tilePlacementEngine.discardPile.map { it.shape })
    }

    @Test
    fun `pending dig shows only the two public candidate tiles and pile counts`() {
        val controller = testController()
        controller.startNewGame(2)
        val target = Position(1, 1)

        val revealResult = controller.revealDigTile(target)
        val state = controller.playScreenUiState()
        val revealed = state.digCandidates.single { it.choice == DigTileChoice.REVEALED }
        val drawn = state.digCandidates.single { it.choice == DigTileChoice.DRAWN }

        assertTrue(revealResult.success)
        assertEquals(TileShape.STRAIGHT, controller.pendingDigPlacement?.revealedTile?.shape)
        assertEquals(TileShape.L_SHAPE, controller.pendingDigPlacement?.drawnTile?.shape)
        assertEquals(
            PublicDeckSummary(
                tileDrawCount = 9,
                tileDiscardCount = 0,
                foodDrawCount = 8,
                foodDiscardCount = 0,
            ),
            controller.publicDeckSummary(),
        )
        assertEquals(TileShape.STRAIGHT, revealed.shape)
        assertTrue(revealed.selected)
        assertTrue(revealed.enabled)
        assertEquals(TileShape.L_SHAPE, drawn.shape)
        assertFalse(drawn.selected)
        assertTrue(drawn.enabled)

        controller.selectPendingDigTile(DigTileChoice.DRAWN)

        val selectedState = controller.playScreenUiState()
        assertFalse(selectedState.digCandidates.single { it.choice == DigTileChoice.REVEALED }.selected)
        assertTrue(selectedState.digCandidates.single { it.choice == DigTileChoice.DRAWN }.selected)
    }

    @Test
    fun `drawn tile can be selected before confirming dig placement`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val target = Position(1, 1)

        val revealResult = controller.revealDigTile(target)
        assertEquals(TileShape.L_SHAPE, controller.pendingDigPlacement?.drawnTile?.shape)
        val selectResult = controller.selectPendingDigTile(DigTileChoice.DRAWN)
        assertEquals(DigTileChoice.DRAWN, controller.pendingDigTileChoice)
        assertEquals(TileShape.L_SHAPE, engine.boardState.getTile(target)!!.shape)
        val rotateResult = controller.setPendingDigRotation(Rotation.DEG_90)
        val confirmResult = controller.confirmPendingDig()

        assertTrue(revealResult.success)
        assertTrue(selectResult.success)
        assertTrue(rotateResult.success)
        assertTrue(confirmResult.success)
        assertEquals(TurnPhase.MOVE, engine.currentPhase)
        assertNull(controller.pendingDigPlacement)
        val placedTile = engine.boardState.getTile(target)!!
        assertEquals(TileShape.L_SHAPE, placedTile.shape)
        assertFalse(placedTile.isFaceDown)
        assertTrue(Direction.LEFT in placedTile.openSides)
        assertTrue(Direction.TOP in placedTile.openSides)
        assertEquals(listOf(TileShape.STRAIGHT), engine.tilePlacementEngine.discardPile.map { it.shape })
    }

    @Test
    fun `dig click confirms only the already revealed tile`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val target = Position(1, 1)

        val reveal = controller.digAt(target, Rotation.DEG_0)
        val wrongTarget = controller.digAt(Position(2, 1), Rotation.DEG_90)
        val confirm = controller.digAt(target, Rotation.DEG_90)

        assertTrue(reveal.success)
        assertFalse(wrongTarget.success)
        assertTrue(confirm.success)
        assertEquals(TurnPhase.MOVE, engine.currentPhase)
    }

    @Test
    fun `current player on T tile can move to connected straight tile on the left`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val tPosition = Position(4, 1)
        val straightPosition = Position(3, 1)
        player.moveTo(tPosition)
        engine.boardState.placeTile(tPosition, HoleTile(TileShape.T_SHAPE).flip())
        engine.boardState.placeTile(straightPosition, HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip())
        engine.advancePhase()

        assertTrue(straightPosition in controller.moveTargets())
    }

    @Test
    fun `current player in right nest can move to 90 degree straight tile on the left`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val straightPosition = Position(4, 1)
        player.moveTo(Position(5, 1))
        engine.boardState.placeTile(straightPosition, HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip())
        engine.advancePhase()

        assertTrue(straightPosition in controller.moveTargets())
    }

    @Test
    fun `digging 90 degree straight tile next to right nest makes it reachable in move phase`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val straightPosition = Position(4, 1)
        player.moveTo(Position(5, 1))

        val revealResult = controller.revealDigTile(straightPosition)
        val rotateResult = controller.setPendingDigRotation(Rotation.DEG_90)
        val confirmResult = controller.confirmPendingDig()

        assertTrue(revealResult.success)
        assertTrue(rotateResult.success)
        assertTrue(confirmResult.success)
        assertEquals(TurnPhase.MOVE, engine.currentPhase)
        assertTrue(straightPosition in controller.moveTargets())
    }

    @Test
    fun `pending captured food decision cannot be skipped by ending turn`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.placeFoodAt(player.position, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true))
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPositionImmediately()

        val result = controller.finishTurn()

        assertFalse(result.success)
        assertEquals(TurnPhase.DECIDE, engine.currentPhase)
        assertEquals(FoodType.BEETLE_LARVA, controller.pendingFoodDecision?.type)
    }

    @Test
    fun `food replenishes immediately when no face down food remains in hot zone`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val target = Board.HOT_ZONE_POSITIONS.first()
        player.moveTo(target)

        Board.HOT_ZONE_POSITIONS.forEach { position ->
            val food = engine.foodPositions[position]
            if (food != null) {
                engine.placeFoodAt(position, food.copy(isFaceDown = false))
            }
        }
        engine.placeFoodAt(target, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true))
        engine.advancePhase()
        engine.advancePhase()

        val result = controller.captureCurrentPositionImmediately()

        assertTrue(result.success)
        Board.HOT_ZONE_POSITIONS.forEach { position ->
            val food = engine.foodPositions[position]
            assertTrue(food != null, "ホットゾーン $position にエサが補充されるべき")
            assertTrue(food!!.isFaceDown, "補充後のエサは裏向きであるべき")
        }
    }

    @Test
    fun `robbed food enters decision without auto carrying`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val thief = engine.players[0]
        val victim = engine.players[1]
        victim.carryFood(FoodCard(FoodType.EARTHWORM, emptyMap(), isFaceDown = false))
        victim.storeFood()
        victim.moveTo(Position(1, 1))
        thief.moveTo(victim.nestPosition)

        val needsDecision = controller.resolveCurrentPlayerPositionEffects()

        assertTrue(needsDecision)
        assertFalse(thief.isCarrying)
        assertEquals(FoodType.EARTHWORM, controller.pendingFoodDecision?.type)
        assertEquals(0, victim.score)
    }

    @Test
    fun `finish turn advances to next active player`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        engine.advancePhase()

        val result = controller.finishTurn()

        assertTrue(result.success)
        assertEquals(1, engine.currentPlayerIndex)
        assertEquals(TurnPhase.DIG, engine.currentPhase)
    }

    @Test
    fun `finish turn ends game when current player elimination leaves one survivor`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val eliminated = engine.players[0]
        val survivor = engine.players[1]
        repeat(12) { eliminated.reduceHealth(isOnSurface = false) }
        engine.advancePhase()

        val result = controller.finishTurn()

        assertTrue(result.success)
        assertTrue(eliminated.isEliminated)
        assertEquals(survivor, engine.checkWinCondition())
        assertEquals(GameState.FINISHED, engine.gameState)
    }

    @Test
    fun `auto advance ends turn when capture phase has no choice`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        engine.advancePhase()
        engine.advancePhase()
        assertEquals(TurnPhase.CAPTURE, engine.currentPhase)
        assertFalse(controller.canCapture())

        val result = controller.autoAdvanceWhileNoChoice()

        assertTrue(result?.success == true)
        assertEquals(1, engine.currentPlayerIndex)
        assertEquals(TurnPhase.DIG, engine.currentPhase)
    }

    @Test
    fun `auto advance stops while a captured food decision is pending`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        engine.placeFoodAt(player.position, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true))
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPositionImmediately()
        assertEquals(TurnPhase.DECIDE, engine.currentPhase)

        val result = controller.autoAdvanceWhileNoChoice()

        assertNull(result)
        assertEquals(TurnPhase.DECIDE, engine.currentPhase)
        assertEquals(0, engine.currentPlayerIndex)
        assertEquals(FoodType.BEETLE_LARVA, controller.pendingFoodDecision?.type)
    }

    private fun testController(): MoguraGameController =
        MoguraGameController(
            diceRoller = FixedDiceRoller(listOf(6)),
            shuffler = FixedShuffler(),
        )
}
