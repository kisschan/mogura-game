package com.moguru.game.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val RULES_SCREEN_TEST_TAG = "rules-screen"
internal const val RULES_SETUP_BUTTON_TEST_TAG = "rules-setup-button"
internal const val RULES_BACK_BUTTON_TEST_TAG = "rules-back-button"
internal const val RULES_GOAL_TEST_TAG = "rules-goal"
internal const val RULES_TURN_FLOW_TEST_TAG = "rules-turn-flow"
internal const val RULES_DETAILS_TEST_TAG = "rules-details"
internal val RULES_MIN_TOUCH_TARGET = 44.dp

private val RulesBackground = Color(0xFFFFF7E4)
private val RulesInk = Color(0xFF2E2115)
private val RulesMutedInk = Color(0xFF5E4935)
private val RulesGreen = Color(0xFF35BC67)
private val RulesGreenDark = Color(0xFF176A37)
private val RulesGold = Color(0xFFF2C94C)
private val RulesBlue = Color(0xFF56A3E8)
private val RulesRed = Color(0xFFE8665A)

private data class RuleStep(
    val number: String,
    val title: String,
    val description: String,
    val accent: Color,
)

private data class RuleDetailSection(
    val icon: String,
    val title: String,
    val bullets: List<String>,
)

@Composable
internal fun RulesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RulesBackground)
            .safeDrawingPadding()
            .testTag(RULES_SCREEN_TEST_TAG),
    ) {
        RulesTopBar(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RulesHeroCard()

            RulesSectionTitle("まず、これだけ覚えよう")
            RulesGoalCard()
            RulesTurnFlow()
            RulesBoardLegend()

            RulesSectionTitle(
                text = "詳しいルール",
                modifier = Modifier.testTag(RULES_DETAILS_TEST_TAG),
            )
            ruleDetailSections().forEach { section ->
                RuleDetailCard(section)
            }

            RulesTipCard()

            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RulesGreen,
                    contentColor = Color(0xFF102F1B),
                ),
            ) {
                Text(
                    text = "ゲームに戻る",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RulesTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .size(RULES_MIN_TOUCH_TARGET)
                .testTag(RULES_BACK_BUTTON_TEST_TAG)
                .semantics {
                    contentDescription = "遊び方を閉じる"
                },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(0.dp),
            border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.12f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White.copy(alpha = 0.72f),
                contentColor = RulesInk,
            ),
        ) {
            Text(
                text = "←",
                modifier = Modifier.padding(end = 1.dp),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = "遊び方",
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .semantics { heading() },
            color = RulesInk,
            fontSize = 21.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.width(RULES_MIN_TOUCH_TARGET))
    }
}

@Composable
private fun RulesHeroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8FFF0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = Color.Black.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(8.dp),
                    ),
                contentScale = ContentScale.Fit,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "3分でわかる遊び方",
                    modifier = Modifier.semantics { heading() },
                    color = RulesInk,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "穴をつなげてエサを捕まえ、巣までレンコウしよう！",
                    color = RulesMutedInk,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun RulesSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .semantics { heading() },
        color = RulesInk,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
