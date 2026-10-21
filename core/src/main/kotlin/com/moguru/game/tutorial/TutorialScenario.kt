package com.moguru.game.tutorial

import com.moguru.game.cpu.CpuAction
import com.moguru.game.engine.GameEngine
import com.moguru.game.engine.GameState
import com.moguru.game.engine.PlayerConfig
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Player
import com.moguru.game.model.Position
import com.moguru.game.model.TileShape
import com.moguru.game.persistence.GameSnapshot
import com.moguru.game.presenter.GameActionResult
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.DiceRoller
import com.moguru.game.util.Shuffler

/**
 * A reproducible practice game: a fixed deal of the regulation tiles and food, an optional prelude
 * of legal moves that sets the scene, the learner's chapters, and the opponent's scripted moves.
 * Everything runs through the normal [MoguraGameController].
 */
class TutorialScenario internal constructor(
    val lesson: TutorialLessonId,
    /** Changes whenever saved progress for an older deal or chapter plan no longer fits. */
    val version: Int,
    val title: String,
    val summary: String,
    internal val learner: PlayerConfig,
    internal val opponent: PlayerConfig,
    tiles: List<TileShape>,
    foods: List<FoodType>,
    private val rolls: List<Int>,
    /** Legal moves for both players, played automatically before the first chapter. */
    private val prelude: List<TutorialAction>,
    internal val chapters: List<TutorialChapter>,
    internal val opponentScript: (TutorialContext) -> CpuAction?,
    /** The learner's operations for each chapter, used to build and check every chapter start. */
    val solution: List<List<TutorialAction>>,
    /** Shown when the lesson is complete. */
    val completionText: String,
    /** Number of learning stages. [TutorialGuidance.stage] runs from 1 to this. */
    val stageCount: Int,
) {
    init {
        require(chapters.isNotEmpty() && chapters.size == solution.size)
        require(learner.playerId != null && opponent.playerId != null)
    }

    val chapterCount: Int get() = chapters.size
    val learnerPlayerId: Int get() = requireNotNull(learner.playerId)
    val opponentPlayerId: Int get() = requireNotNull(opponent.playerId)
    val learnerNest: Position get() = learner.nestPosition

    internal val shuffler: Shuffler = ScenarioShuffler(tiles, foods)

    /** Chapter starts made by replaying [solution] from the deal, built once. */
    private val starts: List<TutorialStart> by lazy { replaySolution() }

    /** The practice game at the start of the first chapter, after any prelude. */
    fun newController(): MoguraGameController = deal().first

    fun chapterStartSnapshot(index: Int): GameSnapshot = start(index).game

    internal fun start(index: Int): TutorialStart = starts[index]

    internal fun dice(position: Int): ScenarioDice = ScenarioDice(rolls, position)

    internal fun deal(): Pair<MoguraGameController, ScenarioDice> {
        val dice = dice(0)
        val controller = MoguraGameController(dice, shuffler)
        check(controller.startNewGame(listOf(learner, opponent), startPlayerIndex = 0).success)
        prelude.forEach { action ->
            val result = controller.runTutorialAction(action)
            check(result == null || result.success) { "$lesson prelude refused $action: ${result?.message}" }
            controller.settle()
        }
        check(controller.currentPlayer?.id == learnerPlayerId) { "$lesson must start on the learner's turn" }
        return controller to dice
    }

    private fun replaySolution(): List<TutorialStart> {
        val session = TutorialSession(this, 0)
        solution.flatten().forEach { action ->
            session.reachLearnerAction()
            val result = session.perform(action)
            check(result.accepted) { "$lesson solution refused $action: ${result.message}" }
            session.settle()
        }
        session.reachCompletion()
        return (0 until chapterCount).map(session::startOf)
    }
}

/** A chapter start: the game and how many scripted dice rolls were already used. */
internal class TutorialStart(val game: GameSnapshot, val dicePosition: Int)

/** Read-only view of a running lesson, for the lesson's own rules. */
internal class TutorialContext(
    val controller: MoguraGameController,
    val chapter: Int,
    val learnerId: Int,
    val opponentId: Int,
    private val shownKeys: Set<String>,
    /** Learner operations accepted in this chapter, in order. */
    val accepted: List<TutorialAction>,
) {
    val engine: GameEngine get() = requireNotNull(controller.engine)
    val phase: TurnPhase get() = engine.currentPhase
    val learner: Player get() = engine.players.first { it.id == learnerId }
    val opponent: Player get() = engine.players.first { it.id == opponentId }
    val learnersTurn: Boolean get() = controller.currentPlayer?.id == learnerId

    fun shown(key: String): Boolean = key in shownKeys
}

