package com.moguru.game.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moguru.game.tutorial.TutorialControl
import com.moguru.game.tutorial.TutorialLessonId

internal const val TUTORIAL_SETUP_BUTTON_TEST_TAG = "tutorial-setup-button"
internal const val TUTORIAL_RULES_BUTTON_TEST_TAG = "tutorial-rules-button"
internal const val TUTORIAL_RESUME_ENTRY_TEST_TAG = "tutorial-resume-entry"
internal const val TUTORIAL_MENU_TEST_TAG = "tutorial-menu"
internal const val TUTORIAL_MENU_BACK_TEST_TAG = "tutorial-menu-back"
internal const val TUTORIAL_PROGRESS_RETRY_TEST_TAG = "tutorial-progress-retry"
internal const val TUTORIAL_NEXT_TEST_TAG = "tutorial-next"
internal const val TUTORIAL_COMPLETE_TEST_TAG = "tutorial-complete"
internal const val TUTORIAL_COMPLETE_RETRY_TEST_TAG = "tutorial-complete-retry"
internal const val TUTORIAL_COMPLETE_LESSONS_TEST_TAG = "tutorial-complete-lessons"
internal const val TUTORIAL_COMPLETE_SETUP_TEST_TAG = "tutorial-complete-setup"
internal const val GAME_MENU_RESTART_CHAPTER_ITEM_TEST_TAG = "game-menu-restart-chapter-item"
internal const val GAME_MENU_EXIT_PRACTICE_ITEM_TEST_TAG = "game-menu-exit-practice-item"
internal const val GAME_MENU_PRACTICE_INFO_TEST_TAG = "game-menu-practice-info"

/** Violet frame for practice targets. It sits outside the existing dig, move, capture and selection marks. */
internal const val TUTORIAL_FRAME_ARGB = 0xFF7B1FA2.toInt()
internal val TUTORIAL_FRAME_WIDTH: Dp = 3.dp
internal const val TUTORIAL_TARGET_STATE = "練習の次の操作"

internal fun tutorialLessonStartTestTag(lesson: TutorialLessonId) = "tutorial-start-${lesson.name}"
internal fun tutorialLessonResumeTestTag(lesson: TutorialLessonId) = "tutorial-resume-${lesson.name}"
internal fun tutorialTargetTestTag(col: Int, row: Int) = "tutorial-target-$col-$row"

/** The practice line of the action bar: the refusal notice while one is shown, otherwise the page. */
internal fun tutorialInstruction(practice: AndroidTutorialUiState): String = practice.notice ?: practice.text

/** Read aloud with the instruction, so the lesson position never depends on colour or layout. */
internal fun tutorialInstructionDescription(practice: AndroidTutorialUiState): String {
    val position = if (practice.lesson == TutorialLessonId.BASIC) {
        "${practice.title} 段階${practice.stage}/${practice.stageCount}"
    } else {
        "${practice.title} 第${practice.chapter}章/${practice.chapterCount}"
    }
    val targets = if (practice.targetCells.isEmpty()) "" else "。枠のマス: " +
        practice.targetCells.sortedWith(compareBy({ it.row }, { it.col })).joinToString("、") { "${it.col + 1}列${it.row + 1}行" }
    return "練習 $position。${tutorialInstruction(practice)}$targets"
}

internal fun tutorialMenuInfoText(practice: AndroidTutorialUiState): String =
    if (practice.lesson == TutorialLessonId.BASIC) {
        "${practice.title} 第${practice.chapter}章・段階${practice.stage}/${practice.stageCount}"
    } else {
        "${practice.title} 第${practice.chapter}章/${practice.chapterCount}"
    }

internal fun tutorialNextLabel(page: Int, pageCount: Int): String = "次へ（$page/$pageCount）"

internal fun tutorialResumeLabel(lesson: AndroidTutorialLessonUiState): String =
    "練習の続き（${lesson.title}・第${(lesson.resumeChapter ?: 0) + 1}章から）"

internal fun AndroidVisibleAction.tutorialControl(): TutorialControl = when (this) {
    AndroidVisibleAction.CAPTURE -> TutorialControl.CAPTURE
    AndroidVisibleAction.ROB -> TutorialControl.ROB
    AndroidVisibleAction.EAT -> TutorialControl.EAT
    AndroidVisibleAction.CARRY -> TutorialControl.CARRY
    AndroidVisibleAction.SKIP -> TutorialControl.SKIP
    AndroidVisibleAction.END_TURN -> TutorialControl.FINISH_TURN
}

/** Frames a practice target control and says so to screen readers. */
internal fun Modifier.tutorialFrame(active: Boolean): Modifier =
    if (!active) {
        this
    } else {
        border(TUTORIAL_FRAME_WIDTH, Color(TUTORIAL_FRAME_ARGB), RoundedCornerShape(10.dp))
            .semantics { stateDescription = TUTORIAL_TARGET_STATE }
    }

/** Shown in the action bar instead of the game controls while an explanation page waits. */
@Composable
internal fun TutorialNextButton(
    practice: AndroidTutorialUiState,
    buttonHeight: Dp,
    onNext: () -> Unit,
) {
    val label = tutorialNextLabel(practice.page ?: 1, practice.pageCount ?: 1)
    Button(
        onClick = onNext,
        modifier = Modifier
            .fillMaxWidth()
            .height(buttonHeight)
            .testTag(TUTORIAL_NEXT_TEST_TAG)
            .tutorialFrame(true)
            .semantics { contentDescription = "$label。説明を読んだら押す" },
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF35BC67),
            contentColor = Color(0xFF102F1B),
        ),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

