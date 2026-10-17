package com.moguru.game.cpu

import com.moguru.game.engine.GameState
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.*
import com.moguru.game.presenter.FoodDecisionSource
import com.moguru.game.presenter.DigTileChoice
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OpponentAwareCpuTest {
    private val home = Position(0, 1)
    private val enemyHome = Position(5, 1)
    private val strategy = HardCpuStrategy()
    private fun player(id: Int, position: Position, nest: Position, hp: Int = 9, score: Int = 0) =
        CpuPlayer(id, position, nest, hp, null, List(score) { FoodType.BEETLE_LARVA })

    private fun view(players: List<CpuPlayer>, actions: List<CpuAction>, phase: TurnPhase = TurnPhase.MOVE) = CpuObservation(
        gameState = GameState.PLAYING, phase = phase, currentPlayerId = 0, players = players,
        tiles = (1..4).flatMap { col -> (1..4).map { Position(col, it) } }
            .associateWith { CpuTile(TileShape.CROSS, Direction.entries.toSet()) },
        foods = emptyMap(), drawnTile = null, pendingDig = null, pendingFood = null,
        pendingFoodSource = null, waitingForCapture = false, canRobAfterStaying = false,
        canEatStoredFoodAfterSkipping = false, legalActions = actions,
    )

    @Test fun `defend nest against an intruder whose next theft wins instead of chasing food`() {
        val foodPosition = Position(2, 1)
        val observation = view(listOf(player(0, Position(1, 1), home, score = 1),
            player(1, home, enemyHome, score = 3)),
            listOf(CpuAction.Skip, CpuAction.Move(home), CpuAction.Move(foodPosition))).copy(
                foods = mapOf(foodPosition to listOf(CpuFood(FoodType.CENTIPEDE))))
        assertEquals(CpuAction.Move(home), strategy.choose(observation))
    }

    @Test fun `race for points with a healthy leading opponent but eat to outlast a starving opponent`() {
        val observation = view(listOf(player(0, Position(1, 1), home, hp = 4),
            player(1, Position(4, 1), enemyHome, hp = 10, score = 3)),
            listOf(CpuAction.Eat, CpuAction.Carry), TurnPhase.DECIDE).copy(
                pendingFood = FoodType.BEETLE_LARVA, pendingFoodSource = FoodDecisionSource.CAPTURE)
        assertEquals(CpuAction.Carry, strategy.choose(observation))
        val starving = observation.copy(players = observation.players.map {
            if (it.id == 1) it.copy(health = 1, storedFoods = emptyList()) else it
        })
        assertEquals(CpuAction.Eat, strategy.choose(starving))
    }

    @Test fun `a score race never allows carrying a catch if returning would be lethal`() {
        val observation = view(listOf(player(0, Position(1, 1), home, hp = 2),
            player(1, Position(4, 1), enemyHome, score = 3)),
            listOf(CpuAction.Eat, CpuAction.Carry), TurnPhase.DECIDE).copy(
                pendingFood = FoodType.CENTIPEDE, pendingFoodSource = FoodDecisionSource.CAPTURE)
        assertEquals(CpuAction.Eat, strategy.choose(observation))
    }

    @Test fun `a nonwinning race delivery keeps a health reserve for the turn after returning`() {
        val observation = view(listOf(player(0, Position(1, 1), home, hp = 3),
            player(1, Position(4, 1), enemyHome, score = 3)),
            listOf(CpuAction.Eat, CpuAction.Carry), TurnPhase.DECIDE).copy(
                pendingFood = FoodType.BEETLE_LARVA, pendingFoodSource = FoodDecisionSource.CAPTURE)
        assertEquals(CpuAction.Eat, strategy.choose(observation))
    }

    @Test fun `take an immediate winning return before defending the nest`() {
        val observation = view(listOf(player(0, Position(1, 1), home, score = 3).copy(carriedFood = FoodType.BEETLE_LARVA),
            player(1, home, enemyHome, score = 3)), listOf(CpuAction.Skip, CpuAction.Move(home)))
        assertEquals(CpuAction.Move(home), strategy.choose(observation))
    }

    @Test fun `prefer denying the leading players store over an equal raid on the trailing player`() {
        val thirdHome = Position(5, 4)
        val observation = view(listOf(player(0, Position(1, 1), home),
            player(1, Position(3, 1), enemyHome, score = 1),
            player(2, Position(4, 1), thirdHome, score = 3)),
            listOf(CpuAction.Move(enemyHome), CpuAction.Move(thirdHome)))
        assertEquals(CpuAction.Move(thirdHome), strategy.choose(observation))
    }

    @Test fun `outlasting applies only to two players and is disabled by a visible recovery chance`() {
        val observation = view(listOf(player(0, Position(1, 1), home),
            player(1, Position(4, 1), enemyHome, hp = 1)), listOf(CpuAction.Skip))
        assertTrue(OpponentAssessment(observation, PublicBoard(observation)).outlast)
        val recovery = observation.copy(foods = mapOf(Position(3, 1) to listOf(CpuFood(FoodType.BEETLE_LARVA))))
        assertFalse(OpponentAssessment(recovery, PublicBoard(recovery)).outlast)
        val multiplayer = observation.copy(players = observation.players + player(2, Position(5, 4), Position(5, 4), hp = 0))
        assertFalse(OpponentAssessment(multiplayer, PublicBoard(multiplayer)).outlast)
    }

    @Test fun `rotate a public tunnel to stop an opponents winning return then confirm without looping`() {
        val position = Position(3, 1)
        val observation = view(listOf(player(0, Position(3, 2), Position(0, 4)),
            player(1, Position(2, 1), enemyHome, score = 1).copy(carriedFood = FoodType.CENTIPEDE)),
            Rotation.entries.map { CpuAction.PrepareDig(DigTileChoice.DRAWN, it) } + CpuAction.ConfirmDig, TurnPhase.DIG).copy(
                tiles = (1..4).associate { Position(it, 1) to CpuTile(TileShape.STRAIGHT, setOf(Direction.LEFT, Direction.RIGHT)) } +
                    (Position(3, 2) to CpuTile(TileShape.CROSS, Direction.entries.toSet())),
                pendingDig = CpuDigPlacement(position, mapOf(DigTileChoice.DRAWN to TileShape.STRAIGHT),
                    DigTileChoice.DRAWN, Rotation.DEG_90))
        assertEquals(CpuAction.PrepareDig(DigTileChoice.DRAWN, Rotation.DEG_0), strategy.choose(observation))
        assertEquals(CpuAction.ConfirmDig, strategy.choose(observation.copy(
            pendingDig = observation.pendingDig!!.copy(selectedRotation = Rotation.DEG_0))))
    }

    @Test fun `do not count a delayed winning raid as certain when the owner can return with food`() {
        val food = Position(2, 1)
        val observation = view(listOf(player(0, Position(1, 1), home, score = 3),
            player(1, Position(4, 1), enemyHome, score = 1).copy(carriedFood = FoodType.MOLE_CRICKET)),
            listOf(CpuAction.Move(enemyHome), CpuAction.Move(food))).copy(
                foods = mapOf(food to listOf(CpuFood(FoodType.BEETLE_LARVA))))
        assertEquals(CpuAction.Move(food), strategy.choose(observation))
    }

    @Test fun `a starving opponent can still dig one connection to a recovery food`() {
        val observation = view(listOf(player(0, Position(1, 1), home),
            player(1, enemyHome, enemyHome, hp = 1)), listOf(CpuAction.Skip)).let {
                it.copy(tiles = it.tiles + (Position(4, 1) to CpuTile(null)),
                    foods = mapOf(Position(3, 1) to listOf(CpuFood(null))))
            }
        val board = PublicBoard(observation)
        assertFalse(Position(3, 1) in board.reachableFor(observation.players[1]))
        assertFalse(OpponentAssessment(observation, board).outlast)
    }

    @Test fun `avoid a surface stop that requires an uncertain catch to survive the next turn`() {
        val ground = Position(1, 0)
        val underground = Position(2, 1)
        val observation = view(listOf(player(0, Position(1, 1), home, hp = 3),
            player(1, Position(4, 1), enemyHome)), listOf(CpuAction.Move(ground), CpuAction.Move(underground)))
            .copy(foods = mapOf(ground to listOf(CpuFood(FoodType.EARTHWORM))))
        assertEquals(CpuAction.Move(underground), strategy.choose(observation))
    }
}
