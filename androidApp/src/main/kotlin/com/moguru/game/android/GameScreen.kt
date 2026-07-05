package com.moguru.game.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Popup
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.Direction
import com.moguru.game.model.FoodType
import com.moguru.game.model.Player
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.presenter.CaptureTargetDisplay
import com.moguru.game.presenter.CaptureOutcomeDisplay
import com.moguru.game.presenter.CaptureOutcomeKind
import com.moguru.game.presenter.DigCandidateDisplay
import com.moguru.game.presenter.DigTileChoice
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.presenter.RobberyTargetDisplay
import com.moguru.game.presenter.displayName

internal const val BOARD_HIGHLIGHT_Z = 15f
internal const val BOARD_TILE_Z = 20f
internal const val BOARD_CONNECTION_PORT_Z = 32f
internal const val BOARD_FOOD_Z = 40f
internal const val BOARD_PLAYER_BASE_Z = 45f
internal const val BOARD_CURRENT_CELL_RING_Z = 72f
internal const val BOARD_CURRENT_PLAYER_OUTLINE_Z = 75f
internal const val BOARD_CLICK_TARGET_Z = 80f

internal val MOBILE_PLAY_HUD_HEIGHT = 56.dp
internal val MOBILE_PLAY_ACTION_BAR_HEIGHT = 104.dp
internal val MOBILE_PLAY_DIG_ACTION_BAR_HEIGHT = 104.dp
internal val MOBILE_PLAY_HORIZONTAL_PADDING = 8.dp
internal val MOBILE_PLAY_VERTICAL_PADDING = 4.dp
internal val MOBILE_PLAY_GAP = 4.dp
internal val MOBILE_PLAY_MAX_BOARD_WIDTH = 420.dp
internal val ACTION_BAR_VERTICAL_PADDING = 5.dp
internal val ACTION_BAR_CONTENT_GAP = 4.dp
internal val EVENT_STRIP_HEIGHT = 40.dp
internal val COMPACT_ACTION_BUTTON_HEIGHT = 44.dp
internal val COMPACT_DIG_BUTTON_HEIGHT = 44.dp
internal val LOG_HISTORY_POPUP_MAX_HEIGHT = 220.dp
internal const val COMPACT_ACTION_CONTROL_MAX_ROWS = 1
internal const val ACTIVE_GAMEPLAY_USES_VERTICAL_SCROLL = false
internal const val EVENT_STRIP_MAX_LINES = 1
internal const val LOG_HISTORY_COLLAPSED_BY_DEFAULT = true

internal enum class ActionBarContentMode {
    STANDARD,
    DIG_PLACEMENT,
}

internal data class MobileGameplayLayoutSpec(
    val hudHeight: Dp,
    val actionBarHeight: Dp,
    val boardViewportHeight: Dp,
    val boardWidth: Dp,
    val boardHeight: Dp,
    val usedHeight: Dp,
    val fitsWithoutScroll: Boolean,
)