/** 「操作を練習する」 and, after an interrupted lesson, its resume offer. */
@Composable
internal fun TutorialEntryButtons(
    resume: AndroidTutorialLessonUiState?,
    onPractice: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedButton(
            onClick = onPractice,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(TUTORIAL_SETUP_BUTTON_TEST_TAG),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            border = BorderStroke(2.dp, Color(TUTORIAL_FRAME_ARGB)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White.copy(alpha = 0.72f),
                contentColor = Color(0xFF2E2115),
            ),
        ) {
            Text("操作を練習する", fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
        resume?.let { lesson ->
            TextButton(
                onClick = onResume,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(TUTORIAL_RESUME_ENTRY_TEST_TAG),
            ) {
                Text(tutorialResumeLabel(lesson), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }
}

/** The lesson list. Starting a lesson never asks about the match save, which stays untouched. */
@Composable
internal fun TutorialMenuScreen(
    menu: AndroidTutorialMenuUiState,
    onStart: (TutorialLessonId, Boolean) -> Unit,
    onBack: () -> Unit,
    onRetrySave: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF7E4))
            .safeDrawingPadding()
            .testTag(TUTORIAL_MENU_TEST_TAG),
    ) {
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
                    .testTag(TUTORIAL_MENU_BACK_TEST_TAG)
                    .semantics { contentDescription = "練習の一覧を閉じる" },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("←", fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            Text(
                text = "操作を練習する",
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .semantics { heading() },
                color = Color(0xFF2E2115),
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(RULES_MIN_TOUCH_TARGET))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "実際の盤面で操作を練習します。練習の盤面は保存されず、通常のゲームの保存も変わりません。",
                color = Color(0xFF5E4935),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            if (menu.progressLoadFailed) {
                TutorialNote("練習の記録を読み込めませんでした。記録なしで始めます。次に保存すると置き換わります。")
            }
            if (menu.progressSaveFailed) {
                TutorialNote("練習の記録を保存できませんでした。練習はそのまま続けられます。")
                TextButton(
                    onClick = onRetrySave,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(TUTORIAL_PROGRESS_RETRY_TEST_TAG),
                ) {
                    Text("記録の保存を再試行", fontWeight = FontWeight.Bold)
                }
            }
            menu.lessons.firstOrNull { it.lesson == TutorialLessonId.BASIC }?.let { basic ->
                Text("基本編", modifier = Modifier.semantics { heading() }, fontSize = 18.sp, fontWeight = FontWeight.Black)
                TutorialLessonCard(basic, onStart)
            }
            Text("追加レッスン", modifier = Modifier.semantics { heading() }, fontSize = 18.sp, fontWeight = FontWeight.Black)
            menu.lessons.filter { it.lesson != TutorialLessonId.BASIC }.forEach { TutorialLessonCard(it, onStart) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TutorialNote(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFE3DF), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = Color(0xFF8A302A),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun TutorialLessonCard(
    lesson: AndroidTutorialLessonUiState,
    onStart: (TutorialLessonId, Boolean) -> Unit,
) {
    val status = buildList {
        if (lesson.completed) add("クリア済み")
        lesson.resumeChapter?.let { add("第${it + 1}章から再開できます") }
    }.joinToString("・").ifEmpty { "未挑戦" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFD0AD78), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(lesson.title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E2115))
        Text(lesson.summary, fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF5E4935))
        Text("全${lesson.chapterCount}章・$status", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF176A37))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onStart(lesson.lesson, true) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag(tutorialLessonStartTestTag(lesson.lesson))
                    .semantics { contentDescription = "${lesson.title}をはじめから練習" },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF35BC67), contentColor = Color(0xFF102F1B)),
            ) {
                Text("はじめから", fontWeight = FontWeight.Black)
            }
            lesson.resumeChapter?.let { chapter ->
                OutlinedButton(
                    onClick = { onStart(lesson.lesson, false) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(tutorialLessonResumeTestTag(lesson.lesson))
                        .semantics { contentDescription = "${lesson.title}を第${chapter + 1}章から練習" },
                ) {
                    Text("第${chapter + 1}章から", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/** Shown over the board when a lesson is complete. It never uses the match's victory display. */
@Composable
internal fun TutorialCompletionDialog(
    completion: AndroidTutorialCompletionUiState,
    onRetry: () -> Unit,
    onLessons: () -> Unit,
    onSetup: () -> Unit,
    onRetrySave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.testTag(TUTORIAL_COMPLETE_TEST_TAG),
        shape = RoundedCornerShape(8.dp),
        containerColor = Color(0xFFFFFBF0),
        title = {
            Text(
                if (completion.isBasic) "練習クリア！" else "レッスンクリア！",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(completion.text, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                if (completion.isBasic) {
                    Text(
                        "本番は、2〜3人なら4点、4人なら5点を先に取ると勝ちです。今回の1点は練習の目標でした。",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
                if (completion.progressSaveFailed) {
                    Text("練習の記録を保存できませんでした。", color = Color(0xFF8A302A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onRetrySave, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("記録の保存を再試行")
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(TUTORIAL_COMPLETE_RETRY_TEST_TAG),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF35BC67), contentColor = Color(0xFF102F1B)),
                ) {
                    Text("もう一度練習", fontWeight = FontWeight.Black)
                }
                OutlinedButton(
                    onClick = onLessons,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(TUTORIAL_COMPLETE_LESSONS_TEST_TAG),
                ) {
                    Text("追加レッスン", fontWeight = FontWeight.Black)
                }
                TextButton(
                    onClick = onSetup,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(TUTORIAL_COMPLETE_SETUP_TEST_TAG),
                ) {
                    Text("ゲーム設定へ", fontWeight = FontWeight.Black)
                }
            }
        },
    )
}
