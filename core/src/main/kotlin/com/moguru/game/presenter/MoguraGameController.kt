package com.moguru.game.presenter

import com.moguru.game.engine.CaptureResult
import com.moguru.game.engine.GameEngine
import com.moguru.game.engine.GameState
import com.moguru.game.engine.PlayerConfig
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Board
import com.moguru.game.model.CellType
import com.moguru.game.model.Direction
import com.moguru.game.model.FoodCard
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Player
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.util.DiceRoller
import com.moguru.game.util.RandomDiceRoller
import com.moguru.game.util.RandomShuffler
import com.moguru.game.util.Shuffler

data class GameActionResult(
    val success: Boolean,
    val message: String,
)

data class PublicDeckSummary(
    val tileDrawCount: Int,
    val tileDiscardCount: Int,
    val foodDrawCount: Int,
    val foodDiscardCount: Int,
)

data class DigCandidateDisplay(
    val choice: DigTileChoice,
    val label: String,
    val shape: TileShape?,
    val selected: Boolean,
    val enabled: Boolean,
)

data class ActionAvailability(
    val canCapture: Boolean,
    val canEat: Boolean,
    val canCarry: Boolean,
    val canSkip: Boolean,
    val canEndTurn: Boolean,
    val activePhase: TurnPhase?,
)

data class PlayScreenUiState(
    val currentPlayer: CurrentPlayerDisplay,
    val deckSummary: PublicDeckSummary,
    val digCandidates: List<DigCandidateDisplay>,
    val selectedRotation: Rotation,
    val lastDiceRoll: Int?,
    val actionAvailability: ActionAvailability,
)

enum class DigTileChoice {
    REVEALED,
    DRAWN,
}

data class PendingDigPlacement(
    val position: Position,
    val revealedTile: HoleTile,
    val drawnTile: HoleTile?,
)

