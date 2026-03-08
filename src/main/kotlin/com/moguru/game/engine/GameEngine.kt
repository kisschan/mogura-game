package com.moguru.game.engine

import com.moguru.game.model.Board
import com.moguru.game.model.CellType
import com.moguru.game.model.EscapeDirection
import com.moguru.game.model.FoodCard
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Player
import com.moguru.game.model.Position
import com.moguru.game.util.DiceRoller
import com.moguru.game.util.Shuffler

/**
 * ターンのフェーズ。
 */
enum class TurnPhase {
    DIG,      // 1. 掘る
    MOVE,     // 2. 移動
    CAPTURE,  // 3. 捕獲
    DECIDE,   // 4. タベる or レンコウ
    END,      // 5. ターン終了
}

/**
 * ゲーム全体の状態。
 */
enum class GameState {
    SETUP,
    PLAYING,
    FINISHED,
}

/**
 * 捕獲結果。
 */
sealed class CaptureResult {
    data object Success : CaptureResult()
    data class Escaped(val direction: EscapeDirection, val diceRoll: Int) : CaptureResult()
}

/**
 * プレイヤー設定。セットアップ時に使用する。
 */
data class PlayerConfig(val name: String, val nestPosition: Position)

/**
 * ターン進行とゲーム状態を管理するエンジン。
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

    private val _foodPositions = mutableMapOf<Position, FoodCard>()
    val foodPositions: Map<Position, FoodCard> get() = _foodPositions.toMap()

    private val foodStock = mutableListOf<FoodCard>()
    private val foodDiscard = mutableListOf<FoodCard>()

    private val winScore: Int
        get() = if (playerCount >= 4) 5 else 4

    private var lastCaptureSuccess = false

    /** ゲームを初期化する。 */
    fun setupGame(configs: List<PlayerConfig>) {
        require(configs.size == playerCount) { "プレイヤー数が一致しません" }

        players.clear()
        boardState.clear()
        _foodPositions.clear()
        foodStock.clear()
        foodDiscard.clear()
        tilePlacementEngine.drawPile.clear()
        tilePlacementEngine.discardPile.clear()
        currentPlayerIndex = 0
        lastCaptureSuccess = false

        configs.forEachIndexed { index, config ->
            players.add(Player(id = index, name = config.name, nestPosition = config.nestPosition))
        }

        val allTiles = shuffler.shuffle(HoleTile.createFullSet())
        val undergroundAndHotZone = mutableListOf<Position>()

        for (row in 0 until Board.ROWS) {
            for (col in 0 until Board.COLS) {
                val position = Position(col, row)
                val cell = board.getCell(position) ?: continue
                if (cell.type == CellType.UNDERGROUND || cell.type == CellType.HOT_ZONE) {
                    undergroundAndHotZone.add(position)
                }
            }
        }

        undergroundAndHotZone.forEachIndexed { index, position ->
            boardState.placeTile(position, allTiles[index])
        }
        tilePlacementEngine.drawPile = allTiles.drop(16).toMutableList()

        val includeFrog = playerCount >= 4
        val foodDeck = shuffler.shuffle(FoodCard.createDeck(includeFrog))
        Board.HOT_ZONE_POSITIONS.forEachIndexed { index, position ->
            _foodPositions[position] = foodDeck[index]
        }
        foodStock.addAll(foodDeck.drop(4))

        gameState = GameState.PLAYING
        currentPhase = TurnPhase.DIG
    }

    /** フェーズを次へ進める。 */
    fun advancePhase() {
        currentPhase = when (currentPhase) {
            TurnPhase.DIG -> TurnPhase.MOVE
            TurnPhase.MOVE -> TurnPhase.CAPTURE
            TurnPhase.CAPTURE -> if (lastCaptureSuccess) TurnPhase.DECIDE else TurnPhase.END
            TurnPhase.DECIDE -> TurnPhase.END
            TurnPhase.END -> {
                advanceToNextPlayer()
                TurnPhase.DIG
            }
        }
    }

    /** ターン終了処理。体力を減らす。 */
    fun endTurn() {
        val player = players[currentPlayerIndex]
        val cell = board.getCell(player.position)
        val isOnSurface = cell?.type == CellType.GROUND
        player.reduceHealth(isOnSurface)
        lastCaptureSuccess = false
    }

    /** エサ1枚に対する捕獲判定。 */
    fun attemptCapture(food: FoodCard): CaptureResult {
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
     * 盤面上のエサに対する捕獲判定。
     *
     * 逃走先が盤外・無効マス・別エサで埋まっている場合は捕獲成功とする。
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

        val escapeTo = escapeDirection.applyTo(foodPosition)
        val escapeCell = board.getCell(escapeTo)
        if (escapeCell == null || escapeCell.type == CellType.INVALID || escapeTo in _foodPositions) {
            lastCaptureSuccess = true
            return CaptureResult.Success
        }

        _foodPositions.remove(foodPosition)
        _foodPositions[escapeTo] = food.copy(isFaceDown = false)
        lastCaptureSuccess = false
        return CaptureResult.Escaped(escapeDirection, roll)
    }

    /** 勝利条件を満たすプレイヤーがいれば返す。 */
    fun checkWinCondition(): Player? {
        return players.firstOrNull { !it.isEliminated && it.score >= winScore }
    }

    /** ゲーム終了状態を更新して返す。 */
    fun checkGameOver(): GameState {
        if (players.isNotEmpty() && (checkWinCondition() != null || players.all { it.isEliminated })) {
            gameState = GameState.FINISHED
        }
        return gameState
    }

    /** 指定位置のエサを取り除く。 */
    fun removeFoodAt(position: Position): FoodCard? = _foodPositions.remove(position)

    /** ホットゾーンに裏向きエサが残っていないか判定する。 */
    fun shouldReplenishFood(): Boolean {
        return Board.HOT_ZONE_POSITIONS.none { position ->
            val food = _foodPositions[position]
            food != null && food.isFaceDown
        }
    }

    /**
     * エサを補充する。
     *
     * 表向きエサは捨て札へ送り、空いたホットゾーンを裏向きエサで埋める。
     */
    fun replenishFood() {
        if (foodStock.isEmpty() && foodDiscard.isEmpty()) return

        if (foodStock.isEmpty()) {
            foodStock.addAll(shuffler.shuffle(foodDiscard))
            foodDiscard.clear()
        }

        Board.HOT_ZONE_POSITIONS.forEach { position ->
            val existing = _foodPositions[position]
            if (existing != null && !existing.isFaceDown) {
                _foodPositions.remove(position)
                foodDiscard.add(existing)
            }

            if (position !in _foodPositions && foodStock.isNotEmpty()) {
                _foodPositions[position] = foodStock.removeFirst()
            }
        }
    }

    /**
     * 強奪を試みる。
     *
     * TODO: 【要確認】3-3 強奪を行うフェーズは仮実装。
     */
    fun attemptRobbery(thief: Player, victim: Player): FoodCard? {
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
     * 巣が防衛されているか判定する。
     *
     * TODO: 【要確認】3-4 巣防衛の詳細は仮実装。
     */
    fun isNestDefended(nestPosition: Position): Boolean {
        return players.any { it.nestPosition == nestPosition && it.position == nestPosition }
    }

    /**
     * 巣に戻った際に侵入者を追い出す。
     *
     * TODO: 【要確認】3-5 追い出し先の選択権は仮実装。
     */
    fun evictFromNest(owner: Player): Boolean {
        owner.moveTo(owner.nestPosition)
        val intruders = players.filter { it != owner && it.position == owner.nestPosition }
        if (intruders.isEmpty()) return false

        intruders.forEach { intruder ->
            val evictTo = board.getValidNeighbors(owner.nestPosition).firstOrNull()
            if (evictTo != null) {
                intruder.moveTo(evictTo)
            }
        }
        return true
    }

    /** 指定位置へエサを配置する。 */
    fun placeFoodAt(position: Position, food: FoodCard) {
        _foodPositions[position] = food
    }

    /** 次のプレイヤーへ移る。脱落プレイヤーは飛ばす。 */
    private fun advanceToNextPlayer() {
        if (players.isEmpty()) return

        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size
        } while (players[currentPlayerIndex].isEliminated && !players.all { it.isEliminated })
    }
}
