package com.moguru.game.engine

import com.moguru.game.model.*
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class GameEngineTest {

    private lateinit var engine: GameEngine
    private val shuffler = FixedShuffler()
    private val diceRoller = FixedDiceRoller(listOf(6)) // デフォルトで捕獲成功（6は逃走目に含まれにくい）

    @BeforeEach
    fun setup() {
        engine = GameEngine(
            playerCount = 2,
            diceRoller = diceRoller,
            shuffler = shuffler,
        )
    }

    // === セットアップ ===

    @Test
    fun `初期状態はSETUP`() {
        assertEquals(GameState.SETUP, engine.gameState)
    }

    @Test
    fun `セットアップ後の状態はPLAYING`() {
        engine.setupGame(
            listOf(
                PlayerConfig("モグオ", Position(0, 1)),
                PlayerConfig("モグタ", Position(5, 1)),
            )
        )
        assertEquals(GameState.PLAYING, engine.gameState)
    }

    @Test
    fun `セットアップで16マスに裏向きタイルが配置される`() {
        engine.setupGame(
            listOf(
                PlayerConfig("モグオ", Position(0, 1)),
                PlayerConfig("モグタ", Position(5, 1)),
            )
        )
        val board = engine.board
        val boardState = engine.boardState

        // 巣と地上を除いた16マスにタイルが配置されているか
        var tileCount = 0
        for (row in 0 until Board.ROWS) {
            for (col in 0 until Board.COLS) {
                val pos = Position(col, row)
                val cell = board.getCell(pos) ?: continue
                if (cell.type == CellType.UNDERGROUND || cell.type == CellType.HOT_ZONE) {
                    if (boardState.hasTile(pos)) {
                        assertTrue(boardState.isFaceDown(pos))
                        tileCount++
                    }
                }
            }
        }
        assertEquals(16, tileCount)
    }

    @Test
    fun `セットアップで山札は10枚`() {
        engine.setupGame(
            listOf(
                PlayerConfig("モグオ", Position(0, 1)),
                PlayerConfig("モグタ", Position(5, 1)),
            )
        )
        assertEquals(10, engine.tilePlacementEngine.drawPile.size)
    }

    @Test
    fun `セットアップでホットゾーン4マスにエサが配置される`() {
        engine.setupGame(
            listOf(
                PlayerConfig("モグオ", Position(0, 1)),
                PlayerConfig("モグタ", Position(5, 1)),
            )
        )
        val foodPositions = engine.foodPositions
        Board.HOT_ZONE_POSITIONS.forEach { pos ->
            assertTrue(pos in foodPositions, "ホットゾーン $pos にエサがない")
        }
    }

    // === ターンフェーズ ===

    @Test
    fun `最初のフェーズはDIG`() {
        setupDefaultGame()
        assertEquals(TurnPhase.DIG, engine.currentPhase)
    }

    @Test
    fun `フェーズはDIG→MOVE→CAPTURE→ENDの順に遷移する`() {
        setupDefaultGame()

        assertEquals(TurnPhase.DIG, engine.currentPhase)
        engine.advancePhase() // DIG → MOVE
        assertEquals(TurnPhase.MOVE, engine.currentPhase)
        engine.advancePhase() // MOVE → CAPTURE
        assertEquals(TurnPhase.CAPTURE, engine.currentPhase)
        engine.advancePhase() // CAPTURE → END（捕獲しないのでDECIDEをスキップ）
        assertEquals(TurnPhase.END, engine.currentPhase)
    }

    // === 捕獲判定 ===

    @Test
    fun `カブトムシの幼虫は確定捕獲`() {
        val food = FoodCard(FoodType.BEETLE_LARVA, emptyMap())
        val result = engine.attemptCapture(food)
        assertTrue(result is CaptureResult.Success)
    }

    @Test
    fun `逃走目以外なら捕獲成功`() {
        // ミミズ: 1,2で逃走。ダイスが6なら成功
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
        // ミミズ: 1でTOP方向に逃走
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

    // === 体力管理 ===

    @Test
    fun `ターン終了時に地下で体力1減少`() {
        setupDefaultGame()
        val player = engine.players[0]
        val initialHealth = player.health

        // プレイヤーは巣（地下扱い）にいる
        engine.endTurn()
        assertEquals(initialHealth - 1, player.health)
    }

    @Test
    fun `ターン終了時に地上で体力2減少`() {
        setupDefaultGame()
        val player = engine.players[0]
        player.moveTo(Position(0, 0)) // 地上に移動
        val initialHealth = player.health

        engine.endTurn()
        assertEquals(initialHealth - 2, player.health)
    }

    @Test
    fun `体力0でプレイヤー脱落`() {
        setupDefaultGame()
        val player = engine.players[0]
        // 体力を1にする
        repeat(12) { player.reduceHealth(isOnSurface = false) }
        assertEquals(1, player.health)

        engine.endTurn()
        assertEquals(0, player.health)
        assertTrue(player.isEliminated)
    }

    // === 勝利条件 ===

    @Test
    fun `2〜3人プレイで4点先取で勝利`() {
        setupDefaultGame()
        val player = engine.players[0]
        // 4点分のエサを貯蔵
        repeat(4) {
            player.carryFood(FoodCard(FoodType.BEETLE_LARVA, emptyMap()))
            player.storeFood()
        }
        val winner = engine.checkWinCondition()
        assertEquals(player, winner)
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
            )
        )
        val player = engine4p.players[0]
        // 4点ではまだ未勝利
        repeat(4) {
            player.carryFood(FoodCard(FoodType.BEETLE_LARVA, emptyMap()))
            player.storeFood()
        }
        assertNull(engine4p.checkWinCondition())

        // 5点で勝利
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

    // === エサ補充 ===

    @Test
    fun `ホットゾーンが空になったらエサを補充`() {
        setupDefaultGame()
        // ホットゾーンのエサを全て除去
        Board.HOT_ZONE_POSITIONS.forEach { pos ->
            engine.removeFoodAt(pos)
        }
        assertTrue(engine.shouldReplenishFood())

        engine.replenishFood()
        Board.HOT_ZONE_POSITIONS.forEach { pos ->
            assertTrue(pos in engine.foodPositions, "補充後のホットゾーン $pos にエサがない")
        }
    }

    // === 強奪 ===

    @Test
    fun `相手の巣が留守の時にエサを奪える`() {
        // TODO: 【要確認】13-3 強奪はターンのどのフェーズで行うか（仮: 捕獲フェーズ）
        setupDefaultGame()
        val thief = engine.players[0]
        val victim = engine.players[1]
        val nestPos = victim.nestPosition

        // victimの巣にエサを貯蔵
        victim.carryFood(FoodCard(FoodType.EARTHWORM, emptyMap()))
        victim.storeFood()

        // victimが巣にいない（別の場所にいる）
        victim.moveTo(Position(1, 1))

        // thiefが相手の巣に移動
        thief.moveTo(nestPos)

        val stolen = engine.attemptRobbery(thief, victim)
        assertNotNull(stolen)
        assertTrue(thief.isCarrying)
    }

    @Test
    fun `相手が巣にいる場合は奪えない`() {
        setupDefaultGame()
        val thief = engine.players[0]
        val victim = engine.players[1]

        victim.carryFood(FoodCard(FoodType.EARTHWORM, emptyMap()))
        victim.storeFood()

        // victimが巣にいる
        // (victimは初期位置が巣)

        thief.moveTo(victim.nestPosition)
        val stolen = engine.attemptRobbery(thief, victim)
        assertNull(stolen)
    }

    // === 巣の防衛 ===

    @Test
    fun `自分が巣にいる場合は他プレイヤーが止まれない`() {
        // TODO: 【要確認】13-4 止まれないだけ（通過は可能）で仮実装
        setupDefaultGame()
        val owner = engine.players[0] // 巣(0,1)にいる
        val intruder = engine.players[1]

        // ownerが巣にいるので、intruderはそのマスに停止不可
        assertTrue(engine.isNestDefended(owner.nestPosition))
    }

    @Test
    fun `巣に戻った時に侵入者を追い出す`() {
        setupDefaultGame()
        val owner = engine.players[0]
        val intruder = engine.players[1]

        // ownerが巣を離れる
        owner.moveTo(Position(1, 1))
        // intruderが巣に侵入
        intruder.moveTo(owner.nestPosition)

        // ownerが巣に戻る→intruderを追い出す
        // TODO: 【要確認】13-5 追い出し先は追い出す側が選ぶ（仮実装）
        val evicted = engine.evictFromNest(owner)
        assertTrue(evicted)
        assertNotEquals(owner.nestPosition, intruder.position)
    }

    // === P1: 逃走先が盤外なら捕獲成功 ===

    @Test
    fun `逃走方向が盤外を指す場合は捕獲成功`() {
        setupDefaultGame()
        // エサを盤面端(0,0)に配置。逃走方向LEFT→(-1,0)は盤外→捕獲成功
        val food = FoodCard(
            FoodType.EARTHWORM,
            mapOf(1 to EscapeDirection.LEFT),
        )
        val edgeEngine = GameEngine(
            playerCount = 2,
            diceRoller = FixedDiceRoller(listOf(1)),
            shuffler = shuffler,
        )
        edgeEngine.setupGame(
            listOf(
                PlayerConfig("モグオ", Position(0, 1)),
                PlayerConfig("モグタ", Position(5, 1)),
            )
        )
        val foodPos = Position(0, 0)
        edgeEngine.placeFoodAt(foodPos, food)

        val result = edgeEngine.attemptCaptureAt(foodPos)
        assertTrue(result is CaptureResult.Success, "盤外への逃走は捕獲成功になるべき")
    }

    @Test
    fun `逃走方向が有効マスを指す場合は逃走`() {
        setupDefaultGame()
        // エサを(3,2)に配置。逃走方向RIGHT→(4,2)は有効マス→逃走
        val food = FoodCard(
            FoodType.EARTHWORM,
            mapOf(1 to EscapeDirection.RIGHT),
        )
        val escapeEngine = GameEngine(
            playerCount = 2,
            diceRoller = FixedDiceRoller(listOf(1)),
            shuffler = shuffler,
        )
        escapeEngine.setupGame(
            listOf(
                PlayerConfig("モグオ", Position(0, 1)),
                PlayerConfig("モグタ", Position(5, 1)),
            )
        )
        val foodPos = Position(3, 2)
        escapeEngine.placeFoodAt(foodPos, food)

        val result = escapeEngine.attemptCaptureAt(foodPos)
        assertTrue(result is CaptureResult.Escaped, "有効マスへの逃走は逃走になるべき")
    }

    // === 逃走先に既存エサがある場合は逃走不可（捕獲成功） ===

    @Test
    fun `逃走先に別のエサがある場合は逃走不可で捕獲成功`() {
        val occupiedEngine = GameEngine(
            playerCount = 2,
            diceRoller = FixedDiceRoller(listOf(1)),
            shuffler = shuffler,
        )
        occupiedEngine.setupGame(
            listOf(
                PlayerConfig("モグオ", Position(0, 1)),
                PlayerConfig("モグタ", Position(5, 1)),
            )
        )
        // (2,2)にエサ配置。逃走方向RIGHT→(3,2)
        val targetFood = FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.RIGHT), isFaceDown = false)
        val blockingFood = FoodCard(FoodType.MOLE_CRICKET, emptyMap(), isFaceDown = false)
        occupiedEngine.placeFoodAt(Position(2, 2), targetFood)
        occupiedEngine.placeFoodAt(Position(3, 2), blockingFood)

        val result = occupiedEngine.attemptCaptureAt(Position(2, 2))
        // 逃走先にエサがあるので逃走不可→捕獲成功
        assertTrue(result is CaptureResult.Success, "逃走先にエサがある場合は捕獲成功になるべき")
        // blockingFoodが消えていないこと
        val remaining = occupiedEngine.foodPositions[Position(3, 2)]
        assertNotNull(remaining, "逃走先の既存エサが消えてはならない")
        assertEquals(FoodType.MOLE_CRICKET, remaining!!.type, "逃走先の既存エサが上書きされてはならない")
    }

    @Test
    fun `逃走先が空きマスなら逃走成功しエサが移動する`() {
        val escEngine = GameEngine(
            playerCount = 2,
            diceRoller = FixedDiceRoller(listOf(1)),
            shuffler = shuffler,
        )
        escEngine.setupGame(
            listOf(
                PlayerConfig("モグオ", Position(0, 1)),
                PlayerConfig("モグタ", Position(5, 1)),
            )
        )
        val food = FoodCard(FoodType.EARTHWORM, mapOf(1 to EscapeDirection.RIGHT), isFaceDown = false)
        escEngine.placeFoodAt(Position(2, 2), food)
        // (3,2)にエサがないことを確認
        escEngine.removeFoodAt(Position(3, 2))

        val result = escEngine.attemptCaptureAt(Position(2, 2))
        assertTrue(result is CaptureResult.Escaped, "逃走先が空なら逃走になるべき")
        assertNull(escEngine.foodPositions[Position(2, 2)], "元の位置からエサが消えるべき")
        assertNotNull(escEngine.foodPositions[Position(3, 2)], "逃走先にエサが移動するべき")
    }

    // === P2: 表向きエサがあるホットゾーンも補充対象 ===

    @Test
    fun `ホットゾーンに表向きエサがある場合も補充される`() {
        setupDefaultGame()
        // ホットゾーンのエサを全て表向きにする（捕獲試行後の状態を模擬）
        Board.HOT_ZONE_POSITIONS.forEach { pos ->
            val food = engine.removeFoodAt(pos)
            if (food != null) {
                engine.placeFoodAt(pos, food.copy(isFaceDown = false))
            }
        }
        // 裏向きエサが0枚なので補充トリガー
        assertTrue(engine.shouldReplenishFood())

        engine.replenishFood()
        // 全ホットゾーンに裏向きエサが配置される
        Board.HOT_ZONE_POSITIONS.forEach { pos ->
            val food = engine.foodPositions[pos]
            assertNotNull(food, "補充後のホットゾーン $pos にエサがない")
            assertTrue(food!!.isFaceDown, "補充されたエサは裏向きであるべき")
        }
    }

    // === P2: セットアップ前のcheckGameOverでFINISHEDにならない ===

    @Test
    fun `セットアップ前にcheckGameOverを呼んでもFINISHEDにならない`() {
        // playersが空のときにall { isEliminated }がtrue（vacuous truth）にならないこと
        val freshEngine = GameEngine(
            playerCount = 2,
            diceRoller = diceRoller,
            shuffler = shuffler,
        )
        val state = freshEngine.checkGameOver()
        assertEquals(GameState.SETUP, state, "セットアップ前はSETUPのままであるべき")
    }

    // === ヘルパー ===

    private fun setupDefaultGame() {
        engine.setupGame(
            listOf(
                PlayerConfig("モグオ", Position(0, 1)),
                PlayerConfig("モグタ", Position(5, 1)),
            )
        )
    }
}
