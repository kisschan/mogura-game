package com.moguru.game.engine

import com.moguru.game.model.Board
import com.moguru.game.model.CellType
import com.moguru.game.model.Direction
import com.moguru.game.model.EscapeDirection
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Position
import com.moguru.game.model.TileShape
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GameEngineTest {

    private lateinit var engine: GameEngine
    private val shuffler = FixedShuffler()
    private val diceRoller = FixedDiceRoller(listOf(6))

    @BeforeEach
    fun setup() {
        engine = GameEngine(
            playerCount = 2,
            diceRoller = diceRoller,
            shuffler = shuffler,
        )
    }

    @Test
    fun `初期状態はSETUP`() {
        assertEquals(GameState.SETUP, engine.gameState)
    }

    @Test
    fun `セットアップ後の状態はPLAYING`() {
        setupDefaultGame()
        assertEquals(GameState.PLAYING, engine.gameState)
    }

    @Test
    fun `セットアップで16マスに裏向きタイルが配置される`() {
        setupDefaultGame()

        var tileCount = 0
        for (row in 0 until Board.ROWS) {
            for (col in 0 until Board.COLS) {
                val position = Position(col, row)
                val cell = engine.board.getCell(position) ?: continue
                if (cell.type == CellType.UNDERGROUND || cell.type == CellType.HOT_ZONE) {
                    if (engine.boardState.hasTile(position)) {
                        assertTrue(engine.boardState.isFaceDown(position))
                        tileCount++
                    }
                }
            }
        }

        assertEquals(16, tileCount)
    }

    @Test
    fun `セットアップで山札は10枚`() {
        setupDefaultGame()
        assertEquals(10, engine.tilePlacementEngine.drawPile.size)
    }

    @Test
    fun `セットアップでエサ山と捨て札の公開枚数を取得できる`() {
        setupDefaultGame()

        assertEquals(8, engine.foodStockCount)
        assertEquals(0, engine.foodDiscardCount)
    }

    @Test
    fun `セットアップでホットゾーン4マスにエサが配置される`() {
        setupDefaultGame()
        Board.HOT_ZONE_POSITIONS.forEach { position ->
            assertTrue(position in engine.foodPositions, "ホットゾーン $position にエサがない")
        }
    }

    @Test
    fun `最初のフェーズはDIG`() {
        setupDefaultGame()
        assertEquals(TurnPhase.DIG, engine.currentPhase)
    }

    @Test
    fun `フェーズはDIGからMOVE CAPTURE ENDへ遷移する`() {
        setupDefaultGame()

        assertEquals(TurnPhase.DIG, engine.currentPhase)
        engine.advancePhase()
        assertEquals(TurnPhase.MOVE, engine.currentPhase)
        engine.advancePhase()
        assertEquals(TurnPhase.CAPTURE, engine.currentPhase)
        engine.advancePhase()
        assertEquals(TurnPhase.END, engine.currentPhase)
    }

    @Test
    fun `カブトムシの幼虫は確定捕獲`() {
        val food = FoodCard(FoodType.BEETLE_LARVA, emptyMap())
        val result = engine.attemptCapture(food)
        assertTrue(result is CaptureResult.Success)
    }

    @Test
    fun `逃走目以外なら捕獲成功`() {
        val food = FoodCard.createDummyCards(FoodType.EARTHWORM).first()
        val captureEngine = GameEngine(
            playerCount = 2,
            diceRoller = FixedDiceRoller(listOf(6)),
            shuffler = shuffler,
        )

        val result = captureEngine.attemptCapture(food)
        assertTrue(result is CaptureResult.Success)
    }

    @Test
    fun `逃走目が出たら逃走`() {
        val food = FoodCard.createDummyCards(FoodType.EARTHWORM).first()
        val captureEngine = GameEngine(
            playerCount = 2,
            diceRoller = FixedDiceRoller(listOf(1)),
            shuffler = shuffler,
        )

        val result = captureEngine.attemptCapture(food)
        assertTrue(result is CaptureResult.Escaped)
        assertEquals(EscapeDirection.TOP, (result as CaptureResult.Escaped).direction)
    }

    @Test
    fun `ターン終了時に地下で体力1減少`() {
        setupDefaultGame()
        val player = engine.players[0]
        val initialHealth = player.health

        engine.endTurn()
        assertEquals(initialHealth - 1, player.health)
    }

    @Test
    fun `ターン終了時に地上で体力2減少`() {
        setupDefaultGame()
        val player = engine.players[0]
        player.moveTo(Position(0, 0))
        val initialHealth = player.health

        engine.endTurn()
        assertEquals(initialHealth - 2, player.health)
    }

    @Test
    fun `体力0でプレイヤー脱落`() {
        setupDefaultGame()
        val player = engine.players[0]

        repeat(12) { player.reduceHealth(isOnSurface = false) }
        assertEquals(1, player.health)

        engine.endTurn()
        assertEquals(0, player.health)
        assertTrue(player.isEliminated)
    }

    @Test
    fun `最後の生存プレイヤーが勝利する`() {
        setupDefaultGame()
        val eliminated = engine.players[0]
        val survivor = engine.players[1]

        repeat(13) { eliminated.reduceHealth(isOnSurface = false) }

        assertEquals(survivor, engine.checkWinCondition())
        assertEquals(GameState.FINISHED, engine.checkGameOver())
    }

    @Test
    fun `2人プレイで4点先取で勝利`() {
        setupDefaultGame()
        val player = engine.players[0]

        repeat(4) {
            player.carryFood(FoodCard(FoodType.BEETLE_LARVA, emptyMap()))
            player.storeFood()
        }

        assertEquals(player, engine.checkWinCondition())
    }

    @Test
    fun `4人プレイで5点先取で勝利`() {
        val engine4p = GameEngine(
            playerCount = 4,
            diceRoller = diceRoller,
            shuffler = shuffler,
        )

        engine4p.setupGame(
            listOf(
                PlayerConfig("モグオ", Position(0, 1)),
                PlayerConfig("モグタ", Position(5, 1)),
                PlayerConfig("モグミ", Position(0, 4)),
                PlayerConfig("モグカ", Position(5, 4)),
            ),
        )

        val player = engine4p.players[0]
        repeat(4) {
            player.carryFood(FoodCard(FoodType.BEETLE_LARVA, emptyMap()))
            player.storeFood()
        }
        assertNull(engine4p.checkWinCondition())

        player.carryFood(FoodCard(FoodType.BEETLE_LARVA, emptyMap()))
        player.storeFood()
        assertEquals(player, engine4p.checkWinCondition())
    }

    @Test
    fun `全員脱落でドロー`() {
        setupDefaultGame()
        engine.players.forEach { player ->
            repeat(13) { player.reduceHealth(isOnSurface = false) }
        }

        assertTrue(engine.players.all { it.isEliminated })
        assertNull(engine.checkWinCondition())
        assertEquals(GameState.FINISHED, engine.checkGameOver())
    }

    @Test
    fun `ホットゾーンが空になったらエサを補充`() {
        setupDefaultGame()
        Board.HOT_ZONE_POSITIONS.forEach { position ->
            engine.removeFoodAt(position)
        }
        assertTrue(engine.shouldReplenishFood())

        engine.replenishFood()
        Board.HOT_ZONE_POSITIONS.forEach { position ->
            assertTrue(position in engine.foodPositions, "補充後のホットゾーン $position にエサがない")
        }
    }

    @Test
    fun `食べたエサはエサ捨て札の公開枚数に反映される`() {
        setupDefaultGame()
        val food = FoodCard(FoodType.EARTHWORM, emptyMap(), isFaceDown = false)

        engine.discardFood(food)

        assertEquals(1, engine.foodDiscardCount)
    }

    @Test
    fun `相手の巣が留守の時にエサを奪える`() {
        // TODO: 【要確認】3-3 強奪フェーズは仮実装。
        setupDefaultGame()
        val thief = engine.players[0]
        val victim = engine.players[1]

        victim.carryFood(FoodCard(FoodType.EARTHWORM, emptyMap()))
        victim.storeFood()
        victim.moveTo(Position(1, 1))
        thief.moveTo(victim.nestPosition)

        val stolen = engine.attemptRobbery(thief, victim)
        assertNotNull(stolen)
        assertFalse(thief.isCarrying)
        assertTrue(victim.storedFoods.isEmpty())
    }

    @Test
    fun `相手が巣にいる場合は奪えない`() {
        setupDefaultGame()
        val thief = engine.players[0]
        val victim = engine.players[1]

        victim.carryFood(FoodCard(FoodType.EARTHWORM, emptyMap()))
        victim.storeFood()
        thief.moveTo(victim.nestPosition)

        val stolen = engine.attemptRobbery(thief, victim)
        assertNull(stolen)
    }

    @Test
    fun `自分の巣にいる場合は巣が防衛される`() {
        // TODO: 【要確認】3-4 巣防衛の詳細は仮実装。
        setupDefaultGame()
        val owner = engine.players[0]
        assertTrue(engine.isNestDefended(owner.nestPosition))
    }

    @Test
    fun `巣に戻った時に侵入者を追い出す`() {
        setupDefaultGame()
        val owner = engine.players[0]
        val intruder = engine.players[1]

        owner.moveTo(Position(1, 1))
        intruder.moveTo(owner.nestPosition)

        val evicted = engine.evictFromNest(owner)
        assertTrue(evicted)
        assertNotEquals(owner.nestPosition, intruder.position)
    }

    @Test
    fun `逃走方向が盤外なら捕獲成功`() {
        val edgeEngine = GameEngine(
            playerCount = 2,
            diceRoller = FixedDiceRoller(listOf(1)),
            shuffler = shuffler,
        )
        edgeEngine.setupGame(defaultConfigs())

        val foodPos = Position(0, 0)
        val food = FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.LEFT))
        edgeEngine.placeFoodAt(foodPos, food)

        val result = edgeEngine.attemptCaptureAt(foodPos)
        assertTrue(result is CaptureResult.Success, "盤外への逃走は捕獲成功になるべき")
    }

    @Test
    fun `逃走方向が有効マスなら逃走`() {
        val escapeEngine = GameEngine(
            playerCount = 2,
            diceRoller = FixedDiceRoller(listOf(1)),
            shuffler = shuffler,
        )
        escapeEngine.setupGame(defaultConfigs())

        val foodPos = Position(3, 2)
        val escapeTo = Position(4, 2)
        val food = FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.RIGHT))
        replaceFoodAt(escapeEngine, foodPos, food)
        placeFaceUpTile(escapeEngine, foodPos, Direction.RIGHT)
        placeFaceUpTile(escapeEngine, escapeTo, Direction.LEFT)

        val result = escapeEngine.attemptCaptureAt(foodPos)
        assertTrue(result is CaptureResult.Escaped, "有効マスへの逃走は成功するべき")
    }

    @Test
    fun `逃走先に別のエサがある場合も逃走成功しエサが重なる`() {
        val occupiedEngine = GameEngine(
            playerCount = 2,
            diceRoller = FixedDiceRoller(listOf(1)),
            shuffler = shuffler,
        )
        occupiedEngine.setupGame(defaultConfigs())

        val targetFood = FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.RIGHT), isFaceDown = false)
        val blockingFood = FoodCard(FoodType.MOLE_CRICKET, emptyMap(), isFaceDown = false)
        replaceFoodAt(occupiedEngine, Position(2, 2), targetFood)
        replaceFoodAt(occupiedEngine, Position(3, 2), blockingFood)
        placeFaceUpTile(occupiedEngine, Position(2, 2), Direction.RIGHT)
        placeFaceUpTile(occupiedEngine, Position(3, 2), Direction.LEFT)

        val result = occupiedEngine.attemptCaptureAt(Position(2, 2))
        assertTrue(result is CaptureResult.Escaped, "逃走先にエサがある場合も逃走できるべき")

        assertNull(occupiedEngine.foodPositions[Position(2, 2)], "逃走したエサは元の位置から消えるべき")
        val stack = occupiedEngine.foodsAt(Position(3, 2))
        assertEquals(
            listOf(FoodType.MOLE_CRICKET, FoodType.EARTHWORM),
            stack.map { it.type },
            "逃走先の既存エサを残したまま逃走エサが追加されるべき",
        )
    }

    @Test
    fun `同じマスに複数のエサを保持できる`() {
        setupDefaultGame()
        val position = Position(2, 2)
        while (engine.removeFoodAt(position) != null) {
            // Start with an empty stack for this cell.
        }

        engine.placeFoodAt(position, FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = false))
        engine.placeFoodAt(position, FoodCard(FoodType.EARTHWORM, emptyMap(), isFaceDown = false))

        assertEquals(
            listOf(FoodType.BEETLE_LARVA, FoodType.EARTHWORM),
            engine.foodsAt(position).map { it.type },
        )
    }

    @Test
    fun `逃走先が空マスなら逃走成功しエサが移動する`() {
        val escapeEngine = GameEngine(
            playerCount = 2,
            diceRoller = FixedDiceRoller(listOf(1)),
            shuffler = shuffler,
        )
        escapeEngine.setupGame(defaultConfigs())

        val food = FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.RIGHT), isFaceDown = false)
        replaceFoodAt(escapeEngine, Position(2, 2), food)
        escapeEngine.removeFoodAt(Position(3, 2))
        placeFaceUpTile(escapeEngine, Position(2, 2), Direction.RIGHT)
        placeFaceUpTile(escapeEngine, Position(3, 2), Direction.LEFT)

        val result = escapeEngine.attemptCaptureAt(Position(2, 2))
        assertTrue(result is CaptureResult.Escaped, "逃走先が空なら逃走になるべき")
        assertNull(escapeEngine.foodPositions[Position(2, 2)], "元の位置からエサが消えるべき")
        assertNotNull(escapeEngine.foodPositions[Position(3, 2)], "逃走先にエサが移動するべき")
    }

    @Test
    fun `出目を指定した捕獲判定はダイスローラーを使わない`() {
        val escapeEngine = GameEngine(
            playerCount = 2,
            diceRoller = FixedDiceRoller(listOf(6)),
            shuffler = shuffler,
        )
        escapeEngine.setupGame(defaultConfigs())

        val food = FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.RIGHT), isFaceDown = false)
        replaceFoodAt(escapeEngine, Position(2, 2), food)
        escapeEngine.removeFoodAt(Position(3, 2))
        placeFaceUpTile(escapeEngine, Position(2, 2), Direction.RIGHT)
        placeFaceUpTile(escapeEngine, Position(3, 2), Direction.LEFT)

        val result = escapeEngine.attemptCaptureAt(Position(2, 2), roll = 1)
        assertTrue(result is CaptureResult.Escaped, "指定した出目1で逃走になるべき（ローラーの6は無視）")
        assertEquals(1, (result as CaptureResult.Escaped).diceRoll)
        assertNull(escapeEngine.foodPositions[Position(2, 2)], "元の位置からエサが消えるべき")
        assertNotNull(escapeEngine.foodPositions[Position(3, 2)], "逃走先にエサが移動するべき")
    }

    @Test
    fun `出目を指定した捕獲判定で逃走目以外なら捕獲成功`() {
        val captureEngine = GameEngine(
            playerCount = 2,
            diceRoller = FixedDiceRoller(listOf(1)),
            shuffler = shuffler,
        )
        captureEngine.setupGame(defaultConfigs())

        val food = FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.RIGHT), isFaceDown = false)
        replaceFoodAt(captureEngine, Position(2, 2), food)

        val result = captureEngine.attemptCaptureAt(Position(2, 2), roll = 6)
        assertTrue(result is CaptureResult.Success, "指定した出目6は逃走目ではないので捕獲成功")
        assertEquals(6, (result as CaptureResult.Success).diceRoll)
    }

    @Test
    fun `出目を指定しても逃走しないエサは確定捕獲`() {
        setupDefaultGame()
        val food = FoodCard(FoodType.BEETLE_LARVA, emptyMap(), isFaceDown = false)
        replaceFoodAt(engine, Position(2, 2), food)

        val result = engine.attemptCaptureAt(Position(2, 2), roll = 3)
        assertTrue(result is CaptureResult.Success)
    }

    @Test
    fun `出目は1から6の範囲外なら例外`() {
        setupDefaultGame()
        val food = FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.RIGHT), isFaceDown = false)
        replaceFoodAt(engine, Position(2, 2), food)

        assertThrows(IllegalArgumentException::class.java) {
            engine.attemptCaptureAt(Position(2, 2), roll = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.attemptCaptureAt(Position(2, 2), roll = 7)
        }
    }

    @Test
    fun `ホットゾーンに表向きエサがある場合も補充される`() {
        setupDefaultGame()
        Board.HOT_ZONE_POSITIONS.forEach { position ->
            val food = engine.removeFoodAt(position)
            if (food != null) {
                engine.placeFoodAt(position, food.copy(isFaceDown = false))
            }
        }

        assertTrue(engine.shouldReplenishFood())

        engine.replenishFood()
        Board.HOT_ZONE_POSITIONS.forEach { position ->
            val food = engine.foodAt(position)
            assertNotNull(food, "補充後のホットゾーン $position にエサがない")
            assertTrue(food!!.isFaceDown, "補充されたエサは裏向きであるべき")
        }
    }

    @Test
    fun `セットアップ前にcheckGameOverを呼んでもFINISHEDにならない`() {
        val freshEngine = GameEngine(
            playerCount = 2,
            diceRoller = diceRoller,
            shuffler = shuffler,
        )

        val state = freshEngine.checkGameOver()
        assertEquals(GameState.SETUP, state, "セットアップ前はSETUPのままであるべき")
    }

    @Test
    fun `placeFoodAt rejects food on nest cells`() {
        setupDefaultGame()
        val nest = Position(0, 1)

        assertThrows(IllegalArgumentException::class.java) {
            engine.placeFoodAt(nest, FoodCard(FoodType.BEETLE_LARVA, emptyMap()))
        }

        assertNull(engine.foodPositions[nest])
    }

    @Test
    fun `placeFoodAt rejects invalid and off-board cells`() {
        setupDefaultGame()
        val invalid = Position(0, 2)
        val offBoard = Position(-1, 0)
        val food = FoodCard(FoodType.BEETLE_LARVA, emptyMap())

        assertThrows(IllegalArgumentException::class.java) {
            engine.placeFoodAt(invalid, food)
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.placeFoodAt(offBoard, food)
        }

        assertNull(engine.foodPositions[invalid])
        assertNull(engine.foodPositions[offBoard])
    }

    @Test
    fun `escape into ground cell without tile path is captured`() {
        val escapeEngine = GameEngine(
            playerCount = 2,
            diceRoller = FixedDiceRoller(listOf(1)),
            shuffler = shuffler,
        )
        escapeEngine.setupGame(defaultConfigs())

        val foodPosition = Position(1, 1)
        val escapeTo = Position(1, 0)
        val food = FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.TOP), isFaceDown = false)
        placeFaceUpTile(escapeEngine, foodPosition, Direction.TOP)
        replaceFoodAt(escapeEngine, foodPosition, food)

        val result = escapeEngine.attemptCaptureAt(foodPosition)

        assertTrue(result is CaptureResult.Success, "escape into a ground cell without a tile path should be captured")
        assertNotNull(escapeEngine.foodPositions[foodPosition], "food should remain at the capture source")
        assertNull(escapeEngine.foodPositions[escapeTo], "food should not move into an unreachable ground cell")
    }

    @Test
    fun `escape into face-up tile without connected openings is captured`() {
        val escapeEngine = GameEngine(
            playerCount = 2,
            diceRoller = FixedDiceRoller(listOf(1)),
            shuffler = shuffler,
        )
        escapeEngine.setupGame(defaultConfigs())

        val foodPosition = Position(2, 2)
        val escapeTo = Position(3, 2)
        val food = FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.RIGHT), isFaceDown = false)
        escapeEngine.removeFoodAt(escapeTo)
        placeFaceUpTile(escapeEngine, foodPosition, Direction.RIGHT)
        placeFaceUpTile(escapeEngine, escapeTo, Direction.RIGHT)
        replaceFoodAt(escapeEngine, foodPosition, food)

        val result = escapeEngine.attemptCaptureAt(foodPosition)

        assertTrue(result is CaptureResult.Success, "escape without connected tile openings should be captured")
        assertNotNull(escapeEngine.foodPositions[foodPosition], "food should remain at the capture source")
        assertNull(escapeEngine.foodPositions[escapeTo], "food should not move into an unreachable tile")
    }

    @Test
    fun `escape into a nest is captured and does not place food on the nest`() {
        val escapeEngine = GameEngine(
            playerCount = 2,
            diceRoller = FixedDiceRoller(listOf(1)),
            shuffler = shuffler,
        )
        escapeEngine.setupGame(defaultConfigs())

        val foodPosition = Position(1, 1)
        val nestPosition = Position(0, 1)
        val food = FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.LEFT), isFaceDown = false)
        replaceFoodAt(escapeEngine, foodPosition, food)

        val result = escapeEngine.attemptCaptureAt(foodPosition)

        assertTrue(result is CaptureResult.Success, "food cannot escape into a nest")
        assertNotNull(escapeEngine.foodPositions[foodPosition], "food should remain at the capture source")
        assertNull(escapeEngine.foodPositions[nestPosition], "nest cells must not receive escaped food")
    }

    @Test
    fun `diagonal escape can move to a valid empty cell`() {
        data class DiagonalEscapeCase(
            val type: FoodType,
            val roll: Int,
            val direction: EscapeDirection,
            val from: Position,
            val to: Position,
        )

        val cases = listOf(
            DiagonalEscapeCase(FoodType.MOLE_CRICKET, 3, EscapeDirection.TOP_RIGHT, Position(2, 2), Position(3, 1)),
            DiagonalEscapeCase(FoodType.CENTIPEDE, 3, EscapeDirection.BOTTOM_LEFT, Position(3, 2), Position(2, 3)),
        )

        cases.forEach { case ->
            val escapeEngine = GameEngine(
                playerCount = 2,
                diceRoller = FixedDiceRoller(listOf(case.roll)),
                shuffler = shuffler,
            )
            escapeEngine.setupGame(defaultConfigs())
            escapeEngine.removeFoodAt(case.to)
            replaceFoodAt(
                escapeEngine,
                case.from,
                FoodCard(case.type, mapOf(case.roll to case.direction), isFaceDown = false),
            )

            val result = escapeEngine.attemptCaptureAt(case.from)

            assertTrue(result is CaptureResult.Escaped, "${case.direction} should escape to a valid empty cell")
            assertEquals(case.direction, (result as CaptureResult.Escaped).direction)
            assertNull(escapeEngine.foodPositions[case.from], "food should leave the source cell")
            assertNotNull(escapeEngine.foodPositions[case.to], "food should move to the diagonal escape cell")
        }
    }

    @Test
    fun `replenish recycles face-up hot-zone food when stock and discard are empty`() {
        setupDefaultGame()

        repeat(2) {
            Board.HOT_ZONE_POSITIONS.forEach { position ->
                engine.removeFoodAt(position)
            }
            engine.replenishFood()
        }
        assertEquals(0, engine.foodStockCount)
        assertEquals(0, engine.foodDiscardCount)

        Board.HOT_ZONE_POSITIONS.forEach { position ->
            val food = engine.removeFoodAt(position)
            if (food != null) {
                engine.placeFoodAt(position, food.copy(isFaceDown = false))
            }
        }
        assertTrue(engine.shouldReplenishFood())

        engine.replenishFood()

        Board.HOT_ZONE_POSITIONS.forEach { position ->
            val food = engine.foodAt(position)
            assertNotNull(food, "hot-zone food should be recycled back into $position")
            assertTrue(food!!.isFaceDown, "recycled hot-zone food should be face down")
        }
    }

    private fun setupDefaultGame() {
        engine.setupGame(defaultConfigs())
    }

    private fun placeFaceUpTile(engine: GameEngine, position: Position, vararg openSides: Direction) {
        engine.boardState.placeTile(
            position,
            HoleTile(TileShape.CROSS, openSides.toSet(), isFaceDown = false),
        )
    }

    private fun replaceFoodAt(engine: GameEngine, position: Position, food: FoodCard) {
        while (engine.removeFoodAt(position) != null) {
            // Remove the previous stack so this test controls the capture target.
        }
        engine.placeFoodAt(position, food)
    }

    private fun defaultConfigs(): List<PlayerConfig> = listOf(
        PlayerConfig("モグオ", Position(0, 1)),
        PlayerConfig("モグタ", Position(5, 1)),
    )
}