@Composable
fun MoguraGameScreen(viewModel: AndroidGameViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFFF7E4),
        ) {
            Box {
                if (state.isGameStarted) {
                    PlayScreen(state = state, viewModel = viewModel)
                } else {
                    SetupScreen(state = state, viewModel = viewModel)
                }
                val rouletteFood = state.playState.diceRouletteFood
                if (state.playState.diceRouletteActive && rouletteFood != null) {
                    DiceRouletteOverlay(
                        foodType = rouletteFood,
                        escapeRolls = state.playState.diceRouletteEscapeRolls,
                        targetFace = state.playState.diceRouletteResult,
                        onTap = viewModel::stopDiceRoulette,
                        onFinished = viewModel::finishDiceRoulette,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(
    state: AndroidGameUiState,
    viewModel: AndroidGameViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "モグラゲーム",
            color = Color(0xFF2E2115),
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "プレイヤー人数",
            modifier = Modifier.padding(top = 28.dp, bottom = 10.dp),
            color = Color(0xFF4B3826),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(2, 3, 4).forEach { count ->
                PlayerCountButton(
                    count = count,
                    selected = state.selectedPlayerCount == count,
                    onClick = { viewModel.selectPlayerCount(count) },
                )
            }
        }
        Text(
            text = "モグラ・巣・先手",
            modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
            color = Color(0xFF4B3826),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.setupPlayers.forEach { player ->
                SetupPlayerRow(
                    player = player,
                    setupPlayers = state.setupPlayers,
                    onMoleSelected = viewModel::selectPlayerMole,
                    onNestSelected = viewModel::selectPlayerNest,
                    onStartSelected = viewModel::selectStartPlayer,
                )
            }
        }
        Button(
            onClick = viewModel::startSelectedGame,
            enabled = state.canStartGame,
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth(0.72f)
                .height(52.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF35BC67),
                contentColor = Color(0xFF102F1B),
            ),
        ) {
            Text("ゲームスタート", fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SetupPlayerRow(
    player: AndroidSetupPlayerUiState,
    setupPlayers: List<AndroidSetupPlayerUiState>,
    onMoleSelected: (Int, Int) -> Unit,
    onNestSelected: (Int, Position) -> Unit,
    onStartSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF8E8))
            .border(2.dp, Color(0xFFD0AD78), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "P${player.seatIndex + 1}: ${player.name}",
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                color = Color(0xFF2E2115),
                fontSize = 15.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
            )
            OutlinedButton(
                onClick = { onStartSelected(player.seatIndex) },
                modifier = Modifier
                    .widthIn(min = 84.dp)
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = startPlayerSemanticsLabel(
                            seatIndex = player.seatIndex,
                            name = player.name,
                            selected = player.isStartPlayer,
                        )
                        selected = player.isStartPlayer
                        stateDescription = if (player.isStartPlayer) "先手に選択中" else "先手ではありません"
                    },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(2.dp, if (player.isStartPlayer) Color(0xFF158A45) else Color(0xFF9A7A52)),
                colors = if (player.isStartPlayer) {
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF35BC67),
                        contentColor = Color(0xFF102F1B),
                    )
                } else {
                    ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E2115))
                },
            ) {
                Text("先手", fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = 2,
        ) {
            MoguraGameController.moleOptions.forEach { option ->
                val selected = player.playerId == option.playerId
                val usedByOther = setupPlayers.firstOrNull {
                    it.seatIndex != player.seatIndex && it.playerId == option.playerId
                }
                MoleChoiceButton(
                    playerId = option.playerId,
                    name = option.name,
                    selected = selected,
                    usedByLabel = usedByOther?.let { setupUsedByLabel(it.seatIndex) },
                    enabled = true,
                    onClick = { onMoleSelected(player.seatIndex, option.playerId) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = 4,
        ) {
            MoguraGameController.nestPositions.forEach { nest ->
                val selected = player.nestPosition == nest
                val usedByOther = setupPlayers.firstOrNull {
                    it.seatIndex != player.seatIndex && it.nestPosition == nest
                }
                NestChoiceButton(
                    position = nest,
                    selected = selected,
                    usedByLabel = usedByOther?.let { setupUsedByLabel(it.seatIndex) },
                    enabled = true,
                    onClick = { onNestSelected(player.seatIndex, nest) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MoleChoiceButton(
    playerId: Int,
    name: String,
    selected: Boolean,
    usedByLabel: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 54.dp)
            .semantics {
                this.selected = selected
                stateDescription = setupChoiceStateDescription(selected, usedByLabel)
            },
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        border = BorderStroke(2.dp, if (selected) Color(0xFF158A45) else Color(0xFF9A7A52)),
        colors = if (selected) {
            ButtonDefaults.buttonColors(
                containerColor = Color(0xFF35BC67),
                contentColor = Color(0xFF102F1B),
                disabledContainerColor = Color(0xFF35BC67),
                disabledContentColor = Color(0xFF102F1B),
            )
        } else {
            ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF2E2115),
                disabledContentColor = Color(0xFF5F5144),
            )
        },
    ) {
        Image(
            painter = painterResource(playerRes(playerId)),
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            contentScale = ContentScale.Fit,
        )
        Column(
            modifier = Modifier.padding(start = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = name,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
            )
            usedByLabel?.let { label ->
                Text(
                    text = label,
                    color = Color(0xFF5F5144),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NestChoiceButton(
    position: Position,
    selected: Boolean,
    usedByLabel: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = nestDisplayLabel(position)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 62.dp)
            .semantics {
                this.selected = selected
                stateDescription = setupChoiceStateDescription(selected, usedByLabel)
                contentDescription = nestChoiceVisualLines(position, usedByLabel)
                    .plus(setupChoiceStateDescription(selected, usedByLabel))
                    .joinToString("、")
            },
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
        border = BorderStroke(2.dp, if (selected) Color(0xFFE64B3F) else Color(0xFF9A7A52)),
        colors = if (selected) {
            ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFD9D3),
                contentColor = Color(0xFF2E2115),
                disabledContainerColor = Color(0xFFFFD9D3),
                disabledContentColor = Color(0xFF2E2115),
            )
        } else {
            ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF2E2115),
                disabledContentColor = Color(0xFF5F5144),
            )
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            nestChoiceVisualLines(position, usedByLabel)
                .drop(1)
                .forEach { line ->
                    Text(
                        text = line,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
        }
    }
}

@Composable
private fun PlayerCountButton(
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) Color(0xFF158A45) else Color(0xFF9A7A52)
    val colors = if (selected) {
        ButtonDefaults.buttonColors(
            containerColor = Color(0xFF35BC67),
            contentColor = Color(0xFF102F1B),
        )
    } else {
        ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E2115))
    }
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 78.dp)
            .heightIn(min = 48.dp)
            .semantics {
                this.selected = selected
                stateDescription = if (selected) "選択中" else "未選択"
            },
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, borderColor),
        colors = colors,
    ) {
        Text("${count}人", fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PlayScreen(
    state: AndroidGameUiState,
    viewModel: AndroidGameViewModel,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        val layout = mobileGameplayLayoutSpec(
            viewportWidth = maxWidth,
            viewportHeight = maxHeight,
            actionBarHeight = actionBarHeightForState(state),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("gameplay-shell")
                .padding(
                    horizontal = MOBILE_PLAY_HORIZONTAL_PADDING,
                    vertical = MOBILE_PLAY_VERTICAL_PADDING,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MOBILE_PLAY_GAP),
        ) {
            CompactPlayHud(
                state = state,
                onNewGame = viewModel::returnToSetup,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("top-hud")
                    .height(layout.hudHeight),
            )
            BoardViewport(
                state = state,
                boardWidth = layout.boardWidth,
                onCellClicked = viewModel::onCellClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("board-viewport")
                    .weight(1f),
            )
            GameplayActionBar(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("action-bar")
                    .height(layout.actionBarHeight),
            )
        }
    }
}

@Composable
private fun CompactPlayHud(
    state: AndroidGameUiState,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = state.playState.currentPlayer
    var showNewGameConfirmation by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF6D8))
            .border(2.dp, Color(0xFFE8AD20), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        current.playerId?.let { playerId ->
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .border(2.dp, playerAccentColor(playerId), RoundedCornerShape(999.dp))
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(playerRes(playerId)),
                    contentDescription = current.titleText,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudChip(compactPhaseLabel(state.playState.actionAvailability.activePhase), accent = Color(0xFFF2C94C))
            HudChip(compactHealthText(current.healthText), accent = Color(0xFF35BC67))
            HudChip(compactScoreText(current.scoreText), accent = Color(0xFF56A3E8))
        }
        OutlinedButton(
            onClick = { showNewGameConfirmation = true },
            modifier = Modifier
                .widthIn(min = 48.dp)
                .heightIn(min = 40.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            border = BorderStroke(2.dp, Color(0xFF9A7A52)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E2115)),
        ) {
            Text("新規", fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
    if (showNewGameConfirmation) {
        AlertDialog(
            onDismissRequest = { showNewGameConfirmation = false },
            title = {
                Text("設定画面に戻りますか？", fontWeight = FontWeight.Black)
            },
            text = {
                Text("進行中のゲームを中断します。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNewGameConfirmation = false
                        onNewGame()
                    },
                ) {
                    Text("戻る", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewGameConfirmation = false }) {
                    Text("続ける")
                }
            },
        )
    }
}

@Composable
private fun HudChip(
    text: String,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFFFBF0),
        border = BorderStroke(1.dp, accent),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            color = Color(0xFF2E2115),
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BoardViewport(
    state: AndroidGameUiState,
    boardWidth: Dp,
    onCellClicked: (Position) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        BoardView(
            state = state,
            boardWidth = boardWidth,
            onCellClicked = onCellClicked,
        )
    }
}