/** One guided learner operation: what is shown, and which operations the lesson accepts. */
internal class TutorialStep(
    val stage: Int,
    val text: String,
    val targetCells: Set<Position> = emptySet(),
    val controls: Set<TutorialControl> = emptySet(),
    /** Null accepts the operation. Otherwise it is the short reason shown instead. */
    val check: (TutorialAction) -> String?,
)

/** Pages shown once per chapter attempt when [showsWhen] first holds. */
internal class TutorialExplanation(
    val key: String,
    val stage: Int,
    val pages: List<String>,
    val targetCells: Set<Position> = emptySet(),
    val showsWhen: (TutorialContext) -> Boolean,
)

/** One learner turn and the opponent turn after it. */
internal class TutorialChapter(
    val stage: Int,
    val explanations: List<TutorialExplanation> = emptyList(),
    /** Checked whenever nothing waits for confirmation. */
    val completesWhen: (TutorialContext) -> Boolean = { false },
    val step: (TutorialContext) -> TutorialStep?,
)

/**
 * Deals the complete tile set and food deck in the scenario's order. Any other list is a discard
 * pile being reused, and keeps the order in which it was discarded. A two-player discard pile can
 * never hold all 26 tiles or all 12 food cards, so the sizes tell the cases apart.
 */
internal class ScenarioShuffler(
    private val tiles: List<TileShape>,
    private val foods: List<FoodType>,
) : Shuffler {
    init {
        require(tiles.groupingBy { it }.eachCount() == HoleTile.createFullSet().groupingBy { it.shape }.eachCount())
        require(foods.groupingBy { it }.eachCount() == FoodCard.createDeck(includeFrog = false).groupingBy { it.type }.eachCount())
    }

    override fun <T> shuffle(list: List<T>): List<T> = when {
        list.size == tiles.size && list.all { it is HoleTile } -> list.arranged(tiles) { (it as HoleTile).shape }
        list.size == foods.size && list.all { it is FoodCard } -> list.arranged(foods) { (it as FoodCard).type }
        else -> list
    }

    private fun <T, K> List<T>.arranged(order: List<K>, key: (T) -> K): List<T> {
        val remaining = toMutableList()
        return order.map { wanted -> remaining.removeAt(remaining.indexOfFirst { key(it) == wanted }) }
    }
}

/** Scripted dice. [position] counts the rolls used, so a chapter restart can continue from it. */
internal class ScenarioDice(private val rolls: List<Int>, start: Int) : DiceRoller {
    var position: Int = start
        private set

    override fun roll(): Int {
        val value = rolls.getOrNull(position) ?: error("This practice has no scripted roll #${position + 1}")
        position++
        return value
    }
}

/** Runs [action] with the same controller operation the play screen uses. Null leaves the game as is. */
internal fun MoguraGameController.runTutorialAction(action: TutorialAction): GameActionResult? = when (action) {
    is TutorialAction.SelectDigCell -> revealDigTile(action.position)
    is TutorialAction.SelectDigTile -> selectPendingDigTile(action.choice)
    is TutorialAction.RotateDigTile -> setPendingDigRotation(action.rotation)
    TutorialAction.ConfirmDig -> confirmPendingDig()
    is TutorialAction.SelectMoveTarget -> if (action.position in moveTargets()) {
        null
    } else {
        GameActionResult(false, "その移動先は選べません。")
    }
    is TutorialAction.Move -> moveTo(action.position)
    is TutorialAction.SelectCaptureTarget -> selectCaptureTarget(action.index)
    TutorialAction.Capture -> captureCurrentPosition()
    TutorialAction.RollDice -> rollCaptureDice()
    TutorialAction.ResolveCapture -> resolveCaptureRoll()
    TutorialAction.Eat -> eatPendingFood()
    TutorialAction.Carry -> carryPendingFood()
    is TutorialAction.SelectRobberyTarget -> selectRobberyTarget(action.index)
    TutorialAction.Rob -> robSelectedFood()
    TutorialAction.Skip -> skipPhase()
    TutorialAction.FinishTurn -> finishTurn()
}

private const val SETTLE_LIMIT = 50

/** The automatic progression the screen applies after every operation. */
internal fun MoguraGameController.settle() {
    repeat(SETTLE_LIMIT) {
        if (engine?.gameState != GameState.PLAYING || autoAdvanceWhileNoChoice() == null) return
    }
    error("Automatic progression did not stop")
}
