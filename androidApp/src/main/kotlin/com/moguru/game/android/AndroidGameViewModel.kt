package com.moguru.game.android

import androidx.lifecycle.ViewModel
import com.moguru.game.cpu.CpuAction
import com.moguru.game.cpu.CpuStrategy
import com.moguru.game.cpu.CpuDifficulty
import com.moguru.game.cpu.applyCpuAction
import com.moguru.game.cpu.cpuObservation
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
import com.moguru.game.presenter.PendingCaptureRoll
import com.moguru.game.presenter.PlayScreenUiState
import com.moguru.game.presenter.TurnConsumptionAnimationEvent
import com.moguru.game.persistence.validate
import com.moguru.game.tutorial.TutorialAction
import com.moguru.game.tutorial.TutorialControl
import com.moguru.game.tutorial.TutorialLessonId
import com.moguru.game.tutorial.TutorialProgress
import com.moguru.game.tutorial.TutorialScenarios
import com.moguru.game.tutorial.TutorialSession
import com.moguru.game.tutorial.TutorialStatus
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
    val digPreviewPosition: Position? = null,
    /** The displayed current player is CPU-operated, so no human game input is offered. */
    val cpuTurn: Boolean = false,
    val cpuRouletteSpinning: Boolean = false,
    val cpuFault: AndroidCpuFault? = null,
    /** Identifies this capture within [playbackGeneration], including the reveal and landing. */
    val diceRouletteEventId: Long? = null,
    /** The practice lesson on the play screen. Practice never reads or writes the match save. */
    val tutorial: AndroidTutorialUiState? = null,
    /** The practice lesson list, while it is open. */
    val tutorialMenu: AndroidTutorialMenuUiState? = null,
    /** The completion screen of the lesson just finished, once its playback is over. */
    val tutorialCompletion: AndroidTutorialCompletionUiState? = null,
    /** A lesson interrupted when the app last stopped, offered on the entry screens. */
    val tutorialResume: AndroidTutorialLessonUiState? = null,
)

data class AndroidTutorialUiState(
    val lesson: TutorialLessonId,
    val title: String,
    /** 1-based chapter number. */
    val chapter: Int,
    val chapterCount: Int,
    val stage: Int,
    val stageCount: Int,
    val text: String,
    /** Why the last operation was refused and what to do instead, until something is accepted. */
    val notice: String?,
    val targetCells: Set<Position>,
    val controls: Set<TutorialControl>,
    /** 1-based explanation page while an explanation waits for 「次へ」. */
    val page: Int?,
    val pageCount: Int?,
    /** Passed back with 「次へ」, so a repeated tap on an old page is ignored. */
    val explanationSerial: Long,
    val opponentTurn: Boolean,
    val completed: Boolean,
) {
    val awaitingAcknowledgement: Boolean get() = page != null
}

data class AndroidTutorialLessonUiState(
    val lesson: TutorialLessonId,
    val title: String,
    val summary: String,
    val chapterCount: Int,
    val completed: Boolean,
    /** 0-based chapter that can be resumed, or null. */
    val resumeChapter: Int?,
)

data class AndroidTutorialMenuUiState(
    val lessons: List<AndroidTutorialLessonUiState>,
    /** Progress could not be read, so the list starts from no record. */
    val progressLoadFailed: Boolean,
    val progressSaveFailed: Boolean,
)

data class AndroidTutorialCompletionUiState(
    val lesson: TutorialLessonId,
    val title: String,
    val text: String,
    val isBasic: Boolean,
    val progressSaveFailed: Boolean,
)

/** Where 「練習を終了」 and the completion screen lead. */
enum class TutorialExit {
    /** The screen the practice was opened from. */
    BACK,
    LESSONS,
    SETUP,
}

/** What the screen showed before practice began, restored when it ends. */
private data class TutorialReturn(
    val showResumeEntry: Boolean,
    val boardPiecesTransparent: Boolean,
    val selectedMovePosition: Position?,
)

/** Why CPU play stopped. It continues only after an explicit retry. */
enum class AndroidCpuFault {
    NO_DECISION,
    ILLEGAL_ACTION,
    NO_PROGRESS,
}

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
    val isCpu: Boolean = false,
    val cpuDifficulty: CpuDifficulty = CpuDifficulty.NORMAL,
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

