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
import com.moguru.game.model.FoodType
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

data class MoleOption(
    val playerId: Int,
    val name: String,
)

data class DigCandidateDisplay(
    val choice: DigTileChoice,
    val label: String,
    val shape: TileShape?,
    val selected: Boolean,
    val enabled: Boolean,
)

data class CaptureTargetDisplay(
    val index: Int,
    val type: FoodType,
    val isFaceDown: Boolean,
    val selected: Boolean,
    val enabled: Boolean,
)

data class RobberyTargetDisplay(
    val index: Int,
    val ownerPlayerId: Int,
    val ownerName: String,
    val type: FoodType,
    val selected: Boolean,
    val enabled: Boolean,
)

data class CaptureOutcomeDisplay(
    val kind: CaptureOutcomeKind,
    val diceRoll: Int?,
    val message: String,
)

enum class CaptureOutcomeKind {
    CAPTURED,
    ESCAPED,
}

data class ActionAvailability(
    val canCapture: Boolean,
    val canEat: Boolean,
    val canCarry: Boolean,
    val canRob: Boolean,
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
    val diceRouletteActive: Boolean,
    val diceRouletteResult: Int?,
    val diceRouletteFood: FoodType?,
    val diceRouletteEscapeRolls: List<Int>,
    val captureTargets: List<CaptureTargetDisplay>,
    val robberyTargets: List<RobberyTargetDisplay>,
    val captureOutcome: CaptureOutcomeDisplay?,
    val pendingDecisionSource: FoodDecisionSource?,
    val actionAvailability: ActionAvailability,
)

enum class DigTileChoice {
    REVEALED,
    DRAWN,
}

data class PendingDigPlacement(
    val position: Position,
    val revealedTile: HoleTile?,
    val drawnTile: HoleTile?,
)

/**
 * ダイスルーレット待ちの捕獲。
 *
 * [roll] が null の間はルーレット回転中（出目未確定）を表す。
 */
data class PendingCaptureRoll(
    val position: Position,
    val foodIndex: Int,
    val food: FoodCard,
    val roll: Int? = null,
)

enum class FoodDecisionSource {
    CAPTURE,
    ROBBERY,
}

private sealed class PendingFoodDecision {
    abstract val food: FoodCard
    abstract val source: FoodDecisionSource

    data class Captured(override val food: FoodCard) : PendingFoodDecision() {
        override val source: FoodDecisionSource = FoodDecisionSource.CAPTURE
    }

    data class Stolen(
        override val food: FoodCard,
        val victimPlayerId: Int,
    ) : PendingFoodDecision() {
        override val source: FoodDecisionSource = FoodDecisionSource.ROBBERY
    }
}

private data class RobberyVisit(
    val nestPosition: Position,
    val eligible: Boolean,
)

