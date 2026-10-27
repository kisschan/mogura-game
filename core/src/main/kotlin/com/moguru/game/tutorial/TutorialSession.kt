package com.moguru.game.tutorial

import com.moguru.game.cpu.CpuAction
import com.moguru.game.cpu.applyCpuAction
import com.moguru.game.cpu.cpuObservation
import com.moguru.game.presenter.GameActionResult
import com.moguru.game.presenter.MoguraGameController

internal const val OPPONENT_TURN_TEXT = "相手役の番だよ。見ていてね"
internal const val WAIT_TEXT = "少し待ってね"
internal const val EXPLANATION_REFUSAL = "説明を読んで「次へ」を押そう"
internal const val OPPONENT_REFUSAL = "相手役の番だよ。少し待ってね"
internal const val COMPLETED_REFUSAL = "この練習はクリア済みだよ"

/**
 * One practice run. It decides which learner operations the lesson accepts before the game sees
 * them, shows explanations, and plays the opponent's scripted legal moves. The game itself is an
 * ordinary [MoguraGameController], so every rule is checked and applied exactly as in a match.
 *
 * A chapter begins on the learner's turn and ends when the opponent's turn after it is over.
 * When the learner practises alone, it ends with the learner's own turn once its explanations
 * have been read, and the next chapter begins on the learner's next turn.
 */
class TutorialSession(val scenario: TutorialScenario, startChapterIndex: Int = 0) {
    var controller: MoguraGameController
        private set

    var chapterIndex: Int = startChapterIndex
        private set

    /** Increases on every chapter restart, so callers can drop anything left from before. */
    var generation: Long = 0
        private set

    /** Increases whenever another explanation page is shown, so a stale confirmation can be ignored. */
    var explanationSerial: Long = 0
        private set

    val learnerPlayerId: Int get() = scenario.learnerPlayerId
    val opponentPlayerId: Int? get() = scenario.opponentPlayerId

    private var dice: ScenarioDice
    private var turnOwner: Int?
    /** Turns ended when the current chapter began, so the end of its turn can be told apart. */
    private var chapterTurnEnds: Long
    private val starts = mutableMapOf<Int, TutorialStart>()
    private var explanation: TutorialExplanation? = null
    private var page = 0
    private val shown = mutableSetOf<String>()
    private val accepted = mutableListOf<TutorialAction>()
    private var notice: String? = null
    private var completed = false

    init {
        require(startChapterIndex in 0 until scenario.chapterCount) { "No chapter $startChapterIndex" }
        if (startChapterIndex == 0) {
            val (dealt, dealtDice) = scenario.deal()
            controller = dealt
            dice = dealtDice
        } else {
            val start = scenario.start(startChapterIndex)
            dice = scenario.dice(start.dicePosition)
            controller = MoguraGameController.fromPracticeSnapshot(start.game, dice, scenario.shuffler)
        }
        turnOwner = controller.currentPlayer?.id
        chapterTurnEnds = controller.turnEndCount
        starts[chapterIndex] = TutorialStart(controller.exportSnapshot(), dice.position)
        refresh()
    }

    val status: TutorialStatus
        get() = when {
            completed -> TutorialStatus.COMPLETED
            explanation != null -> TutorialStatus.AWAITING_ACKNOWLEDGEMENT
            opponentPlayerId != null && controller.currentPlayer?.id == opponentPlayerId -> TutorialStatus.OPPONENT_TURN
            else -> TutorialStatus.LEARNER_ACTION
        }

    val guidance: TutorialGuidance
        get() {
            val chapter = scenario.chapters[chapterIndex]
            explanation?.let {
                return TutorialGuidance(
                    stage = it.stage, text = it.pages[page], targetCells = it.targetCells,
                    controls = setOf(TutorialControl.ACKNOWLEDGE), notice = notice,
                    page = page + 1, pageCount = it.pages.size,
                )
            }
            if (completed) return TutorialGuidance(scenario.stageCount, scenario.completionText, notice = notice)
            if (status == TutorialStatus.OPPONENT_TURN) {
                return TutorialGuidance(chapter.stage, OPPONENT_TURN_TEXT, notice = notice)
            }
            val step = chapter.step(context()) ?: return TutorialGuidance(chapter.stage, WAIT_TEXT, notice = notice)
            return TutorialGuidance(step.stage, step.text, step.targetCells, step.controls, notice)
        }

    /** Confirms the explanation page now shown. Returns false when nothing waited for it. */
    fun acknowledge(): Boolean {
        val current = explanation ?: return false
        notice = null
        if (page < current.pages.lastIndex) {
            page++
            explanationSerial++
        } else {
            explanation = null
            page = 0
            refresh()
        }
        return true
    }

    /** Null when the lesson accepts [action] now; otherwise the reason it would be refused. */
    fun check(action: TutorialAction): String? = when (status) {
        TutorialStatus.COMPLETED -> COMPLETED_REFUSAL
        TutorialStatus.AWAITING_ACKNOWLEDGEMENT -> EXPLANATION_REFUSAL
        TutorialStatus.OPPONENT_TURN -> OPPONENT_REFUSAL
        TutorialStatus.LEARNER_ACTION -> {
            val step = scenario.chapters[chapterIndex].step(context())
            if (step == null) WAIT_TEXT else step.check(action)
        }
    }