class MoguraGameController(
    private val diceRoller: DiceRoller = RandomDiceRoller(),
    private val shuffler: Shuffler = RandomShuffler(),
) {
    var engine: GameEngine? = null
        private set

    var lastCaptureResult: CaptureResult? = null
        private set

    var lastDiceRoll: Int? = null
        private set

    var pendingFoodDecision: FoodCard? = null
        private set

    var pendingDigPlacement: PendingDigPlacement? = null
        private set

    var pendingDigRotation: Rotation? = null
        private set

    var pendingDigTileChoice: DigTileChoice? = null
        private set

    private val messages = ArrayDeque<String>()
    val logs: List<String> get() = messages.toList()

    val currentPlayer: Player?
        get() {
            val current = engine ?: return null
            if (current.players.isEmpty()) return null
            return current.players[current.currentPlayerIndex]
        }

    fun publicDeckSummary(): PublicDeckSummary {
        val current = engine
        return PublicDeckSummary(
            tileDrawCount = current?.tilePlacementEngine?.drawPile?.size ?: 0,
            tileDiscardCount = current?.tilePlacementEngine?.discardPile?.size ?: 0,
            foodDrawCount = current?.foodStockCount ?: 0,
            foodDiscardCount = current?.foodDiscardCount ?: 0,
        )
    }

    fun playScreenUiState(): PlayScreenUiState {
        val current = engine
        val phase = current?.currentPhase
        val isPlaying = current?.gameState == GameState.PLAYING
        val hasPendingDecision = phase == TurnPhase.DECIDE && pendingFoodDecision != null
        val hasPendingDig = phase == TurnPhase.DIG && pendingDigPlacement != null
        val canAdvanceFromDig = canAdvanceFromDigWithoutTargets()

        return PlayScreenUiState(
            currentPlayer = currentPlayerDisplay(currentPlayer, phase),
            deckSummary = publicDeckSummary(),
            digCandidates = digCandidateDisplays(hasPendingDig),
            selectedRotation = rotationSelectionForPendingDig(hasPendingDig, pendingDigRotation),
            lastDiceRoll = lastDiceRoll,
            actionAvailability = ActionAvailability(
                canCapture = isPlaying && canCapture(),
                canEat = isPlaying && hasPendingDecision,
                canCarry = isPlaying && hasPendingDecision,
                canSkip = isPlaying && !hasPendingDecision &&
                    (phase != TurnPhase.DIG || canAdvanceFromDig),
                canEndTurn = isPlaying && phase != TurnPhase.DIG && !hasPendingDecision,
                activePhase = phase,
            ),
        )
    }

    fun startNewGame(playerCount: Int): GameActionResult {
        require(playerCount in 2..4) { "プレイヤー人数は2〜4人にしてください。" }

        val nextEngine = GameEngine(
            playerCount = playerCount,
            diceRoller = diceRoller,
            shuffler = shuffler,
        )
        nextEngine.setupGame(defaultConfigs(playerCount))

        engine = nextEngine
        lastCaptureResult = null
        lastDiceRoll = null
        pendingFoodDecision = null
        pendingDigPlacement = null
        pendingDigRotation = null
        pendingDigTileChoice = null
        messages.clear()
        addLog("${playerCount}人プレイで開始しました。")
        addLog("${currentPlayer?.name} の番です。隣の裏向きタイルを掘ってください。")
        return GameActionResult(true, "新しいゲームを開始しました。")
    }

    fun digTargets(): List<Position> {
        val current = engine ?: return emptyList()
        val player = currentPlayer ?: return emptyList()
        if (current.currentPhase != TurnPhase.DIG) return emptyList()

        val allowedDirections = digDirectionsFromCurrentPosition(current, player.position)
        if (allowedDirections.isEmpty()) return emptyList()

        return current.tilePlacementEngine
            .getAdjacentFaceDownTiles(player.position, current.boardState, current.board)
            .filter { (position, _) ->
                directionBetween(player.position, position) in allowedDirections
            }
            .map { it.first }
    }

    fun canAdvanceFromDigWithoutTargets(): Boolean {
        val current = engine ?: return false
        return current.currentPhase == TurnPhase.DIG &&
            pendingDigPlacement == null &&
            digTargets().isEmpty()
    }

    fun moveTargets(): Set<Position> {
        val current = engine ?: return emptySet()
        val player = currentPlayer ?: return emptySet()
        if (current.currentPhase != TurnPhase.MOVE) return emptySet()

        val occupied = current.players
            .filter { it != player && !it.isEliminated }
            .map { it.position }
            .toSet()

        return current.movementEngine.findReachablePositions(
            start = player.position,
            boardState = current.boardState,
            occupiedPositions = occupied,
        )
    }

    fun canCapture(): Boolean {
        val current = engine ?: return false
        val player = currentPlayer ?: return false
        return current.currentPhase == TurnPhase.CAPTURE &&
            !player.isCarrying &&
            player.position in current.foodPositions
    }

    fun digAt(position: Position, rotation: Rotation): GameActionResult {
        val pending = pendingDigPlacement
        if (pending != null) {
            if (pending.position != position) {
                return GameActionResult(false, "めくったタイルと同じマスをクリックして配置を確定してください。")
            }
            setPendingDigRotation(rotation)
            return confirmPendingDig()
        }
        return revealDigTile(position)
    }

    fun revealDigTile(position: Position): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        if (current.currentPhase != TurnPhase.DIG) {
            return GameActionResult(false, "掘れるのは「掘る」フェーズだけです。")
        }
        if (pendingDigPlacement != null) {
            return GameActionResult(false, "先にめくったタイルの配置を確定してください。")
        }
        if (position !in digTargets()) {
            return GameActionResult(false, "現在のプレイヤーに隣接する裏向きタイルを選んでください。")
        }

        val drawn = current.tilePlacementEngine.drawFromPile()
        val revealed = current.boardState.getTile(position)
            ?: return GameActionResult(false, "その場所に置けるタイルがありません。")

        pendingDigPlacement = PendingDigPlacement(position, revealed, drawn)
        pendingDigTileChoice = DigTileChoice.REVEALED
        setPendingDigRotation(Rotation.DEG_0)
        val drawnLabel = drawn?.shape?.displayName() ?: "なし"
        addLog("${currentPlayer?.name} が ${position.label()} の ${revealed.shape.displayName()} をめくりました。山札: $drawnLabel。配置するタイルと回転を選んでください。")
        return GameActionResult(true, "タイルをめくりました。")
    }

    fun selectPendingDigTile(choice: DigTileChoice): GameActionResult {
        val pending = pendingDigPlacement
            ?: return GameActionResult(false, "選択するタイルがありません。")
        if (choice == DigTileChoice.DRAWN && pending.drawnTile == null) {
            return GameActionResult(false, "山札に選択できるタイルがありません。")
        }

        pendingDigTileChoice = choice
        val rotationResult = setPendingDigRotation(pendingDigRotation ?: Rotation.DEG_0)
        if (!rotationResult.success) return rotationResult
        return GameActionResult(true, "${choice.label()}を選びました。")
    }

    fun setPendingDigRotation(rotation: Rotation): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val pending = pendingDigPlacement
            ?: return GameActionResult(false, "回転するタイルがありません。")
        val selectedTile = selectedPendingDigTile(pending)
            ?: return GameActionResult(false, "配置するタイルを選んでください。")

        val previewTile = selectedTile.rotate(rotation).flip()
        current.boardState.placeTile(pending.position, previewTile)
        pendingDigRotation = rotation
        return GameActionResult(true, "タイルの向きを ${rotation.label()} にしました。")
    }

    fun confirmPendingDig(): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val pending = pendingDigPlacement
            ?: return GameActionResult(false, "配置するタイルがありません。")

        val rotation = pendingDigRotation ?: Rotation.DEG_0
        val choice = pendingDigTileChoice ?: DigTileChoice.REVEALED
        val rotationResult = setPendingDigRotation(rotation)
        if (!rotationResult.success) return rotationResult

        when (choice) {
            DigTileChoice.REVEALED -> pending.drawnTile?.let(current.tilePlacementEngine::discard)
            DigTileChoice.DRAWN -> current.tilePlacementEngine.discard(pending.revealedTile)
        }
        pendingDigPlacement = null
        pendingDigRotation = null
        pendingDigTileChoice = null
        val placedTile = current.boardState.getTile(pending.position)
        addLog("${currentPlayer?.name} が ${pending.position.label()} に ${choice.label()}の ${placedTile?.shape?.displayName() ?: "タイル"} を置きました（${rotation.label()}）。")
        current.advancePhase()
        return GameActionResult(true, "タイルを置きました。")
    }

    fun moveTo(position: Position): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val player = currentPlayer ?: return GameActionResult(false, "現在のプレイヤーがいません。")
        if (current.currentPhase != TurnPhase.MOVE) {
            return GameActionResult(false, "移動できるのは「移動」フェーズだけです。")
        }
        if (position !in moveTargets()) {
            return GameActionResult(false, "ハイライトされた到達可能マスを選んでください。")
        }

        player.moveTo(position)
        addLog("${player.name} が ${position.label()} に移動しました。")
        val needsDecision = resolveCurrentPlayerPositionEffects()
        if (needsDecision) {
            // TODO: 【要確認】13-3 強奪は移動で止まった時点で自動発動する仮実装。
            current.enterDecisionPhase()
        } else {
            current.advancePhase()
        }
        return GameActionResult(true, "移動しました。")
    }

    fun captureCurrentPosition(): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val player = currentPlayer ?: return GameActionResult(false, "現在のプレイヤーがいません。")
        if (!canCapture()) {
            return GameActionResult(false, "ここには捕獲できるエサがありません。")
        }

        val position = player.position
        val food = current.foodPositions[position]
            ?: return GameActionResult(false, "ここにはエサがありません。")

        val result = current.attemptCaptureAt(position)
        lastCaptureResult = result
        lastDiceRoll = when (result) {
            is CaptureResult.Success -> result.diceRoll
            is CaptureResult.Escaped -> result.diceRoll
        }

        when (result) {
            is CaptureResult.Success -> {
                val captured = current.removeFoodAt(position) ?: food
                pendingFoodDecision = captured.copy(isFaceDown = false)
                addLog("${player.name} が ${captured.type.displayName()} を捕獲しました。タベるかレンコウを選んでください。")
            }

            is CaptureResult.Escaped -> {
                pendingFoodDecision = null
                addLog(
                    "${food.type.displayName()} はダイス ${result.diceRoll} で " +
                        "${result.direction.displayName()} に逃げました。",
                )
            }
        }

        replenishFoodIfNeeded()
        current.advancePhase()
        return GameActionResult(true, "捕獲判定を解決しました。")
    }

    fun eatPendingFood(): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val player = currentPlayer ?: return GameActionResult(false, "現在のプレイヤーがいません。")
        val food = pendingFoodDecision
            ?: return GameActionResult(false, "タベるエサがありません。")
        if (current.currentPhase != TurnPhase.DECIDE) {
            return GameActionResult(false, "タベるのは捕獲成功後だけです。")
        }

        player.heal(food.type.recovery)
        current.discardFood(food)
        pendingFoodDecision = null
        current.advancePhase()
        addLog("${player.name} が ${food.type.displayName()} をタベました。体力を ${food.type.recovery} 回復しました。")
        return GameActionResult(true, "エサをタベました。")
    }

    fun carryPendingFood(): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val player = currentPlayer ?: return GameActionResult(false, "現在のプレイヤーがいません。")
        val food = pendingFoodDecision
            ?: return GameActionResult(false, "レンコウするエサがありません。")
        if (current.currentPhase != TurnPhase.DECIDE) {
            return GameActionResult(false, "レンコウは捕獲成功後だけです。")
        }
        if (player.isCarrying) {
            return GameActionResult(false, "すでにエサをレンコウ中です。")
        }

        player.carryFood(food)
        pendingFoodDecision = null
        current.advancePhase()
        addLog("${player.name} が ${food.type.displayName()} をレンコウします。巣まで持ち帰ってください。")
        return GameActionResult(true, "エサをレンコウします。")
    }

    fun skipPhase(): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        if (current.gameState == GameState.FINISHED) {
            return GameActionResult(false, "ゲームはすでに終了しています。")
        }
        if (current.currentPhase == TurnPhase.DIG) {
            if (canAdvanceFromDigWithoutTargets()) {
                current.advancePhase()
                addLog("掘れる裏向きタイルがないため、移動へ進みました。")
                return GameActionResult(true, "移動へ進みました。")
            }
            return GameActionResult(false, "掘るフェーズはスキップできません。")
        }
        if (current.currentPhase == TurnPhase.DECIDE && pendingFoodDecision != null) {
            return GameActionResult(false, "タベるかレンコウを選んでください。")
        }

        return if (current.currentPhase == TurnPhase.END) {
            finishTurn()
        } else {
            val skipped = current.currentPhase
            current.advancePhase()
            addLog("${skipped.displayName()} フェーズをスキップしました。")
            GameActionResult(true, "フェーズをスキップしました。")
        }
    }

    /**
     * 選択肢の無いフェーズを自動で進める。
     *
     * 掘る対象なし・移動先なし・捕獲不可・エサ判断なし・END の各局面では
     * プレイヤーが決めることが無いため、[skipPhase] で自動的に先へ進める
     * （ENDでは [finishTurn] が呼ばれてターンが終わる）。選択肢のある局面で停止する。
     * 次プレイヤーへも連鎖しうるが、通常は次の番の掘る局面で止まる。
     *
     * @return 自動で進めた最後の結果。何も進めなければ `null`。
     */
    fun autoAdvanceWhileNoChoice(): GameActionResult? {
        val current = engine ?: return null
        var last: GameActionResult? = null
        var guard = 0
        val max = (current.players.size + 1) * 6
        while (current.gameState == GameState.PLAYING && guard++ < max) {
            if (hasGenuineChoice()) break
            val result = skipPhase()
            if (!result.success) break
            last = result
        }
        return last
    }

    private fun hasGenuineChoice(): Boolean {
        val current = engine ?: return false
        return when (current.currentPhase) {
            TurnPhase.DIG -> pendingDigPlacement != null || digTargets().isNotEmpty()
            TurnPhase.MOVE -> moveTargets().isNotEmpty()
            TurnPhase.CAPTURE -> canCapture()
            TurnPhase.DECIDE -> pendingFoodDecision != null
            TurnPhase.END -> false
        }
    }

    fun finishTurn(): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val player = currentPlayer ?: return GameActionResult(false, "現在のプレイヤーがいません。")
        if (current.gameState == GameState.FINISHED) {
            return GameActionResult(false, "ゲームはすでに終了しています。")
        }
        if (current.currentPhase == TurnPhase.DIG) {
            return GameActionResult(false, "掘るフェーズを終えるまでターン終了できません。")
        }
        if (current.currentPhase == TurnPhase.DECIDE && pendingFoodDecision != null) {
            return GameActionResult(false, "タベるかレンコウを選んでください。")
        }

        moveToEndPhase()
        resolveHomecoming(player)
        current.endTurn()
        addLog("${player.name} の番を終了しました。体力: ${player.health}")

        replenishFoodIfNeeded()

        val winner = current.checkWinCondition()
        if (winner != null) {
            current.checkGameOver()
            addLog("${winner.name} の勝利です（${winner.score}点）。")
            return GameActionResult(true, "ゲーム終了です。")
        }

        if (current.checkGameOver() == GameState.FINISHED) {
            addLog("全員の体力がなくなりました。ゲーム終了です。")
            return GameActionResult(true, "ゲーム終了です。")
        }

        current.advancePhase()
        val nextPlayer = currentPlayer
        addLog("${nextPlayer?.name} の番です。隣の裏向きタイルを掘ってください。")
        return GameActionResult(true, "ターンを終了しました。")
    }

    fun resolveCurrentPlayerPositionEffects(): Boolean {
        val current = engine ?: return false
        val player = currentPlayer ?: return false

        resolveHomecoming(player)

        if (!player.isCarrying && pendingFoodDecision == null) {
            val robbery = current.players
                .filter { it != player && !it.isEliminated }
                .firstNotNullOfOrNull { victim ->
                    current.attemptRobbery(player, victim)?.let { stolen -> victim to stolen }
                }
            if (robbery != null) {
                val (victim, stolen) = robbery
                pendingFoodDecision = stolen
                addLog("${player.name} が ${victim.name} の巣から ${stolen.type.displayName()} を奪いました。")
                addLog("タベるかレンコウを選んでください。")
                return true
            }
        }

        return false
    }

    private fun moveToEndPhase() {
        val current = engine ?: return
        while (current.gameState == GameState.PLAYING && current.currentPhase != TurnPhase.END) {
            current.advancePhase()
        }
    }

    private fun resolveHomecoming(player: Player) {
        val current = engine ?: return
        if (player.position != player.nestPosition) return

        val carried = player.carriedFood
        if (carried != null) {
            player.storeFood()
            addLog("${player.name} が ${carried.type.displayName()} を巣に持ち帰りました（${carried.type.points}点）。")
        }

        if (current.evictFromNest(player)) {
            addLog("${player.name} が巣を守り、侵入者を追い出しました。")
        }
    }

    private fun addLog(message: String) {
        messages.addLast(message)
        while (messages.size > MAX_LOG_LINES) {
            messages.removeFirst()
        }
    }

    private fun replenishFoodIfNeeded() {
        val current = engine ?: return
        if (current.shouldReplenishFood()) {
            current.replenishFood()
            addLog("ホットゾーンにエサを補充しました。")
        }
    }

    private fun digDirectionsFromCurrentPosition(current: GameEngine, position: Position): Set<Direction> {
        val cell = current.board.getCell(position) ?: return emptySet()
        if (cell.type == CellType.NEST) {
            return Direction.entries.toSet()
        }

        val tile = current.boardState.getTile(position) ?: return emptySet()
        return if (tile.isFaceDown) emptySet() else tile.openSides
    }

    private fun selectedPendingDigTile(pending: PendingDigPlacement): HoleTile? =
        when (pendingDigTileChoice ?: DigTileChoice.REVEALED) {
            DigTileChoice.REVEALED -> pending.revealedTile
            DigTileChoice.DRAWN -> pending.drawnTile
        }

    private fun digCandidateDisplays(hasPendingDig: Boolean): List<DigCandidateDisplay> {
        val pending = pendingDigPlacement
        return DigTileChoice.entries.map { choice ->
            val tile = when (choice) {
                DigTileChoice.REVEALED -> pending?.revealedTile
                DigTileChoice.DRAWN -> pending?.drawnTile
            }
            DigCandidateDisplay(
                choice = choice,
                label = choice.label(),
                shape = tile?.shape,
                selected = pendingDigTileChoice == choice,
                enabled = hasPendingDig && (choice != DigTileChoice.DRAWN || tile != null),
            )
        }
    }

    companion object {
        private const val MAX_LOG_LINES = 80

        fun defaultConfigs(playerCount: Int): List<PlayerConfig> {
            val configs = listOf(
                PlayerConfig("モグオ", Position(0, 1)),
                PlayerConfig("モグタ", Position(5, 1)),
                PlayerConfig("モグミ", Position(0, 4)),
                PlayerConfig("モグカ", Position(5, 4)),
            )
            return configs.take(playerCount)
        }
    }
}

