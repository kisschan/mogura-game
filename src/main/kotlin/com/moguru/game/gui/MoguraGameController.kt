package com.moguru.game.gui

import com.moguru.game.engine.CaptureResult
import com.moguru.game.engine.GameEngine
import com.moguru.game.engine.GameState
import com.moguru.game.engine.PlayerConfig
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Board
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

    private val messages = ArrayDeque<String>()
    val logs: List<String> get() = messages.toList()

    val currentPlayer: Player?
        get() {
            val current = engine ?: return null
            if (current.players.isEmpty()) return null
            return current.players[current.currentPlayerIndex]
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
        messages.clear()
        addLog("${playerCount}人プレイで開始しました。")
        addLog("${currentPlayer?.name} の番です。隣の裏向きタイルを掘ってください。")
        return GameActionResult(true, "新しいゲームを開始しました。")
    }

    fun digTargets(): List<Position> {
        val current = engine ?: return emptyList()
        val player = currentPlayer ?: return emptyList()
        if (current.currentPhase != TurnPhase.DIG) return emptyList()

        return current.tilePlacementEngine
            .getAdjacentFaceDownTiles(player.position, current.boardState, current.board)
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
        setPendingDigRotation(Rotation.DEG_0)
        addLog("${currentPlayer?.name} が ${position.label()} の ${revealed.shape.displayName()} をめくりました。回転を選んで同じマスをクリックしてください。")
        return GameActionResult(true, "タイルをめくりました。")
    }

    fun setPendingDigRotation(rotation: Rotation): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val pending = pendingDigPlacement
            ?: return GameActionResult(false, "回転するタイルがありません。")

        val previewTile = pending.revealedTile.rotate(rotation).flip()
        current.boardState.placeTile(pending.position, previewTile)
        pendingDigRotation = rotation
        return GameActionResult(true, "タイルの向きを ${rotation.label()} にしました。")
    }

    fun confirmPendingDig(): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val pending = pendingDigPlacement
            ?: return GameActionResult(false, "配置するタイルがありません。")

        val rotation = pendingDigRotation ?: Rotation.DEG_0
        setPendingDigRotation(rotation)

        // TODO: 【要確認】13-2 山札から引いたタイルとの選択は未実装。現状はめくったタイルを使う。
        pending.drawnTile?.let(current.tilePlacementEngine::discard)
        pendingDigPlacement = null
        pendingDigRotation = null
        addLog("${currentPlayer?.name} が ${pending.position.label()} に ${pending.revealedTile.shape.displayName()} を置きました（${rotation.label()}）。")
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
        lastDiceRoll = (result as? CaptureResult.Escaped)?.diceRoll

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

fun Rotation.label(): String = when (this) {
    Rotation.DEG_0 -> "0度"
    Rotation.DEG_90 -> "90度"
    Rotation.DEG_180 -> "180度"
    Rotation.DEG_270 -> "270度"
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
