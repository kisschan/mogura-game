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
    DECIDE,   // 4. 食べる or 巣へ持ち帰る
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
    data class Success(val diceRoll: Int? = null) : CaptureResult()
    data class Escaped(
        val direction: EscapeDirection,
        val diceRoll: Int,
        val to: Position? = null,
    ) : CaptureResult()
}

/**
 * プレイヤー設定。セットアップ時に使用する。
 */
data class PlayerConfig(
    val name: String,
    val nestPosition: Position,
    val playerId: Int? = null,
)

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

    private val _foodPositions = mutableMapOf<Position, MutableList<FoodCard>>()
    val foodPositions: Map<Position, List<FoodCard>>
        get() = _foodPositions.mapValues { (_, foods) -> foods.toList() }

    private val foodStock = mutableListOf<FoodCard>()
    private val foodDiscard = mutableListOf<FoodCard>()

    val foodStockCount: Int
        get() = foodStock.size

    val foodDiscardCount: Int
        get() = foodDiscard.size

    private val winScore: Int
        get() = if (playerCount >= 4) 5 else 4

    private var lastCaptureSuccess = false

    /** ゲームを初期化する。 */
    fun setupGame(configs: List<PlayerConfig>, startPlayerIndex: Int = 0) {
        validateSetupConfigs(configs, startPlayerIndex)

        players.clear()
        boardState.clear()
        _foodPositions.clear()
        foodStock.clear()
        foodDiscard.clear()
        tilePlacementEngine.drawPile.clear()
        tilePlacementEngine.discardPile.clear()
        currentPlayerIndex = startPlayerIndex
        lastCaptureSuccess = false

        configs.forEachIndexed { index, config ->
            players.add(
                Player(
                    id = config.playerId ?: index,
                    name = config.name,
                    nestPosition = config.nestPosition,
                ),
            )
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
            placeFoodAt(position, foodDeck[index])
        }
        foodStock.addAll(foodDeck.drop(4))

        gameState = GameState.PLAYING
        currentPhase = TurnPhase.DIG
    }

    private fun validateSetupConfigs(configs: List<PlayerConfig>, startPlayerIndex: Int) {
        require(configs.size == playerCount) { "プレイヤー数が一致しません" }
        require(startPlayerIndex in configs.indices) { "先手プレイヤーが選択範囲外です" }

        val nestPositions = configs.map { it.nestPosition }
        require(nestPositions.all { board.getCell(it)?.type == CellType.NEST }) {
            "巣は4箇所の巣マスから選んでください"
        }
        require(nestPositions.toSet().size == nestPositions.size) {
            "同じ巣を複数プレイヤーに割り当てることはできません"
        }

        val playerIds = configs.mapIndexed { index, config -> config.playerId ?: index }
        require(playerIds.all { it in 0 until Board.NEST_POSITIONS.size }) {
            "モグラIDは0〜${Board.NEST_POSITIONS.size - 1}で指定してください"
        }
        require(playerIds.toSet().size == playerIds.size) {
            "同じモグラを複数プレイヤーに割り当てることはできません"
        }
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
            return CaptureResult.Success()
        }

        val roll = diceRoller.roll()
        val escapeDirection = food.escapeMap[roll]
        return if (escapeDirection != null) {
            lastCaptureSuccess = false
            CaptureResult.Escaped(escapeDirection, roll)
        } else {
            lastCaptureSuccess = true
            CaptureResult.Success(roll)
        }
    }

    /**
     * 盤面上のエサに対する捕獲判定。
     *
     * 逃走先が盤外・無効マス・巣マス・道なしの場合は捕獲成功とする。
     * 逃走先に別エサがいる場合は同じマスに重ねて配置する。
     */
    fun attemptCaptureAt(foodPosition: Position): CaptureResult {
        return attemptCaptureAtFoodIndex(foodPosition, foodIndex = 0)
    }

    fun attemptCaptureAtFoodIndex(foodPosition: Position, foodIndex: Int): CaptureResult {
        val food = foodAt(foodPosition, foodIndex)
            ?: error("指定位置に捕獲対象のエサがありません: $foodPosition index=$foodIndex")

        if (food.escapeMap.isEmpty()) {
            lastCaptureSuccess = true
            return CaptureResult.Success()
        }

        return attemptCaptureAt(foodPosition, foodIndex = foodIndex, roll = diceRoller.roll())
    }

    /**
     * 指定したダイス目で盤面上のエサの捕獲判定を解決する。
     *
     * UI 側でルーレット演出を挟むなど、出目を外部で確定させてから
     * 解決したい場合に使う。判定ルールは [attemptCaptureAt] と同一。
     */
    fun attemptCaptureAt(foodPosition: Position, roll: Int): CaptureResult {
        return attemptCaptureAt(foodPosition, foodIndex = 0, roll = roll)
    }

    fun attemptCaptureAt(foodPosition: Position, foodIndex: Int, roll: Int): CaptureResult {
        require(roll in 1..6) { "ダイス目は1〜6にしてください: $roll" }
        val food = foodAt(foodPosition, foodIndex)
            ?: error("指定位置に捕獲対象のエサがありません: $foodPosition index=$foodIndex")

        if (food.escapeMap.isEmpty()) {
            lastCaptureSuccess = true
            return CaptureResult.Success()
        }

        val escapeDirection = food.escapeMap[roll]
        if (escapeDirection == null) {
            lastCaptureSuccess = true
            return CaptureResult.Success(roll)
        }

        val escapeTo = escapeDirection.applyTo(foodPosition)
        val escapeCell = board.getCell(escapeTo)
        if (
            escapeCell == null ||
            escapeCell.type == CellType.INVALID ||
            escapeCell.type == CellType.NEST ||
            !hasValidEscapePath(foodPosition, escapeTo, escapeDirection)
        ) {
            lastCaptureSuccess = true
            return CaptureResult.Success(roll)
        }

        removeFoodAt(foodPosition, foodIndex)
        placeFoodAt(escapeTo, food.copy(isFaceDown = false))
        lastCaptureSuccess = false
        return CaptureResult.Escaped(escapeDirection, roll, escapeTo)
    }

    /** 勝利条件を満たすプレイヤーがいれば返す。 */
    fun checkWinCondition(): Player? {
        val scoreWinner = players.firstOrNull { !it.isEliminated && it.score >= winScore }
        if (scoreWinner != null) return scoreWinner

        return players.filter { !it.isEliminated }.singleOrNull()
    }

    /** ゲーム終了状態を更新して返す。 */
    fun checkGameOver(): GameState {
        if (players.isNotEmpty() && (checkWinCondition() != null || players.all { it.isEliminated })) {
            gameState = GameState.FINISHED
        }
        return gameState
    }

    /** 指定位置で次に捕獲対象になるエサを返す。 */
    fun foodAt(position: Position): FoodCard? = _foodPositions[position]?.firstOrNull()

    fun foodAt(position: Position, foodIndex: Int): FoodCard? = _foodPositions[position]?.getOrNull(foodIndex)

    /** 指定位置にあるエサスタックを返す。 */
    fun foodsAt(position: Position): List<FoodCard> = _foodPositions[position]?.toList().orEmpty()

    /** 指定位置のエサを1枚取り除く。 */
    fun removeFoodAt(position: Position, food: FoodCard? = null): FoodCard? {
        val stack = _foodPositions[position] ?: return null
        val index = if (food == null) {
            0
        } else {
            stack.indexOf(food).takeIf { it >= 0 } ?: return null
        }
        val removed = stack.removeAt(index)
        if (stack.isEmpty()) {
            _foodPositions.remove(position)
        }
        return removed
    }

    fun removeFoodAt(position: Position, foodIndex: Int): FoodCard? {
        val stack = _foodPositions[position] ?: return null
        if (foodIndex !in stack.indices) return null
        val removed = stack.removeAt(foodIndex)
        if (stack.isEmpty()) {
            _foodPositions.remove(position)
        }
        return removed
    }

    /** ホットゾーンに裏向きエサが残っていないか判定する。 */
    fun shouldReplenishFood(): Boolean {
        return Board.HOT_ZONE_POSITIONS.none { position ->
            _foodPositions[position].orEmpty().any { it.isFaceDown }
        }
    }

    /**
     * エサを補充する。
     *
     * 表向きエサは捨て札へ送り、裏向きエサがないホットゾーンへ裏向きエサを補充する。
     */
    fun replenishFood(preserveFaceUpHotZonePositions: Set<Position> = emptySet()) {
        sweepFaceUpHotZoneFood(preserveFaceUpHotZonePositions)

        if (foodStock.isEmpty() && foodDiscard.isEmpty()) return

        if (foodStock.isEmpty()) {
            foodStock.addAll(shuffler.shuffle(foodDiscard.map { it.copy(isFaceDown = true) }))
            foodDiscard.clear()
        }

        Board.HOT_ZONE_POSITIONS.forEach { position ->
            val hasFaceDownFood = _foodPositions[position].orEmpty().any { it.isFaceDown }
            if (!hasFaceDownFood && foodStock.isNotEmpty()) {
                placeFoodAt(position, foodStock.removeFirst())
            }
        }
    }

    private fun sweepFaceUpHotZoneFood(preserveFaceUpHotZonePositions: Set<Position>) {
        Board.HOT_ZONE_POSITIONS.forEach { position ->
            if (position in preserveFaceUpHotZonePositions) return@forEach
            val foods = _foodPositions[position] ?: return@forEach
            val iterator = foods.iterator()
            while (iterator.hasNext()) {
                val existing = iterator.next()
                if (!existing.isFaceDown) {
                    iterator.remove()
                    foodDiscard.add(existing.copy(isFaceDown = true))
                }
            }
            if (foods.isEmpty()) {
                _foodPositions.remove(position)
            }
        }
    }

    private fun hasValidEscapePath(from: Position, to: Position, direction: EscapeDirection): Boolean {
        if (!direction.isOrthogonal()) return true
        return movementEngine.isConnected(from, to, boardState)
    }

    /** 要確認フェーズの効果で、捕獲後選択へ直接入る。 */
    fun enterDecisionPhase() {
        currentPhase = TurnPhase.DECIDE
    }

    /** 食べたエサを捨て札へ送る。 */
    fun discardFood(food: FoodCard) {
        foodDiscard.add(food.copy(isFaceDown = true))
    }

    /** 強奪の基本条件を満たしているか判定する。 */
    fun canAttemptRobbery(thief: Player, victim: Player): Boolean {
        if (thief.position != victim.nestPosition) return false
        if (victim.position == victim.nestPosition) return false
        if (victim.storedFoods.isEmpty()) return false
        if (thief.isCarrying) return false
        return true
    }

    /**
     * 強奪を試みる。
     *
     * 強奪の実行タイミングと「次の自分の手番以降」の状態管理は presenter 側で扱う。
     */
    fun attemptRobbery(thief: Player, victim: Player, foodIndex: Int = victim.storedFoods.lastIndex): FoodCard? {
        if (!canAttemptRobbery(thief, victim)) return null

        return victim.removeStoredFoodAt(foodIndex)
            ?.copy(isFaceDown = false)
    }

    /**
     * 巣が防衛されているか判定する。
     */
    fun isNestDefended(nestPosition: Position): Boolean {
        return players.any { it.nestPosition == nestPosition && it.position == nestPosition }
    }

    /**
     * 巣に戻った際に侵入者を追い出す。
     */
    fun evictFromNest(owner: Player): Boolean {
        owner.moveTo(owner.nestPosition)
        val intruders = players.filter { it != owner && it.position == owner.nestPosition }
        if (intruders.isEmpty()) return false

        val evictTo = Board.NEST_EVICTION_DESTINATIONS[owner.nestPosition] ?: return false
        intruders.forEach { intruder ->
            intruder.moveTo(evictTo)
        }
        return true
    }

    /** 指定位置へエサを配置する。 */
    fun placeFoodAt(position: Position, food: FoodCard) {
        val cell = board.getCell(position)
            ?: throw IllegalArgumentException("Food cannot be placed outside the board: $position")
        require(cell.type != CellType.INVALID) { "Food cannot be placed on an invalid cell: $position" }
        require(cell.type != CellType.NEST) { "Food cannot be placed on a nest cell: $position" }

        _foodPositions.getOrPut(position) { mutableListOf() }.add(food)
    }

    /** 次のプレイヤーへ移る。脱落プレイヤーは飛ばす。 */
    private fun advanceToNextPlayer() {
        if (players.isEmpty()) return

        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size
        } while (players[currentPlayerIndex].isEliminated && !players.all { it.isEliminated })
    }
}

private fun EscapeDirection.isOrthogonal(): Boolean = dc == 0 || dr == 0
