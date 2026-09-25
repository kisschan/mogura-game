package com.moguru.game.android

import androidx.lifecycle.ViewModel
import com.moguru.game.engine.GameState
import com.moguru.game.engine.PlayerConfig
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Board
import com.moguru.game.model.CellType
import com.moguru.game.model.Direction
import com.moguru.game.model.FoodType
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Player
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.presenter.DigTileChoice
import com.moguru.game.presenter.CaptureAnimationEvent
import com.moguru.game.presenter.EatAnimationEvent
import com.moguru.game.presenter.GameActionResult
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.presenter.PlayScreenUiState
import com.moguru.game.presenter.TurnConsumptionAnimationEvent
import com.moguru.game.persistence.validate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AndroidGameUiState(
    val isGameStarted: Boolean,
    val selectedPlayerCount: Int,
    val setupPlayers: List<AndroidSetupPlayerUiState>,
    val selectedStartPlayerIndex: Int,
    val canStartGame: Boolean,
    val playState: PlayScreenUiState,
    val boardState: AndroidBoardUiState,
    val hungerMarkers: List<AndroidHungerMarkerUiState>,
    val visibleActions: List<AndroidVisibleAction>,
    val showDigControls: Boolean,
    val logs: List<String>,
    val lastMessage: String?,
    val gameResult: AndroidGameResultUiState?,
    val showGameResultOverlay: Boolean,
    val captureAnimation: AndroidCaptureAnimationUiState? = null,
    val eatAnimation: EatAnimationEvent? = null,
    val turnConsumptionAnimation: TurnConsumptionAnimationEvent? = null,
    val persistence: GamePersistenceUiState = GamePersistenceUiState(),
    val selectedMovePosition: Position? = null,
    val boardPiecesTransparent: Boolean = false,
    val playbackGeneration: Long = 0,
)

/** Keeps the original stack visible until the resolved capture has finished moving. */
data class AndroidCaptureAnimationUiState(
    val event: CaptureAnimationEvent,
    val boardBefore: AndroidBoardUiState,
    val boardAfter: AndroidBoardUiState,
)

data class AndroidGameResultUiState(
    val winnerPlayerId: Int?,
    val winnerName: String?,
    val players: List<AndroidGameResultPlayerUiState>,
)

data class AndroidGameResultPlayerUiState(
    val playerId: Int,
    val name: String,
    val score: Int,
    val health: Int,
    val isEliminated: Boolean,
    val isWinner: Boolean,
)

data class AndroidSetupPlayerUiState(
    val seatIndex: Int,
    val playerId: Int,
    val name: String,
    val nestPosition: Position,
    val isStartPlayer: Boolean,
)

/** 腹減りメーターに表示する各プレイヤーの体力マーカー。 */
data class AndroidHungerMarkerUiState(
    val playerId: Int,
    val health: Int,
    val isCurrent: Boolean,
    /** Stable meter lane based on engine player order; draw order is handled separately. */
    val layoutIndex: Int,
)

private data class AndroidTurnConsumptionDisplaySnapshot(
    val playState: PlayScreenUiState,
    val boardState: AndroidBoardUiState,
    val hungerMarkers: List<AndroidHungerMarkerUiState>,
    val logs: List<String>,
    val lastMessage: String?,
)

data class AndroidBoardUiState(
    val cells: List<AndroidBoardCellUiState>,
)

data class AndroidBoardCellUiState(
    val position: Position,
    val cellType: CellType,
    val tile: AndroidTileUiState?,
    val foods: List<AndroidFoodUiState>,
    val players: List<AndroidPlayerTokenUiState>,
    val highlight: AndroidHighlightTone?,
    val isCurrentPlayerCell: Boolean = false,
    val connectionEdges: List<AndroidConnectionEdgeUiState> = emptyList(),
)

data class AndroidTileUiState(
    val shape: TileShape,
    val rotation: Rotation,
    val isFaceDown: Boolean,
    val openSides: Set<Direction> = emptySet(),
)

data class AndroidFoodUiState(
    val type: FoodType,
    val isFaceDown: Boolean,
)

data class AndroidPlayerTokenUiState(
    val playerId: Int,
    val accessibilityLabel: String,
    val isCurrent: Boolean,
    val carriedFoodType: FoodType? = null,
)

data class AndroidConnectionEdgeUiState(
    val direction: Direction,
    val tone: AndroidConnectionTone,
)

enum class AndroidConnectionTone {
    OPEN,
    CONNECTED,
    BLOCKED,
}

enum class AndroidHighlightTone {
    DIG,
    MOVE,
    CAPTURE,
}

enum class AndroidVisibleAction {
    CAPTURE,
    ROB,
    EAT,
    CARRY,
    SKIP,
    END_TURN,
}