fun Position.label(): String = "(${col + 1},${row + 1})"

fun directionBetween(from: Position, to: Position): Direction? {
    val dc = to.col - from.col
    val dr = to.row - from.row
    return when {
        dc == 0 && dr == -1 -> Direction.TOP
        dc == 1 && dr == 0 -> Direction.RIGHT
        dc == 0 && dr == 1 -> Direction.BOTTOM
        dc == -1 && dr == 0 -> Direction.LEFT
        else -> null
    }
}

fun Rotation.label(): String = when (this) {
    Rotation.DEG_0 -> "0°"
    Rotation.DEG_90 -> "90°"
    Rotation.DEG_180 -> "180°"
    Rotation.DEG_270 -> "270°"
}

fun DigTileChoice.label(): String = when (this) {
    DigTileChoice.REVEALED -> "めくったタイル"
    DigTileChoice.DRAWN -> "山札タイル"
}

fun FoodCard.shortLabel(): String = type.displayName()

fun com.moguru.game.model.FoodType.displayName(): String = when (this) {
    com.moguru.game.model.FoodType.BEETLE_LARVA -> "カブトムシの幼虫"
    com.moguru.game.model.FoodType.EARTHWORM -> "ミミズ"
    com.moguru.game.model.FoodType.MOLE_CRICKET -> "ケラ"
    com.moguru.game.model.FoodType.CENTIPEDE -> "ムカデ"
    com.moguru.game.model.FoodType.FROG -> "カエル"
}