@Composable
private fun GameplayActionBar(
    state: AndroidGameUiState,
    viewModel: AndroidGameViewModel,
    modifier: Modifier = Modifier,
) {
    val singleBoardAction = singlePrimaryBoardAction(
        state.boardState.cells,
        state.playState.actionAvailability.activePhase,
    )
    val preferSingleBoardAction = preferSingleBoardAction(singleBoardAction, state.visibleActions)
    val activePhase = state.playState.actionAvailability.activePhase
    var showLogHistory by remember { mutableStateOf(!LOG_HISTORY_COLLAPSED_BY_DEFAULT) }
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFFF8E8),
            border = BorderStroke(2.dp, Color(0xFFD0AD78)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = ACTION_BAR_VERTICAL_PADDING),
                verticalArrangement = Arrangement.spacedBy(ACTION_BAR_CONTENT_GAP),
            ) {
                EventStrip(
                    text = latestEventText(state),
                    hasHistory = state.logs.isNotEmpty(),
                    onHistoryClick = { showLogHistory = true },
                )
                when {
                    state.showDigControls -> CompactDigPlacementControls(
                        state = state,
                        onChoice = viewModel::selectDigChoice,
                        onRotation = viewModel::selectRotation,
                        onConfirm = viewModel::confirmDigPlacement,
                    )
                    state.playState.captureTargets.size > 1 && state.visibleActions.contains(AndroidVisibleAction.CAPTURE) ->
                        CompactTargetActionRow(
                            labels = state.playState.captureTargets.map { captureTargetLabel(it, state.playState.captureTargets.size) },
                            selectedIndex = state.playState.captureTargets.indexOfFirst { it.selected },
                            onSelect = viewModel::selectCaptureTarget,
                            action = AndroidVisibleAction.CAPTURE,
                            onAction = viewModel::capture,
                            extraActions = state.visibleActions.filter { it != AndroidVisibleAction.CAPTURE },
                            onExtraAction = { action -> viewModel.performVisibleAction(action) },
                        )
                    state.playState.robberyTargets.size > 1 && state.visibleActions.contains(AndroidVisibleAction.ROB) ->
                        CompactTargetActionRow(
                            labels = state.playState.robberyTargets.map {
                                "${robberyOwnerLabel(it)} ${robberyTargetLabel(it, state.playState.robberyTargets.size)}"
                            },
                            selectedIndex = state.playState.robberyTargets.indexOfFirst { it.selected },
                            onSelect = viewModel::selectRobberyTarget,
                            action = AndroidVisibleAction.ROB,
                            onAction = viewModel::rob,
                            extraActions = state.visibleActions.filter { it != AndroidVisibleAction.ROB },
                            onExtraAction = { action -> viewModel.performVisibleAction(action) },
                        )
                    preferSingleBoardAction && singleBoardAction != null -> CompactBoardActionRow(
                        singleBoardAction = singleBoardAction,
                        onBoardAction = { viewModel.onCellClicked(singleBoardAction.position) },
                        extraActions = visibleActionsAfterSingleBoardAction(activePhase, state.visibleActions),
                        onExtraAction = { action -> viewModel.performVisibleAction(action) },
                    )
                    state.visibleActions.isNotEmpty() -> ActionControls(state = state, viewModel = viewModel)
                    else -> Text(
                        text = actionBarInstruction(state),
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF4B3826),
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (showLogHistory && state.logs.isNotEmpty()) {
            LogHistoryPopup(
                logs = state.logs,
                onDismiss = { showLogHistory = false },
            )
        }
    }
}