class AndroidGameViewModel(
    private var controller: MoguraGameController = MoguraGameController(),
    private val saveRepository: GameSaveRepository = MemoryGameSaveRepository(),
    private val saveIo: GameSaveIo = ImmediateGameSaveIo,
) : ViewModel() {
    private var savedGame: SavedGame? = null
    private var persistenceBusy = false
    private var saveFailed = false
    private var loadIssue: GameSaveLoadIssue? = null
    private var showResumeEntry = false
    private var confirmOverwrite = false
    private var pendingSave: Pair<SavedGame, AndroidGameUiState>? = null
    private var selectedMovePosition: Position? = null
    private var boardPiecesTransparent = false
    private var playbackGeneration = 0L
    private var alive = true
    private var selectedPlayerCount = 2
    private var setupPlayerIds = defaultSetupPlayerIds(selectedPlayerCount)
    private var setupNestPositions = defaultSetupNestPositions(selectedPlayerCount)
    private var selectedStartPlayerIndex = 0
    private var gameResultOverlayDismissed = false
    private var captureAnimation: AndroidCaptureAnimationUiState? = null
    private val captureFailureSoundEffectTrigger = CaptureFailureSoundEffectTrigger()
    private var eatAnimation: EatAnimationEvent? = null
    private val eatRecoverySoundEffectTrigger = EatRecoverySoundEffectTrigger()
    private var turnConsumptionAnimation: TurnConsumptionAnimationEvent? = null
    private var turnConsumptionDisplaySnapshot: AndroidTurnConsumptionDisplaySnapshot? = null
    private var turnConsumptionPostMessage: String? = null

    private val _uiState = MutableStateFlow(
        snapshot(
            isGameStarted = false,
            lastMessage = null,
        ),
    )
    val uiState: StateFlow<AndroidGameUiState> = _uiState.asStateFlow()

    init { reloadSavedGame() }

    fun reloadSavedGame() {
        if (operationBlocked()) return
        persistenceBusy = true
        publishPersistence()
        val generation = playbackGeneration
        saveIo.execute({ saveRepository.load()?.also { it.game.validate() } }) { result ->
            if (!alive || generation != playbackGeneration) return@execute
            persistenceBusy = false
            result.fold(
                onSuccess = { savedGame = it; loadIssue = null; showResumeEntry = it != null },
                onFailure = {
                    loadIssue = when (it) {
                        is InvalidGameSaveException -> it.issue
                        is IllegalArgumentException -> GameSaveLoadIssue.CORRUPT
                        else -> GameSaveLoadIssue.IO
                    }
                    showResumeEntry = true
                },
            )
            publishPersistence()
        }
    }

    fun chooseNewGame() {
        if (operationBlocked()) return
        showResumeEntry = false
        confirmOverwrite = false
        _uiState.value = snapshot(false, null)
    }

    fun cancelOverwrite() {
        confirmOverwrite = false
        publishPersistence()
    }

    fun confirmNewGame() {
        if (operationBlocked() || !confirmOverwrite) return
        confirmOverwrite = false
        beginSelectedGame()
    }

    fun resumeSavedGame() {
        if (operationBlocked() || loadIssue != null) return
        val saved = savedGame ?: return
        val restored = runCatching { controller.restored(saved.game).apply { settleAfterRestore() } }
        if (restored.isFailure) {
            loadIssue = GameSaveLoadIssue.CORRUPT
            showResumeEntry = true
            publishPersistence()
            return
        }
        controller = restored.getOrThrow()
        playbackGeneration++
        clearPresentation()
        selectedPlayerCount = saved.game.engine.players.size
        setupPlayerIds = saved.game.engine.players.map { it.id }
        setupNestPositions = saved.game.engine.players.map { it.nestPosition }
        selectedStartPlayerIndex = saved.game.engine.currentPlayerIndex
        selectedMovePosition = saved.selectedMovePosition
        boardPiecesTransparent = saved.boardPiecesTransparent
        showResumeEntry = false
        val changed = controller.exportSnapshot() != saved.game
        commitState(snapshot(true, if (changed) null else saved.lastMessage))
    }

    fun retrySave() {
        if (!alive || persistenceBusy || pendingSave == null) return
        executePendingSave()
    }

    fun selectMoveTarget(position: Position) {
        if (operationBlocked() || animationInProgress() || position !in controller.moveTargets()) return
        selectedMovePosition = position
        refresh(_uiState.value.lastMessage)
    }

    fun setBoardPiecesTransparent(transparent: Boolean) {
        if (operationBlocked() || animationInProgress()) return
        boardPiecesTransparent = transparent
        refresh(_uiState.value.lastMessage)
    }

    private fun operationBlocked() = !alive || persistenceBusy || pendingSave != null

    private fun persistenceState(): GamePersistenceUiState {
        val engine = savedGame?.game?.engine
        return GamePersistenceUiState(
            busy = persistenceBusy, saveFailed = saveFailed, loadIssue = loadIssue,
            hasSavedGame = savedGame != null, showEntry = showResumeEntry, confirmOverwrite = confirmOverwrite,
            finished = engine?.gameState == GameState.FINISHED,
            playerCount = engine?.players?.size ?: 0,
            currentPlayerName = engine?.players?.getOrNull(engine.currentPlayerIndex)?.name.orEmpty(),
        )
    }

    private fun publishPersistence() {
        _uiState.value = _uiState.value.copy(persistence = persistenceState())
    }

    private fun commitState(next: AndroidGameUiState) {
        if (!next.isGameStarted) {
            _uiState.value = next.copy(persistence = persistenceState())
            return
        }
        check(pendingSave == null)
        val game = controller.exportSnapshot()
        val previous = savedGame
        if (previous != null && game == previous.game &&
            next.selectedMovePosition == previous.selectedMovePosition &&
            boardPiecesTransparent == previous.boardPiecesTransparent
        ) {
            _uiState.value = next.copy(persistence = persistenceState())
            return
        }
        val saved = SavedGame(
            game = game, revision = (savedGame?.revision ?: 0) + 1,
            savedAtMillis = System.currentTimeMillis(),
            selectedMovePosition = next.selectedMovePosition,
            boardPiecesTransparent = boardPiecesTransparent,
            lastMessage = if (turnConsumptionAnimation != null) turnConsumptionPostMessage else next.lastMessage,
        )
        pendingSave = saved to next
        executePendingSave()
    }

    private fun executePendingSave() {
        val pending = pendingSave ?: return
        persistenceBusy = true
        saveFailed = false
        publishPersistence()
        val generation = playbackGeneration
        saveIo.execute({ saveRepository.save(pending.first) }) { result ->
            if (!alive || generation != playbackGeneration) return@execute
            persistenceBusy = false
            if (result.isSuccess) {
                savedGame = pending.first
                loadIssue = null
                pendingSave = null
                _uiState.value = pending.second.copy(persistence = persistenceState())
            } else {
                saveFailed = true
                publishPersistence()
            }
        }
    }

    private fun clearPresentation() {
        captureAnimation = null
        eatAnimation = null
        clearTurnConsumptionAnimation()
        gameResultOverlayDismissed = false
    }

    override fun onCleared() {
        alive = false
        playbackGeneration++
        saveIo.close()
        super.onCleared()
    }

    fun selectPlayerCount(playerCount: Int) {
        if (operationBlocked()) return
        require(playerCount in 2..4) { "プレイヤー人数は2〜4人にしてください。" }
        resetSetupDefaults(playerCount)
        _uiState.value = snapshot(
            isGameStarted = _uiState.value.isGameStarted,
            lastMessage = null,
        )
    }

    fun selectPlayerMole(seatIndex: Int, playerId: Int) {
        if (operationBlocked()) return
        if (seatIndex !in 0 until selectedPlayerCount) return
        if (MoguraGameController.moleOptions.none { it.playerId == playerId }) return

        setupPlayerIds = setupPlayerIds.swappingSelection(seatIndex, playerId)
        refresh(null)
    }

    fun selectPlayerNest(seatIndex: Int, nestPosition: Position) {
        if (operationBlocked()) return
        if (seatIndex !in 0 until selectedPlayerCount) return
        if (nestPosition !in MoguraGameController.nestPositions) return

        setupNestPositions = setupNestPositions.swappingSelection(seatIndex, nestPosition)
        refresh(null)
    }

    fun selectStartPlayer(seatIndex: Int) {
        if (operationBlocked()) return
        if (seatIndex !in 0 until selectedPlayerCount) return

        selectedStartPlayerIndex = seatIndex
        refresh(null)
    }

    fun startSelectedGame() {
        if (operationBlocked()) return
        if (savedGame != null || loadIssue != null) {
            confirmOverwrite = true
            publishPersistence()
            return
        }
        beginSelectedGame()
    }

    private fun beginSelectedGame() {
        playbackGeneration++
        selectedMovePosition = null
        showResumeEntry = false
        captureAnimation = null
        eatAnimation = null
        clearTurnConsumptionAnimation()
        gameResultOverlayDismissed = false
        val result = controller.startNewGame(setupConfigs(), selectedStartPlayerIndex)
        commitState(snapshot(
            isGameStarted = true,
            lastMessage = messageForCurrentTurn(result.message),
        ))
    }

    fun startNewGame(playerCount: Int) {
        if (operationBlocked()) return
        playbackGeneration++
        selectedMovePosition = null
        showResumeEntry = false
        captureAnimation = null
        eatAnimation = null
        clearTurnConsumptionAnimation()
        gameResultOverlayDismissed = false
        resetSetupDefaults(playerCount)
        val result = controller.startNewGame(setupConfigs(), selectedStartPlayerIndex)
        commitState(snapshot(
            isGameStarted = true,
            lastMessage = messageForCurrentTurn(result.message),
        ))
    }

    fun returnToSetup() {
        if (operationBlocked()) return
        playbackGeneration++
        showResumeEntry = savedGame != null || loadIssue != null
        captureAnimation = null
        eatAnimation = null
        clearTurnConsumptionAnimation()
        gameResultOverlayDismissed = false
        _uiState.value = snapshot(
            isGameStarted = false,
            lastMessage = null,
        )
    }

    fun dismissGameResultOverlay() {
        if (operationBlocked()) return
        if (_uiState.value.gameResult == null) return
        gameResultOverlayDismissed = true
        refresh(_uiState.value.lastMessage)
    }

    fun onCellClicked(position: Position) {
        if (operationBlocked()) return
        if (animationInProgress()) return
        val engine = controller.engine ?: return refresh(null)
        val result = when (engine.currentPhase) {
            TurnPhase.DIG -> controller.digAt(position, _uiState.value.playState.selectedRotation)
            TurnPhase.MOVE -> controller.moveTo(position)
            TurnPhase.CAPTURE -> if (controller.currentPlayer?.position == position) {
                controller.captureCurrentPosition()
            } else {
                GameActionResult(false, "捕獲するには現在のプレイヤーがいるマスを選んでください。")
            }
            TurnPhase.DECIDE,
            TurnPhase.END,
            -> GameActionResult(false, "右側の操作ボタンを使ってください。")
        }
        if (result.success) {
            resolveAfterSuccessfulAction(null)
        } else {
            refresh(result.message)
        }
    }

    fun selectDigChoice(choice: DigTileChoice) {
        runAction { controller.selectPendingDigTile(choice) }
    }

    fun selectRotation(rotation: Rotation) {
        runAction { controller.setPendingDigRotation(rotation) }
    }

    fun confirmDigPlacement() {
        runAction { controller.confirmPendingDig() }
    }

    fun selectCaptureTarget(index: Int) {
        runAction { controller.selectCaptureTarget(index) }
    }

    fun selectRobberyTarget(index: Int) {
        runAction { controller.selectRobberyTarget(index) }
    }

    fun capture() {
        runAction { controller.captureCurrentPosition() }
    }

    fun rob() {
        runAction { controller.robSelectedFood() }
    }

    /**
     * ルーレットを止めて出目を確定する（着地演出は UI 側で継続）。
     * 連打などで既に確定済みの場合は黙って無視する。
     */
    fun stopDiceRoulette() {
        if (operationBlocked()) return
        if (animationInProgress()) return
        val result = controller.rollCaptureDice()
        if (result.success) refresh(null)
    }

    /**
     * 着地演出の終了後に捕獲を解決し、オーバーレイを閉じる。
     * 再コンポーズによる重複呼び出しは黙って無視する。
     */
    fun finishDiceRoulette(generation: Long = playbackGeneration) {
        if (operationBlocked() || generation != playbackGeneration) return
        if (animationInProgress() || !_uiState.value.isGameStarted) return
        val boardBefore = AndroidBoardUiState(buildBoardCells())
        val result = controller.resolveCaptureRoll()
        if (result.success) {
            val event = controller.playScreenUiState().captureOutcome?.animation
            if (event != null) {
                captureAnimation = AndroidCaptureAnimationUiState(
                    event = event,
                    boardBefore = boardBefore,
                    boardAfter = AndroidBoardUiState(buildBoardCells()),
                )
                refresh(result.message)
            } else {
                resolveAfterSuccessfulAction(result.message)
            }
        }
    }

    /** The ViewModel retains this consumption state when the Activity is recreated. */
    internal fun captureFailureSoundEffectFor(
        event: CaptureAnimationEvent?,
    ): AndroidSoundEffect? = captureFailureSoundEffectTrigger.soundEffectFor(event)

    /** Keep played meal IDs across Activity recreation, just like capture sounds. */
    internal fun eatRecoverySoundEffectFor(
        event: EatAnimationEvent?,
    ): AndroidSoundEffect? = eatRecoverySoundEffectTrigger.soundEffectFor(event)

    /** An old completion callback must never finish a newer capture or advance twice. */
    fun finishCaptureAnimation(eventId: Long, generation: Long = playbackGeneration) {
        if (operationBlocked() || generation != playbackGeneration) return
        if (captureAnimation?.event?.id != eventId) return
        captureAnimation = null
        resolveAfterSuccessfulAction(_uiState.value.lastMessage)
    }

    fun eat() {
        if (operationBlocked()) return
        if (animationInProgress()) return
        val result = controller.eatPendingFood()
        if (!result.success) {
            refresh(result.message)
            return
        }

        val event = result.eatAnimation
        if (event == null) {
            resolveAfterSuccessfulAction(result.message)
            return
        }

        eatAnimation = event
        // Keep the just-finished DECIDE state visible until the one-shot playback ends.
        refresh(result.message)
    }

    /** An old completion callback must never finish a newer meal or advance twice. */
    fun finishEatAnimation(eventId: Long, generation: Long = playbackGeneration) {
        if (operationBlocked() || generation != playbackGeneration) return
        if (eatAnimation?.id != eventId) return
        eatAnimation = null
        resolveAfterSuccessfulAction(_uiState.value.lastMessage)
    }

    /** Stale or duplicate callbacks must never advance the next turn twice. */
    fun finishTurnConsumptionAnimation(eventId: Long, generation: Long = playbackGeneration) {
        if (operationBlocked() || generation != playbackGeneration) return
        if (turnConsumptionAnimation?.id != eventId) return
        val postMessage = turnConsumptionPostMessage
        clearTurnConsumptionAnimation()
        if (controller.engine?.gameState == GameState.PLAYING) {
            // Continue automatic phase skipping only after this playback has fully finished.
            // A following turn consumption is captured by resolveAfterSuccessfulAction and
            // becomes the next serialized animation instead of overlapping this one.
            resolveAfterSuccessfulAction(postMessage)
        } else {
            refresh(postMessage)
        }
    }

    fun carry() {
        runAction { controller.carryPendingFood() }
    }

    fun skip() {
        runAction { controller.skipPhase() }
    }

    fun finishTurn() {
        runAction { controller.finishTurn() }
    }

    private fun runAction(action: () -> GameActionResult) {
        if (operationBlocked()) return
        if (animationInProgress()) return
        val displayBeforeAction = displayedTurnConsumptionSnapshot()
        val result = action()
        if (result.success) {
            resolveAfterSuccessfulAction(
                message = result.message,
                directTurnConsumption = result.turnConsumptionAnimation,
                directDisplaySnapshot = displayBeforeAction,
            )
        } else {
            refresh(result.message)
        }
    }

    /**
     * ユーザー操作の解決後に、選択肢の無いフェーズを自動で進める。
     * 自動進行が発火したらメッセージはログ（「○○ の番です」等）に委ねる。
     */
    private fun resolveAfterSuccessfulAction(
        message: String?,
        directTurnConsumption: TurnConsumptionAnimationEvent? = null,
        directDisplaySnapshot: AndroidTurnConsumptionDisplaySnapshot? = null,
    ) {
        if (directTurnConsumption != null) {
            val postMessage = messageForCurrentTurn(message)
            holdTurnConsumptionAnimation(
                event = directTurnConsumption,
                displaySnapshot = directDisplaySnapshot ?: displayedTurnConsumptionSnapshot(),
                postMessage = postMessage,
            )
            refresh(postMessage)
            return
        }

        // This must come from the controller's live pre-auto state, never the possibly
        // frozen _uiState. It keeps chained consumption animations exactly one turn apart.
        val displayBeforeAutoAdvance = liveTurnConsumptionSnapshot(messageForCurrentTurn(message))
        val autoResult = controller.autoAdvanceWhileNoChoice()
        val resolvedMessage = if (autoResult != null) null else message
        val postMessage = messageForCurrentTurn(resolvedMessage)
        autoResult?.turnConsumptionAnimation?.let { event ->
            holdTurnConsumptionAnimation(
                event = event,
                displaySnapshot = displayBeforeAutoAdvance,
                postMessage = postMessage,
            )
        }
        refresh(postMessage)
    }

    private fun messageForCurrentTurn(message: String?): String? {
        val engine = controller.engine
        return if (engine?.currentPhase == TurnPhase.DIG && controller.pendingDigDrawnTile != null) {
            null
        } else {
            message
        }
    }

    private fun refresh(lastMessage: String?) {
        commitState(snapshot(
            isGameStarted = _uiState.value.isGameStarted,
            lastMessage = lastMessage,
        ))
    }

    private fun snapshot(
        isGameStarted: Boolean,
        lastMessage: String?,
    ): AndroidGameUiState {
        val frozenDisplay = turnConsumptionDisplaySnapshot
        val playState = frozenDisplay?.playState ?: controller.playScreenUiState()
        // Winner/elimination copy must not leak through the event strip or action bar
        // before the final health marker movement has completed.
        val gameResult = if (turnConsumptionAnimation != null) null else buildGameResult(isGameStarted)
        return AndroidGameUiState(
            isGameStarted = isGameStarted,
            selectedPlayerCount = selectedPlayerCount,
            setupPlayers = buildSetupPlayers(),
            selectedStartPlayerIndex = selectedStartPlayerIndex,
            canStartGame = canStartConfiguredGame(),
            playState = playState,
            boardState = frozenDisplay?.boardState
                ?: captureAnimation?.boardBefore
                ?: AndroidBoardUiState(
                    cells = if (isGameStarted) buildBoardCells() else emptyList(),
                ),
            hungerMarkers = frozenDisplay?.hungerMarkers
                ?: if (isGameStarted) buildHungerMarkers() else emptyList(),
            visibleActions = if (isGameStarted && !animationInProgress()) visibleActions(playState) else emptyList(),
            showDigControls = isGameStarted && !animationInProgress() && playState.digCandidates.any { it.enabled },
            logs = frozenDisplay?.logs
                ?: if (isGameStarted) controller.logs else emptyList(),
            lastMessage = if (frozenDisplay != null) frozenDisplay.lastMessage else lastMessage,
            gameResult = gameResult,
            showGameResultOverlay = gameResult != null &&
                turnConsumptionAnimation == null &&
                !gameResultOverlayDismissed,
            captureAnimation = captureAnimation,
            eatAnimation = eatAnimation,
            turnConsumptionAnimation = turnConsumptionAnimation,
            persistence = persistenceState(),
            selectedMovePosition = if (controller.engine?.currentPhase == TurnPhase.MOVE) {
                val targets = controller.moveTargets()
                selectedMovePosition?.takeIf { it in targets }
                    ?: targets.sortedWith(compareBy<Position> { it.row }.thenBy { it.col }).firstOrNull()
            } else null,
            boardPiecesTransparent = boardPiecesTransparent,
            playbackGeneration = playbackGeneration,
        )
    }

    private fun holdTurnConsumptionAnimation(
        event: TurnConsumptionAnimationEvent,
        displaySnapshot: AndroidTurnConsumptionDisplaySnapshot,
        postMessage: String?,
    ) {
        turnConsumptionDisplaySnapshot = displaySnapshot
        turnConsumptionPostMessage = postMessage
        turnConsumptionAnimation = event
    }

    private fun clearTurnConsumptionAnimation() {
        turnConsumptionAnimation = null
        turnConsumptionDisplaySnapshot = null
        turnConsumptionPostMessage = null
    }

    private fun displayedTurnConsumptionSnapshot(): AndroidTurnConsumptionDisplaySnapshot {
        val displayed = _uiState.value
        return AndroidTurnConsumptionDisplaySnapshot(
            playState = displayed.playState,
            boardState = displayed.boardState,
            hungerMarkers = displayed.hungerMarkers,
            logs = displayed.logs,
            lastMessage = displayed.lastMessage,
        )
    }

    private fun liveTurnConsumptionSnapshot(lastMessage: String?): AndroidTurnConsumptionDisplaySnapshot {
        val isGameStarted = _uiState.value.isGameStarted
        return AndroidTurnConsumptionDisplaySnapshot(
            playState = controller.playScreenUiState(),
            boardState = AndroidBoardUiState(
                cells = if (isGameStarted) buildBoardCells() else emptyList(),
            ),
            hungerMarkers = if (isGameStarted) buildHungerMarkers() else emptyList(),
            logs = if (isGameStarted) controller.logs else emptyList(),
            lastMessage = lastMessage,
        )
    }

    private fun animationInProgress(): Boolean =
        captureAnimation != null || eatAnimation != null || turnConsumptionAnimation != null

    private fun buildGameResult(isGameStarted: Boolean): AndroidGameResultUiState? {
        val engine = controller.engine ?: return null
        if (!isGameStarted || engine.gameState != GameState.FINISHED) return null

        val winner = engine.checkWinCondition()
        return AndroidGameResultUiState(
            winnerPlayerId = winner?.id,
            winnerName = winner?.name,
            players = engine.players.map { player ->
                player.toAndroidGameResultPlayerUiState(winnerPlayerId = winner?.id)
            },
        )
    }

    private fun resetSetupDefaults(playerCount: Int) {
        selectedPlayerCount = playerCount
        setupPlayerIds = defaultSetupPlayerIds(playerCount)
        setupNestPositions = defaultSetupNestPositions(playerCount)
        selectedStartPlayerIndex = 0
    }

    private fun setupConfigs(): List<PlayerConfig> =
        setupPlayerIds.zip(setupNestPositions).map { (playerId, nestPosition) ->
            PlayerConfig(
                name = MoguraGameController.playerNameForId(playerId),
                nestPosition = nestPosition,
                playerId = playerId,
            )
        }

    private fun buildSetupPlayers(): List<AndroidSetupPlayerUiState> =
        setupPlayerIds.zip(setupNestPositions).mapIndexed { index, (playerId, nestPosition) ->
            AndroidSetupPlayerUiState(
                seatIndex = index,
                playerId = playerId,
                name = MoguraGameController.playerNameForId(playerId),
                nestPosition = nestPosition,
                isStartPlayer = index == selectedStartPlayerIndex,
            )
        }

    private fun canStartConfiguredGame(): Boolean =
        setupPlayerIds.size == selectedPlayerCount &&
            setupNestPositions.size == selectedPlayerCount &&
            setupPlayerIds.toSet().size == selectedPlayerCount &&
            setupNestPositions.toSet().size == selectedPlayerCount &&
            selectedStartPlayerIndex in 0 until selectedPlayerCount

    private fun visibleActions(playState: PlayScreenUiState): List<AndroidVisibleAction> {
        val actions = playState.actionAvailability
        return buildList {
            if (actions.canCapture) add(AndroidVisibleAction.CAPTURE)
            if (actions.canRob) add(AndroidVisibleAction.ROB)
            if (actions.canEat) add(AndroidVisibleAction.EAT)
            if (actions.canCarry) add(AndroidVisibleAction.CARRY)
            // At these stages skipping offers the same choice as ending the turn.
            val skipDuplicatesEnd = actions.canEndTurn &&
                actions.activePhase in listOf(TurnPhase.DECIDE, TurnPhase.END)
            if (actions.canSkip && !skipDuplicatesEnd) add(AndroidVisibleAction.SKIP)
            if (actions.canEndTurn) add(AndroidVisibleAction.END_TURN)
        }
    }

    private fun buildHungerMarkers(): List<AndroidHungerMarkerUiState> {
        val engine = controller.engine ?: return emptyList()
        val currentPlayer = controller.currentPlayer
        val activePlayers = engine.players.mapIndexedNotNull { layoutIndex, player ->
            if (player.isEliminated) null else player to layoutIndex
        }

        // Draw the current marker last for z-order without changing its meter lane.
        return (
            activePlayers.filter { (player) -> player != currentPlayer } +
                activePlayers.filter { (player) -> player == currentPlayer }
            ).map { (player, layoutIndex) ->
                AndroidHungerMarkerUiState(
                    playerId = player.id,
                    health = player.health,
                    isCurrent = player == controller.currentPlayer,
                    layoutIndex = layoutIndex,
                )
            }
    }

    private fun buildBoardCells(): List<AndroidBoardCellUiState> {
        val engine = controller.engine ?: return emptyList()
        val highlights = buildHighlights()
        val playersByPosition = engine.players
            .filter { !it.isEliminated }
            .groupBy { it.position }
        val currentPlayer = controller.currentPlayer
        val tilesByPosition = buildMap {
            for (row in 0 until Board.ROWS) {
                for (col in 0 until Board.COLS) {
                    val position = Position(col, row)
                    engine.boardState.getTile(position)?.toAndroidTileUiState()?.let { tile ->
                        put(position, tile)
                    }
                }
            }
        }

        return buildList {
            for (row in 0 until Board.ROWS) {
                for (col in 0 until Board.COLS) {
                    val position = Position(col, row)
                    val cell = engine.board.getCell(position) ?: continue
                    if (cell.type == CellType.INVALID) continue
                    val tile = tilesByPosition[position]
                    val isCurrentPlayerCell = currentPlayer?.position == position

                    add(
                        AndroidBoardCellUiState(
                            position = position,
                            cellType = cell.type,
                            tile = tile,
                            foods = engine.foodsAt(position).map {
                                AndroidFoodUiState(it.type, it.isFaceDown)
                            },
                            players = playersByPosition[position].orEmpty().map { player ->
                                AndroidPlayerTokenUiState(
                                    playerId = player.id,
                                    accessibilityLabel = boardPlayerContentDescription(
                                        name = player.name,
                                        isCurrent = player == currentPlayer,
                                    ),
                                    isCurrent = player == controller.currentPlayer,
                                    carriedFoodType = player.carriedFood?.type,
                                )
                            },
                            highlight = highlights[position],
                            isCurrentPlayerCell = isCurrentPlayerCell,
                            connectionEdges = connectionEdgesForCell(
                                tile = tile,
                                cellType = cell.type,
                                isCurrentCell = isCurrentPlayerCell,
                                neighborCellType = { direction ->
                                    neighborPosition(position, direction)?.let { neighbor ->
                                        engine.board.getCell(neighbor)?.type
                                    }
                                },
                                neighborOpenSides = { direction ->
                                    neighborPosition(position, direction)?.let { neighbor ->
                                        tilesByPosition[neighbor]?.openSides
                                    }
                                },
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun buildHighlights(): Map<Position, AndroidHighlightTone> {
        val engine = controller.engine ?: return emptyMap()
        return when (engine.currentPhase) {
            TurnPhase.DIG -> {
                val targets = controller.pendingDigPlacement?.let { setOf(it.position) }
                    ?: controller.digTargets().toSet()
                targets.associateWith { AndroidHighlightTone.DIG }
            }
            TurnPhase.MOVE -> controller.moveTargets().associateWith { AndroidHighlightTone.MOVE }
            TurnPhase.CAPTURE -> if (controller.canCapture()) {
                setOfNotNull(controller.currentPlayer?.position).associateWith { AndroidHighlightTone.CAPTURE }
            } else {
                emptyMap()
            }
            TurnPhase.DECIDE,
            TurnPhase.END,
            -> emptyMap()
        }
    }

    private fun HoleTile.toAndroidTileUiState(): AndroidTileUiState =
        AndroidTileUiState(
            shape = shape,
            rotation = rotationFor(this),
            isFaceDown = isFaceDown,
            openSides = androidTileOpenSides(shape, rotationFor(this), isFaceDown),
        )

    private fun Player.toAndroidGameResultPlayerUiState(winnerPlayerId: Int?): AndroidGameResultPlayerUiState =
        AndroidGameResultPlayerUiState(
            playerId = id,
            name = name,
            score = score,
            health = health,
            isEliminated = isEliminated,
            isWinner = id == winnerPlayerId,
        )

    private fun rotationFor(tile: HoleTile): Rotation =
        Rotation.entries.firstOrNull { rotation ->
            HoleTile(tile.shape).rotate(rotation).openSides == tile.openSides
        } ?: Rotation.DEG_0
}

internal fun androidTileOpenSides(
    shape: TileShape,
    rotation: Rotation,
    isFaceDown: Boolean,
): Set<Direction> =
    if (isFaceDown) {
        emptySet()
    } else {
        HoleTile(shape).rotate(rotation).openSides
    }

internal fun connectionToneFor(
    currentOpenSides: Set<Direction>,
    currentCellType: CellType,
    neighborOpenSides: Set<Direction>?,
    neighborCellType: CellType?,
    direction: Direction,
    isCurrentCell: Boolean,
): AndroidConnectionTone? {
    if (neighborCellType == null || neighborCellType == CellType.INVALID) return null
    val currentHasPath = when (currentCellType) {
        CellType.NEST -> true
        CellType.GROUND -> isCurrentCell
        else -> direction in currentOpenSides
    }
    if (!currentHasPath) return null
    if (!isCurrentCell) return AndroidConnectionTone.OPEN
    if (
        (currentCellType == CellType.NEST && neighborCellType == CellType.GROUND) ||
        (currentCellType == CellType.GROUND && neighborCellType == CellType.NEST)
    ) {
        return AndroidConnectionTone.BLOCKED
    }
    val neighborHasPath = when (neighborCellType) {
        CellType.NEST,
        CellType.GROUND,
        -> true
        else -> neighborOpenSides?.contains(direction.opposite()) == true
    }
    return if (neighborHasPath) {
        AndroidConnectionTone.CONNECTED
    } else {
        AndroidConnectionTone.BLOCKED
    }
}

internal fun connectionEdgesForCell(
    tile: AndroidTileUiState?,
    cellType: CellType,
    isCurrentCell: Boolean,
    neighborCellType: (Direction) -> CellType?,
    neighborOpenSides: (Direction) -> Set<Direction>?,
): List<AndroidConnectionEdgeUiState> {
    return Direction.entries.mapNotNull { direction ->
        val tone = connectionToneFor(
            currentOpenSides = tile?.takeUnless { it.isFaceDown }?.openSides.orEmpty(),
            currentCellType = cellType,
            neighborOpenSides = neighborOpenSides(direction),
            neighborCellType = neighborCellType(direction),
            direction = direction,
            isCurrentCell = isCurrentCell,
        ) ?: return@mapNotNull null
        AndroidConnectionEdgeUiState(direction, tone)
    }
}

private fun neighborPosition(position: Position, direction: Direction): Position? =
    when (direction) {
        Direction.TOP -> Position(position.col, position.row - 1)
        Direction.RIGHT -> Position(position.col + 1, position.row)
        Direction.BOTTOM -> Position(position.col, position.row + 1)
        Direction.LEFT -> Position(position.col - 1, position.row)
    }.takeIf { it.col in 0 until Board.COLS && it.row in 0 until Board.ROWS }

private fun defaultSetupPlayerIds(playerCount: Int): List<Int> =
    MoguraGameController.defaultConfigs(playerCount).mapIndexed { index, config ->
        config.playerId ?: index
    }

private fun defaultSetupNestPositions(playerCount: Int): List<Position> =
    MoguraGameController.defaultConfigs(playerCount).map { it.nestPosition }

private fun <T> List<T>.swappingSelection(seatIndex: Int, selectedValue: T): List<T> {
    val currentValue = getOrNull(seatIndex) ?: return this
    val otherSeatIndex = indexOfFirst { it == selectedValue }.takeIf { it >= 0 && it != seatIndex }
    return toMutableList().also { values ->
        values[seatIndex] = selectedValue
        if (otherSeatIndex != null) {
            values[otherSeatIndex] = currentValue
        }
    }
}
