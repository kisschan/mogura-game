package com.moguru.game.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moguru.game.model.FoodType
import com.moguru.game.presenter.displayName
import kotlinx.coroutines.delay

/** 回転中の目の切り替え間隔。 */
private const val SPIN_FRAME_MILLIS = 70L

/** タップ後に減速しながら目を送る各フレームの待機時間。 */
private val LANDING_FRAME_MILLIS = listOf(90L, 130L, 190L, 270L, 380L)

/** 着地した出目を見せておく時間。 */
private const val RESULT_PAUSE_MILLIS = 700L

/**
 * 捕獲ダイスのルーレット演出オーバーレイ。
 *
 * 2段階演出: まず見つけたエサカードを公開し（[foodType]・[escapeRolls]）、
 * ボタンでルーレットが回り始める。回転中（[targetFace] が null）にもう一度
 * ボタンを押すと [onTap] で出目を確定させ、減速して [targetFace] に着地し、
 * 出目を見せたあと [onFinished] で捕獲の解決を依頼する。
 */
@Composable
fun DiceRouletteOverlay(
    foodType: FoodType,
    escapeRolls: List<Int>,
    targetFace: Int?,
    onTap: () -> Unit,
    onFinished: () -> Unit,
) {
    var face by remember { mutableIntStateOf(1) }
    var landed by remember { mutableStateOf(false) }
    // カード公開→回転開始は純演出のためローカル state で持つ。
    var spinning by remember { mutableStateOf(false) }

    // ルーレット中に戻る操作でゲーム状態と表示がずれないよう消費する。
    BackHandler {}

    fun handlePrimaryAction() {
        when {
            targetFace != null -> Unit
            // 逃走ダイスの無いエサはカード公開からそのまま確定捕獲する。
            !spinning && escapeRolls.isEmpty() -> onFinished()
            !spinning -> spinning = true
            else -> onTap()
        }
    }

    LaunchedEffect(targetFace, spinning) {
        if (targetFace == null) {
            landed = false
            while (spinning) {
                delay(SPIN_FRAME_MILLIS)
                face = face % 6 + 1
            }
        } else {
            LANDING_FRAME_MILLIS.forEach { millis ->
                delay(millis)
                face = face % 6 + 1
            }
            face = targetFace
            landed = true
            delay(RESULT_PAUSE_MILLIS)
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000)),
        contentAlignment = Alignment.Center,
    ) {
        if (!spinning && targetFace == null) {
            FoodRevealContent(foodType, escapeRolls, onPrimaryAction = ::handlePrimaryAction)
        } else {
            DiceSpinContent(foodType, escapeRolls, face, landed, targetFace, onPrimaryAction = ::handlePrimaryAction)
        }
    }
}

/** 公開フェーズ: 見つけたエサカードを大きく見せる。 */
@Composable
private fun FoodRevealContent(
    foodType: FoodType,
    escapeRolls: List<Int>,
    onPrimaryAction: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(foodRes(foodType)),
            contentDescription = foodType.displayName(),
            modifier = Modifier.size(180.dp),
        )
        Text(
            text = "${foodType.displayName()} を見つけた！",
            modifier = Modifier.padding(top = 20.dp),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = escapeRollsLabel(escapeRolls),
            modifier = Modifier.padding(top = 8.dp),
            color = Color(0xFFFFD54F),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = rouletteRevealStatus(escapeRolls),
            modifier = Modifier.padding(top = 20.dp),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        DicePrimaryButton(
            text = roulettePrimaryActionLabel(escapeRolls),
            enabled = true,
            onClick = onPrimaryAction,
        )
    }
}

/** 回転・着地フェーズ: 上部にエサ情報を残し、中央でダイスを回す。 */
@Composable
private fun DiceSpinContent(
    foodType: FoodType,
    escapeRolls: List<Int>,
    face: Int,
    landed: Boolean,
    targetFace: Int?,
    onPrimaryAction: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(foodRes(foodType)),
                contentDescription = foodType.displayName(),
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = "${foodType.displayName()}（${escapeRollsLabel(escapeRolls)}）",
                modifier = Modifier.padding(start = 10.dp),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Image(
            painter = painterResource(diceRes(face)),
            contentDescription = "ダイスの目 $face",
            modifier = Modifier
                .padding(top = 24.dp)
                .size(180.dp),
        )
        Text(
            text = if (landed) "$targetFace が出た！" else "ダイス回転中",
            modifier = Modifier.padding(top = 20.dp),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
        )
        DicePrimaryButton(
            text = if (targetFace == null) rouletteStopActionLabel() else "結果確認中",
            enabled = targetFace == null,
            onClick = onPrimaryAction,
        )
    }
}

internal fun rouletteStopActionLabel(): String = "ダイスを止める"

@Composable
private fun DicePrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(top = 20.dp)
            .widthIn(min = 180.dp)
            .heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFFD54F),
            contentColor = Color(0xFF2E2115),
            disabledContainerColor = Color(0xFF6B6255),
            disabledContentColor = Color.White,
        ),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

private fun escapeRollsLabel(escapeRolls: List<Int>): String =
    if (escapeRolls.isEmpty()) "逃走なし" else "逃走目：${escapeRolls.joinToString("・")}"

private fun diceRes(face: Int): Int = when (face) {
    1 -> R.drawable.dice_1
    2 -> R.drawable.dice_2
    3 -> R.drawable.dice_3
    4 -> R.drawable.dice_4
    5 -> R.drawable.dice_5
    else -> R.drawable.dice_6
}