    /**
     * Runs one learner operation if the lesson accepts it. A refused operation never reaches the
     * game: the board, piles, health, score and history stay as they were and only the notice changes.
     * Automatic progression is left to [advanceAutomatic], as on the play screen.
     */
    fun perform(action: TutorialAction): TutorialResult {
        check(action)?.let { reason ->
            notice = reason
            return TutorialResult(accepted = false, tutorialRejected = true, message = reason, game = null)
        }
        val game = controller.runTutorialAction(action)
        if (game != null && !game.success) {
            notice = game.message
            return TutorialResult(accepted = false, tutorialRejected = false, message = game.message, game = game)
        }
        notice = null
        accepted += action
        refresh()
        return TutorialResult(accepted = true, tutorialRejected = false, message = game?.message ?: "移動先を選びました。", game = game)
    }

    /** Skips phases without a choice, unless an explanation or the finished lesson holds the game. */
    fun advanceAutomatic(): GameActionResult? {
        if (status != TutorialStatus.LEARNER_ACTION && status != TutorialStatus.OPPONENT_TURN) return null
        val result = controller.autoAdvanceWhileNoChoice() ?: return null
        refresh()
        return result
    }

    /** The opponent's scripted step for this moment, or null when it is not the opponent's move. */
    fun nextOpponentAction(): CpuAction? {
        if (status != TutorialStatus.OPPONENT_TURN) return null
        val action = scenario.opponentScript(context()) ?: return null
        return action.takeIf { it in controller.cpuObservation().legalActions }
    }

    /** Plays [action] for the opponent. Only the scripted legal step is accepted. */
    fun performOpponent(action: CpuAction): TutorialResult {
        val scripted = nextOpponentAction()
        if (scripted == null || action != scripted) {
            val reason = if (status == TutorialStatus.OPPONENT_TURN) "台本にない相手役の操作です。" else OPPONENT_REFUSAL
            return TutorialResult(accepted = false, tutorialRejected = true, message = reason, game = null)
        }
        val game = controller.applyCpuAction(action)
        if (!game.success) return TutorialResult(accepted = false, tutorialRejected = false, message = game.message, game = game)
        notice = null
        refresh()
        return TutorialResult(accepted = true, tutorialRejected = false, message = game.message, game = game)
    }

    /** Returns to the state in which the current chapter began in this run, as a new attempt. */
    fun restartChapter() {
        val start = starts.getValue(chapterIndex)
        dice = scenario.dice(start.dicePosition)
        controller = MoguraGameController.fromPracticeSnapshot(start.game, dice, scenario.shuffler)
        chapterTurnEnds = controller.turnEndCount
        generation++
        completed = false
        resetChapterState()
        turnOwner = controller.currentPlayer?.id
        refresh()
    }

    internal fun startOf(chapter: Int): TutorialStart = starts.getValue(chapter)

    private fun context() = TutorialContext(
        controller, chapterIndex, learnerPlayerId, opponentPlayerId, shown.toSet(), accepted.toList(),
        turnOver = controller.turnEndCount > chapterTurnEnds,
    )

    private fun refresh() {
        val owner = controller.currentPlayer?.id
        if (!scenario.solo && owner == learnerPlayerId && turnOwner == opponentPlayerId) beginNextChapter()
        turnOwner = owner
        if (completed || explanation != null) return
        val chapter = scenario.chapters[chapterIndex]
        val context = context()
        val next = chapter.explanations.firstOrNull { it.key !in shown && it.showsWhen(context) }
        if (next != null) {
            shown += next.key
            explanation = next
            page = 0
            explanationSerial++
            return
        }
        if (chapter.completesWhen(context)) {
            completed = true
        } else if (scenario.solo && context.turnOver && chapterIndex + 1 < scenario.chapterCount) {
            // Alone, the learner's next turn has already begun. It opens the next chapter.
            beginNextChapter()
            refresh()
        }
    }

    private fun beginNextChapter() {
        kotlin.check(chapterIndex + 1 < scenario.chapterCount) { "${scenario.lesson} has no chapter after $chapterIndex" }
        chapterIndex++
        resetChapterState()
        chapterTurnEnds = controller.turnEndCount
        starts[chapterIndex] = TutorialStart(controller.exportSnapshot(), dice.position)
    }

    private fun resetChapterState() {
        explanation = null
        page = 0
        shown.clear()
        accepted.clear()
        notice = null
        explanationSerial++
    }
}

private const val DRIVE_LIMIT = 400

/** Automatic progression until the game waits for a choice, an explanation or the end. */
internal fun TutorialSession.settle() {
    repeat(DRIVE_LIMIT) { advanceAutomatic() ?: return }
    error("Automatic progression did not stop")
}

/** Confirms explanations and plays the opponent until the learner may operate. */
internal fun TutorialSession.reachLearnerAction() {
    repeat(DRIVE_LIMIT) {
        when (status) {
            TutorialStatus.LEARNER_ACTION -> return
            TutorialStatus.AWAITING_ACKNOWLEDGEMENT -> { acknowledge(); settle() }
            TutorialStatus.OPPONENT_TURN -> playOpponentStep()
            TutorialStatus.COMPLETED -> error("${scenario.lesson} is already complete")
        }
    }
    error("The learner never got to act")
}

/** Confirms explanations and plays the opponent until the lesson is complete. */
internal fun TutorialSession.reachCompletion() {
    repeat(DRIVE_LIMIT) {
        when (status) {
            TutorialStatus.COMPLETED -> return
            TutorialStatus.AWAITING_ACKNOWLEDGEMENT -> { acknowledge(); settle() }
            TutorialStatus.OPPONENT_TURN -> playOpponentStep()
            TutorialStatus.LEARNER_ACTION -> error("${scenario.lesson} still waits for the learner")
        }
    }
    error("The lesson did not complete")
}

private fun TutorialSession.playOpponentStep() {
    val action = checkNotNull(nextOpponentAction()) { "${scenario.lesson} has no scripted opponent step" }
    kotlin.check(performOpponent(action).accepted) { "${scenario.lesson} refused the scripted $action" }
    settle()
}