/** A playback or dice landing the UI reported as finished. */
private sealed interface PresentationCompletion {
    data class DiceLanding(val eventId: Long, val roll: PendingCaptureRoll) : PresentationCompletion
    data class Capture(val eventId: Long) : PresentationCompletion
    data class Eat(val eventId: Long) : PresentationCompletion
    data class TurnConsumption(val eventId: Long) : PresentationCompletion
}

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
    controller: MoguraGameController = MoguraGameController(),
    private val saveRepository: GameSaveRepository = MemoryGameSaveRepository(),
    private val saveIo: GameSaveIo = ImmediateGameSaveIo,
    private val cpuStrategy: CpuStrategy? = null,
    private val cpuScheduler: CpuStepScheduler = ManualCpuStepScheduler(),
    private val tutorialProgressRepository: TutorialProgressRepository = MemoryTutorialProgressRepository(),
) : ViewModel() {
    /** The match, with its own randomness. Practice never replaces or plays it. */
    private var gameController: MoguraGameController = controller
    private var tutorial: TutorialSession? = null
    /** The game on screen: the practice game during a lesson, otherwise the match. */
    private val controller: MoguraGameController
        get() = tutorial?.controller ?: gameController
    /** Players moved automatically: the scripted opponent in practice (none alone), otherwise the CPU seats. */
    private val automatedPlayerIds: Set<Int>
        get() = tutorial?.let { setOfNotNull(it.opponentPlayerId) } ?: cpuPlayerIds
    private var tutorialProgress = TutorialProgress()
    /** Counts local progress changes. A later load or an older save completion must not undo them. */
    private var tutorialProgressRevision = 0L
    private var tutorialProgressLoadFailed = false
    private var tutorialProgressSaveFailed = false
    private var showTutorialMenu = false
    private var tutorialReturn: TutorialReturn? = null
    private var tutorialRecordedChapter: Int? = null
    private var tutorialCompleted: TutorialLessonId? = null
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
    // The operator belongs to the seat during setup, so swapping moles does not move it.
    private var setupCpuSeats = List(selectedPlayerCount) { false }
    private var setupCpuDifficulties = List(selectedPlayerCount) { CpuDifficulty.NORMAL }
    private val difficultyStrategies = CpuDifficulty.entries.associateWith { it.createStrategy() }
    private var selectedStartPlayerIndex = 0
    private var gameResultOverlayDismissed = false
    private var captureAnimation: AndroidCaptureAnimationUiState? = null
    private val captureFailureSoundEffectTrigger = CaptureFailureSoundEffectTrigger()
    private var eatAnimation: EatAnimationEvent? = null
    private val eatRecoverySoundEffectTrigger = EatRecoverySoundEffectTrigger()
    private var turnConsumptionAnimation: TurnConsumptionAnimationEvent? = null
    private var turnConsumptionDisplaySnapshot: AndroidTurnConsumptionDisplaySnapshot? = null
    private var turnConsumptionPostMessage: String? = null
    // Fixed to player IDs once a game starts, and saved with it.
    private var cpuPlayerIds: Set<Int> = emptySet()
    private var cpuDifficulties: Map<Int, CpuDifficulty> = emptyMap()
    private var cpuHostResumed = false
    private var cpuPlayScreenClear = false
    private var cpuStep: CpuStepCancellation? = null
    private var cpuStepToken = 0L
    private var cpuFault: AndroidCpuFault? = null
    private var cpuMovePreview: Position? = null
    private var cpuRouletteSpinning = false
    private var deferredCompletion: PresentationCompletion? = null

    private val _uiState = MutableStateFlow(
        snapshot(
            isGameStarted = false,
            lastMessage = null,
        ),
    )
    val uiState: StateFlow<AndroidGameUiState> = _uiState.asStateFlow()

    init { reloadSavedGame() }

    fun reloadSavedGame() {
        if (setupBlocked()) return
        persistenceBusy = true
        publishPersistence()
        val generation = playbackGeneration
        // Learning progress is read in the same task, so the match save still loads in one step.
        // Its result never decides the match save's state.
        saveIo.execute({
            val progress = runCatching { tutorialProgressRepository.load() }
            val game = runCatching {
                saveRepository.load()?.also {
                    it.game.validate()
                    it.requireCpuParticipants()
                }
            }
            game to progress
        }) { loaded ->
            if (!alive || generation != playbackGeneration) return@execute
            persistenceBusy = false
            val parts = loaded.getOrNull()
            parts?.second?.let(::applyLoadedTutorialProgress)
            val result: Result<SavedGame?> = parts?.first
                ?: Result.failure(loaded.exceptionOrNull() ?: IllegalStateException("Load did not finish"))
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
        if (setupBlocked()) return
        showResumeEntry = false
        confirmOverwrite = false
        _uiState.value = snapshot(false, null)
    }

    fun cancelOverwrite() {
        confirmOverwrite = false
        publishPersistence()
    }

    fun confirmNewGame() {
        if (setupBlocked() || !confirmOverwrite) return
        confirmOverwrite = false
        beginSelectedGame()
    }

    fun resumeSavedGame() {
        if (operationBlocked() || loadIssue != null || tutorial != null) return
        val saved = savedGame ?: return
        val restored = runCatching { gameController.restored(saved.game).apply { settleAfterRestore() } }
        if (restored.isFailure) {
            loadIssue = GameSaveLoadIssue.CORRUPT
            showResumeEntry = true
            publishPersistence()
            return
        }
        gameController = restored.getOrThrow()
        playbackGeneration++
        clearPresentation()
        selectedPlayerCount = saved.game.engine.players.size
        setupPlayerIds = saved.game.engine.players.map { it.id }
        setupNestPositions = saved.game.engine.players.map { it.nestPosition }
        setupCpuSeats = saved.game.engine.players.map { it.id in saved.cpuPlayerIds }
        cpuPlayerIds = saved.cpuPlayerIds
        cpuDifficulties = saved.cpuDifficulties
        setupCpuDifficulties = setupPlayerIds.map { cpuDifficulties[it] ?: CpuDifficulty.NORMAL }
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
        if (humanInputBlocked() || position !in controller.moveTargets()) return
        val refused = tutorial?.perform(TutorialAction.SelectMoveTarget(position))?.accepted == false
        if (!refused) selectedMovePosition = position
        refresh(_uiState.value.lastMessage)
    }

    fun setBoardPiecesTransparent(transparent: Boolean) {
        if (operationBlocked() || animationInProgress()) return
        boardPiecesTransparent = transparent
        refresh(_uiState.value.lastMessage)
    }

    private fun operationBlocked() = !alive || persistenceBusy || pendingSave != null

    /** Setup and the match save stay as they are while practice is on screen. */
    private fun setupBlocked() = operationBlocked() || tutorial != null

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
        _uiState.value = _uiState.value.copy(
            persistence = persistenceState(),
            tutorialMenu = tutorialMenuUiState(),
            tutorialResume = tutorialResumeUiState(),
        )
    }

    private fun commitState(next: AndroidGameUiState) {
        if (!next.isGameStarted) {
            _uiState.value = next.copy(persistence = persistenceState())
            return
        }
        if (tutorial != null) {
            // Practice is published without saving: the match save stays exactly as it was.
            syncTutorialProgress()
            publishCommitted(next.copy(tutorialCompletion = tutorialCompletionUiState()))
            return
        }
        check(pendingSave == null)
        val game = controller.exportSnapshot()
        val previous = savedGame
        if (previous != null && game == previous.game &&
            next.selectedMovePosition == previous.selectedMovePosition &&
            boardPiecesTransparent == previous.boardPiecesTransparent &&
            cpuPlayerIds == previous.cpuPlayerIds && cpuDifficulties == previous.cpuDifficulties
        ) {
            publishCommitted(next)
            return
        }
        val saved = SavedGame(
            game = game, revision = (savedGame?.revision ?: 0) + 1,
            savedAtMillis = System.currentTimeMillis(),
            selectedMovePosition = next.selectedMovePosition,
            boardPiecesTransparent = boardPiecesTransparent,
            lastMessage = if (turnConsumptionAnimation != null) turnConsumptionPostMessage else next.lastMessage,
            cpuPlayerIds = cpuPlayerIds,
            cpuDifficulties = cpuDifficulties,
        )
        pendingSave = saved to next
        executePendingSave()
    }

    /**
     * The single point where a started game becomes visible: after its save, or when nothing
     * needed saving. Play continues from here, either with a held completion or the next CPU step.
     */
    private fun publishCommitted(next: AndroidGameUiState) {
        _uiState.value = next.copy(persistence = persistenceState())
        if (!deliverDeferredCompletion()) armCpuStep()
    }

    private fun executePendingSave() {
        val pending = pendingSave ?: return
        cancelCpuStep()
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
                publishCommitted(pending.second)
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
        resetCpuAutomation()
    }

    /** Leaving or replacing a game drops its reserved step, held completion, preview and fault. */
    private fun resetCpuAutomation() {
        cancelCpuStep()
        deferredCompletion = null
        cpuFault = null
        cpuMovePreview = null
        cpuRouletteSpinning = false
    }

    override fun onCleared() {
        alive = false
        playbackGeneration++
        resetCpuAutomation()
        saveIo.close()
        super.onCleared()
    }

    fun openTutorialMenu() {
        if (setupBlocked()) return
        showTutorialMenu = true
        publishPersistence()
    }

    fun closeTutorialMenu() {
        if (!showTutorialMenu) return
        showTutorialMenu = false
        publishPersistence()
    }

    /**
     * Opens a practice lesson at its recorded chapter, or from the beginning. The match controller,
     * its save and the setup stay as they are, so starting never asks to overwrite anything.
     */
    fun startTutorial(lesson: TutorialLessonId, fromBeginning: Boolean = false) {
        if (operationBlocked()) return
        val scenario = TutorialScenarios.of(lesson)
        val chapter = if (fromBeginning) 0 else tutorialProgress.resumeChapter(scenario) ?: 0
        val session = runCatching { TutorialSession(scenario, chapter) }.getOrNull() ?: TutorialSession(scenario, 0)
        if (tutorial == null) {
            tutorialReturn = TutorialReturn(showResumeEntry, boardPiecesTransparent, selectedMovePosition)
        }
        tutorial = session
        tutorialCompleted = null
        tutorialRecordedChapter = null
        showTutorialMenu = false
        playbackGeneration++
        clearPresentation()
        selectedMovePosition = null
        boardPiecesTransparent = false
        showResumeEntry = false
        commitState(snapshot(isGameStarted = true, lastMessage = null))
    }

    /** Continues the lesson that was open when the app last stopped, from its chapter start. */
    fun resumeInterruptedTutorial() {
        val lesson = tutorialProgress.interrupted()?.lesson ?: return
        startTutorial(lesson)
    }

    /**
     * 「次へ」 for the page the screen showed with [generation] and [explanationSerial]. Both are
     * required: a new lesson or attempt numbers its pages again, so a tap left over from an earlier
     * page, chapter attempt or lesson does nothing.
     */
    fun acknowledgeTutorial(generation: Long, explanationSerial: Long) {
        val session = tutorial ?: return
        if (operationBlocked() || animationInProgress()) return
        if (generation != playbackGeneration || explanationSerial != session.explanationSerial) return
        if (!session.acknowledge()) return
        resolveAfterSuccessfulAction(_uiState.value.lastMessage)
    }

    /** Back to the start of the current chapter. Reserved opponent steps and playback callbacks become stale. */
    fun restartTutorialChapter() {
        val session = tutorial ?: return
        if (operationBlocked()) return
        session.restartChapter()
        tutorialCompleted = null
        playbackGeneration++
        clearPresentation()
        selectedMovePosition = null
        commitState(snapshot(isGameStarted = true, lastMessage = null))
    }

    /** Leaves practice. The match, its controller and the setup come back exactly as before. */
    fun exitTutorial(destination: TutorialExit = TutorialExit.BACK) {
        val session = tutorial ?: return
        if (!alive) return
        if (session.status != TutorialStatus.COMPLETED) updateTutorialProgress(tutorialProgress.left())
        tutorial = null
        tutorialCompleted = null
        tutorialRecordedChapter = null
        playbackGeneration++
        clearPresentation()
        val back = tutorialReturn
        tutorialReturn = null
        boardPiecesTransparent = back?.boardPiecesTransparent ?: false
        selectedMovePosition = back?.selectedMovePosition
        val entry = back?.showResumeEntry ?: (savedGame != null || loadIssue != null)
        showResumeEntry = destination != TutorialExit.SETUP && entry
        showTutorialMenu = destination == TutorialExit.LESSONS
        _uiState.value = snapshot(isGameStarted = false, lastMessage = null)
    }

    /** Saving progress failed. Practice goes on; only the record is written again. */
    fun retryTutorialProgressSave() {
        if (!alive || !tutorialProgressSaveFailed) return
        saveTutorialProgress()
    }

    /** Human input in practice: the lesson accepts or refuses it before the game sees it. */
    private fun runPracticeAction(action: TutorialAction) {
        val session = tutorial ?: return
        val displayBeforeAction = displayedTurnConsumptionSnapshot()
        val result = session.perform(action)
        val game = result.game
        if (result.accepted && game != null) {
            presentSuccessfulAction(game, displayBeforeAction, game.message)
        } else {
            // A refusal only changes the lesson's notice, which the guidance shows.
            refresh(_uiState.value.lastMessage)
        }
    }

    /** Records a new chapter or the completion once, whenever practice is published. */
    private fun syncTutorialProgress() {
        val session = tutorial ?: return
        val scenario = session.scenario
        if (session.status == TutorialStatus.COMPLETED) {
            if (tutorialCompleted != scenario.lesson) {
                tutorialCompleted = scenario.lesson
                updateTutorialProgress(tutorialProgress.completed(scenario))
            }
        } else if (session.chapterIndex != tutorialRecordedChapter) {
            tutorialRecordedChapter = session.chapterIndex
            updateTutorialProgress(tutorialProgress.started(scenario, session.chapterIndex))
        }
    }

    private fun applyLoadedTutorialProgress(result: Result<TutorialProgress>) {
        // Anything recorded since start-up is newer than the file.
        if (tutorialProgressRevision != 0L) return
        result.fold(
            onSuccess = {
                tutorialProgress = it
                tutorialProgressLoadFailed = false
            },
            onFailure = { tutorialProgressLoadFailed = true },
        )
    }

    private fun updateTutorialProgress(next: TutorialProgress) {
        if (next == tutorialProgress) return
        tutorialProgress = next
        tutorialProgressRevision++
        saveTutorialProgress()
    }

    /** Writes the latest progress. Only the newest request decides whether a failure is shown. */
    private fun saveTutorialProgress() {
        val revision = tutorialProgressRevision
        val progress = tutorialProgress
        saveIo.execute({ tutorialProgressRepository.save(progress) }) { result ->
            if (!alive || revision != tutorialProgressRevision) return@execute
            tutorialProgressSaveFailed = result.isFailure
            if (result.isSuccess) tutorialProgressLoadFailed = false
            _uiState.value = _uiState.value.copy(
                tutorialMenu = tutorialMenuUiState(),
                tutorialCompletion = tutorialCompletionUiState(),
                tutorialResume = tutorialResumeUiState(),
            )
        }
    }

    private fun tutorialUiState(): AndroidTutorialUiState? {
        val session = tutorial ?: return null
        val scenario = session.scenario
        val guidance = session.guidance
        val status = session.status
        return AndroidTutorialUiState(
            lesson = scenario.lesson,
            title = scenario.title,
            chapter = session.chapterIndex + 1,
            chapterCount = scenario.chapterCount,
            stage = guidance.stage,
            stageCount = scenario.stageCount,
            text = guidance.text,
            notice = guidance.notice,
            targetCells = guidance.targetCells,
            controls = guidance.controls,
            page = guidance.page,
            pageCount = guidance.pageCount,
            explanationSerial = session.explanationSerial,
            opponentTurn = status == TutorialStatus.OPPONENT_TURN,
            completed = status == TutorialStatus.COMPLETED,
        )
    }

    private fun tutorialLessonUiState(lesson: TutorialLessonId): AndroidTutorialLessonUiState {
        val scenario = TutorialScenarios.of(lesson)
        return AndroidTutorialLessonUiState(
            lesson = lesson,
            title = scenario.title,
            summary = scenario.summary,
            chapterCount = scenario.chapterCount,
            completed = tutorialProgress.isCompleted(lesson),
            resumeChapter = tutorialProgress.resumeChapter(scenario),
        )
    }

    private fun tutorialMenuUiState(): AndroidTutorialMenuUiState? =
        if (!showTutorialMenu || tutorial != null) {
            null
        } else {
            AndroidTutorialMenuUiState(
                lessons = TutorialLessonId.entries.map(::tutorialLessonUiState),
                progressLoadFailed = tutorialProgressLoadFailed,
                progressSaveFailed = tutorialProgressSaveFailed,
            )
        }

    /** Shown only once the last playback is over, so it never covers a running meal or capture. */
    private fun tutorialCompletionUiState(): AndroidTutorialCompletionUiState? {
        val session = tutorial ?: return null
        if (session.status != TutorialStatus.COMPLETED || animationInProgress()) return null
        val scenario = session.scenario
        return AndroidTutorialCompletionUiState(
            lesson = scenario.lesson,
            title = scenario.title,
            text = scenario.completionText,
            isBasic = scenario.lesson == TutorialLessonId.BASIC,
            progressSaveFailed = tutorialProgressSaveFailed,
        )
    }

    private fun tutorialResumeUiState(): AndroidTutorialLessonUiState? =
        if (tutorial != null) null else tutorialProgress.interrupted()?.lesson?.let(::tutorialLessonUiState)

    /** The destination the lesson guides to, used as the first tentative move target. */
    private fun guidedMoveTarget(targets: Set<Position>): Position? {
        val session = tutorial ?: return null
        if (session.status != TutorialStatus.LEARNER_ACTION) return null
        val guidance = session.guidance
        if (TutorialControl.MOVE !in guidance.controls) return null
        return guidance.targetCells.firstOrNull { it in targets }
    }

    fun selectPlayerCount(playerCount: Int) {
        if (setupBlocked()) return
        require(playerCount in 2..4) { "プレイヤー人数は2〜4人にしてください。" }
        resetSetupDefaults(playerCount)
        _uiState.value = snapshot(
            isGameStarted = _uiState.value.isGameStarted,
            lastMessage = null,
        )
    }

    fun selectPlayerMole(seatIndex: Int, playerId: Int) {
        if (setupBlocked()) return
        if (seatIndex !in 0 until selectedPlayerCount) return
        if (MoguraGameController.moleOptions.none { it.playerId == playerId }) return

        setupPlayerIds = setupPlayerIds.swappingSelection(seatIndex, playerId)
        refresh(null)
    }

    fun selectPlayerNest(seatIndex: Int, nestPosition: Position) {
        if (setupBlocked()) return
        if (seatIndex !in 0 until selectedPlayerCount) return
        if (nestPosition !in MoguraGameController.nestPositions) return

        setupNestPositions = setupNestPositions.swappingSelection(seatIndex, nestPosition)
        refresh(null)
    }

    fun selectPlayerKind(seatIndex: Int, isCpu: Boolean) {
        if (setupBlocked()) return
        if (seatIndex !in 0 until selectedPlayerCount) return

        setupCpuSeats = setupCpuSeats.toMutableList().also { it[seatIndex] = isCpu }
        refresh(null)
    }

    fun selectCpuDifficulty(seatIndex: Int, difficulty: CpuDifficulty) {
        if (setupBlocked() || _uiState.value.isGameStarted) return
        if (seatIndex !in 0 until selectedPlayerCount || !setupCpuSeats[seatIndex]) return
        setupCpuDifficulties = setupCpuDifficulties.toMutableList().also { it[seatIndex] = difficulty }
        refresh(null)
    }

    fun selectStartPlayer(seatIndex: Int) {
        if (setupBlocked()) return
        if (seatIndex !in 0 until selectedPlayerCount) return

        selectedStartPlayerIndex = seatIndex
        refresh(null)
    }

    fun startSelectedGame() {
        if (setupBlocked()) return
        if (savedGame != null || loadIssue != null) {
            confirmOverwrite = true
            publishPersistence()
            return
        }
        beginSelectedGame()
    }

    private fun beginSelectedGame() {
        if (tutorial != null) return
        playbackGeneration++
        selectedMovePosition = null
        boardPiecesTransparent = false
        showResumeEntry = false
        showTutorialMenu = false
        clearPresentation()
        cpuPlayerIds = configuredCpuPlayerIds()
        cpuDifficulties = configuredCpuDifficulties()
        val result = gameController.startNewGame(setupConfigs(), selectedStartPlayerIndex)
        commitState(snapshot(
            isGameStarted = true,
            lastMessage = messageForCurrentTurn(result.message),
        ))
    }

    fun startNewGame(playerCount: Int) {
        if (operationBlocked() || tutorial != null) return
        playbackGeneration++
        selectedMovePosition = null
        boardPiecesTransparent = false
        showResumeEntry = false
        showTutorialMenu = false
        clearPresentation()
        resetSetupDefaults(playerCount)
        cpuPlayerIds = configuredCpuPlayerIds()
        cpuDifficulties = configuredCpuDifficulties()
        val result = gameController.startNewGame(setupConfigs(), selectedStartPlayerIndex)
        commitState(snapshot(
            isGameStarted = true,
            lastMessage = messageForCurrentTurn(result.message),
        ))
    }

    fun returnToSetup() {
        if (operationBlocked()) return
        if (tutorial != null) return exitTutorial()
        playbackGeneration++
        showResumeEntry = savedGame != null || loadIssue != null
        clearPresentation()
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
        if (humanInputBlocked()) return
        val engine = controller.engine ?: return refresh(null)
        if (tutorial != null) {
            val practice = when (engine.currentPhase) {
                TurnPhase.DIG -> TutorialAction.SelectDigCell(position)
                TurnPhase.MOVE -> TutorialAction.Move(position)
                TurnPhase.CAPTURE -> TutorialAction.Capture.takeIf { controller.currentPlayer?.position == position }
                TurnPhase.DECIDE, TurnPhase.END -> null
            }
            if (practice != null) return runPracticeAction(practice)
        }
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
        runAction(TutorialAction.SelectDigTile(choice)) { controller.selectPendingDigTile(choice) }
    }

    fun selectRotation(rotation: Rotation) {
        runAction(TutorialAction.RotateDigTile(rotation)) { controller.setPendingDigRotation(rotation) }
    }

    fun confirmDigPlacement() {
        runAction(TutorialAction.ConfirmDig) { controller.confirmPendingDig() }
    }

    fun selectCaptureTarget(index: Int) {
        runAction(TutorialAction.SelectCaptureTarget(index)) { controller.selectCaptureTarget(index) }
    }

    fun selectRobberyTarget(index: Int) {
        runAction(TutorialAction.SelectRobberyTarget(index)) { controller.selectRobberyTarget(index) }
    }

    fun capture() {
        runAction(TutorialAction.Capture) { controller.captureCurrentPosition() }
    }

    fun rob() {
        runAction(TutorialAction.Rob) { controller.robSelectedFood() }
    }

    /**
     * ルーレットを止めて出目を確定する（着地演出は UI 側で継続）。
     * 連打などで既に確定済みの場合は黙って無視する。
     */
    fun stopDiceRoulette() {
        if (humanInputBlocked()) return
        rollDiceRoulette()
    }

    private fun rollDiceRoulette(): Boolean {
        val session = tutorial
        val result = if (session != null) {
            // Only the learner captures with dice in practice, so the lesson decides first.
            session.perform(TutorialAction.RollDice).let { it.game ?: GameActionResult(false, it.message) }
        } else {
            controller.rollCaptureDice()
        }
        if (result.success) refresh(null) else if (session != null) refresh(_uiState.value.lastMessage)
        return result.success
    }

    /**
     * 着地演出の終了後に捕獲を解決し、オーバーレイを閉じる。
     * 再コンポーズによる重複呼び出しは黙って無視する。
     */
    fun finishDiceRoulette(
        generation: Long = playbackGeneration,
        eventId: Long? = _uiState.value.diceRouletteEventId,
    ) {
        if (!alive || generation != playbackGeneration || !_uiState.value.isGameStarted) return
        if (eventId == null || eventId != _uiState.value.diceRouletteEventId) return
        val pending = controller.pendingCaptureRoll ?: return
        if (pending.roll != null) {
            onPresentationFinished(PresentationCompletion.DiceLanding(eventId, pending), generation)
        } else if (!humanInputBlocked()) {
            // Nothing has landed: this is the human confirming a capture that needs no dice.
            resolveDiceRoulette()
        }
    }

    private fun resolveDiceRoulette(): Boolean {
        val boardBefore = AndroidBoardUiState(buildBoardCells())
        val session = tutorial
        val result = if (session != null) {
            session.perform(TutorialAction.ResolveCapture).let { it.game ?: GameActionResult(false, it.message) }
        } else {
            controller.resolveCaptureRoll()
        }
        if (!result.success) {
            if (session != null) refresh(_uiState.value.lastMessage)
            return false
        }
        cpuRouletteSpinning = false
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
        return true
    }

    /**
     * The ViewModel retains this consumption state when the Activity is recreated. [generation] is
     * the one the screen showed [event] in: a restarted chapter, another lesson or a new match
     * reuses event IDs and plays them again, while an event of an earlier generation stays silent.
     */
    internal fun captureFailureSoundEffectFor(
        event: CaptureAnimationEvent?,
        generation: Long = playbackGeneration,
    ): AndroidSoundEffect? =
        if (generation != playbackGeneration) null
        else captureFailureSoundEffectTrigger.soundEffectFor(event, generation)

    /** Keep played meal IDs across Activity recreation, per generation just like capture sounds. */
    internal fun eatRecoverySoundEffectFor(
        event: EatAnimationEvent?,
        generation: Long = playbackGeneration,
    ): AndroidSoundEffect? =
        if (generation != playbackGeneration) null
        else eatRecoverySoundEffectTrigger.soundEffectFor(event, generation)

    /** An old completion callback must never finish a newer capture or advance twice. */
    fun finishCaptureAnimation(eventId: Long, generation: Long = playbackGeneration) {
        onPresentationFinished(PresentationCompletion.Capture(eventId), generation)
    }

    fun eat() {
        runAction(TutorialAction.Eat) { controller.eatPendingFood() }
    }

    /** An old completion callback must never finish a newer meal or advance twice. */
    fun finishEatAnimation(eventId: Long, generation: Long = playbackGeneration) {
        onPresentationFinished(PresentationCompletion.Eat(eventId), generation)
    }

    /** Stale or duplicate callbacks must never advance the next turn twice. */
    fun finishTurnConsumptionAnimation(eventId: Long, generation: Long = playbackGeneration) {
        onPresentationFinished(PresentationCompletion.TurnConsumption(eventId), generation)
    }

    /**
     * Completion callbacks are not human input, so they stay open on CPU turns. A valid one that
     * arrives mid-save or while a CPU game or practice is paused is held and applied once play may continue.
     */
    private fun onPresentationFinished(completion: PresentationCompletion, generation: Long) {
        if (!alive || generation != playbackGeneration || !isShownPresentation(completion)) return
        if (completionMustWait()) {
            deferredCompletion = completion
            return
        }
        completePresentation(completion)
    }

    /** Only a playback the UI has actually been shown can finish; anything else is stale. */
    private fun isShownPresentation(completion: PresentationCompletion): Boolean {
        val shown = _uiState.value
        return when (completion) {
            is PresentationCompletion.DiceLanding -> shown.isGameStarted && !animationInProgress() &&
                shown.diceRouletteEventId == completion.eventId &&
                controller.pendingCaptureRoll == completion.roll &&
                shown.playState.diceRouletteResult == completion.roll.roll
            is PresentationCompletion.Capture -> captureAnimation?.event?.id == completion.eventId &&
                shown.captureAnimation?.event?.id == completion.eventId
            is PresentationCompletion.Eat -> eatAnimation?.id == completion.eventId &&
                shown.eatAnimation?.id == completion.eventId
            is PresentationCompletion.TurnConsumption -> turnConsumptionAnimation?.id == completion.eventId &&
                shown.turnConsumptionAnimation?.id == completion.eventId
        }
    }

    private fun completionMustWait(): Boolean =
        persistenceBusy || pendingSave != null ||
            ((tutorial != null || automatedPlayerIds.isNotEmpty()) && !cpuHostActive())

    private fun deliverDeferredCompletion(): Boolean {
        val completion = deferredCompletion ?: return false
        if (completionMustWait()) return false
        deferredCompletion = null
        if (!isShownPresentation(completion)) return false
        completePresentation(completion)
        return true
    }

    private fun completePresentation(completion: PresentationCompletion) {
        when (completion) {
            is PresentationCompletion.DiceLanding -> resolveDiceRoulette()
            is PresentationCompletion.Capture -> {
                captureAnimation = null
                resolveAfterSuccessfulAction(_uiState.value.lastMessage)
            }
            is PresentationCompletion.Eat -> {
                eatAnimation = null
                resolveAfterSuccessfulAction(_uiState.value.lastMessage)
            }
            is PresentationCompletion.TurnConsumption -> {
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
        }
    }

    fun carry() {
        runAction(TutorialAction.Carry) { controller.carryPendingFood() }
    }

    fun skip() {
        runAction(TutorialAction.Skip) { controller.skipPhase() }
    }

    fun finishTurn() {
        runAction(TutorialAction.FinishTurn) { controller.finishTurn() }
    }

    private fun humanInputBlocked(): Boolean =
        operationBlocked() || animationInProgress() || cpuControlsCurrentTurn()

    /**
     * Human action-bar input. In practice the lesson decides on [practice] before the game sees it.
     * CPU operations enter through [runCpuStep] instead.
     */
    private fun runAction(practice: TutorialAction, action: () -> GameActionResult) {
        if (humanInputBlocked()) return
        if (tutorial != null) return runPracticeAction(practice)
        val displayBeforeAction = displayedTurnConsumptionSnapshot()
        val result = action()
        if (result.success) {
            presentSuccessfulAction(result, displayBeforeAction, result.message)
        } else {
            refresh(result.message)
        }
    }

    /** Shared by human and CPU operations so both get the same meal and consumption playback. */
    private fun presentSuccessfulAction(
        result: GameActionResult,
        displayBeforeAction: AndroidTurnConsumptionDisplaySnapshot,
        message: String?,
    ) {
        val event = result.eatAnimation
        if (event != null) {
            eatAnimation = event
            // Keep the just-finished DECIDE state visible until the one-shot playback ends.
            refresh(message)
            return
        }
        resolveAfterSuccessfulAction(
            message = message,
            directTurnConsumption = result.turnConsumptionAnimation,
            directDisplaySnapshot = displayBeforeAction,
        )
    }

    /** Activity half of the CPU host gate, told directly because composition pauses in the background. */
    fun setCpuHostResumed(resumed: Boolean) {
        if (!alive || cpuHostResumed == resumed) return
        cpuHostResumed = resumed
        onCpuHostChanged()
    }

    /** UI half: the play screen is showing with rules, audio, history and menus closed. */
    fun setCpuPlayScreenClear(clear: Boolean) {
        if (!alive || cpuPlayScreenClear == clear) return
        cpuPlayScreenClear = clear
        onCpuHostChanged()
    }

    /** One more CPU attempt after a fault. Only the player can ask for it. */
    fun retryCpuStep() {
        if (operationBlocked() || cpuFault == null) return
        cpuFault = null
        refresh(_uiState.value.lastMessage)
    }

    private fun cpuHostActive(): Boolean = cpuHostResumed && cpuPlayScreenClear

    private fun onCpuHostChanged() {
        if (!cpuHostActive()) {
            cancelCpuStep()
        } else if (!deliverDeferredCompletion()) {
            armCpuStep()
        }
    }

    /** Read from the live controller: a frozen consumption display still shows the previous player. */
    private fun cpuControlsCurrentTurn(): Boolean =
        controller.engine?.gameState == GameState.PLAYING &&
            controller.currentPlayer?.let { it.id in automatedPlayerIds } == true

    private fun cpuStepReady(): Boolean =
        _uiState.value.isGameStarted && !operationBlocked() && !animationInProgress() &&
            cpuHostActive() && cpuFault == null && cpuControlsCurrentTurn() &&
            // The practice opponent also waits for every open explanation and stops at completion.
            (tutorial?.let { it.status == TutorialStatus.OPPONENT_TURN } ?: true) &&
            // A confirmed roll belongs to the overlay until its landing callback resolves it.
            controller.pendingCaptureRoll?.roll == null

    /** Reserves the next CPU step from a publish point. Nothing acts inline, e.g. inside a save callback. */
    private fun armCpuStep() {
        cancelCpuStep()
        if (!cpuStepReady()) return
        val generation = playbackGeneration
        val token = cpuStepToken
        val delayMillis = if (controller.pendingCaptureRoll != null) {
            CPU_CAPTURE_STAGE_MILLIS
        } else {
            CPU_STEP_DELAY_MILLIS
        }
        cpuStep = cpuScheduler.schedule(delayMillis) {
            // A cancelled or superseded reservation stays inert even if its timer still fires.
            if (alive && generation == playbackGeneration && token == cpuStepToken) {
                cancelCpuStep()
                runCpuStep()
            }
        }
    }

    private fun cancelCpuStep() {
        cpuStepToken++
        cpuStep?.cancel()
        cpuStep = null
    }

    /** One CPU operation, chosen from the game as it is now rather than when the step was reserved. */
    private fun runCpuStep() {
        if (!cpuStepReady()) return
        controller.pendingCaptureRoll?.let { return advanceCpuCapture(it) }
        // A previewed move is confirmed as shown instead of being decided and saved again.
        val previewed = cpuMovePreview?.takeIf { it in controller.moveTargets() }
        cpuMovePreview = previewed
        val session = tutorial
        val action = if (previewed != null) {
            CpuAction.Move(previewed)
        } else if (session != null) {
            // The practice opponent plays its scripted legal step instead of deciding.
            session.nextOpponentAction() ?: return stopCpu(AndroidCpuFault.NO_DECISION)
        } else {
            runCatching {
                val difficulty = cpuDifficulties[controller.currentPlayer?.id] ?: CpuDifficulty.NORMAL
                (cpuStrategy ?: difficultyStrategies.getValue(difficulty)).choose(controller.cpuObservation())
            }.getOrNull()
                ?: return stopCpu(AndroidCpuFault.NO_DECISION)
        }
        if (action is CpuAction.Move && previewed == null) return previewCpuMove(action.position)

        val displayBeforeAction = displayedTurnConsumptionSnapshot()
        val before = controller.exportSnapshot()
        val result = runCatching {
            if (session != null) {
                session.performOpponent(action).let { it.game ?: GameActionResult(false, it.message) }
            } else {
                controller.applyCpuAction(action)
            }
        }.getOrNull()
        when {
            result == null || !result.success -> stopCpu(AndroidCpuFault.ILLEGAL_ACTION)
            // Re-selecting the rotation already shown, for example, would otherwise repeat forever.
            controller.exportSnapshot() == before -> stopCpu(AndroidCpuFault.NO_PROGRESS)
            // The log already names the mole and what it did.
            else -> presentSuccessfulAction(result, displayBeforeAction, message = null)
        }
    }

    /** Shows the destination for one full step; the default selection waits just as long. */
    private fun previewCpuMove(position: Position) {
        if (position !in controller.moveTargets()) return stopCpu(AndroidCpuFault.ILLEGAL_ACTION)
        cpuMovePreview = position
        selectedMovePosition = position
        refresh(_uiState.value.lastMessage)
    }

    /** Reveal, spin, then roll. Landing and resolution stay with the overlay's callback. */
    private fun advanceCpuCapture(pending: PendingCaptureRoll) {
        val advanced = when {
            pending.food.escapeMap.isEmpty() -> resolveDiceRoulette()
            cpuRouletteSpinning -> rollDiceRoulette()
            else -> {
                cpuRouletteSpinning = true
                refresh(_uiState.value.lastMessage)
                true
            }
        }
        if (!advanced) stopCpu(AndroidCpuFault.ILLEGAL_ACTION)
    }

    /** No automatic retry and no handing the turn to a human: play waits for [retryCpuStep]. */
    private fun stopCpu(fault: AndroidCpuFault) {
        cpuFault = fault
        cpuMovePreview = null
        refresh(_uiState.value.lastMessage)
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
        // Rules actions advance play; a move preview belongs only to its current choice.
        // UI-only selection and transparency changes use refresh without clearing it.
        selectedMovePosition = null
        cpuMovePreview = null
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
        // In practice an open explanation or the finished lesson holds automatic progression.
        val session = tutorial
        val autoResult = if (session != null) session.advanceAutomatic() else controller.autoAdvanceWhileNoChoice()
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
        // Follows the displayed player, so the label stays with a frozen consumption display.
        val cpuTurn = isGameStarted && gameResult == null &&
            playState.currentPlayer.playerId?.let { it in automatedPlayerIds } == true
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
            visibleActions = if (isGameStarted && !animationInProgress() && !cpuTurn) {
                visibleActions(playState)
            } else {
                emptyList()
            },
            showDigControls = isGameStarted && !animationInProgress() && !cpuTurn &&
                playState.digCandidates.any { it.enabled },
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
                    // Practice starts on the guided destination instead of the first one.
                    ?: guidedMoveTarget(targets)
                    ?: targets.sortedWith(compareBy<Position> { it.row }.thenBy { it.col }).firstOrNull()
            } else null,
            boardPiecesTransparent = boardPiecesTransparent,
            playbackGeneration = playbackGeneration,
            digPreviewPosition = if (isGameStarted) controller.pendingDigPlacement?.position else null,
            cpuTurn = cpuTurn,
            cpuRouletteSpinning = cpuTurn && cpuRouletteSpinning && playState.diceRouletteActive,
            cpuFault = cpuFault.takeIf { cpuTurn },
            // Every capture resolution increments this counter, even if food, position and roll repeat.
            diceRouletteEventId = if (isGameStarted && controller.pendingCaptureRoll != null) {
                controller.exportSnapshot().captureAnimationId + 1
            } else null,
            tutorial = if (isGameStarted) tutorialUiState() else null,
            tutorialMenu = tutorialMenuUiState(),
            tutorialCompletion = tutorialCompletionUiState(),
            tutorialResume = tutorialResumeUiState(),
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
        setupCpuSeats = List(playerCount) { false }
        setupCpuDifficulties = List(playerCount) { CpuDifficulty.NORMAL }
        selectedStartPlayerIndex = 0
    }

    private fun configuredCpuPlayerIds(): Set<Int> =
        setupPlayerIds.filterIndexed { seatIndex, _ -> setupCpuSeats[seatIndex] }.toSet()

    private fun configuredCpuDifficulties(): Map<Int, CpuDifficulty> =
        setupPlayerIds.mapIndexedNotNull { index, id ->
            if (setupCpuSeats[index]) id to setupCpuDifficulties[index] else null
        }.toMap()

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
                isCpu = setupCpuSeats[index],
                cpuDifficulty = setupCpuDifficulties[index],
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
