package com.moguru.game.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

@Composable
internal fun GameResumeScreen(
    state: GamePersistenceUiState,
    onResume: () -> Unit,
    onNewGame: () -> Unit,
    onRetryLoad: () -> Unit,
    onRules: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("モグる・タベる・イキのこる", fontSize = 26.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(28.dp))
        if (state.loadIssue != null) {
            Text(when (state.loadIssue) {
                GameSaveLoadIssue.UNSUPPORTED -> "このバージョンでは保存データを開けません。アプリの更新を確認してください。"
                GameSaveLoadIssue.CORRUPT -> "保存データを読み込めませんでした。"
                GameSaveLoadIssue.IO -> "保存データを読み込めませんでした。もう一度お試しください。"
            })
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetryLoad, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("再試行")
            }
        } else {
            Text(if (state.finished) "前回のゲームが終了しました" else
                state.playerCount.toString() + "人プレイ・" + state.currentPlayerName + " の番",
                fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onResume, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("resume-game")) {
                Text(if (state.finished) "前回の結果を見る" else "続きから", fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onNewGame, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("new-game-setup")) {
            Text("新しく始める")
        }
        TextButton(onClick = onRules, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
            Text("遊び方")
        }
        Text("ゲームは自動で保存されます", color = Color(0xFF6F5943), modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
internal fun GameSaveDialogs(state: GamePersistenceUiState, viewModel: AndroidGameViewModel) {
    BackHandler(enabled = state.busy || state.saveFailed) {}
    if (state.saveFailed) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("保存できませんでした") },
            text = { Text("空き容量などを確認して、再試行してください。保存できるまでゲームを一時停止します。") },
            confirmButton = { TextButton(onClick = viewModel::retrySave) { Text("再試行") } },
        )
    } else if (state.confirmOverwrite) {
        AlertDialog(
            onDismissRequest = viewModel::cancelOverwrite,
            title = { Text("新しく始めますか？") },
            text = { Text("前のゲームの保存データに上書きします。") },
            confirmButton = { TextButton(onClick = viewModel::confirmNewGame) { Text("新しく始める") } },
            dismissButton = { TextButton(onClick = viewModel::cancelOverwrite) { Text("戻る") } },
        )
    }
}

@Composable
internal fun GameSaveInputBlocker() {
    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(250); showProgress = true }
    Box(
        Modifier.fillMaxSize().zIndex(10f).testTag("game-save-busy")
            .semantics { contentDescription = "ゲームを保存・読み込み中" }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (showProgress) {
            Column(
                Modifier.background(Color(0xFFFDF7E9)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text("準備しています…", modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