private fun RulesGoalCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RULES_GOAL_TEST_TAG),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1C5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "🏆 勝ち方",
                modifier = Modifier.semantics { heading() },
                color = RulesInk,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "エサを巣へ持ち帰り、だれよりも先に目標点へ到達したら勝ちです。",
                color = RulesMutedInk,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RulesScorePill(
                    label = "2〜3人",
                    score = "4点先取",
                    modifier = Modifier.weight(1f),
                )
                RulesScorePill(
                    label = "4人",
                    score = "5点先取",
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "2人では、どちらかの体力が0になると、得点に関係なく残ったプレイヤーの勝ちです。3〜4人では脱落者の手番を飛ばして続けます。全員が脱落した場合は引き分けです。",
                color = RulesMutedInk,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun RulesScorePill(
    label: String,
    score: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            color = RulesMutedInk,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = score,
            color = RulesGreenDark,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun RulesTurnFlow() {
    val steps = listOf(
        RuleStep(
            number = "1",
            title = "掘る",
            description = "毎ターン必須。隣のマスに穴タイルを置き、道をつなげます。",
            accent = RulesGold,
        ),
        RuleStep(
            number = "2",
            title = "いどう",
            description = "つながった道なら距離制限なし。動かずに次へ進んでもOKです。",
            accent = RulesBlue,
        ),
        RuleStep(
            number = "3",
            title = "捕獲",
            description = "エサのマスに止まったら挑戦できます。捕獲はパスもできます。",
            accent = RulesRed,
        ),
        RuleStep(
            number = "4",
            title = "タベる or レンコウ",
            description = "体力を回復するか、巣へ持ち帰って得点にするかを選びます。",
            accent = RulesGreen,
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RULES_TURN_FLOW_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "1ターンの順番",
            modifier = Modifier.semantics { heading() },
            color = RulesInk,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
        )
        steps.forEach { step ->
            RuleStepCard(step)
        }
        Text(
            text = "手番の終わりに体力が減ります：地下は−1、地上は−2。",
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFFFE3DF))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            color = Color(0xFF8A302A),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "④を選べるのは決まったタイミングだけです。相手の巣では「強奪」に置き換わります。",
            color = RulesMutedInk,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun RuleStepCard(step: RuleStep) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.78f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(step.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = step.number,
                    color = RulesInk,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = step.title,
                    color = RulesInk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = step.description,
                    color = RulesMutedInk,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun RulesBoardLegend() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2EADB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = "👀 盤面の見方",
                modifier = Modifier.semantics { heading() },
                color = RulesInk,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
            )
            RulesLegendRow(color = RulesGold, label = "黄の破線", description = "掘れるマス")
            RulesLegendRow(color = RulesBlue, label = "青の実線", description = "移動できるマス")
            RulesLegendRow(color = RulesRed, label = "赤の二重線", description = "捕獲できるエサ")
            Text(
                text = "迷ったら、光っているマスか画面下のボタンを選べば進められます。",
                color = RulesMutedInk,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun RulesLegendRow(
    color: Color,
    label: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            modifier = Modifier.width(72.dp),
            color = RulesInk,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = description,
            color = RulesMutedInk,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun RuleDetailCard(section: RuleDetailSection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.80f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${section.icon} ${section.title}",
                modifier = Modifier.semantics { heading() },
                color = RulesInk,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Black,
            )
            section.bullets.forEach { bullet ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "•",
                        color = RulesGreenDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = bullet,
                        modifier = Modifier.weight(1f),
                        color = RulesMutedInk,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RulesTipCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8FFF0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "💡 はじめてのコツ",
                modifier = Modifier.semantics { heading() },
                color = RulesInk,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "最初は近くのエサを狙い、体力が少なくなったら「タベる」を選びましょう。得点が必要なときは「レンコウ」で自分の巣を目指します。",
                color = RulesMutedInk,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

private fun ruleDetailSections(): List<RuleDetailSection> = listOf(
    RuleDetailSection(
        icon = "🎮",
        title = "ゲームの準備",
        bullets = listOf(
            "2〜4人で遊びます。人数を決め、各プレイヤーのモグラ・巣・先手を選びます。",
            "穴タイルとエサの初期配置、山札の準備はアプリが自動で行います。",
        ),
    ),
    RuleDetailSection(
        icon = "⛏️",
        title = "掘る",
        bullets = listOf(
            "山札から穴タイルを1枚引き、自分の上下左右にある掘れるマスを選びます。",
            "そのマスの既存タイルと引いたタイルから1枚を選び、回転して「置く」で確定します。選ばなかった1枚は捨て札になります。",
            "掘るのは毎ターン必須です。ただし掘れる対象がない場合は自動で「いどう」へ進みます。",
            "地上・巣・他のモグラが止まっているマスには穴タイルを置けません。",
        ),
    ),
    RuleDetailSection(
        icon = "🐾",
        title = "いどう",
        bullets = listOf(
            "道がつながっていれば、上下左右へ何マスでも移動できます。斜め移動はできません。",
            "他のモグラがいるマスは通過できますが、同じマスに止まることはできません。",
            "巣は通過できません。巣へ入る場合は、そこで移動を終えます。",
            "移動せずに次のフェーズへ進むこともできます。",
        ),
    ),
    RuleDetailSection(
        icon = "🎲",
        title = "捕獲",
        bullets = listOf(
            "エサと同じマスに止まったときだけ捕獲に挑戦できます。捕獲はパスできます。",
            "カブトムシの幼虫はダイスなしで捕獲成功。その他のエサはダイスを振ります。",
            "出目がエサに書かれた数字なら、矢印の方向へエサが逃げます。書かれていない数字なら捕獲成功です。",
            "逃げ先が盤外・無効マス・巣なら逃げられないため、捕獲成功になります。",
        ),
    ),
    RuleDetailSection(
        icon = "🍖",
        title = "タベる と レンコウ",
        bullets = listOf(
            "タベる：その場でエサを食べ、表示された量だけ体力を回復します。体力の上限は13です。",
            "レンコウ：エサを連れて実際に自分の巣へ移動し、到着すると得点を獲得します。",
            "レンコウ中も掘る・移動はできますが、別のエサは捕獲できず、途中で捨てることもできません。",
            "④が発生するのは、捕獲成功直後、自分の巣へ戻った直後（巣にエサがあるとき）、またはエサのある相手の巣に入った次の自分の手番です。ただし相手の巣では「強奪」に置き換わります。",
            "タベるとレンコウの両方を同じ手番に行うことはできません。",
        ),
    ),
    RuleDetailSection(
        icon = "🥷",
        title = "強奪 と 巣の防衛",
        bullets = listOf(
            "相手が留守の巣に入り、次の自分の手番までその巣に残ると、巣のエサを1枚強奪できます。",
            "強奪したら、すぐにタベるかレンコウを選びます。レンコウならエサを即座に自分の巣へ移し、その時点で得点になります。",
            "巣の持ち主が戻ると侵入者は固定のマスへ追い出されます。自分がいる巣へ相手は入れません。",
        ),
    ),
    RuleDetailSection(
        icon = "☀️",
        title = "地上 と 体力",
        bullets = listOf(
            "体力は13から始まり、手番の終わりに地下では1、地上では2減ります。0になると脱落です。",
            "地上には穴タイルを置きません。地下の穴が上向きにつながっていれば地上へ出られ、地上同士は自由に移動できます。",
            "地上から地下へ戻ると、体力の減少は通常の−1へ戻ります。",
        ),
    ),
    RuleDetailSection(
        icon = "🔄",
        title = "エサの補充 と ゲーム終了",
        bullets = listOf(
            "赤枠のホットゾーンから裏向きのエサがなくなると、4マスすべてへエサが即時補充されます。",
            "2〜3人は4点、4人は5点を先に獲得したプレイヤーの勝利です。",
            "2人では、どちらかの体力が0になると、得点に関係なく残ったプレイヤーの勝利です。",
            "3〜4人では、体力0のプレイヤーを脱落として手番を飛ばし、ゲームを続けます。",
            "全員の体力が0になった場合は引き分けになります。",
        ),
    ),
)
