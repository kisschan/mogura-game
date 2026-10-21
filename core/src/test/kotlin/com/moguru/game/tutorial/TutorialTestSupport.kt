package com.moguru.game.tutorial

import com.moguru.game.cpu.CpuAction
import com.moguru.game.cpu.cpuObservation
import com.moguru.game.engine.GameState
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.persistence.GameSnapshot
import com.moguru.game.persistence.PlayerSnapshot
import com.moguru.game.presenter.DigTileChoice
import com.moguru.game.presenter.GameActionResult
import com.moguru.game.presenter.TurnConsumptionAnimationEvent
import com.moguru.game.presenter.displayName
import org.junit.jupiter.api.Assertions.*

internal val LEARNER_NEST = Position(0, 1)
internal val OPPONENT_NEST = Position(5, 4)
internal const val LEARNER_ID = 0
internal const val OPPONENT_ID = 1
private const val MAX_STEPS = 200

internal fun p(col: Int, row: Int) = Position(col, row)

internal fun GameSnapshot.player(id: Int): PlayerSnapshot = engine.players.single { it.id == id }

internal val PlayerSnapshot.score: Int get() = storedFoods.sumOf { it.type.points }

/** Words that would tell the learner what a face-down card is. */
internal fun FoodType.hiddenNames(): List<String> =
    listOf(displayName()) + if (this == FoodType.BEETLE_LARVA) listOf("幼虫") else emptyList()

/** The learner path of the basic lesson as specified in chapter 16, one list per chapter. */
internal object BasicLesson {
    val chapter0 = listOf(
        TutorialAction.SelectDigCell(p(1, 1)),
        TutorialAction.RotateDigTile(Rotation.DEG_90),
        TutorialAction.ConfirmDig,
        TutorialAction.SelectMoveTarget(p(1, 1)),
        TutorialAction.Move(p(1, 1)),
    )
    val chapter1 = listOf(
        TutorialAction.SelectDigCell(p(2, 1)),
        TutorialAction.SelectDigTile(DigTileChoice.DRAWN),
        TutorialAction.ConfirmDig,
        TutorialAction.SelectMoveTarget(p(2, 1)),
        TutorialAction.Move(p(2, 1)),
    )
    val chapter2 = listOf(
        TutorialAction.SelectDigCell(p(2, 2)),
        TutorialAction.RotateDigTile(Rotation.DEG_180),
        TutorialAction.ConfirmDig,
        TutorialAction.SelectMoveTarget(p(2, 2)),
        TutorialAction.Move(p(2, 2)),
        TutorialAction.Capture,
        TutorialAction.ResolveCapture,
        TutorialAction.Eat,
    )
    val chapter3 = listOf(
        TutorialAction.SelectDigCell(p(3, 2)),
        TutorialAction.RotateDigTile(Rotation.DEG_90),
        TutorialAction.ConfirmDig,
        TutorialAction.SelectMoveTarget(p(3, 2)),
        TutorialAction.Move(p(3, 2)),
        TutorialAction.SelectCaptureTarget(0),
        TutorialAction.Capture,
        TutorialAction.ResolveCapture,
        TutorialAction.Carry,
    )
    val chapter4 = listOf(
        TutorialAction.SelectDigCell(p(4, 2)),
        TutorialAction.ConfirmDig,
        TutorialAction.SelectMoveTarget(LEARNER_NEST),
        TutorialAction.Move(LEARNER_NEST),
        TutorialAction.FinishTurn,
    )
    val chapter5 = listOf(
        TutorialAction.SelectDigCell(p(1, 1)),
        TutorialAction.ConfirmDig,
        TutorialAction.SelectMoveTarget(p(2, 1)),
        TutorialAction.Move(p(2, 1)),
    )
    val chapters = listOf(chapter0, chapter1, chapter2, chapter3, chapter4, chapter5)

