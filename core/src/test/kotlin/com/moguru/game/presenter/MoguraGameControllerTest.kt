package com.moguru.game.presenter

import com.moguru.game.engine.GameEngine
import com.moguru.game.engine.GameState
import com.moguru.game.engine.PlayerConfig
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Board
import com.moguru.game.model.Direction
import com.moguru.game.model.EscapeDirection
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Player
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
    fun `new game accepts custom mole nest and start player selections`() {
        val controller = testController()
        val configs = listOf(
            PlayerConfig("モグカ", Position(5, 4), playerId = 3),
            PlayerConfig("モグオ", Position(0, 1), playerId = 0),
        )

        val result = controller.startNewGame(configs, startPlayerIndex = 1)

        val engine = controller.engine!!
        assertTrue(result.success)
        assertEquals(listOf(3, 0), engine.players.map { it.id })
        assertEquals(listOf(Position(5, 4), Position(0, 1)), engine.players.map { it.nestPosition })
        assertEquals(1, engine.currentPlayerIndex)
        assertEquals("モグオ", controller.currentPlayer?.name)
        assertTrue(controller.logs.any { it.contains("モグオ の番") })
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
        assertFalse(state.actionAvailability.canRob)
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
        placeFoodForCapture(engine, player, food)
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
    fun `capture success removes only the captured food from a stacked cell`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val target = Position(1, 1)
        player.moveTo(target)
        engine.placeFoodAt(target, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true))
        engine.placeFoodAt(target, FoodCard(FoodType.EARTHWORM, emptyMap(), isFaceDown = true))
        engine.advancePhase()
        engine.advancePhase()

        val result = controller.captureCurrentPositionImmediately()

        assertTrue(result.success)
        assertEquals(FoodType.BEETLE_LARVA, controller.pendingFoodDecision?.type)
        assertEquals(listOf(FoodType.EARTHWORM), engine.foodsAt(target).map { it.type })
        assertEquals(TurnPhase.DECIDE, engine.currentPhase)
    }

    @Test
    fun `capture target can be selected from stacked food`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val target = Position(1, 1)
        player.moveTo(target)
        engine.placeFoodAt(target, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true))
        engine.placeFoodAt(target, FoodCard(FoodType.MOLE_CRICKET, emptyMap(), isFaceDown = true))
        engine.advancePhase()
        engine.advancePhase()

        val initialTargets = controller.playScreenUiState().captureTargets
        assertEquals(listOf(FoodType.BEETLE_LARVA, FoodType.MOLE_CRICKET), initialTargets.map { it.type })
        assertTrue(initialTargets[0].selected)

        val selectResult = controller.selectCaptureTarget(1)
        val selectedTargets = controller.playScreenUiState().captureTargets
        val captureResult = controller.captureCurrentPositionImmediately()

        assertTrue(selectResult.success)
        assertTrue(selectedTargets[1].selected)
        assertTrue(captureResult.success)
        assertEquals(FoodType.MOLE_CRICKET, controller.pendingFoodDecision?.type)
        assertEquals(listOf(FoodType.BEETLE_LARVA), engine.foodsAt(target).map { it.type })
    }

    @Test
    fun `last dice roll clears when a later capture needs no dice`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        placeFoodForCapture(engine, player, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
        engine.advancePhase()
        engine.advancePhase()
        controller.captureCurrentPositionImmediately()
        assertEquals(6, controller.lastDiceRoll, "ダイス捕獲では出目が残るべき")

        controller.eatPendingFood()
        engine.advancePhase()
        val nextPlayer = controller.currentPlayer!!
        placeFoodForCapture(engine, nextPlayer, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true))
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
        placeFoodForCapture(engine, player, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
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
        placeFoodForCapture(engine, player, food)
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
    fun `same capture conditions resolve the same for player one and player two`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val capturePosition = Position(2, 2)

        fun captureForCurrentPlayer(): Pair<TurnPhase, FoodType?> {
            val player = controller.currentPlayer!!
            player.moveTo(capturePosition)
            replaceFoodAt(
                engine,
                capturePosition,
                FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.RIGHT), isFaceDown = true),
            )
            engine.advancePhase()
            engine.advancePhase()

            val result = controller.captureCurrentPositionImmediately()

            assertTrue(result.success)
            return engine.currentPhase to controller.pendingFoodDecision?.type
        }

        val playerOneResult = captureForCurrentPlayer()
        controller.eatPendingFood()
        controller.finishTurn()
        val playerTwoResult = captureForCurrentPlayer()

        assertEquals(TurnPhase.DECIDE to FoodType.EARTHWORM, playerOneResult)
        assertEquals(playerOneResult, playerTwoResult)
    }

    @Test
    fun `capture with escape dice enters roulette pending without resolving`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val food = FoodCard.createDummyCards(FoodType.EARTHWORM).first()
        placeFoodForCapture(engine, player, food)
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
        assertFalse(state.actionAvailability.canRob)
        assertFalse(state.actionAvailability.canSkip)
        assertFalse(state.actionAvailability.canEndTurn)
    }

    @Test
    fun `roll capture dice fixes the roll without resolving capture`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        placeFoodForCapture(engine, player, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
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
        placeFoodForCapture(engine, player, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
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
        replaceFoodAt(engine, foodPos, FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.RIGHT)))
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
    fun `escaped hot zone food remains when immediate replenish runs`() {
        val controller = MoguraGameController(
            diceRoller = FixedDiceRoller(listOf(1)),
            shuffler = FixedShuffler(),
        )
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val source = Position(2, 2)
        val escapeTo = Position(3, 2)
        Board.HOT_ZONE_POSITIONS.forEach { position ->
            while (engine.removeFoodAt(position) != null) {
                // Remove setup food so this test controls the last face-down hot-zone food.
            }
        }
        player.moveTo(source)
        engine.placeFoodAt(source, FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.RIGHT), isFaceDown = true))
        engine.placeFoodAt(escapeTo, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = false))
        engine.boardState.placeTile(source, HoleTile(TileShape.CROSS, setOf(Direction.RIGHT), isFaceDown = false))
        engine.boardState.placeTile(escapeTo, HoleTile(TileShape.CROSS, setOf(Direction.LEFT), isFaceDown = false))
        engine.advancePhase()
        engine.advancePhase()

        val result = controller.captureCurrentPositionImmediately()

        assertTrue(result.success)
        val escapeStack = engine.foodsAt(escapeTo)
        assertEquals(
            listOf(FoodType.BEETLE_LARVA, FoodType.EARTHWORM),
            escapeStack.take(2).map { it.type },
        )
        assertTrue(escapeStack.take(2).none { it.isFaceDown }, "逃走先の表向きスタックは補充で捨てない")
        assertEquals(3, escapeStack.size, "保存対象のホットゾーンにも裏向きエサを補充する")
        assertTrue(escapeStack.last().isFaceDown, "保存対象のホットゾーンにも裏向きエサを補充する")
        assertEquals(
            4,
            Board.HOT_ZONE_POSITIONS.sumOf { position -> engine.foodsAt(position).count { it.isFaceDown } },
            "補充後はホットゾーン4マスすべてに裏向きエサがある",
        )
        assertTrue(engine.foodAt(source)?.isFaceDown == true, "逃走元の空いたホットゾーンは補充される")
        assertEquals(0, engine.foodDiscardCount)
    }

    @Test
    fun `capture is rejected while roulette pending`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        placeFoodForCapture(engine, player, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
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
        placeFoodForCapture(engine, player, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
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
        placeFoodForCapture(engine, player, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
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

        placeFoodForCapture(engine, player, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
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

        placeFoodForCapture(engine, player, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
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
        placeFoodForCapture(engine, player, FoodCard.createDummyCards(FoodType.EARTHWORM).first())
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

        val initialActions = controller.playScreenUiState().actionAvailability
        assertEquals(TurnPhase.DIG, initialActions.activePhase)
        assertFalse(initialActions.canCapture)
        assertFalse(initialActions.canEat)
        assertFalse(initialActions.canCarry)
        assertFalse(initialActions.canRob)
        assertFalse(initialActions.canSkip)
        assertFalse(initialActions.canEndTurn)

        placeFoodForCapture(engine, player, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true))
        engine.advancePhase()
        engine.advancePhase()
        val captureActions = controller.playScreenUiState().actionAvailability
        assertEquals(TurnPhase.CAPTURE, captureActions.activePhase)
        assertTrue(captureActions.canCapture)
        assertFalse(captureActions.canEat)
        assertFalse(captureActions.canCarry)
        assertFalse(captureActions.canRob)
        assertTrue(captureActions.canSkip)
        assertTrue(captureActions.canEndTurn)

        controller.captureCurrentPositionImmediately()

        val decideActions = controller.playScreenUiState().actionAvailability
        assertEquals(TurnPhase.DECIDE, decideActions.activePhase)
        assertFalse(decideActions.canCapture)
        assertTrue(decideActions.canEat)
        assertTrue(decideActions.canCarry)
        assertFalse(decideActions.canRob)
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
        placeFoodForCapture(engine, player, FoodCard(FoodType.MOLE_CRICKET, emptyMap(), isFaceDown = true))
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
        placeFoodForCapture(engine, player, FoodCard(FoodType.EARTHWORM, emptyMap(), isFaceDown = true))
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
    fun `player carrying food cannot capture another food`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        player.carryFood(FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = false))
        placeFoodForCapture(engine, player, FoodCard(FoodType.EARTHWORM, emptyMap(), isFaceDown = true))
        engine.advancePhase()
        engine.advancePhase()

        val result = controller.captureCurrentPosition()

        assertFalse(controller.canCapture())
        assertFalse(result.success)
        assertNull(controller.pendingCaptureRoll)
        assertNull(controller.pendingFoodDecision)
        assertEquals(FoodType.EARTHWORM, engine.foodAt(player.position)?.type)
        assertEquals(FoodType.BEETLE_LARVA, player.carriedFood?.type)
        assertEquals(TurnPhase.CAPTURE, engine.currentPhase)
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
    fun `stored food in own nest can be eaten after returning home`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val stored = FoodCard(FoodType.MOLE_CRICKET, emptyMap(), isFaceDown = false)
        repeat(5) { player.reduceHealth(isOnSurface = false) }
        player.carryFood(stored)
        player.storeFood()
        player.moveTo(Position(1, 1))
        engine.boardState.placeTile(
            Position(1, 1),
            HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip(),
        )
        engine.advancePhase()

        val moveHome = controller.moveTo(player.nestPosition)
        val enterDecide = controller.skipPhase()
        val actions = controller.playScreenUiState().actionAvailability
        val eat = controller.eatPendingFood()

        assertTrue(moveHome.success)
        assertTrue(enterDecide.success)
        assertEquals(TurnPhase.DECIDE, actions.activePhase)
        assertTrue(actions.canEat)
        assertFalse(actions.canCarry)
        assertTrue(eat.success)
        assertEquals(11, player.health)
        assertTrue(player.storedFoods.isEmpty())
        assertEquals(0, player.score)
        assertEquals(TurnPhase.END, engine.currentPhase)
    }

    @Test
    fun `winning homecoming does not allow own nest eating before game over`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        player.carryFood(FoodCard(FoodType.CENTIPEDE, emptyMap(), isFaceDown = false))
        player.storeFood()
        player.carryFood(FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = false))
        player.moveTo(Position(1, 1))
        engine.boardState.placeTile(
            Position(1, 1),
            HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip(),
        )
        engine.advancePhase()

        val moveHome = controller.moveTo(player.nestPosition)
        val actionsAfterHomecoming = controller.playScreenUiState().actionAvailability
        val skipCapture = controller.skipPhase()
        val finish = controller.finishTurn()

        assertTrue(moveHome.success)
        assertEquals(4, player.score)
        assertEquals(TurnPhase.CAPTURE, actionsAfterHomecoming.activePhase)
        assertFalse(actionsAfterHomecoming.canEat)
        assertTrue(skipCapture.success)
        assertTrue(finish.success)
        assertEquals(GameState.FINISHED, engine.gameState)
        assertEquals(player, engine.checkWinCondition())
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
    fun `dig phase advances to move when no adjacent hole tile exists`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        engine.boardState.clear()

        val result = controller.skipPhase()

        assertTrue(controller.digTargets().isEmpty())
        assertTrue(result.success)
        assertEquals(TurnPhase.MOVE, engine.currentPhase)
    }

    @Test
    fun `dig phase on surface can dig adjacent underground tile`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val target = Position(1, 1)
        engine.boardState.clear()
        engine.boardState.placeTile(target, HoleTile(TileShape.STRAIGHT).flip())
        player.moveTo(Position(1, 0))

        val targets = controller.digTargets()

        assertEquals(listOf(target), targets)
        assertFalse(controller.canAdvanceFromDigWithoutTargets())
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
    fun `dig targets only include hole tiles along current tile open sides`() {
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
    fun `vertical straight tile can dig only top and bottom hole tiles`() {
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
    fun `dig targets exclude empty ground cells even along current tile open sides`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val currentPosition = Position(1, 1)
        val ground = Position(1, 0)
        player.moveTo(currentPosition)
        engine.boardState.placeTile(
            currentPosition,
            HoleTile(TileShape.CROSS, setOf(Direction.TOP), isFaceDown = false),
        )

        val targets = controller.digTargets().toSet()

        assertFalse(ground in targets)
        assertTrue(controller.canAdvanceFromDigWithoutTargets())
    }

    @Test
    fun `dig cannot place drawn tile on empty ground cell`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val currentPosition = Position(1, 1)
        val ground = Position(1, 0)
        player.moveTo(currentPosition)
        engine.boardState.placeTile(
            currentPosition,
            HoleTile(TileShape.CROSS, setOf(Direction.TOP), isFaceDown = false),
        )

        val revealResult = controller.revealDigTile(ground)

        assertFalse(revealResult.success)
        assertNull(controller.pendingDigTileChoice)
        assertNull(engine.boardState.getTile(ground))
        assertEquals(TurnPhase.DIG, engine.currentPhase)
    }

    @Test
    fun `dig targets include face up hole tiles along current tile open sides`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val currentPosition = Position(3, 1)
        val target = Position(4, 1)
        player.moveTo(currentPosition)
        engine.boardState.placeTile(
            currentPosition,
            HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip(),
        )
        engine.boardState.placeTile(target, HoleTile(TileShape.T_SHAPE).flip())

        val targets = controller.digTargets().toSet()

        assertTrue(target in targets)
    }

    @Test
    fun `dig targets exclude hole tiles occupied by another player`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val target = Position(1, 1)
        engine.players[1].moveTo(target)

        val targets = controller.digTargets().toSet()
        val revealResult = controller.revealDigTile(target)

        assertFalse(target in targets)
        assertFalse(revealResult.success)
    }

    @Test
    fun `dig targets include hole tiles occupied only by food`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val target = Position(1, 1)
        engine.placeFoodAt(target, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true))

        val targets = controller.digTargets().toSet()
        val revealResult = controller.revealDigTile(target)

        assertTrue(target in targets)
        assertTrue(revealResult.success)
    }

    @Test
    fun `dig can replace a face up hole tile with the drawn tile`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val currentPosition = Position(3, 1)
        val target = Position(4, 1)
        player.moveTo(currentPosition)
        engine.boardState.placeTile(
            currentPosition,
            HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip(),
        )
        engine.boardState.placeTile(target, HoleTile(TileShape.T_SHAPE).flip())

        val revealResult = controller.revealDigTile(target)
        val selectResult = controller.selectPendingDigTile(DigTileChoice.DRAWN)
        val confirmResult = controller.confirmPendingDig()

        assertTrue(revealResult.success)
        assertTrue(selectResult.success)
        assertTrue(confirmResult.success)
        assertEquals(TurnPhase.MOVE, engine.currentPhase)
        val placedTile = engine.boardState.getTile(target)!!
        assertEquals(TileShape.L_SHAPE, placedTile.shape)
        assertFalse(placedTile.isFaceDown)
        assertEquals(listOf(TileShape.T_SHAPE), engine.tilePlacementEngine.discardPile.map { it.shape })
    }

    @Test
    fun `revealed face up dig tile rotation starts from canonical orientation`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val player = controller.currentPlayer!!
        val currentPosition = Position(3, 1)
        val target = Position(4, 1)
        player.moveTo(currentPosition)
        engine.boardState.placeTile(
            currentPosition,
            HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip(),
        )
        engine.boardState.placeTile(
            target,
            HoleTile(TileShape.L_SHAPE).rotate(Rotation.DEG_90).flip(),
        )

        val revealResult = controller.revealDigTile(target)
        val rotateResult = controller.setPendingDigRotation(Rotation.DEG_90)
        val confirmResult = controller.confirmPendingDig()

        assertTrue(revealResult.success)
        assertTrue(rotateResult.success)
        assertTrue(confirmResult.success)
        val placedTile = engine.boardState.getTile(target)!!
        assertEquals(TileShape.L_SHAPE, placedTile.shape)
        assertFalse(placedTile.isFaceDown)
        assertEquals(HoleTile(TileShape.L_SHAPE).rotate(Rotation.DEG_90).openSides, placedTile.openSides)
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
    fun `defended opponent nest is not included in move targets`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        for (col in 1..4) {
            engine.boardState.placeTile(
                Position(col, 1),
                HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip(),
            )
        }
        engine.advancePhase()

        val targets = controller.moveTargets()

        assertTrue(Position(4, 1) in targets)
        assertFalse(Position(5, 1) in targets)
    }

    @Test
    fun `unoccupied opponent nest remains a move target`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        engine.players[1].moveTo(Position(2, 2))
        for (col in 1..4) {
            engine.boardState.placeTile(
                Position(col, 1),
                HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip(),
            )
        }
        engine.advancePhase()

        assertTrue(Position(5, 1) in controller.moveTargets())
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
        placeFoodForCapture(engine, player, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true))
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
            val food = engine.removeFoodAt(position)
            if (food != null) {
                engine.placeFoodAt(position, food.copy(isFaceDown = false))
            }
        }
        while (engine.removeFoodAt(target) != null) {
            // Keep only the capture target as the remaining face-down hot-zone food.
        }
        engine.placeFoodAt(target, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true))
        engine.advancePhase()
        engine.advancePhase()

        val result = controller.captureCurrentPositionImmediately()

        assertTrue(result.success)
        Board.HOT_ZONE_POSITIONS.forEach { position ->
            val food = engine.foodAt(position)
            assertTrue(food != null, "ホットゾーン $position にエサが補充されるべき")
            assertTrue(food!!.isFaceDown, "補充後のエサは裏向きであるべき")
        }
    }

    @Test
    fun `entering opponent nest does not rob on the same turn`() {
        val controller = testController()
        controller.startNewGame(2)
        val engine = controller.engine!!
        val thief = engine.players[0]
        val victim = engine.players[1]
        victim.carryFood(FoodCard(FoodType.EARTHWORM, emptyMap(), isFaceDown = false))
        victim.storeFood()
        victim.moveTo(Position(1, 1))
        connectLeftNestToRightNest(engine)
        engine.advancePhase()

        val moveResult = controller.moveTo(victim.nestPosition)
        val captureSkip = controller.skipPhase()

        assertTrue(moveResult.success)
        assertTrue(captureSkip.success)
        assertEquals(TurnPhase.END, engine.currentPhase)
        assertNull(controller.pendingFoodDecision)
        assertFalse(thief.isCarrying)
        assertEquals(1, victim.storedFoods.size)
        assertFalse(controller.playScreenUiState().actionAvailability.canRob)
    }

    @Test
    fun `robbery becomes available on the next own turn if player remains in opponent nest`() {
        val controller = testController()
        val (thief, victim) = advanceToRobberyDecision(controller)
        val state = controller.playScreenUiState()

        assertEquals(TurnPhase.DECIDE, controller.engine!!.currentPhase)
        assertTrue(state.actionAvailability.canRob)
        assertFalse(state.actionAvailability.canEat)
        assertFalse(state.actionAvailability.canCarry)
        assertEquals(listOf(FoodType.EARTHWORM), state.robberyTargets.map { it.type })

        val robbery = controller.robSelectedFood()

        assertTrue(robbery.success)
        assertEquals(FoodDecisionSource.ROBBERY, controller.pendingFoodDecisionSource)
        assertEquals(FoodType.EARTHWORM, controller.pendingFoodDecision?.type)
        assertTrue(victim.storedFoods.isEmpty())
        assertFalse(thief.isCarrying)
    }

    @Test
    fun `carrying robbed food scores only after returning to own nest`() {
        val controller = testController()
        val (thief, _) = advanceToRobberyDecision(controller)
        controller.robSelectedFood()

        val carry = controller.carryPendingFood()

        assertTrue(carry.success)
        assertTrue(thief.isCarrying)
        assertTrue(thief.storedFoods.isEmpty())
        assertEquals(0, thief.score)
        assertEquals(TurnPhase.END, controller.engine!!.currentPhase)

        thief.moveTo(thief.nestPosition)
        val finish = controller.finishTurn()

        assertTrue(finish.success)
        assertFalse(thief.isCarrying)
        assertEquals(FoodType.EARTHWORM, thief.storedFoods.single().type)
        assertEquals(1, thief.score)
    }

    @Test
    fun `selected robbery target is stolen from stored food stack`() {
        val controller = testController()
        val (thief, victim) = advanceToRobberyDecision(
            controller,
            storedFoods = listOf(
                FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = false),
                FoodCard(FoodType.EARTHWORM, emptyMap(), isFaceDown = false),
            ),
        )

        val initialTargets = controller.playScreenUiState().robberyTargets
        val select = controller.selectRobberyTarget(1)
        val robbery = controller.robSelectedFood()

        assertEquals(listOf(FoodType.BEETLE_LARVA, FoodType.EARTHWORM), initialTargets.map { it.type })
        assertTrue(select.success)
        assertTrue(robbery.success)
        assertEquals(FoodType.EARTHWORM, controller.pendingFoodDecision?.type)
        assertEquals(listOf(FoodType.BEETLE_LARVA), victim.storedFoods.map { it.type })
        assertFalse(thief.isCarrying)
    }

    @Test
    fun `robbery is consumed for the current turn after eating stolen food`() {
        val controller = testController()
        val (_, victim) = advanceToRobberyDecision(
            controller,
            storedFoods = listOf(
                FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = false),
                FoodCard(FoodType.EARTHWORM, emptyMap(), isFaceDown = false),
            ),
        )
        controller.robSelectedFood()

        val eat = controller.eatPendingFood()
        val finish = controller.finishTurn()

        assertTrue(eat.success)
        assertTrue(finish.success)
        assertEquals(listOf(FoodType.EARTHWORM), victim.storedFoods.map { it.type })
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
        placeFoodForCapture(engine, player, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = true))
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

    private fun replaceFoodAt(engine: GameEngine, position: Position, food: FoodCard) {
        while (engine.removeFoodAt(position) != null) {
            // Remove the previous stack so this test controls the capture target.
        }
        engine.placeFoodAt(position, food)
    }

    private fun advanceToRobberyDecision(
        controller: MoguraGameController,
        storedFoods: List<FoodCard> = listOf(FoodCard(FoodType.EARTHWORM, emptyMap(), isFaceDown = false)),
    ): Pair<Player, Player> {
        controller.startNewGame(2)
        val engine = controller.engine!!
        val thief = engine.players[0]
        val victim = engine.players[1]
        storedFoods.forEach { food ->
            victim.carryFood(food)
            victim.storeFood()
        }
        victim.moveTo(Position(1, 1))
        connectLeftNestToRightNest(engine)

        engine.advancePhase()
        assertTrue(controller.moveTo(victim.nestPosition).success)
        assertTrue(controller.skipPhase().success)
        assertEquals(TurnPhase.END, engine.currentPhase)
        assertTrue(controller.finishTurn().success)

        engine.advancePhase()
        assertTrue(controller.finishTurn().success)
        assertEquals(0, engine.currentPlayerIndex)

        engine.advancePhase()
        assertTrue(controller.skipPhase().success)
        assertTrue(controller.skipPhase().success)
        return thief to victim
    }

    private fun connectLeftNestToRightNest(engine: GameEngine) {
        for (col in 1..4) {
            engine.boardState.placeTile(
                Position(col, 1),
                HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip(),
            )
        }
    }

    private fun placeFoodForCapture(engine: GameEngine, player: Player, food: FoodCard) {
        player.moveTo(Position(1, 1))
        engine.placeFoodAt(player.position, food)
    }

    private fun testController(): MoguraGameController =
        MoguraGameController(
            diceRoller = FixedDiceRoller(listOf(6)),
            shuffler = FixedShuffler(),
        )
}