fun Board.cellLabel(position: Position): String =
    getCell(position)?.type?.name ?: "範囲外"

fun TurnPhase.displayName(): String = when (this) {
    TurnPhase.DIG -> "掘る"
    TurnPhase.MOVE -> "移動"
    TurnPhase.CAPTURE -> "捕獲"
    TurnPhase.DECIDE -> "判断"
    TurnPhase.END -> "ターン終了"
}

fun TileShape.displayName(): String = when (this) {
    TileShape.STRAIGHT -> "直線タイル"
    TileShape.L_SHAPE -> "L字タイル"
    TileShape.T_SHAPE -> "T字タイル"
    TileShape.CROSS -> "十字タイル"
}

fun com.moguru.game.model.EscapeDirection.displayName(): String = when (this) {
    com.moguru.game.model.EscapeDirection.TOP -> "上"
    com.moguru.game.model.EscapeDirection.TOP_RIGHT -> "右上"
    com.moguru.game.model.EscapeDirection.RIGHT -> "右"
    com.moguru.game.model.EscapeDirection.BOTTOM_RIGHT -> "右下"
    com.moguru.game.model.EscapeDirection.BOTTOM -> "下"
    com.moguru.game.model.EscapeDirection.BOTTOM_LEFT -> "左下"
    com.moguru.game.model.EscapeDirection.LEFT -> "左"
    com.moguru.game.model.EscapeDirection.TOP_LEFT -> "左上"
}
