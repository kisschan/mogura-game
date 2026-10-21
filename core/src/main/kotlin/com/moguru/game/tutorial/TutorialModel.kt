package com.moguru.game.tutorial

import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.presenter.DigTileChoice
import com.moguru.game.presenter.GameActionResult

/**
 * Longest guidance page, explanation page or notice, in characters. The play screen reserves the
 * instruction line for this many full-width characters, so practice text never moves the board.
 */
const val TUTORIAL_PAGE_MAX_CHARS = 26

/** Practice lessons in the order they are offered. The basic lesson comes first. */
enum class TutorialLessonId {
    BASIC,
    DICE_ESCAPE,
    SURFACE_HEALTH,
    ROBBERY_DEFENSE,
    ROBBERY_CARRY,
    NEST_EAT,
}

/** One learner operation. Each one matches an existing play screen input. */
sealed interface TutorialAction {
    data class SelectDigCell(val position: Position) : TutorialAction
    data class SelectDigTile(val choice: DigTileChoice) : TutorialAction
    data class RotateDigTile(val rotation: Rotation) : TutorialAction
    data object ConfirmDig : TutorialAction
    /** The screen's tentative move target. It does not change the game. */
    data class SelectMoveTarget(val position: Position) : TutorialAction
    data class Move(val position: Position) : TutorialAction
    data class SelectCaptureTarget(val index: Int) : TutorialAction
    data object Capture : TutorialAction
    data object RollDice : TutorialAction
    data object ResolveCapture : TutorialAction
    data object Eat : TutorialAction
    data object Carry : TutorialAction
    data class SelectRobberyTarget(val index: Int) : TutorialAction
    data object Rob : TutorialAction
    data object Skip : TutorialAction
    data object FinishTurn : TutorialAction
}

enum class TutorialStatus {
    /** An explanation is shown. Nothing else moves until it is confirmed. */
    AWAITING_ACKNOWLEDGEMENT,
    LEARNER_ACTION,
    OPPONENT_TURN,
    COMPLETED,
}

/** Screen controls a step points at, in addition to its board cells. */
enum class TutorialControl {
    ACKNOWLEDGE,
    DIG_TILE_REVEALED,
    DIG_TILE_DRAWN,
    ROTATE,
    CONFIRM_DIG,
    MOVE,
    CAPTURE,
    ROLL_DICE,
    RESOLVE_CAPTURE,
    EAT,
    CARRY,
    ROB,
    SKIP,
    FINISH_TURN,
}

/**
 * What the learner is shown now. [text] is one short page for the existing instruction line.
 * [notice] explains the last refused operation and the next one, until something is accepted.
 */
data class TutorialGuidance(
    /** Learning stage of the lesson, starting at 1. */
    val stage: Int,
    val text: String,
    val targetCells: Set<Position> = emptySet(),
    val controls: Set<TutorialControl> = emptySet(),
    val notice: String? = null,
    /** 1-based page and page count while an explanation waits for confirmation. */
    val page: Int? = null,
    val pageCount: Int? = null,
)

/**
 * [tutorialRejected] means the lesson refused the operation before the game saw it, so [game]
 * is null and nothing changed. A refusal by the game itself keeps its result in [game].
 */
data class TutorialResult(
    val accepted: Boolean,
    val tutorialRejected: Boolean,
    val message: String,
    val game: GameActionResult?,
)