    /** Learner health, opponent health and learner score when each chapter begins. */
    val chapterStartStats = listOf(
        Triple(13, 13, 0),
        Triple(12, 12, 0),
        Triple(11, 11, 0),
        Triple(11, 10, 0),
        Triple(10, 9, 0),
        Triple(9, 8, 1),
    )

    fun withChapter(index: Int, actions: List<TutorialAction>): List<List<TutorialAction>> =
        chapters.toMutableList().also { it[index] = actions }
}

/** One accepted learner operation, with the states around it. */
internal data class LearnerStep(
    val chapter: Int,
    val action: TutorialAction,
    /** What the learner was shown when choosing this operation. */
    val guidance: TutorialGuidance,
    val before: GameSnapshot,
    /** Directly after the operation, before any automatic progression. */
    val afterAction: GameSnapshot,
    val robberyOpenAfterAction: Boolean,
)

/**
 * Plays a session the way the screen does: every accepted operation is followed by the automatic
 * progression, explanations are acknowledged, and the opponent plays only its scripted steps.
 */
internal class TutorialDriver(val session: TutorialSession) {
    val learnerSteps = mutableListOf<LearnerStep>()
    val opponentActions = mutableListOf<CpuAction>()
    val results = mutableListOf<GameActionResult>()
    /** The state in which each chapter actually began during this run. */
    val chapterStarts = linkedMapOf(session.chapterIndex to snapshot())
    /** Every guidance the learner was shown, for wording checks across a whole lesson. */
    val shownGuidance = mutableListOf(session.guidance)

    fun snapshot(): GameSnapshot = session.controller.exportSnapshot()

    fun learner(): PlayerSnapshot = snapshot().player(session.learnerPlayerId)

    fun opponent(): PlayerSnapshot = snapshot().player(session.opponentPlayerId)

    fun learnerConsumptions(): List<TurnConsumptionAnimationEvent> =
        results.mapNotNull { it.turnConsumptionAnimation }.filter { it.playerId == session.learnerPlayerId }

    fun perform(action: TutorialAction): TutorialResult {
        assertEquals(TutorialStatus.LEARNER_ACTION, session.status, "$action must wait for the learner's turn")
        assertNoHiddenFoodNamed()
        val guidance = session.guidance
        val before = snapshot()
        val chapter = session.chapterIndex
        val result = session.perform(action)
        assertTrue(result.accepted, "$action was refused in chapter $chapter: ${result.message}")
        assertFalse(result.tutorialRejected)
        result.game?.let { assertTrue(it.success, it.message); results += it }
        val controller = session.controller
        learnerSteps += LearnerStep(
            chapter, action, guidance, before, snapshot(),
            controller.playScreenUiState().actionAvailability.canRob ||
                controller.hasRobberyOpportunityForCurrentPlayer(),
        )
        track()
        settle()
        return result
    }

    fun acknowledge() {
        assertEquals(TutorialStatus.AWAITING_ACKNOWLEDGEMENT, session.status)
        assertNoHiddenFoodNamed()
        assertTrue(session.acknowledge(), "the explanation was not acknowledged")
        track()
        settle()
    }

    /** Plays the scripted opponent until its turn is over. */
    fun opponentTurn() {
        repeat(MAX_STEPS) {
            if (session.status != TutorialStatus.OPPONENT_TURN) return
            val action = requireNotNull(session.nextOpponentAction()) { "the opponent has no scripted step" }
            assertTrue(action in session.controller.cpuObservation().legalActions, "$action is not a legal opponent step")
            val result = session.performOpponent(action)
            assertTrue(result.accepted, "the scripted $action was refused: ${result.message}")
            opponentActions += action
            result.game?.let { assertTrue(it.success, it.message); results += it }
            track()
            settle()
        }
        fail<Unit>("the opponent turn did not finish")
    }

