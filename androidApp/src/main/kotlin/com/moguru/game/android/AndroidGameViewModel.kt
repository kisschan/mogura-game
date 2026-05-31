package com.moguru.game.android

import androidx.lifecycle.ViewModel
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Board
import com.moguru.game.model.CellType
import com.moguru.game.model.FoodType
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.presenter.DigTileChoice
import com.moguru.game.presenter.GameActionResult
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.presenter.PlayScreenUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AndroidGameUiState(
    val isGameStarted: Boolean,
    val selectedPlayerCount: Int,
    val playState: PlayScreenUiState,
    val boardState: AndroidBoardUiState,
    val visibleActions: List<AndroidVisibleAction>,
    val showDigControls: Boolean,
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
    val food: AndroidFoodUiState?,
    val players: List<AndroidPlayerTokenUiState>,
    val highlight: AndroidHighlightTone?,
)

data class AndroidTileUiState(
    val shape: TileShape,
    val rotation: Rotation,
    val isFaceDown: Boolean,
)

data class AndroidFoodUiState(
    val type: FoodType,
    val isFaceDown: Boolean,
)

data class AndroidPlayerTokenUiState(
    val playerId: Int,
    val name: String,
    val isCurrent: Boolean,
)

enum class AndroidHighlightTone {
    DIG,
    MOVE,
    CAPTURE,
}

enum class AndroidVisibleAction {
    CAPTURE,
    EAT,
    CARRY,
    SKIP,
    END_TURN,
}

class AndroidGameViewModel(
    private val controller: MoguraGameController = MoguraGameController(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        snapshot(
            isGameStarted = false,
            selectedPlayerCount = 2,
            lastMessage = null,
        ),
    )
    val uiState: StateFlow<AndroidGameUiState> = _uiState.asStateFlow()

    fun selectPlayerCount(playerCount: Int) {
        require(playerCount in 2..4) { "プレイヤー人数は2〜4人にしてください。" }
        _uiState.value = snapshot(
            isGameStarted = _uiState.value.isGameStarted,
            selectedPlayerCount = playerCount,
            lastMessage = null,
        )
    }

    fun startSelectedGame() {
        startNewGame(_uiState.value.selectedPlayerCount)
    }

    fun startNewGame(playerCount: Int) {
        val result = controller.startNewGame(playerCount)
        _uiState.value = snapshot(
            isGameStarted = true,
            selectedPlayerCount = playerCount,
            lastMessage = result.message,
        )
    }

    fun returnToSetup() {
        _uiState.value = snapshot(
            isGameStarted = false,
            selectedPlayerCount = _uiState.value.selectedPlayerCount,
            lastMessage = null,
        )
    }

    fun onCellClicked(position: Position) {
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
        refresh(result.message.takeIf { !result.success })
    }

    fun selectDigChoice(choice: DigTileChoice) {
        runAction { controller.selectPendingDigTile(choice) }
    }

    fun selectRotation(rotation: Rotation) {
        runAction { controller.setPendingDigRotation(rotation) }
    }

    fun capture() {
        runAction { controller.captureCurrentPosition() }
    }

    fun eat() {
        runAction { controller.eatPendingFood() }
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
        val result = action()
        refresh(if (result.success) result.message else result.message)
    }

    private fun refresh(lastMessage: String?) {
        _uiState.value = snapshot(
            isGameStarted = _uiState.value.isGameStarted,
            selectedPlayerCount = _uiState.value.selectedPlayerCount,
            lastMessage = lastMessage,
        )
    }

    private fun snapshot(
        isGameStarted: Boolean,
        selectedPlayerCount: Int,
        lastMessage: String?,
    ): AndroidGameUiState {
        val playState = controller.playScreenUiState()
        return AndroidGameUiState(
            isGameStarted = isGameStarted,
            selectedPlayerCount = selectedPlayerCount,
            playState = playState,
            boardState = AndroidBoardUiState(
                cells = if (isGameStarted) buildBoardCells() else emptyList(),
            ),
            visibleActions = if (isGameStarted) visibleActions(playState) else emptyList(),
            showDigControls = isGameStarted && playState.digCandidates.any { it.enabled },
            logs = if (isGameStarted) controller.logs.takeLast(5) else emptyList(),
            lastMessage = lastMessage,
        )
    }

    private fun visibleActions(playState: PlayScreenUiState): List<AndroidVisibleAction> {
        val actions = playState.actionAvailability
        return buildList {
            if (actions.canCapture) add(AndroidVisibleAction.CAPTURE)
            if (actions.canEat) add(AndroidVisibleAction.EAT)
            if (actions.canCarry) add(AndroidVisibleAction.CARRY)
            if (actions.canSkip) add(AndroidVisibleAction.SKIP)
            if (actions.canEndTurn) add(AndroidVisibleAction.END_TURN)
        }
    }

    private fun buildBoardCells(): List<AndroidBoardCellUiState> {
        val engine = controller.engine ?: return emptyList()
        val highlights = buildHighlights()
        val playersByPosition = engine.players
            .filter { !it.isEliminated }
            .groupBy { it.position }

        return buildList {
            for (row in 0 until Board.ROWS) {
                for (col in 0 until Board.COLS) {
                    val position = Position(col, row)
                    val cell = engine.board.getCell(position) ?: continue
                    if (cell.type == CellType.INVALID) continue

                    add(
                        AndroidBoardCellUiState(
                            position = position,
                            cellType = cell.type,
                            tile = engine.boardState.getTile(position)?.toAndroidTileUiState(),
                            food = engine.foodPositions[position]?.let {
                                AndroidFoodUiState(it.type, it.isFaceDown)
                            },
                            players = playersByPosition[position].orEmpty().map { player ->
                                AndroidPlayerTokenUiState(
                                    playerId = player.id,
                                    name = player.name,
                                    isCurrent = player == controller.currentPlayer,
                                )
                            },
                            highlight = highlights[position],
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
        )

    private fun rotationFor(tile: HoleTile): Rotation =
        Rotation.entries.firstOrNull { rotation ->
            HoleTile(tile.shape).rotate(rotation).openSides == tile.openSides
        } ?: Rotation.DEG_0
}
