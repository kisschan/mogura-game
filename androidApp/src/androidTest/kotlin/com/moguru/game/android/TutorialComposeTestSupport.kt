package com.moguru.game.android

import com.moguru.game.tutorial.TutorialAction
import com.moguru.game.tutorial.TutorialLessonId
import com.moguru.game.tutorial.TutorialProgress
import com.moguru.game.tutorial.TutorialScenarios

/** Counts match save access; practice must leave it alone. */
internal class CountingGameSaveRepository : GameSaveRepository {
    var loads = 0
    var saves = 0
    override fun load(): SavedGame? {
        loads++
        return null
    }
    override fun save(game: SavedGame) {
        saves++
    }
}

/**
 * Moves a practice lesson on its ViewModel the way the play screen would: it reports shown
 * playback as finished, confirms pages, lets the opponent's timer run and sends the lesson's
 * solution as learner input. Compose tests use it to reach a state before or between frames.
 */
internal class PracticeDriver(lesson: TutorialLessonId, startChapter: Int? = null) {
    private val scenario = TutorialScenarios.of(lesson)
    val store = CountingGameSaveRepository()
    val scheduler = ManualCpuStepScheduler()
    val vm = AndroidGameViewModel(
        saveRepository = store,
        cpuScheduler = scheduler,
        tutorialProgressRepository = MemoryTutorialProgressRepository(
            startChapter?.let { TutorialProgress().started(scenario, it) } ?: TutorialProgress(),
        ),
    )
    val state: AndroidGameUiState get() = vm.uiState.value
    private val queued = ArrayDeque<TutorialAction>()

    init {
        vm.setCpuHostResumed(true)
        vm.setCpuPlayScreenClear(true)
        vm.startTutorial(lesson, fromBeginning = startChapter == null)
        queueFrom(startChapter ?: 0)
    }

    fun queueFrom(chapter: Int) {
        queued.clear()
        queued.addAll(scenario.solution.drop(chapter).flatten())
    }

    /** One thing the screen does next. False when practice waits for input that is not queued. */
    fun step(): Boolean {
        val now = state
        check(now.cpuFault == null) { "the opponent stopped: ${now.tutorial?.text}" }
        val generation = now.playbackGeneration
        now.captureAnimation?.let { vm.finishCaptureAnimation(it.event.id, generation); return true }
        now.eatAnimation?.let { vm.finishEatAnimation(it.id, generation); return true }
        now.turnConsumptionAnimation?.let { vm.finishTurnConsumptionAnimation(it.id, generation); return true }
        val practice = now.tutorial ?: return false
        if (practice.awaitingAcknowledgement) {
            vm.acknowledgeTutorial(generation, practice.explanationSerial)
            return true
        }
        if (practice.opponentTurn) {
            check(scheduler.pendingCount > 0) { "the opponent has no step reserved" }
            scheduler.advanceBy(OPPONENT_TURN_MILLIS)
            return true
        }
        when (val action = queued.removeFirstOrNull() ?: return false) {
            is TutorialAction.SelectDigCell -> vm.onCellClicked(action.position)
            is TutorialAction.SelectDigTile -> vm.selectDigChoice(action.choice)
            is TutorialAction.RotateDigTile -> vm.selectRotation(action.rotation)
            TutorialAction.ConfirmDig -> vm.confirmDigPlacement()
            is TutorialAction.SelectMoveTarget -> vm.selectMoveTarget(action.position)
            is TutorialAction.Move -> vm.onCellClicked(action.position)
            is TutorialAction.SelectCaptureTarget -> vm.selectCaptureTarget(action.index)
            TutorialAction.Capture -> vm.capture()
            TutorialAction.RollDice -> vm.stopDiceRoulette()
            TutorialAction.ResolveCapture -> vm.finishDiceRoulette(generation, now.diceRouletteEventId)
            TutorialAction.Eat -> vm.eat()
            TutorialAction.Carry -> vm.carry()
            is TutorialAction.SelectRobberyTarget -> vm.selectRobberyTarget(action.index)
            TutorialAction.Rob -> vm.rob()
            TutorialAction.Skip -> vm.skip()
            TutorialAction.FinishTurn -> vm.finishTurn()
        }
        return true
    }

    fun runUntil(condition: (AndroidGameUiState) -> Boolean) {
        repeat(STEP_LIMIT) {
            if (condition(state)) return
            check(step()) { "practice stopped before the expected state: ${state.tutorial?.text}" }
        }
        error("practice did not reach the expected state")
    }

    private companion object {
        const val STEP_LIMIT = 1_000
        const val OPPONENT_TURN_MILLIS = 10_000L
    }
}