@Composable
private fun EventStrip(
    text: String?,
    hasHistory: Boolean,
    onHistoryClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text.orEmpty(),
            modifier = Modifier
                .weight(1f)
                .height(EVENT_STRIP_HEIGHT),
            color = Color(0xFF4B3826),
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = EVENT_STRIP_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        if (hasHistory) {
            TextButton(
                onClick = onHistoryClick,
                modifier = Modifier.height(EVENT_STRIP_HEIGHT),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Text("履歴", fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LogHistoryPopup(
    logs: List<String>,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier
                .padding(8.dp)
                .widthIn(max = 360.dp)
                .heightIn(max = LOG_HISTORY_POPUP_MAX_HEIGHT)
                .testTag("log-history-drawer"),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFFFBF0),
            border = BorderStroke(2.dp, Color(0xFFD0AD78)),
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "履歴",
                    color = Color(0xFF2E2115),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                )
                logs.asReversed().forEach { log ->
                    Text(
                        text = log,
                        color = Color(0xFF4B3826),
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("閉じる", fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun CompactDigPlacementControls(
    state: AndroidGameUiState,
    onChoice: (DigTileChoice) -> Unit,
    onRotation: (Rotation) -> Unit,
    onConfirm: () -> Unit,
) {
    val enabledCandidates = state.playState.digCandidates.filter { it.enabled }
    val rotationDegrees = state.playState.selectedRotation.steps * 90
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (enabledCandidates.size > 1) {
            enabledCandidates.forEach { candidate ->
                OutlinedButton(
                    onClick = { onChoice(candidate.choice) },
                    modifier = Modifier
                        .weight(0.72f)
                        .height(COMPACT_DIG_BUTTON_HEIGHT),
                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        2.dp,
                        if (candidate.selected) Color(0xFF158A45) else Color(0xFF9A7A52),
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (candidate.selected) Color(0xFFE8FFF0) else Color(0xFFFFFBF0),
                        contentColor = Color(0xFF2E2115),
                    ),
                ) {
                    Text(digCandidateShortLabel(candidate.choice), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
        }
        OutlinedButton(
            onClick = { onRotation(state.playState.selectedRotation.nextClockwise()) },
            modifier = Modifier
                .weight(0.9f)
                .height(COMPACT_DIG_BUTTON_HEIGHT),
            contentPadding = PaddingValues(0.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(2.dp, Color(0xFF9A7A52)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFFFFFBF0),
                contentColor = Color(0xFF2E2115),
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "回転 ${rotationDegrees}度" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "↺",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 3.dp),
                    shape = RoundedCornerShape(5.dp),
                    border = BorderStroke(1.dp, Color(0xFF9A7A52)),
                    color = Color(0xFFFFE8A8),
                    contentColor = Color(0xFF2E2115),
                ) {
                    Text(
                        text = "${rotationDegrees}°",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                }
            }
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .weight(1.05f)
                .height(COMPACT_DIG_BUTTON_HEIGHT),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF35BC67),
                contentColor = Color(0xFF102F1B),
            ),
        ) {
            Text("置く", fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun CompactTargetActionRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    action: AndroidVisibleAction,
    onAction: () -> Unit,
    extraActions: List<AndroidVisibleAction> = emptyList(),
    onExtraAction: (AndroidVisibleAction) -> Unit = {},
) {
    val selected = selectedIndex.takeIf { it in labels.indices } ?: 0
    val next = if (labels.isEmpty()) 0 else (selected + 1) % labels.size
    val showTargetCycler = labels.size > 1
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showTargetCycler) {
            OutlinedButton(
                onClick = { onSelect(next) },
                modifier = Modifier
                    .weight(1.35f)
                    .height(COMPACT_ACTION_BUTTON_HEIGHT),
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(2.dp, Color(0xFF158A45)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFFE8FFF0),
                    contentColor = Color(0xFF2E2115),
                ),
            ) {
                Text(
                    text = compactTargetCyclerLabel(
                        selectedLabel = labels.getOrNull(selected).orEmpty(),
                        selectedIndex = selected,
                        total = labels.size,
                    ),
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ActionButton(
            action = action,
            modifier = Modifier.weight(1f),
            testTag = "primary-action",
            onClick = onAction,
        )
        extraActions.forEach { extraAction ->
            ActionButton(
                action = extraAction,
                modifier = Modifier.weight(1f),
                onClick = { onExtraAction(extraAction) },
            )
        }
    }
}

@Composable
private fun CompactBoardActionRow(
    singleBoardAction: MobilePrimaryBoardAction,
    onBoardAction: () -> Unit,
    extraActions: List<AndroidVisibleAction>,
    onExtraAction: (AndroidVisibleAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onBoardAction,
            modifier = Modifier
                .weight(1.2f)
                .testTag("primary-action")
                .height(COMPACT_ACTION_BUTTON_HEIGHT),
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF35BC67),
                contentColor = Color(0xFF102F1B),
            ),
        ) {
            Text(
                singleBoardAction.label,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        extraActions.forEach { action ->
            ActionButton(
                action = action,
                modifier = Modifier.weight(1f),
                onClick = { onExtraAction(action) },
            )
        }
    }
}

@Composable
private fun BoardView(
    state: AndroidGameUiState,
    boardWidth: Dp,
    onCellClicked: (Position) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .width(boardWidth)
            .aspectRatio(BOARD_SOURCE_WIDTH / BOARD_SOURCE_HEIGHT)
            .testTag("game-board")
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFC88A4A))
            .border(2.dp, Color(0xFF6F4726), RoundedCornerShape(8.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.board_main),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        HungerMeterOverlay(maxWidth = maxWidth, maxHeight = maxHeight, markers = state.hungerMarkers)

        state.boardState.cells.forEach { cell ->
            cell.highlight?.let { tone ->
                Box(
                    modifier = Modifier
                        .boardRect(maxWidth, maxHeight, cellRect(cell.position, scale = 0.98f))
                        .zIndex(BOARD_HIGHLIGHT_Z)
                        .background(highlightFill(tone))
                        .border(3.dp, highlightStroke(tone)),
                )
            }
        }

        state.boardState.cells.forEach { cell ->
            cell.tile?.let { tile ->
                Image(
                    painter = painterResource(if (tile.isFaceDown) R.drawable.tile_back else tileRes(tile.shape)),
                    contentDescription = null,
                    modifier = Modifier
                        .boardRect(maxWidth, maxHeight, cellRect(cell.position, scale = 0.84f))
                        .zIndex(BOARD_TILE_Z)
                        .graphicsLayer {
                            if (!tile.isFaceDown) rotationZ = tile.rotation.steps * 90f
                        },
                    contentScale = ContentScale.Fit,
                )
            }

            cell.connectionEdges.forEach { edge ->
                Box(
                    modifier = Modifier
                        .boardRect(maxWidth, maxHeight, connectionPortRect(cell.position, edge.direction))
                        .zIndex(BOARD_CONNECTION_PORT_Z)
                        .clip(RoundedCornerShape(999.dp))
                        .background(connectionPortColor(edge.tone))
                        .border(
                            width = connectionPortStrokeWidth(edge.tone),
                            color = connectionPortStrokeColor(edge.tone),
                            shape = RoundedCornerShape(999.dp),
                        ),
                )
            }

            cell.foods.forEachIndexed { index, food ->
                val phase = state.playState.actionAvailability.activePhase
                val scale = if (phase == TurnPhase.DIG || phase == TurnPhase.MOVE) 0.58f else 0.76f
                Image(
                    painter = painterResource(if (food.isFaceDown) R.drawable.food_card_back else foodRes(food.type)),
                    contentDescription = null,
                    modifier = Modifier
                        .boardRect(maxWidth, maxHeight, foodRect(cell.position, scale, index, cell.foods.size))
                        .zIndex(BOARD_FOOD_Z + (cell.foods.size - index) * 0.01f),
                    contentScale = ContentScale.Fit,
                )
            }

            cell.players.forEachIndexed { index, player ->
                Box(
                    modifier = Modifier
                        .boardRect(maxWidth, maxHeight, playerRect(cell.position, index, cell.players.size))
                        .zIndex(BOARD_PLAYER_BASE_Z + index),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(999.dp))
                            .border(2.dp, playerAccentColor(player.playerId), RoundedCornerShape(999.dp))
                            .padding(2.dp),
                    ) {
                        BoardPlayerImage(
                            playerId = player.playerId,
                            contentDescription = player.accessibilityLabel,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        state.boardState.cells
            .filter { it.isCurrentPlayerCell }
            .forEach { cell ->
                Box(
                    modifier = Modifier
                        .boardRect(maxWidth, maxHeight, cellRect(cell.position, scale = 1.03f))
                        .zIndex(BOARD_CURRENT_CELL_RING_Z)
                        .border(3.dp, Color(0xFF102F1B), RoundedCornerShape(6.dp)),
                )
            }

        state.boardState.cells
            .forEach { cell ->
                cell.players.forEachIndexed { index, player ->
                    if (player.isCurrent) {
                        Box(
                            modifier = Modifier
                                .boardRect(maxWidth, maxHeight, playerRect(cell.position, index, cell.players.size))
                                .zIndex(BOARD_CURRENT_PLAYER_OUTLINE_Z)
                                .border(3.dp, Color(0xFF2E2115), RoundedCornerShape(999.dp))
                                .padding(2.dp)
                                .border(2.dp, Color.White, RoundedCornerShape(999.dp)),
                        )
                    }
                }
            }

        state.boardState.cells
            .filter { isBoardPrimaryActionCell(it, state.playState.actionAvailability.activePhase) }
            .forEach { cell ->
                Box(
                    modifier = Modifier
                        .boardRect(maxWidth, maxHeight, cellRect(cell.position, scale = 1f))
                        .zIndex(BOARD_CLICK_TARGET_Z)
                        .semantics {
                            contentDescription = cellDescription(cell)
                        }
                        .clickable(
                            onClickLabel = cellClickLabel(cell),
                            role = Role.Button,
                        ) { onCellClicked(cell.position) },
                )
            }
    }
}

@Composable
private fun BoardPlayerImage(
    playerId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val image = ImageBitmap.imageResource(playerRes(playerId))
    val source = playerImageSourceRect(playerId)
    Image(
        painter = BitmapPainter(
            image = image,
            srcOffset = IntOffset(source.left, source.top),
            srcSize = IntSize(source.width, source.height),
        ),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun HungerMeterOverlay(
    maxWidth: Dp,
    maxHeight: Dp,
    markers: List<AndroidHungerMarkerUiState>,
) {
    Image(
        painter = painterResource(R.drawable.hunger_meter_reference_transparent),
        contentDescription = null,
        modifier = Modifier
            .boardRect(maxWidth, maxHeight, BoardRectSpec(METER_LEFT, METER_TOP, METER_WIDTH, METER_HEIGHT))
            .zIndex(10f),
        contentScale = ContentScale.Fit,
    )
    markers.forEachIndexed { index, marker ->
        Box(
            modifier = Modifier
                .boardRect(maxWidth, maxHeight, hungerMarkerRect(marker.health, index))
                .zIndex(11f + index),
        ) {
            Image(
                painter = painterResource(playerRes(marker.playerId)),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            if (marker.isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, Color(0xFF2E2115), RoundedCornerShape(999.dp))
                        .padding(1.dp)
                        .border(1.dp, Color.White, RoundedCornerShape(999.dp)),
                )
            }
        }
    }
}

/**
 * 体力に応じた腹減りメーター上のマーカー位置を求める。
 * メーターは U 字で、満タン(13)が左上、空(0)が左下になるよう左上→右→下→左下へ進む。
 * モックアップ (mockups/android/index.html) の hungerPoint と同じ計算を移植したもの。
 */
private fun hungerMarkerRect(health: Int, index: Int): BoardRectSpec {
    val w = BOARD_SOURCE_WIDTH
    val h = BOARD_SOURCE_HEIGHT
    val rectX = METER_LEFT * w
    val rectY = METER_TOP * h
    val rectW = METER_WIDTH * w
    val rectH = METER_HEIGHT * h
    val left = rectX + rectW * 0.13f
    val right = rectX + rectW * 0.88f
    val top = rectY + rectH * 0.27f
    val bottom = rectY + rectH * 0.78f
    val topLength = right - left
    val sideLength = bottom - top
    val progress = (Player.MAX_HEALTH - health.coerceIn(0, Player.MAX_HEALTH)).toFloat() / Player.MAX_HEALTH
    val distance = progress * (topLength + sideLength + topLength)
    val pointX: Float
    val pointY: Float
    when {
        distance <= topLength -> {
            pointX = left + distance
            pointY = top
        }
        distance <= topLength + sideLength -> {
            pointX = right
            pointY = top + (distance - topLength)
        }
        else -> {
            pointX = right - (distance - topLength - sideLength)
            pointY = bottom
        }
    }
    val markerSize = rectH * 0.44f
    val leftPx = pointX - markerSize / 2f - index * markerSize * 0.12f
    val topPx = pointY - markerSize / 2f + index * markerSize * 0.08f
    return BoardRectSpec(
        left = leftPx / w,
        top = topPx / h,
        width = markerSize / w,
        height = markerSize / h,
    )
}

private fun digCandidateShortLabel(choice: DigTileChoice): String = when (choice) {
    DigTileChoice.REVEALED -> "めくり"
    DigTileChoice.DRAWN -> "山札"
}

@Composable
private fun ActionControls(
    state: AndroidGameUiState,
    viewModel: AndroidGameViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val primaryTag = if (state.visibleActions.size == 1) "primary-action" else null
        state.visibleActions.forEach { action ->
            ActionButton(
                action = action,
                modifier = Modifier.weight(1f),
                testTag = primaryTag,
                onClick = { viewModel.performVisibleAction(action) },
            )
        }
    }
}

@Composable
private fun ActionButton(
    action: AndroidVisibleAction,
    modifier: Modifier,
    testTag: String? = null,
    onClick: () -> Unit,
) {
    val colors = when (action) {
        AndroidVisibleAction.CAPTURE,
        AndroidVisibleAction.EAT,
        AndroidVisibleAction.CARRY,
        -> ButtonDefaults.buttonColors(
            containerColor = Color(0xFF35BC67),
            contentColor = Color(0xFF102F1B),
        )
        AndroidVisibleAction.ROB -> ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFFD9D3),
            contentColor = Color(0xFF5A180F),
        )
        AndroidVisibleAction.SKIP,
        AndroidVisibleAction.END_TURN,
        -> ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFFF1CF),
            contentColor = Color(0xFF2E2115),
        )
    }
    val taggedModifier = testTag?.let { modifier.testTag(it) } ?: modifier
    Button(
        onClick = onClick,
        modifier = taggedModifier
            .height(COMPACT_ACTION_BUTTON_HEIGHT)
            .semantics {
                contentDescription = action.accessibilityLabel()
            },
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
        shape = RoundedCornerShape(8.dp),
        colors = colors,
    ) {
        Text(
            action.displayLabel(),
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun AndroidVisibleAction.displayLabel(): String = when (this) {
    AndroidVisibleAction.CAPTURE -> "捕獲"
    AndroidVisibleAction.ROB -> "強奪"
    AndroidVisibleAction.EAT -> "タベる"
    AndroidVisibleAction.CARRY -> "レンコウ"
    AndroidVisibleAction.SKIP -> "スキップ"
    AndroidVisibleAction.END_TURN -> "ターン終了"
}

internal fun AndroidVisibleAction.accessibilityLabel(): String = when (this) {
    AndroidVisibleAction.EAT -> "タベる（食べる）"
    AndroidVisibleAction.CARRY -> "レンコウ（巣へ持ち帰る）"
    else -> displayLabel()
}

private fun phaseInstruction(phase: TurnPhase?): String = when (phase) {
    TurnPhase.DIG -> "ハイライトされた隣の穴タイルを選んで掘る"
    TurnPhase.MOVE -> "ハイライトされた到達可能マスへ移動"
    TurnPhase.CAPTURE -> "捕獲対象を確認して捕獲する"
    TurnPhase.DECIDE -> "タベる/レンコウ/強奪を選択"
    TurnPhase.END -> "ターン終了を押してください"
    null -> ""
}

internal fun playBoardMaxWidthForHeight(availableHeight: Dp): Dp =
    when {
        availableHeight < 680.dp -> 300.dp
        availableHeight < 760.dp -> 340.dp
        else -> 420.dp
    }

internal fun mobileGameplayLayoutSpec(
    viewportWidth: Dp,
    viewportHeight: Dp,
    actionBarHeight: Dp = MOBILE_PLAY_ACTION_BAR_HEIGHT,
): MobileGameplayLayoutSpec {
    val boardViewportHeight = (
        viewportHeight -
            MOBILE_PLAY_VERTICAL_PADDING * 2 -
            MOBILE_PLAY_GAP * 2 -
            MOBILE_PLAY_HUD_HEIGHT -
            actionBarHeight
        ).coerceAtLeast(0.dp)
    val availableWidth = (viewportWidth - MOBILE_PLAY_HORIZONTAL_PADDING * 2).coerceAtLeast(0.dp)
    val maxWidthByHeight = boardViewportHeight * (BOARD_SOURCE_WIDTH / BOARD_SOURCE_HEIGHT)
    val boardWidth = minOf(availableWidth, maxWidthByHeight, MOBILE_PLAY_MAX_BOARD_WIDTH)
    val boardHeight = boardWidth / (BOARD_SOURCE_WIDTH / BOARD_SOURCE_HEIGHT)
    val usedHeight = MOBILE_PLAY_VERTICAL_PADDING * 2 +
        MOBILE_PLAY_GAP * 2 +
        MOBILE_PLAY_HUD_HEIGHT +
        actionBarHeight +
        boardHeight
    return MobileGameplayLayoutSpec(
        hudHeight = MOBILE_PLAY_HUD_HEIGHT,
        actionBarHeight = actionBarHeight,
        boardViewportHeight = boardViewportHeight,
        boardWidth = boardWidth,
        boardHeight = boardHeight,
        usedHeight = usedHeight,
        fitsWithoutScroll = usedHeight <= viewportHeight && boardHeight <= boardViewportHeight,
    )
}

private fun actionBarHeightForState(state: AndroidGameUiState): Dp =
    if (state.showDigControls) MOBILE_PLAY_DIG_ACTION_BAR_HEIGHT else MOBILE_PLAY_ACTION_BAR_HEIGHT

internal fun compactActionBarContentHeight(mode: ActionBarContentMode): Dp =
    ACTION_BAR_VERTICAL_PADDING * 2 +
        EVENT_STRIP_HEIGHT +
        ACTION_BAR_CONTENT_GAP +
        when (mode) {
            ActionBarContentMode.STANDARD -> COMPACT_ACTION_BUTTON_HEIGHT
            ActionBarContentMode.DIG_PLACEMENT -> COMPACT_DIG_BUTTON_HEIGHT
        }

internal fun compactTargetActionSlotCount(targetCount: Int): Int =
    if (targetCount > 1) 2 else 1

internal fun compactTargetCyclerLabel(
    selectedLabel: String,
    selectedIndex: Int,
    total: Int,
): String = "次対象 ${selectedIndex + 1}/$total: $selectedLabel"

private fun compactPhaseLabel(phase: TurnPhase?): String = when (phase) {
    TurnPhase.DIG -> "掘る"
    TurnPhase.MOVE -> "いどう"
    TurnPhase.CAPTURE -> "捕獲"
    TurnPhase.DECIDE -> "タベる/レンコウ"
    TurnPhase.END -> "終了"
    null -> "-"
}

private fun compactHealthText(text: String): String =
    text.replace("体力: ", "HP ")

private fun compactScoreText(text: String): String =
    text.replace("点: ", "点 ")

private fun latestEventText(state: AndroidGameUiState): String? =
    resultBannerText(state.playState.captureOutcome)
        ?: state.lastMessage
        ?: state.logs.lastOrNull()
        ?: phaseInstruction(state.playState.actionAvailability.activePhase)

private fun actionBarInstruction(state: AndroidGameUiState): String =
    if (state.boardState.cells.any { isBoardPrimaryActionCell(it, state.playState.actionAvailability.activePhase) }) {
        phaseInstruction(state.playState.actionAvailability.activePhase)
    } else {
        when (state.playState.actionAvailability.activePhase) {
            TurnPhase.DIG -> "掘れる隣接タイルなし"
            TurnPhase.MOVE -> "移動できるマスなし"
            else -> phaseInstruction(state.playState.actionAvailability.activePhase)
        }
    }

private data class BoardRectSpec(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

private data class SourceImageRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

private fun sourceBoardRect(left: Float, top: Float, width: Float, height: Float): BoardRectSpec =
    BoardRectSpec(
        left = left / BOARD_SOURCE_WIDTH,
        top = top / BOARD_SOURCE_HEIGHT,
        width = width / BOARD_SOURCE_WIDTH,
        height = height / BOARD_SOURCE_HEIGHT,
    )

private fun Modifier.boardRect(maxWidth: Dp, maxHeight: Dp, rect: BoardRectSpec): Modifier =
    offset(x = maxWidth * rect.left, y = maxHeight * rect.top)
        .width(maxWidth * rect.width)
        .height(maxHeight * rect.height)

private fun cellRect(position: Position, scale: Float): BoardRectSpec {
    val x = GRID_LEFT + position.col * CELL_WIDTH + CELL_WIDTH * (1f - scale) / 2f
    val y = GRID_TOP + position.row * CELL_HEIGHT + CELL_HEIGHT * (1f - scale) / 2f
    return BoardRectSpec(
        left = x / BOARD_SOURCE_WIDTH,
        top = y / BOARD_SOURCE_HEIGHT,
        width = CELL_WIDTH * scale / BOARD_SOURCE_WIDTH,
        height = CELL_HEIGHT * scale / BOARD_SOURCE_HEIGHT,
    )
}

private fun foodRect(
    position: Position,
    scale: Float,
    stackIndex: Int = 0,
    stackSize: Int = 1,
): BoardRectSpec {
    val cellLeft = GRID_LEFT + position.col * CELL_WIDTH
    val cellTop = GRID_TOP + position.row * CELL_HEIGHT
    val size = minOf(CELL_WIDTH, CELL_HEIGHT) * scale
    val maxOffset = (minOf(CELL_WIDTH, CELL_HEIGHT) - size - FOOD_CARD_BOARD_PADDING).coerceAtLeast(0f)
    val offset = ((stackSize - 1 - stackIndex).coerceAtLeast(0) * FOOD_CARD_STACK_OFFSET)
        .coerceAtMost(maxOffset)
    return sourceBoardRect(
        left = cellLeft + CELL_WIDTH - size - FOOD_CARD_BOARD_PADDING - offset,
        top = cellTop + CELL_HEIGHT - size - FOOD_CARD_BOARD_PADDING - offset,
        width = size,
        height = size,
    )
}

private fun connectionPortRect(position: Position, direction: Direction): BoardRectSpec {
    val cellLeft = GRID_LEFT + position.col * CELL_WIDTH
    val cellTop = GRID_TOP + position.row * CELL_HEIGHT
    val shortSide = minOf(CELL_WIDTH, CELL_HEIGHT)
    val portLong = shortSide * 0.28f
    val portShort = shortSide * 0.055f
    return when (direction) {
        Direction.TOP -> sourceBoardRect(
            left = cellLeft + (CELL_WIDTH - portLong) / 2f,
            top = cellTop + CELL_HEIGHT * 0.12f,
            width = portLong,
            height = portShort,
        )
        Direction.RIGHT -> sourceBoardRect(
            left = cellLeft + CELL_WIDTH * 0.86f,
            top = cellTop + (CELL_HEIGHT - portLong) / 2f,
            width = portShort,
            height = portLong,
        )
        Direction.BOTTOM -> sourceBoardRect(
            left = cellLeft + (CELL_WIDTH - portLong) / 2f,
            top = cellTop + CELL_HEIGHT * 0.84f,
            width = portLong,
            height = portShort,
        )
        Direction.LEFT -> sourceBoardRect(
            left = cellLeft + CELL_WIDTH * 0.11f,
            top = cellTop + (CELL_HEIGHT - portLong) / 2f,
            width = portShort,
            height = portLong,
        )
    }
}

private fun connectionPortColor(tone: AndroidConnectionTone): Color = when (tone) {
    AndroidConnectionTone.OPEN -> Color(0xCCFFF1A6)
    AndroidConnectionTone.CONNECTED -> Color(0xDD35BC67)
    AndroidConnectionTone.BLOCKED -> Color(0xDDA56E61)
}

internal fun connectionPortStrokeWidth(tone: AndroidConnectionTone): Dp = when (tone) {
    AndroidConnectionTone.OPEN -> 1.dp
    AndroidConnectionTone.CONNECTED -> 2.dp
    AndroidConnectionTone.BLOCKED -> 2.dp
}

private fun connectionPortStrokeColor(tone: AndroidConnectionTone): Color = when (tone) {
    AndroidConnectionTone.OPEN -> Color(0xFF8B6F1E)
    AndroidConnectionTone.CONNECTED -> Color.White
    AndroidConnectionTone.BLOCKED -> Color(0xFF5A180F)
}

private fun cellDescription(cell: AndroidBoardCellUiState): String =
    buildList {
        add("マス ${cell.position.col + 1},${cell.position.row + 1}")
        cell.tile?.let { tile ->
            add(if (tile.isFaceDown) "裏向きタイル" else "${tile.shape.boardLabel()} ${tile.rotation.steps * 90}度")
        }
        if (cell.foods.isNotEmpty()) {
            add(
                cell.foods.joinToString(prefix = "エサ ", separator = "、") { food ->
                    if (food.isFaceDown) "裏向き" else food.type.boardLabel()
                },
            )
        }
        if (cell.players.isNotEmpty()) {
            add("プレイヤー ${cell.players.joinToString { it.accessibilityLabel }}")
        }
        cell.highlight?.let { tone ->
            add(tone.boardLabel())
        }
    }.joinToString("、")

private fun cellClickLabel(cell: AndroidBoardCellUiState): String =
    when (cell.highlight) {
        AndroidHighlightTone.DIG -> "このマスを掘る"
        AndroidHighlightTone.MOVE -> "このマスへ移動"
        AndroidHighlightTone.CAPTURE -> "このマスで捕獲"
        null -> "このマスは選択できません"
    }

internal data class MobilePrimaryBoardAction(
    val label: String,
    val position: Position,
)

internal fun singlePrimaryBoardAction(
    cells: List<AndroidBoardCellUiState>,
    phase: TurnPhase?,
): MobilePrimaryBoardAction? {
    val targets = cells.filter { isBoardPrimaryActionCell(it, phase) }
    if (targets.size != 1) return null
    val target = targets.single()
    val label = when (target.highlight) {
        AndroidHighlightTone.DIG -> "このタイルを掘る"
        AndroidHighlightTone.MOVE -> "このマスへ移動"
        AndroidHighlightTone.CAPTURE -> "このマスで捕獲"
        null -> return null
    }
    return MobilePrimaryBoardAction(label, target.position)
}

internal fun isBoardPrimaryActionCell(cell: AndroidBoardCellUiState, phase: TurnPhase?): Boolean =
    cell.highlight != null &&
        (phase == TurnPhase.DIG || phase == TurnPhase.MOVE || phase == TurnPhase.CAPTURE)

internal fun preferSingleBoardAction(
    singleBoardAction: MobilePrimaryBoardAction?,
    _visibleActions: List<AndroidVisibleAction>,
): Boolean = singleBoardAction != null

internal fun visibleActionsAfterSingleBoardAction(
    phase: TurnPhase?,
    visibleActions: List<AndroidVisibleAction>,
): List<AndroidVisibleAction> {
    val consumedAction = when (phase) {
        TurnPhase.CAPTURE -> AndroidVisibleAction.CAPTURE
        else -> null
    }
    return visibleActions.filter { it != consumedAction }
}

private fun AndroidGameViewModel.performVisibleAction(action: AndroidVisibleAction) {
    when (action) {
        AndroidVisibleAction.CAPTURE -> capture()
        AndroidVisibleAction.ROB -> rob()
        AndroidVisibleAction.EAT -> eat()
        AndroidVisibleAction.CARRY -> carry()
        AndroidVisibleAction.SKIP -> skip()
        AndroidVisibleAction.END_TURN -> finishTurn()
    }
}

internal fun boardPlayerVisibleLabel(name: String): String? = null

internal fun boardPlayerContentDescription(name: String, isCurrent: Boolean): String =
    if (isCurrent) "${name}の駒、現在の手番" else "${name}の駒"

private fun playerRect(position: Position, index: Int, count: Int): BoardRectSpec {
    val base = cellRect(position, 1f)
    val size = when (count) {
        1 -> minOf(base.width, base.height) * 0.95f
        2 -> minOf(base.width, base.height) * 0.70f
        3 -> minOf(base.width, base.height) * 0.64f
        else -> minOf(base.width, base.height) * 0.60f
    }
    val offsets = when (count) {
        1 -> listOf(0.5f to 0.5f)
        2 -> listOf(0.36f to 0.36f, 0.64f to 0.64f)
        3 -> listOf(0.35f to 0.35f, 0.65f to 0.35f, 0.5f to 0.68f)
        else -> listOf(0.35f to 0.35f, 0.65f to 0.35f, 0.35f to 0.65f, 0.65f to 0.65f)
    }
    val (centerX, centerY) = offsets.getOrElse(index) { 0.5f to 0.5f }
    return BoardRectSpec(
        left = base.left + base.width * centerX - size / 2f,
        top = base.top + base.height * centerY - size / 2f,
        width = size,
        height = size,
    )
}

private fun highlightFill(tone: AndroidHighlightTone): Color = when (tone) {
    AndroidHighlightTone.DIG -> Color(0x55F2C94C)
    AndroidHighlightTone.MOVE -> Color(0x5535BC67)
    AndroidHighlightTone.CAPTURE -> Color(0x55E64B3F)
}

private fun highlightStroke(tone: AndroidHighlightTone): Color = when (tone) {
    AndroidHighlightTone.DIG -> Color(0xFFF2C94C)
    AndroidHighlightTone.MOVE -> Color(0xFF158A45)
    AndroidHighlightTone.CAPTURE -> Color(0xFFE64B3F)
}

private fun AndroidHighlightTone.boardLabel(): String = when (this) {
    AndroidHighlightTone.DIG -> "掘る候補"
    AndroidHighlightTone.MOVE -> "移動候補"
    AndroidHighlightTone.CAPTURE -> "捕獲候補"
}

private fun TileShape.boardLabel(): String = when (this) {
    TileShape.STRAIGHT -> "直線タイル"
    TileShape.L_SHAPE -> "L字タイル"
    TileShape.T_SHAPE -> "T字タイル"
    TileShape.CROSS -> "十字タイル"
}

private fun FoodType.boardLabel(): String = when (this) {
    FoodType.BEETLE_LARVA -> "カブトムシの幼虫"
    FoodType.EARTHWORM -> "ミミズ"
    FoodType.MOLE_CRICKET -> "ケラ"
    FoodType.CENTIPEDE -> "ムカデ"
    FoodType.FROG -> "カエル"
}

internal data class NestDisplayLabel(
    val name: String,
    val location: String,
)

internal fun nestDisplayLabel(position: Position): NestDisplayLabel = when (position) {
    Position(0, 1) -> NestDisplayLabel("巣A", "左上")
    Position(5, 1) -> NestDisplayLabel("巣B", "右上")
    Position(0, 4) -> NestDisplayLabel("巣C", "左下")
    Position(5, 4) -> NestDisplayLabel("巣D", "右下")
    else -> NestDisplayLabel("${position.col + 1},${position.row + 1}", "盤面")
}

private fun nestLabel(position: Position): String = nestDisplayLabel(position).name

internal fun setupUsedByLabel(seatIndex: Int): String = "P${seatIndex + 1}使用中"

internal fun nestChoiceVisualLines(position: Position, usedByLabel: String?): List<String> =
    buildList {
        val label = nestDisplayLabel(position)
        add(label.name)
        add(label.location)
        usedByLabel?.let(::add)
    }

internal fun startPlayerSemanticsLabel(seatIndex: Int, name: String, selected: Boolean): String =
    "P${seatIndex + 1} ${name}を先手にする、${if (selected) "選択中" else "未選択"}"

private fun setupChoiceStateDescription(selected: Boolean, usedByLabel: String?): String =
    when {
        selected -> "選択中"
        usedByLabel != null -> "$usedByLabel のため選択不可"
        else -> "未選択"
    }

internal fun captureTargetLabel(target: CaptureTargetDisplay, total: Int): String =
    selectedPrefix(target.selected) + if (target.isFaceDown) {
        "裏向き ${target.index + 1}/$total"
    } else if (total > 1) {
        "${target.type.boardLabel()} ${target.index + 1}/$total"
    } else {
        target.type.boardLabel()
    }

internal fun captureTargetSummary(targets: List<CaptureTargetDisplay>): String? {
    val selected = targets.firstOrNull { it.selected } ?: return null
    return "捕獲対象: ${captureTargetLabel(selected, targets.size)}"
}

internal fun digCandidateSemanticLabel(candidate: DigCandidateDisplay): String {
    val tileName = candidate.shape?.displayName() ?: "裏向き"
    val state = if (candidate.selected) "選択中" else "未選択"
    return "${digCandidateShortLabel(candidate.choice)}、$tileName、$state"
}

internal fun digCandidateActionLabel(candidate: DigCandidateDisplay): String =
    "${digCandidateShortLabel(candidate.choice)}を選ぶ"

internal fun captureTargetSemanticLabel(target: CaptureTargetDisplay, total: Int): String =
    "捕獲対象、${captureTargetLabel(target, total)}、${if (target.enabled) "選択できます" else "選択できません"}"

internal fun captureTargetActionLabel(target: CaptureTargetDisplay, total: Int): String =
    "捕獲対象を選ぶ: ${captureTargetLabel(target, total)}"

internal fun robberyOwnerLabel(target: RobberyTargetDisplay): String = "${target.ownerName}の巣"

internal fun robberyTargetLabel(target: RobberyTargetDisplay, total: Int): String =
    selectedPrefix(target.selected) + if (total > 1) {
        "${target.type.boardLabel()} ${target.index + 1}/$total"
    } else {
        "${target.type.boardLabel()} 1/1"
    }

internal fun robberyTargetSummary(targets: List<RobberyTargetDisplay>): String? {
    val selected = targets.firstOrNull { it.selected } ?: return null
    return "強奪対象: ${robberyOwnerLabel(selected)} / ${robberyTargetLabel(selected, targets.size)}"
}

internal fun robberyTargetSemanticLabel(target: RobberyTargetDisplay, total: Int): String =
    "強奪対象、${robberyOwnerLabel(target)}、${robberyTargetLabel(target, total)}、${if (target.enabled) "選択できます" else "選択できません"}"

internal fun robberyTargetActionLabel(target: RobberyTargetDisplay, total: Int): String =
    "強奪対象を選ぶ: ${robberyOwnerLabel(target)} / ${robberyTargetLabel(target, total)}"

internal fun resultBannerText(outcome: CaptureOutcomeDisplay?): String? {
    outcome ?: return null
    val prefix = outcome.diceRoll?.let { "ダイス: $it" } ?: "逃走なし"
    return "$prefix　${outcome.message}"
}

internal data class ResultBannerColors(
    val containerArgb: Int,
    val borderArgb: Int,
    val contentArgb: Int,
)

internal const val RESULT_BANNER_MAX_LINES = 4

internal fun resultBannerColors(kind: CaptureOutcomeKind): ResultBannerColors =
    when (kind) {
        CaptureOutcomeKind.CAPTURED -> ResultBannerColors(
            containerArgb = 0xFFE8FFF0.toInt(),
            borderArgb = 0xFF158A45.toInt(),
            contentArgb = 0xFF102F1B.toInt(),
        )
        CaptureOutcomeKind.ESCAPED -> ResultBannerColors(
            containerArgb = 0xFFFFF1CF.toInt(),
            borderArgb = 0xFFE8AD20.toInt(),
            contentArgb = 0xFF4B3826.toInt(),
        )
    }

internal fun rouletteRevealStatus(escapeRolls: List<Int>): String =
    if (escapeRolls.isEmpty()) "逃走なし" else "ダイスで逃走判定"

internal fun roulettePrimaryActionLabel(escapeRolls: List<Int>): String =
    if (escapeRolls.isEmpty()) "捕獲する" else "ダイスを振る"

private fun selectedPrefix(selected: Boolean): String = if (selected) "選択中: " else ""

private fun tileRes(shape: TileShape): Int = when (shape) {
    TileShape.STRAIGHT -> R.drawable.tile_straight
    TileShape.L_SHAPE -> R.drawable.tile_l_shape
    TileShape.T_SHAPE -> R.drawable.tile_t_shape
    TileShape.CROSS -> R.drawable.tile_cross
}

internal fun foodRes(type: FoodType): Int = when (type) {
    FoodType.BEETLE_LARVA -> R.drawable.food_beetle_larva
    FoodType.EARTHWORM -> R.drawable.food_earthworm
    FoodType.MOLE_CRICKET -> R.drawable.food_mole_cricket
    FoodType.CENTIPEDE -> R.drawable.food_centipede
    FoodType.FROG -> R.drawable.food_frog
}

private fun playerRes(playerId: Int): Int = when (playerId) {
    0 -> R.drawable.player_moguo_blue
    1 -> R.drawable.player_moguta_orange
    2 -> R.drawable.player_mogumi_pink
    else -> R.drawable.player_moguka_yellow
}

private fun playerAccentColor(playerId: Int): Color = when (playerId) {
    0 -> Color(0xFF2F80ED)
    1 -> Color(0xFFF2994A)
    2 -> Color(0xFFE88DB5)
    else -> Color(0xFFF2C94C)
}

private fun Rotation.nextClockwise(): Rotation =
    Rotation.entries[(ordinal + 1) % Rotation.entries.size]

private fun playerImageSourceRect(playerId: Int): SourceImageRect = when (playerId) {
    0 -> SourceImageRect(left = 264, top = 216, width = 726, height = 751)
    1 -> SourceImageRect(left = 196, top = 145, width = 856, height = 890)
    2 -> SourceImageRect(left = 206, top = 165, width = 839, height = 866)
    else -> SourceImageRect(left = 252, top = 202, width = 768, height = 773)
}

private const val BOARD_SOURCE_WIDTH = 1086f
private const val BOARD_SOURCE_HEIGHT = 1448f
private const val GRID_LEFT = 48f
private const val GRID_TOP = 488f
private const val GRID_RIGHT = 1038f
private const val GRID_BOTTOM = 1364f
private const val CELL_WIDTH = (GRID_RIGHT - GRID_LEFT) / 6f
private const val CELL_HEIGHT = (GRID_BOTTOM - GRID_TOP) / 5f
private const val FOOD_CARD_BOARD_PADDING = 4f
private const val FOOD_CARD_STACK_OFFSET = 6f

// 腹減りメーター画像の配置(盤面に対する比率)。高さは画像の自然比(1536:762)に
// 合わせ、ContentScale.Fit でレターボックスが出ないようにする(マーカー位置が合う)。
private const val METER_LEFT = 0.14f
private const val METER_TOP = 0.018f
private const val METER_WIDTH = 0.72f
private const val METER_HEIGHT = 0.268f
