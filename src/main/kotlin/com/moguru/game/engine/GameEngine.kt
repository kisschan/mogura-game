package com.moguru.game.engine

import com.moguru.game.model.*
import com.moguru.game.util.DiceRoller
import com.moguru.game.util.Shuffler

/**
 * ターンのフェーズ
 */
enum class TurnPhase {
    DIG,      // ① 掘る
    MOVE,     // ② いどう
    CAPTURE,  // ③ 捕獲（任意）
    DECIDE,   // ④ タベる or レンコウ（捕獲成功時のみ）
    END,      // ターン終了（体力減少処理）
}

/**
 * ゲーム全体の状態
 */
enum class GameState {
    SETUP,
    PLAYING,
    FINISHED,
}

/**
 * 捕獲結果
 */
sealed class CaptureResult {
    data object Success : CaptureResult()
    data class Escaped(val direction: EscapeDirection, val diceRoll: Int) : CaptureResult()
}

/**
 * プレイヤー設定（セットアップ時に使用）
 */
data class PlayerConfig(val name: String, val nestPosition: Position)

/**
 * ターン進行管理・ゲーム状態を管理するエンジン。
 */
class GameEngine(
    val playerCount: Int,
    private val diceRoller: DiceRoller,
    private val shuffler: Shuffler,
) {
    val board = Board()
    val boardState = BoardState(board)
    val movementEngine = MovementEngine(board)
    val tilePlacementEngine = TilePlacementEngine(shuffler)

    var gameState: GameState = GameState.SETUP
        private set

    var currentPhase: TurnPhase = TurnPhase.DIG
        private set

    var currentPlayerIndex: Int = 0
        private set

    val players = mutableListOf<Player>()

    /** 盤面上のエサ配置: 位置 → エサカード */
    private val _foodPositions = mutableMapOf<Position, FoodCard>()
    val foodPositions: Map<Position, FoodCard> get() = _foodPositions.toMap()

    /** エサストック（まだ盤面に出ていないエサ） */
    private val foodStock = mutableListOf<FoodCard>()

    /** エサ捨て山 */
    private val foodDiscard = mutableListOf<FoodCard>()

    /** 勝利に必要な得点 */
    private val winScore: Int get() = if (playerCount >= 4) 5 else 4

    /** 直前の捕獲が成功したか（DECIDEフェーズ判定用） */
    private var lastCaptureSuccess = false

    /**
     * ゲームのセットアップを行う。
     */
    fun setupGame(configs: List<PlayerConfig>) {
        require(configs.size == playerCount) { "プレイヤー数が一致しません" }

        // プレイヤー作成
        configs.forEachIndexed { index, config ->
            players.add(Player(id = index, name = config.name, nestPosition = config.nestPosition))
        }

        // 穴タイル26枚を生成
        val allTiles = shuffler.shuffle(HoleTile.createFullSet())

        // 巣と地上を除いた16マスに裏向きで配置
        val undergroundAndHotZone = mutableListOf<Position>()
        for (row in 0 until Board.ROWS) {
            for (col in 0 until Board.COLS) {
                val pos = Position(col, row)
                val cell = board.getCell(pos) ?: continue
                if (cell.type == CellType.UNDERGROUND || cell.type == CellType.HOT_ZONE) {
                    undergroundAndHotZone.add(pos)
                }
            }
        }

        // 16マスにタイル配置
        undergroundAndHotZone.forEachIndexed { index, pos ->
            boardState.placeTile(pos, allTiles[index])
        }

        // 残り10枚を山札に
        tilePlacementEngine.drawPile = allTiles.drop(16).toMutableList()

        // エサデッキ生成
        val includeFrog = playerCount >= 4
        val foodDeck = shuffler.shuffle(FoodCard.createDeck(includeFrog))

        // ホットゾーン4マスにエサ配置
        Board.HOT_ZONE_POSITIONS.forEachIndexed { index, pos ->
            _foodPositions[pos] = foodDeck[index]
        }

        // 残りをストックに
        foodStock.addAll(foodDeck.drop(4))

        gameState = GameState.PLAYING
        currentPhase = TurnPhase.DIG
    }

    /**
     * フェーズを次に進める。
     */
    fun advancePhase() {
        currentPhase = when (currentPhase) {
            TurnPhase.DIG -> TurnPhase.MOVE
            TurnPhase.MOVE -> TurnPhase.CAPTURE
            TurnPhase.CAPTURE -> {
                if (lastCaptureSuccess) TurnPhase.DECIDE else TurnPhase.END
            }
            TurnPhase.DECIDE -> TurnPhase.END
            TurnPhase.END -> {
                // 次のプレイヤーに移行
                advanceToNextPlayer()
                TurnPhase.DIG
            }
        }
    }

    /**
     * ターン終了処理: 体力減少。
     */
    fun endTurn() {
        val player = players[currentPlayerIndex]
        val cell = board.getCell(player.position)
        val isOnSurface = cell?.type == CellType.GROUND
        player.reduceHealth(isOnSurface)
        lastCaptureSuccess = false
    }

    /**
     * 捕獲を試みる（位置情報なし版）。逃走先の盤面検証を行わない。
     * 位置が不要な場面や後方互換用。
     */
    fun attemptCapture(food: FoodCard): CaptureResult {
        // カブトムシの幼虫は確定捕獲
        if (food.escapeMap.isEmpty()) {
            lastCaptureSuccess = true
            return CaptureResult.Success
        }

        val roll = diceRoller.roll()
        val escapeDirection = food.escapeMap[roll]

        return if (escapeDirection != null) {
            lastCaptureSuccess = false
            CaptureResult.Escaped(escapeDirection, roll)
        } else {
            lastCaptureSuccess = true
            CaptureResult.Success
        }
    }

    /**
     * 盤面上の指定位置にいるエサに対して捕獲を試みる。
     * 逃走先が盤外または無効マスの場合は逃走不可＝捕獲成功とする。
     */
    fun attemptCaptureAt(foodPosition: Position): CaptureResult {
        val food = _foodPositions[foodPosition] ?: error("指定位置にエサがありません: $foodPosition")

        if (food.escapeMap.isEmpty()) {
            lastCaptureSuccess = true
            return CaptureResult.Success
        }

        val roll = diceRoller.roll()
        val escapeDirection = food.escapeMap[roll]

        if (escapeDirection == null) {
            lastCaptureSuccess = true
            return CaptureResult.Success
        }

        // 逃走先の座標を計算
        val escapeTo = escapeDirection.applyTo(foodPosition)
        val escapeCell = board.getCell(escapeTo)

        // 盤外、無効マス、または逃走先に別のエサがある場合は逃走不可→捕獲成功
        if (escapeCell == null || escapeCell.type == CellType.INVALID || escapeTo in _foodPositions) {
            lastCaptureSuccess = true
            return CaptureResult.Success
        }

        // 逃走成功: エサを移動先に配置
        _foodPositions.remove(foodPosition)
        _foodPositions[escapeTo] = food.copy(isFaceDown = false)
        lastCaptureSuccess = false
        return CaptureResult.Escaped(escapeDirection, roll)
    }

    /**
     * 勝利条件を確認。勝者がいればそのプレイヤーを返す。
     */
    fun checkWinCondition(): Player? {
        return players.firstOrNull { !it.isEliminated && it.score >= winScore }
    }

    /**
     * ゲーム終了判定。全員脱落ならFINISHED。
     */
    fun checkGameOver(): GameState {
        if (players.isNotEmpty() && (checkWinCondition() != null || players.all { it.isEliminated })) {
            gameState = GameState.FINISHED
        }
        return gameState
    }

    /**
     * 指定位置のエサを除去する。
     */
    fun removeFoodAt(pos: Position): FoodCard? {
        return _foodPositions.remove(pos)
    }

    /**
     * ホットゾーンに裏向きエサが1枚もないか判定。
     */
    fun shouldReplenishFood(): Boolean {
        return Board.HOT_ZONE_POSITIONS.none { pos ->
            val food = _foodPositions[pos]
            food != null && food.isFaceDown
        }
    }

    /**
     * エサを補充する。ホットゾーン全4マスにストックから配置。
     * ストック切れ時は捨て山をシャッフル。
     */
    fun replenishFood() {
        if (foodStock.isEmpty() && foodDiscard.isEmpty()) return

        if (foodStock.isEmpty()) {
            foodStock.addAll(shuffler.shuffle(foodDiscard))
            foodDiscard.clear()
        }

        Board.HOT_ZONE_POSITIONS.forEach { pos ->
            // 表向きエサが残っている場合は捨て札に移してから補充
            val existing = _foodPositions[pos]
            if (existing != null && !existing.isFaceDown) {
                _foodPositions.remove(pos)
                foodDiscard.add(existing)
            }
            if (pos !in _foodPositions && foodStock.isNotEmpty()) {
                _foodPositions[pos] = foodStock.removeFirst()
            }
        }
    }

    /**
     * 強奪を試みる。
     * // TODO: 【要確認】13-3 強奪はターンのどのフェーズで行うか（仮: 捕獲フェーズ）
     */
    fun attemptRobbery(thief: Player, victim: Player): FoodCard? {
        // 条件: thiefが相手の巣にいる & victimが巣にいない & victimの巣にエサがある
        if (thief.position != victim.nestPosition) return null
        if (victim.position == victim.nestPosition) return null
        if (victim.storedFoods.isEmpty()) return null
        if (thief.isCarrying) return null

        val stolenFood = victim.storedFoods.last()
        victim.removeStoredFood(stolenFood)
        thief.carryFood(stolenFood)
        return stolenFood
    }

    /**
     * 巣が防衛されているか（オーナーが巣にいるか）。
     * // TODO: 【要確認】13-4 止まれないだけ（通過は可能）で仮実装
     */
    fun isNestDefended(nestPosition: Position): Boolean {
        return players.any { it.nestPosition == nestPosition && it.position == nestPosition }
    }

    /**
     * 巣に戻った際に侵入者を追い出す。
     * // TODO: 【要確認】13-5 追い出し先は追い出す側が選ぶ（仮実装）
     */
    fun evictFromNest(owner: Player): Boolean {
        owner.moveTo(owner.nestPosition)
        val intruders = players.filter { it != owner && it.position == owner.nestPosition }
        if (intruders.isEmpty()) return false

        intruders.forEach { intruder ->
            // 隣接マスの最初の有効マスに追い出す
            val evictTo = board.getValidNeighbors(owner.nestPosition).firstOrNull()
            if (evictTo != null) {
                intruder.moveTo(evictTo)
            }
        }
        return true
    }

    /**
     * エサを盤面上に配置する（逃走時など）。
     */
    fun placeFoodAt(pos: Position, food: FoodCard) {
        _foodPositions[pos] = food
    }

    /** 次のプレイヤーに移行（脱落プレイヤーをスキップ） */
    private fun advanceToNextPlayer() {
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size
        } while (players[currentPlayerIndex].isEliminated && !players.all { it.isEliminated })
    }
}