    /** Automatic progression, as the screen requests it after an operation or a finished playback. */
    fun settle() {
        repeat(MAX_STEPS) {
            val engine = requireNotNull(session.controller.engine)
            assertEquals(GameState.PLAYING, engine.gameState, "practice must never finish the game")
            assertNull(engine.checkWinCondition(), "practice must never produce a winner")
            val advanced = session.advanceAutomatic() ?: return
            results += advanced
            track()
        }
        fail<Unit>("automatic progression did not stop")
    }

    /** Acknowledges explanations and plays opponent turns until the learner may operate. */
    fun reachLearnerAction() {
        repeat(MAX_STEPS) {
            when (session.status) {
                TutorialStatus.LEARNER_ACTION -> return
                TutorialStatus.AWAITING_ACKNOWLEDGEMENT -> acknowledge()
                TutorialStatus.OPPONENT_TURN -> opponentTurn()
                else -> fail<Unit>("the learner cannot act: ${session.status}")
            }
        }
        fail<Unit>("the learner never got to act")
    }

    fun play(actions: List<TutorialAction>) = actions.forEach { reachLearnerAction(); perform(it) }

    /** Plays [chapters] and everything after them until the lesson is complete. */
    fun finish(chapters: List<List<TutorialAction>>) {
        play(chapters.flatten())
        repeat(MAX_STEPS) {
            when (session.status) {
                TutorialStatus.COMPLETED -> return
                TutorialStatus.AWAITING_ACKNOWLEDGEMENT -> acknowledge()
                TutorialStatus.OPPONENT_TURN -> opponentTurn()
                else -> fail<Unit>("the lesson did not complete: ${session.status}")
            }
        }
        fail<Unit>("the lesson did not complete")
    }

    /**
     * A face-down card in a guided cell must not be named until a capture attempt reveals it.
     * This is decided per card: another card of the same type being public does not reveal this one.
     */
    fun assertNoHiddenFoodNamed() = assertGuidanceKeepsHiddenFood(session)

    private fun track() {
        shownGuidance += session.guidance
        val chapter = session.chapterIndex
        if (chapter !in chapterStarts) chapterStarts[chapter] = snapshot()
    }
}

internal fun TutorialGuidance.shownText(): String = listOfNotNull(text, notice).joinToString("\n")

/** Face-down cards in the guided cells, except the one a running capture attempt has turned over. */
internal fun hiddenGuidedCards(guidance: TutorialGuidance, game: GameSnapshot): List<FoodCard> =
    guidance.targetCells.flatMap { position ->
        game.engine.foods[position].orEmpty().filterIndexed { index, card ->
            val revealed = game.pendingCaptureRoll?.let { it.position == position && it.foodIndex == index } == true
            card.isFaceDown && !revealed
        }
    }

internal fun assertGuidanceKeepsHiddenFood(session: TutorialSession) {
    val guidance = session.guidance
    val shown = guidance.shownText()
    hiddenGuidedCards(guidance, session.controller.exportSnapshot()).forEach { card ->
        card.type.hiddenNames().forEach { name ->
            assertFalse(name in shown, "guidance names a hidden ${card.type}: $shown")
        }
    }
}

/** The tutorial refuses [action] before the game sees it, and only its notice changes. */
internal fun assertRefusedByTutorial(session: TutorialSession, action: TutorialAction) {
    val before = session.controller.exportSnapshot()
    val status = session.status
    val chapter = session.chapterIndex
    val generation = session.generation
    val stage = session.guidance.stage
    val result = session.perform(action)
    assertFalse(result.accepted, "$action should be refused")
    assertTrue(result.tutorialRejected, "$action should be refused by the tutorial, not the game")
    assertNull(result.game, "the game must not see a refused $action")
    assertTrue(result.message.isNotBlank())
    assertEquals(result.message, session.guidance.notice, "a refusal explains itself in the guidance")
    assertEquals(before, session.controller.exportSnapshot(), "refusing $action changed the game")
    assertEquals(status, session.status)
    assertEquals(chapter, session.chapterIndex)
    assertEquals(generation, session.generation)
    assertEquals(stage, session.guidance.stage)
}