private data class RobberyCandidate(
    val victim: Player,
    val foods: List<FoodCard>,
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

    private var captureOutcome: CaptureOutcomeDisplay? = null

    private var pendingDecision: PendingFoodDecision? = null

    val pendingFoodDecision: FoodCard?
        get() = pendingDecision?.food

    val pendingFoodDecisionSource: FoodDecisionSource?
        get() = pendingDecision?.source

    var pendingCaptureRoll: PendingCaptureRoll? = null
        private set

    var pendingDigPlacement: PendingDigPlacement? = null
        private set

    var pendingDigRotation: Rotation? = null
        private set

    var pendingDigTileChoice: DigTileChoice? = null
        private set

    private val pendingDigRotations = mutableMapOf<DigTileChoice, Rotation>()

    var selectedCaptureFoodIndex: Int? = null
        private set

    var selectedRobberyFoodIndex: Int? = null
        private set

    private val robberyVisits = mutableMapOf<Int, RobberyVisit>()
    private val ownNestEatEligiblePlayers = mutableSetOf<Int>()

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
        val hasPendingDecision = phase == TurnPhase.DECIDE && pendingDecision != null
        val hasOwnNestFoodToEat = isPlaying && canEatOwnNestStoredFood()
        val hasPendingRobberyChoice = isPlaying && phase == TurnPhase.DECIDE && canRobbery()
        val hasUpcomingRobberyChoice = isPlaying && (phase == TurnPhase.MOVE || phase == TurnPhase.CAPTURE) &&
            hasRobberyOpportunityForCurrentPlayer()
        val hasPendingDig = phase == TurnPhase.DIG && pendingDigPlacement != null
        val canAdvanceFromDig = canAdvanceFromDigWithoutTargets()

        return PlayScreenUiState(
            currentPlayer = currentPlayerDisplay(currentPlayer, phase),
            deckSummary = publicDeckSummary(),
            digCandidates = digCandidateDisplays(hasPendingDig),
            selectedRotation = rotationSelectionForPendingDig(hasPendingDig, pendingDigRotation),
            lastDiceRoll = lastDiceRoll,
            diceRouletteActive = pendingCaptureRoll != null,
            diceRouletteResult = pendingCaptureRoll?.roll,
            diceRouletteFood = pendingCaptureRoll?.food?.type,
            diceRouletteEscapeRolls = pendingCaptureRoll?.food?.escapeMap?.keys?.sorted().orEmpty(),
            captureTargets = captureTargetDisplays(),
            robberyTargets = robberyTargetDisplays(),
            captureOutcome = captureOutcome,
            pendingDecisionSource = pendingFoodDecisionSource,
            actionAvailability = ActionAvailability(
                canCapture = isPlaying && canCapture() && pendingCaptureRoll == null,
                canEat = isPlaying && (hasPendingDecision || hasOwnNestFoodToEat),
                canCarry = isPlaying && hasPendingDecision,
                canRob = hasPendingRobberyChoice,
                canSkip = isPlaying && !hasPendingDecision && !hasPendingRobberyChoice && pendingCaptureRoll == null &&
                    (phase != TurnPhase.DIG || canAdvanceFromDig),
                canEndTurn = isPlaying && phase != TurnPhase.DIG && !hasPendingDecision &&
                    !hasPendingRobberyChoice && !hasUpcomingRobberyChoice && pendingCaptureRoll == null,
                activePhase = phase,
            ),
        )
    }

    fun startNewGame(playerCount: Int): GameActionResult {
        require(playerCount in 2..4) { "プレイヤー人数は2〜4人にしてください。" }

        return startNewGame(defaultConfigs(playerCount), startPlayerIndex = 0)
    }

    fun startNewGame(configs: List<PlayerConfig>, startPlayerIndex: Int = 0): GameActionResult {
        require(configs.size in 2..4) { "プレイヤー人数は2〜4人にしてください。" }

        val nextEngine = GameEngine(
            playerCount = configs.size,
            diceRoller = diceRoller,
            shuffler = shuffler,
        )
        nextEngine.setupGame(configs, startPlayerIndex)

        engine = nextEngine
        lastCaptureResult = null
        lastDiceRoll = null
        captureOutcome = null
        pendingDecision = null
        pendingCaptureRoll = null
        pendingDigPlacement = null
        pendingDigRotation = null
        pendingDigTileChoice = null
        pendingDigRotations.clear()
        selectedCaptureFoodIndex = null
        selectedRobberyFoodIndex = null
        robberyVisits.clear()
        ownNestEatEligiblePlayers.clear()
        messages.clear()
        addLog("${configs.size}人プレイで開始しました。")
        addLog("${currentPlayer?.name} の番です。隣の穴タイルを掘ってください。")
        return GameActionResult(true, "新しいゲームを開始しました。")
    }

    fun digTargets(): List<Position> {
        val current = engine ?: return emptyList()
        val player = currentPlayer ?: return emptyList()
        if (current.currentPhase != TurnPhase.DIG) return emptyList()

        val allowedDirections = digDirectionsFromCurrentPosition(current, player.position)
        if (allowedDirections.isEmpty()) return emptyList()

        val occupiedByOtherPlayers = current.players
            .filter { it != player && !it.isEliminated }
            .map { it.position }
            .toSet()

        return current.tilePlacementEngine
            .getDiggableAdjacentPositions(player.position, current.boardState, current.board)
            .filter { position ->
                position !in occupiedByOtherPlayers &&
                    directionBetween(player.position, position) in allowedDirections
            }
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
        val defendedNests = current.players
            .filter { it != player && !it.isEliminated && it.position == it.nestPosition }
            .map { it.nestPosition }
            .toSet()

        return current.movementEngine.findReachablePositions(
            start = player.position,
            boardState = current.boardState,
            occupiedPositions = occupied,
            blockedPositions = defendedNests,
        )
    }

    fun canCapture(): Boolean {
        val current = engine ?: return false
        val player = currentPlayer ?: return false
        return current.currentPhase == TurnPhase.CAPTURE &&
            !player.isCarrying &&
            selectedCaptureFood(current, player.position) != null
    }

    fun digAt(position: Position, rotation: Rotation): GameActionResult {
        val pending = pendingDigPlacement
        if (pending != null) {
            if (pending.position != position) {
                return GameActionResult(false, "先にめくったタイルの配置を確定してください。")
            }
            return GameActionResult(false, "操作バーの「置く」でタイルの配置を確定してください。")
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
            return GameActionResult(false, "現在のプレイヤーに隣接する掘れる穴タイルを選んでください。")
        }

        val drawn = current.tilePlacementEngine.drawFromPile()
        if (current.boardState.getTile(position) == null) {
            val drawnTile = drawn
                ?: return GameActionResult(false, "配置できるタイルがありません。")
            pendingDigPlacement = PendingDigPlacement(
                position = position,
                revealedTile = null,
                drawnTile = HoleTile(drawnTile.shape),
            )
            pendingDigTileChoice = DigTileChoice.DRAWN
            pendingDigRotations.clear()
            pendingDigRotations[DigTileChoice.DRAWN] = Rotation.DEG_0
            setPendingDigRotation(Rotation.DEG_0)
            addLog("${currentPlayer?.name} が ${position.label()} に置く ${drawnTile.shape.displayName()} を山札から引きました。")
            return GameActionResult(true, "タイルを引きました。")
        }
        val revealed = current.boardState.getTile(position)
            ?: return GameActionResult(false, "その場所に置けるタイルがありません。")

        pendingDigPlacement = PendingDigPlacement(
            position = position,
            revealedTile = HoleTile(revealed.shape),
            drawnTile = drawn?.let { HoleTile(it.shape) },
        )
        pendingDigTileChoice = DigTileChoice.REVEALED
        pendingDigRotations.clear()
        pendingDigRotations[DigTileChoice.REVEALED] = revealed.canonicalRotation()
        if (drawn != null) {
            pendingDigRotations[DigTileChoice.DRAWN] = Rotation.DEG_0
        }
        setPendingDigRotation(pendingDigRotations.getValue(DigTileChoice.REVEALED))
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

        if (choice == DigTileChoice.REVEALED && pending.revealedTile == null) {
            return GameActionResult(false, "選択できるタイルがありません。")
        }

        pendingDigTileChoice = choice
        val rotationResult = setPendingDigRotation(pendingDigRotations[choice] ?: Rotation.DEG_0)
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
        pendingDigTileChoice?.let { choice -> pendingDigRotations[choice] = rotation }
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
            DigTileChoice.DRAWN -> pending.revealedTile?.let(current.tilePlacementEngine::discard)
        }
        pendingDigPlacement = null
        pendingDigRotation = null
        pendingDigTileChoice = null
        pendingDigRotations.clear()
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
        selectedCaptureFoodIndex = null
        selectedRobberyFoodIndex = null
        if (position != player.nestPosition) {
            ownNestEatEligiblePlayers.remove(player.id)
        }
        updateRobberyVisitAfterMove(player)
        addLog("${player.name} が ${position.label()} に移動しました。")
        resolveCurrentPlayerPositionEffects()
        foreignNestOwnerFor(player)?.let { owner ->
            addLog("${owner.name} の巣に入りました。強奪は次の自分の手番以降に選べます。")
        }
        current.advancePhase()
        return GameActionResult(true, "移動しました。")
    }

    fun selectCaptureTarget(index: Int): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val player = currentPlayer ?: return GameActionResult(false, "現在のプレイヤーがいません。")
        if (current.currentPhase != TurnPhase.CAPTURE) {
            return GameActionResult(false, "捕獲対象を選べるのは捕獲フェーズだけです。")
        }
        if (pendingCaptureRoll != null) {
            return GameActionResult(false, "ダイス判定の解決を待っています。")
        }
        if (player.isCarrying) {
            return GameActionResult(false, "エサを持っている間は捕獲できません。")
        }

        val foods = current.foodsAt(player.position)
        if (index !in foods.indices) {
            return GameActionResult(false, "その捕獲対象は選べません。")
        }

        selectedCaptureFoodIndex = index
        return GameActionResult(true, "${foods[index].type.displayName()} を捕獲対象にしました。")
    }

    fun canRobbery(): Boolean {
        val current = engine ?: return false
        return current.currentPhase == TurnPhase.DECIDE &&
            pendingDecision == null &&
            pendingCaptureRoll == null &&
            robberyCandidateForCurrentPlayer() != null
    }

    fun selectRobberyTarget(index: Int): GameActionResult {
        val candidate = robberyCandidateForCurrentPlayer()
            ?: return GameActionResult(false, "強奪できるエサがありません。")
        if (index !in candidate.foods.indices) {
            return GameActionResult(false, "その強奪対象は選べません。")
        }

        selectedRobberyFoodIndex = index
        return GameActionResult(true, "${candidate.foods[index].type.displayName()} を強奪対象にしました。")
    }

    fun robSelectedFood(): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val player = currentPlayer ?: return GameActionResult(false, "現在のプレイヤーがいません。")
        if (!canRobbery()) {
            return GameActionResult(false, "今は強奪できません。")
        }

        val candidate = robberyCandidateForCurrentPlayer()
            ?: return GameActionResult(false, "強奪できるエサがありません。")
        val targetIndex = selectedRobberyFoodIndex?.takeIf { it in candidate.foods.indices } ?: 0
        val stolen = current.attemptRobbery(player, candidate.victim, targetIndex)
            ?: return GameActionResult(false, "強奪できるエサがありません。")

        pendingDecision = PendingFoodDecision.Stolen(stolen, candidate.victim.id)
        robberyVisits[player.id] = RobberyVisit(candidate.victim.nestPosition, eligible = false)
        selectedRobberyFoodIndex = null
        addLog("${player.name} が ${candidate.victim.name} の巣から ${stolen.type.displayName()} を強奪しました。")
        addLog("強奪したエサをタベるか、レンコウするか選んでください。")
        return GameActionResult(true, "エサを強奪しました。")
    }

    fun captureCurrentPosition(): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val player = currentPlayer ?: return GameActionResult(false, "現在のプレイヤーがいません。")
        if (pendingCaptureRoll != null) {
            return GameActionResult(false, "ダイス判定の解決を待っています。")
        }
        if (!canCapture()) {
            return GameActionResult(false, "ここには捕獲できるエサがありません。")
        }

        val position = player.position
        val targetIndex = selectedCaptureIndex(current, position)
        val food = targetIndex?.let { current.foodAt(position, it) }
            ?: return GameActionResult(false, "ここにはエサがありません。")

        pendingCaptureRoll = PendingCaptureRoll(position, targetIndex, food)
        if (food.escapeMap.isEmpty()) {
            addLog("${player.name} が ${food.type.displayName()} を見つけました。逃走なしです。捕獲を確定してください。")
        } else {
            addLog("${player.name} が ${food.type.displayName()} を見つけました。逃走判定のダイスを進めてください。")
        }
        return GameActionResult(true, "エサカードを公開しました。")
    }

    /**
     * ルーレット演出を挟まずに捕獲判定を一括で解決する。
     *
     * カード公開演出を持たないUI（デスクトップ版など）向け。
     */
    fun captureCurrentPositionImmediately(): GameActionResult {
        val started = captureCurrentPosition()
        if (!started.success) return started
        val pending = pendingCaptureRoll ?: return started
        if (pending.food.escapeMap.isNotEmpty()) {
            val rolled = rollCaptureDice()
            if (!rolled.success) return rolled
        }
        return resolveCaptureRoll()
    }

    /** ルーレットを止めて出目を確定する（演出はUI側で継続する）。 */
    fun rollCaptureDice(): GameActionResult {
        val pending = pendingCaptureRoll
            ?: return GameActionResult(false, "ダイス判定はありません。")
        if (pending.roll != null) {
            return GameActionResult(false, "すでにダイスは振られています。")
        }

        val roll = diceRoller.roll()
        pendingCaptureRoll = pending.copy(roll = roll)
        lastDiceRoll = roll
        addLog("ダイスの目は $roll！")
        return GameActionResult(true, "ダイスの目が確定しました。")
    }

    /**
     * 確定した出目で捕獲を解決し、フェーズを進める。
     *
     * 逃走ダイスの無いエサはダイスを振らずに（出目未確定のまま）解決できる。
     */
    fun resolveCaptureRoll(): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val pending = pendingCaptureRoll
            ?: return GameActionResult(false, "ダイス判定はありません。")
        val roll = pending.roll
        if (roll == null && pending.food.escapeMap.isNotEmpty()) {
            return GameActionResult(false, "先にダイスを止めてください。")
        }

        pendingCaptureRoll = null
        val result = if (roll == null) {
            current.attemptCaptureAtFoodIndex(pending.position, pending.foodIndex)
        } else {
            current.attemptCaptureAt(pending.position, pending.foodIndex, roll)
        }
        return resolveCaptureOutcome(pending.position, pending.foodIndex, pending.food, result)
    }

    /** 捕獲判定の結果を盤面・ログ・フェーズへ反映する。 */
    private fun resolveCaptureOutcome(
        position: Position,
        foodIndex: Int,
        food: FoodCard,
        result: CaptureResult,
    ): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val player = currentPlayer ?: return GameActionResult(false, "現在のプレイヤーがいません。")

        lastCaptureResult = result
        lastDiceRoll = when (result) {
            is CaptureResult.Success -> result.diceRoll
            is CaptureResult.Escaped -> result.diceRoll
        }

        when (result) {
            is CaptureResult.Success -> {
                val captured = current.removeFoodAt(position, foodIndex) ?: food
                pendingDecision = PendingFoodDecision.Captured(captured.copy(isFaceDown = false))
                val message = "${player.name} が ${captured.type.displayName()} を捕獲しました。タベるか、レンコウするか選んでください。"
                captureOutcome = CaptureOutcomeDisplay(
                    kind = CaptureOutcomeKind.CAPTURED,
                    diceRoll = result.diceRoll,
                    message = message,
                )
                addLog(message)
            }

            is CaptureResult.Escaped -> {
                pendingDecision = null
                selectedCaptureFoodIndex = null
                val message = "${food.type.displayName()} はダイス ${result.diceRoll} で ${result.direction.displayName()} に逃げました。"
                captureOutcome = CaptureOutcomeDisplay(
                    kind = CaptureOutcomeKind.ESCAPED,
                    diceRoll = result.diceRoll,
                    message = message,
                )
                addLog(message)
            }
        }

        val preserveFaceUpHotZonePositions = when (result) {
            is CaptureResult.Escaped -> result.to?.let(::setOf).orEmpty()
            is CaptureResult.Success -> emptySet()
        }
        replenishFoodIfNeeded(preserveFaceUpHotZonePositions)
        if (result is CaptureResult.Success) {
            selectedCaptureFoodIndex = null
        }
        current.advancePhase()
        return GameActionResult(true, "捕獲判定を解決しました。")
    }

    fun eatPendingFood(): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val player = currentPlayer ?: return GameActionResult(false, "現在のプレイヤーがいません。")
        if (current.currentPhase != TurnPhase.DECIDE) {
            return GameActionResult(false, "タベるのは捕獲・強奪後、または自分の巣に戻った直後だけです。")
        }

        val decision = pendingDecision
        if (decision == null) {
            return eatOwnNestStoredFood(current, player)
        }

        val food = decision.food

        player.heal(food.type.recovery)
        current.discardFood(food)
        pendingDecision = null
        captureOutcome = null
        current.advancePhase()
        val prefix = if (decision.source == FoodDecisionSource.ROBBERY) "強奪した " else ""
        addLog("${player.name} が $prefix${food.type.displayName()} をタベました。体力を ${food.type.recovery} 回復しました。")
        return GameActionResult(true, "エサをタベました。")
    }

    private fun eatOwnNestStoredFood(current: GameEngine, player: Player): GameActionResult {
        if (!canEatOwnNestStoredFood()) {
            return GameActionResult(false, "タベるエサがありません。")
        }

        val food = player.removeStoredFoodAt(0)
            ?: return GameActionResult(false, "タベるエサがありません。")
        player.heal(food.type.recovery)
        current.discardFood(food)
        ownNestEatEligiblePlayers.remove(player.id)
        selectedRobberyFoodIndex = null
        captureOutcome = null
        current.advancePhase()
        addLog("${player.name} が巣の ${food.type.displayName()} をタベました。体力を ${food.type.recovery} 回復し、${food.type.points}点を失いました。")
        return GameActionResult(true, "巣のエサをタベました。")
    }

    fun carryPendingFood(): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val player = currentPlayer ?: return GameActionResult(false, "現在のプレイヤーがいません。")
        val decision = pendingDecision
            ?: return GameActionResult(false, "レンコウするエサがありません。")
        val food = decision.food
        if (current.currentPhase != TurnPhase.DECIDE) {
            return GameActionResult(false, "レンコウできるのは捕獲または強奪後だけです。")
        }
        if (player.isCarrying) {
            return GameActionResult(false, "すでにエサをレンコウ中です。")
        }

        when (decision) {
            is PendingFoodDecision.Captured -> {
                player.carryFood(food)
                addLog("${player.name} が ${food.type.displayName()} をレンコウします。")
            }
            is PendingFoodDecision.Stolen -> {
                player.carryFood(food)
                addLog("${player.name} が強奪した ${food.type.displayName()} をレンコウします。")
            }
        }
        pendingDecision = null
        captureOutcome = null
        current.advancePhase()
        return GameActionResult(true, "エサをレンコウします。")
    }

    fun skipPhase(): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        if (current.gameState == GameState.FINISHED) {
            return GameActionResult(false, "ゲームはすでに終了しています。")
        }
        if (pendingCaptureRoll != null) {
            return GameActionResult(false, "ダイスルーレット中はスキップできません。")
        }
        if (current.currentPhase == TurnPhase.DIG) {
            if (canAdvanceFromDigWithoutTargets()) {
                current.advancePhase()
                addLog("掘れる穴タイルがないため、移動へ進みました。")
                return GameActionResult(true, "移動へ進みました。")
            }
            return GameActionResult(false, "掘るフェーズはスキップできません。")
        }
        if (current.currentPhase == TurnPhase.CAPTURE && hasRobberyOpportunityForCurrentPlayer()) {
            selectedCaptureFoodIndex = null
            current.enterDecisionPhase()
            addLog("${currentPlayer?.name} は強奪を選べます。")
            return GameActionResult(true, "強奪を選べます。")
        }
        if (current.currentPhase == TurnPhase.CAPTURE && hasOwnNestStoredFoodOpportunityForCurrentPlayer()) {
            selectedCaptureFoodIndex = null
            current.enterDecisionPhase()
            addLog("${currentPlayer?.name} は巣のエサをタベられます。")
            return GameActionResult(true, "巣のエサをタベられます。")
        }
        if (current.currentPhase == TurnPhase.DECIDE && pendingDecision != null) {
            return GameActionResult(false, "タベるか、レンコウするか選んでください。")
        }
        if (current.currentPhase == TurnPhase.DECIDE && canRobbery()) {
            return GameActionResult(false, "強奪するエサを選んでください。")
        }

        return if (current.currentPhase == TurnPhase.END) {
            finishTurn()
        } else {
            val skipped = current.currentPhase
            selectedCaptureFoodIndex = null
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
            TurnPhase.CAPTURE -> canCapture() || pendingCaptureRoll != null
            TurnPhase.DECIDE -> pendingDecision != null || canRobbery() || canEatOwnNestStoredFood()
            TurnPhase.END -> captureOutcome != null
        }
    }

    fun finishTurn(): GameActionResult {
        val current = engine ?: return GameActionResult(false, "先にゲームを開始してください。")
        val player = currentPlayer ?: return GameActionResult(false, "現在のプレイヤーがいません。")
        if (current.gameState == GameState.FINISHED) {
            return GameActionResult(false, "ゲームはすでに終了しています。")
        }
        if (pendingCaptureRoll != null) {
            return GameActionResult(false, "ダイスルーレット中はターン終了できません。")
        }
        if (current.currentPhase == TurnPhase.DIG) {
            return GameActionResult(false, "掘るフェーズを終えるまでターン終了できません。")
        }
        if (current.currentPhase == TurnPhase.DECIDE && pendingDecision != null) {
            return GameActionResult(false, "タベるか、レンコウするか選んでください。")
        }
        if (canRobbery() || hasRobberyOpportunityForCurrentPlayer()) {
            return GameActionResult(false, "強奪を選べるため、先にフェーズを進めてください。")
        }

        moveToEndPhase()
        selectedCaptureFoodIndex = null
        captureOutcome = null
        resolveHomecoming(player)
        ownNestEatEligiblePlayers.remove(player.id)
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
        clearStaleRobberyVisits()
        markRobberyEligibilityForCurrentPlayer()
        val nextPlayer = currentPlayer
        addLog("${nextPlayer?.name} の番です。隣の穴タイルを掘ってください。")
        return GameActionResult(true, "ターンを終了しました。")
    }

    fun resolveCurrentPlayerPositionEffects(): Boolean {
        engine ?: return false
        val player = currentPlayer ?: return false

        resolveHomecoming(player)
        clearStaleRobberyVisits()
        return false
    }

    private fun hasRobberyOpportunityForCurrentPlayer(): Boolean =
        pendingDecision == null &&
            pendingCaptureRoll == null &&
            robberyCandidateForCurrentPlayer() != null

    private fun canEatOwnNestStoredFood(): Boolean {
        val current = engine ?: return false
        return current.currentPhase == TurnPhase.DECIDE && hasOwnNestStoredFoodOpportunityForCurrentPlayer()
    }

    private fun hasOwnNestStoredFoodOpportunityForCurrentPlayer(): Boolean {
        val player = currentPlayer ?: return false
        return pendingDecision == null &&
            pendingCaptureRoll == null &&
            player.id in ownNestEatEligiblePlayers &&
            player.position == player.nestPosition &&
            player.storedFoods.isNotEmpty()
    }

    private fun robberyCandidateForCurrentPlayer(): RobberyCandidate? {
        clearStaleRobberyVisits()
        val current = engine ?: return null
        val player = currentPlayer ?: return null
        if (player.isCarrying) return null

        val visit = robberyVisits[player.id] ?: return null
        if (!visit.eligible || visit.nestPosition != player.position) return null

        val victim = foreignNestOwnerFor(player) ?: return null
        if (!current.canAttemptRobbery(player, victim)) return null

        return RobberyCandidate(victim, victim.storedFoods)
    }

    private fun updateRobberyVisitAfterMove(player: Player) {
        val owner = foreignNestOwnerFor(player)
        if (owner == null) {
            robberyVisits.remove(player.id)
        } else {
            robberyVisits[player.id] = RobberyVisit(owner.nestPosition, eligible = false)
        }
    }

    private fun markRobberyEligibilityForCurrentPlayer() {
        clearStaleRobberyVisits()
        val player = currentPlayer ?: return
        val visit = robberyVisits[player.id] ?: return
        val owner = foreignNestOwnerFor(player) ?: return
        if (visit.nestPosition == owner.nestPosition && player.position == visit.nestPosition) {
            robberyVisits[player.id] = visit.copy(eligible = true)
        }
    }

    private fun clearStaleRobberyVisits() {
        val current = engine ?: return
        val playersById = current.players.associateBy { it.id }
        val iterator = robberyVisits.iterator()
        while (iterator.hasNext()) {
            val (playerId, visit) = iterator.next()
            val player = playersById[playerId]
            val ownerExists = current.players.any { it != player && !it.isEliminated && it.nestPosition == visit.nestPosition }
            if (player == null || !ownerExists || player.position != visit.nestPosition) {
                iterator.remove()
            }
        }
    }

    private fun foreignNestOwnerFor(player: Player): Player? {
        val current = engine ?: return null
        return current.players.firstOrNull { owner ->
            owner != player &&
                !owner.isEliminated &&
                owner.nestPosition == player.position
        }
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
            addLog("${player.name} が ${carried.type.displayName()} を巣へ持ち帰りました（${carried.type.points}点）。")
        }

        if (current.evictFromNest(player)) {
            addLog("${player.name} が巣を守り、侵入者を追い出しました。")
        }

        if (player.storedFoods.isNotEmpty() && current.checkWinCondition() == null) {
            ownNestEatEligiblePlayers.add(player.id)
        }
    }

    private fun addLog(message: String) {
        messages.addLast(message)
        while (messages.size > MAX_LOG_LINES) {
            messages.removeFirst()
        }
    }

    private fun replenishFoodIfNeeded(preserveFaceUpHotZonePositions: Set<Position> = emptySet()) {
        val current = engine ?: return
        if (current.shouldReplenishFood()) {
            current.replenishFood(preserveFaceUpHotZonePositions)
            addLog("ホットゾーンにエサを補充しました。")
        }
    }

    private fun digDirectionsFromCurrentPosition(current: GameEngine, position: Position): Set<Direction> {
        val cell = current.board.getCell(position) ?: return emptySet()
        if (cell.type == CellType.NEST || cell.type == CellType.GROUND) {
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
                enabled = hasPendingDig && tile != null,
            )
        }
    }

    private fun selectedCaptureIndex(current: GameEngine, position: Position): Int? {
        val foods = current.foodsAt(position)
        if (foods.isEmpty()) return null
        return selectedCaptureFoodIndex?.takeIf { it in foods.indices } ?: 0
    }

    private fun selectedCaptureFood(current: GameEngine, position: Position): FoodCard? =
        selectedCaptureIndex(current, position)?.let { index -> current.foodAt(position, index) }

    private fun captureTargetDisplays(): List<CaptureTargetDisplay> {
        val current = engine ?: return emptyList()
        val player = currentPlayer ?: return emptyList()
        if (current.currentPhase != TurnPhase.CAPTURE || player.isCarrying || pendingCaptureRoll != null) {
            return emptyList()
        }

        val foods = current.foodsAt(player.position)
        val selectedIndex = selectedCaptureIndex(current, player.position)
        return foods.mapIndexed { index, food ->
            CaptureTargetDisplay(
                index = index,
                type = food.type,
                isFaceDown = food.isFaceDown,
                selected = index == selectedIndex,
                enabled = true,
            )
        }
    }

    private fun selectedRobberyIndex(candidate: RobberyCandidate): Int? {
        if (candidate.foods.isEmpty()) return null
        return selectedRobberyFoodIndex?.takeIf { it in candidate.foods.indices } ?: 0
    }

    private fun robberyTargetDisplays(): List<RobberyTargetDisplay> {
        val current = engine ?: return emptyList()
        if (current.currentPhase != TurnPhase.DECIDE || pendingDecision != null) {
            return emptyList()
        }

        val candidate = robberyCandidateForCurrentPlayer() ?: return emptyList()
        val selectedIndex = selectedRobberyIndex(candidate)
        return candidate.foods.mapIndexed { index, food ->
            RobberyTargetDisplay(
                index = index,
                ownerPlayerId = candidate.victim.id,
                ownerName = candidate.victim.name,
                type = food.type,
                selected = index == selectedIndex,
                enabled = true,
            )
        }
    }

    companion object {
        private const val MAX_LOG_LINES = 80

        val moleOptions = listOf(
            MoleOption(0, "モグオ"),
            MoleOption(1, "モグタ"),
            MoleOption(2, "モグミ"),
            MoleOption(3, "モグカ"),
        )

        val nestPositions = listOf(
            Position(0, 1),
            Position(5, 1),
            Position(0, 4),
            Position(5, 4),
        )

        fun playerNameForId(playerId: Int): String =
            moleOptions.firstOrNull { it.playerId == playerId }?.name
                ?: "モグラ${playerId + 1}"

        fun defaultConfigs(playerCount: Int): List<PlayerConfig> {
            return moleOptions.zip(nestPositions)
                .take(playerCount)
                .map { (mole, nest) ->
                    PlayerConfig(mole.name, nest, playerId = mole.playerId)
                }
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
