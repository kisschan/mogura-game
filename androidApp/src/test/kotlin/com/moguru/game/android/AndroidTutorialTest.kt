package com.moguru.game.android

import com.moguru.game.cpu.CpuDifficulty
import com.moguru.game.engine.GameState
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.presenter.CaptureOutcomeKind
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.tutorial.TutorialAction
import com.moguru.game.tutorial.TutorialLessonId
import com.moguru.game.tutorial.TutorialProgress
import com.moguru.game.tutorial.TutorialScenarios
import com.moguru.game.util.DiceRoller
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import com.moguru.game.util.Shuffler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Practice driven through the same ViewModel inputs, opponent timer and playback callbacks as the play screen. */
class AndroidTutorialTest {
    private class MatchStore(var saved: SavedGame? = null, private val loadFailure: Exception? = null) : GameSaveRepository {
        var loads = 0
        var saves = 0
        override fun load(): SavedGame? {
            loads++
            loadFailure?.let { throw it }
            return saved
        }
        override fun save(game: SavedGame) {
            saves++
            saved = game
        }
    }

    private class ProgressStore(var value: TutorialProgress = TutorialProgress()) : TutorialProgressRepository {
        var loadFailure: Exception? = null
        var failSaves = false
        override fun load(): TutorialProgress {
            loadFailure?.let { throw it }
            return value
        }
        override fun save(progress: TutorialProgress) {
            if (failSaves) throw java.io.IOException("test")
            value = progress
        }
    }

    /** Runs reserved steps on demand. A test may keep a reservation and run it after it was cancelled. */
    private class Timers : CpuStepScheduler {
        val pending = mutableListOf<() -> Unit>()
        override fun schedule(delayMillis: Long, task: () -> Unit): CpuStepCancellation {
            pending += task
            return CpuStepCancellation { pending.remove(task) }
        }
        fun runNext() = pending.removeAt(0).invoke()
    }

    /** The explanation page as the screen captured it for its 「次へ」 button. */
    private data class ShownPage(val generation: Long, val serial: Long)

    private enum class Playback {
        CAPTURE, MEAL, TURN_CONSUMPTION;

        fun shownId(state: AndroidGameUiState): Long? = when (this) {
            CAPTURE -> state.captureAnimation?.event?.id
            MEAL -> state.eatAnimation?.id
            TURN_CONSUMPTION -> state.turnConsumptionAnimation?.id
        }

        fun finish(vm: AndroidGameViewModel, id: Long, generation: Long) = when (this) {
            CAPTURE -> vm.finishCaptureAnimation(id, generation)
            MEAL -> vm.finishEatAnimation(id, generation)
            TURN_CONSUMPTION -> vm.finishTurnConsumptionAnimation(id, generation)
        }
    }

    /** A sound the play screen asks for while it shows a playback, and the lesson whose first chapter plays it. */
    private enum class Sound(val lesson: TutorialLessonId, val effect: AndroidSoundEffect) {
        ESCAPE(TutorialLessonId.DICE_ESCAPE, AndroidSoundEffect.CAPTURE_FAILURE),
        MEAL(TutorialLessonId.NEST_EAT, AndroidSoundEffect.EAT_RECOVERY);

        fun shownId(state: AndroidGameUiState): Long? = when (this) {
            ESCAPE -> state.captureAnimation?.event?.takeIf { it.kind == CaptureOutcomeKind.ESCAPED }?.id
            MEAL -> state.eatAnimation?.id
        }

        /** As the screen asks: for the playback in [state], with the generation [state] was shown in. */
        fun ask(vm: AndroidGameViewModel, state: AndroidGameUiState): AndroidSoundEffect? = when (this) {
            ESCAPE -> vm.captureFailureSoundEffectFor(state.captureAnimation?.event, state.playbackGeneration)
            MEAL -> vm.eatRecoverySoundEffectFor(state.eatAnimation, state.playbackGeneration)
        }
    }

    private class Harness(
        val store: MatchStore = MatchStore(),
        val progress: ProgressStore = ProgressStore(),
        match: MoguraGameController = matchController(),
    ) {
        val timers = Timers()
        val vm = AndroidGameViewModel(match, store, cpuScheduler = timers, tutorialProgressRepository = progress).apply {
            setCpuHostResumed(true)
            setCpuPlayScreenClear(true)
        }
        val state: AndroidGameUiState get() = vm.uiState.value
        private val queued = ArrayDeque<TutorialAction>()

        fun queue(actions: List<TutorialAction>) {
            queued.clear()
            queued.addAll(actions)
        }

        fun shownPage(): ShownPage {
            val practice = requireNotNull(state.tutorial)
            assertTrue(practice.awaitingAcknowledgement, "no explanation page is shown")
            return ShownPage(state.playbackGeneration, practice.explanationSerial)
        }

        /**
         * One thing the screen does next: report the shown playback as finished, confirm the page it
         * shows, let the opponent's timer fire, or send the learner's next queued input.
         * False when practice waits for input that is not queued.
         */
        fun step(learnerInput: Boolean = true): Boolean {
            val now = state
            assertNull(now.cpuFault, "the opponent stopped: ${now.tutorial?.text}")
            assertNull(now.gameResult, "practice used the match result")
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
                assertTrue(timers.pending.isNotEmpty(), "the opponent has no step reserved")
                timers.runNext()
                return true
            }
            if (!learnerInput) return false
            perform(queued.removeFirstOrNull() ?: return false)
            return true
        }

        fun runUntil(condition: (AndroidGameUiState) -> Boolean) {
            repeat(STEP_LIMIT) {
                if (condition(state)) return
                assertTrue(step(), "practice stopped before the expected state: ${state.tutorial?.text}")
            }
            fail<Unit>("practice did not reach the expected state")
        }

        /** Sends [actions] and lets everything after them run, until practice waits for the learner. */
        fun play(actions: List<TutorialAction>) {
            queue(actions)
            repeat(STEP_LIMIT) { if (!step()) return }
            fail<Unit>("practice did not settle")
        }

        fun settle() {
            repeat(STEP_LIMIT) { if (!step(learnerInput = false)) return }
            fail<Unit>("practice did not settle")
        }

        private fun perform(action: TutorialAction) {
            when (action) {
                is TutorialAction.SelectDigCell -> vm.onCellClicked(action.position)
                is TutorialAction.SelectDigTile -> vm.selectDigChoice(action.choice)
                is TutorialAction.RotateDigTile -> vm.selectRotation(action.rotation)
                TutorialAction.ConfirmDig -> vm.confirmDigPlacement()
                is TutorialAction.SelectMoveTarget -> vm.selectMoveTarget(action.position)
                // 「このマスへ移動」 confirms through the same entry as a board tap.
                is TutorialAction.Move -> vm.onCellClicked(action.position)
                is TutorialAction.SelectCaptureTarget -> vm.selectCaptureTarget(action.index)
                TutorialAction.Capture -> vm.capture()
                TutorialAction.RollDice -> vm.stopDiceRoulette()
                TutorialAction.ResolveCapture -> vm.finishDiceRoulette(state.playbackGeneration, state.diceRouletteEventId)
                TutorialAction.Eat -> vm.eat()
                TutorialAction.Carry -> vm.carry()
                is TutorialAction.SelectRobberyTarget -> vm.selectRobberyTarget(action.index)
                TutorialAction.Rob -> vm.rob()
                TutorialAction.Skip -> vm.skip()
                TutorialAction.FinishTurn -> vm.finishTurn()
            }
        }
    }

    private fun solution(lesson: TutorialLessonId) = TutorialScenarios.of(lesson).solution

    private fun playbackShown(state: AndroidGameUiState) =
        state.captureAnimation != null || state.eatAnimation != null || state.turnConsumptionAnimation != null

    // ---- 「次へ」 belongs to the page, attempt and lesson it was shown for ----

    @Test
    fun `next confirms the page on screen once and a repeated tap is ignored`() {
        val h = Harness()
        h.vm.startTutorial(TutorialLessonId.SURFACE_HEALTH, fromBeginning = true)
        val first = h.shownPage()
        val firstPage = requireNotNull(h.state.tutorial)
        assertEquals(1, firstPage.page)

        h.vm.acknowledgeTutorial(first.generation, first.serial)
        val second = requireNotNull(h.state.tutorial)
        assertEquals(2, second.page)
        assertNotEquals(firstPage.text, second.text)
        assertEquals(first.generation, h.state.playbackGeneration, "confirming a page is not a new attempt")

        h.vm.acknowledgeTutorial(first.generation, first.serial)
        assertEquals(second, h.state.tutorial)
    }

    @Test
    fun `next captured in the previous lesson cannot advance the lesson that replaced it`() {
        val h = Harness()
        h.vm.startTutorial(TutorialLessonId.BASIC, fromBeginning = true)
        val old = h.shownPage()
        h.vm.startTutorial(TutorialLessonId.SURFACE_HEALTH, fromBeginning = true)
        val replacement = requireNotNull(h.state.tutorial)
        // The new session numbers its pages from the start again; only the generation tells them apart.
        assertEquals(old.serial, replacement.explanationSerial)
        assertNotEquals(old.generation, h.state.playbackGeneration)

        h.vm.acknowledgeTutorial(old.generation, old.serial)
        assertEquals(replacement, h.state.tutorial)

        val current = h.shownPage()
        h.vm.acknowledgeTutorial(current.generation, current.serial)
        assertEquals(2, h.state.tutorial?.page)
    }

    @Test
    fun `next captured before a chapter restart cannot advance the restarted chapter`() {
        val h = Harness()
        h.vm.startTutorial(TutorialLessonId.SURFACE_HEALTH, fromBeginning = true)
        val old = h.shownPage()
        h.vm.restartTutorialChapter()
        val restarted = requireNotNull(h.state.tutorial)
        assertEquals(1, restarted.page, "the chapter shows its explanation again")

        h.vm.acknowledgeTutorial(old.generation, old.serial)
        assertEquals(restarted, h.state.tutorial)
        // Even the page number now on screen is refused with the earlier attempt's generation.
        h.vm.acknowledgeTutorial(old.generation, restarted.explanationSerial)
        assertEquals(restarted, h.state.tutorial)

        val current = h.shownPage()
        h.vm.acknowledgeTutorial(current.generation, current.serial)
        assertEquals(2, h.state.tutorial?.page)
    }

    @Test
    fun `next captured before leaving practice cannot advance a lesson opened afterwards`() {
        val h = Harness()
        h.vm.startTutorial(TutorialLessonId.BASIC, fromBeginning = true)
        val old = h.shownPage()
        h.vm.exitTutorial()
        val left = h.state
        h.vm.acknowledgeTutorial(old.generation, old.serial)
        assertEquals(left, h.state)

        h.vm.startTutorial(TutorialLessonId.BASIC, fromBeginning = true)
        val reopened = requireNotNull(h.state.tutorial)
        assertEquals(old.serial, reopened.explanationSerial)
        h.vm.acknowledgeTutorial(old.generation, old.serial)
        assertEquals(reopened, h.state.tutorial)

        val current = h.shownPage()
        h.vm.acknowledgeTutorial(current.generation, current.serial)
        assertNotEquals(reopened, h.state.tutorial)
    }

    // ---- Every lesson through the play screen's entry points ----

    @Test
    fun `every lesson completes through the screen inputs, the opponent timer and playback callbacks`() {
        for (lesson in TutorialLessonId.entries) {
            val h = Harness()
            val scenario = TutorialScenarios.of(lesson)
            val chapters = mutableSetOf<Int>()
            h.vm.startTutorial(lesson, fromBeginning = true)
            h.queue(scenario.solution.flatten())
            h.runUntil { now ->
                now.tutorial?.let { chapters += it.chapter }
                if (playbackShown(now) || now.tutorial?.awaitingAcknowledgement == true) {
                    assertTrue(h.timers.pending.isEmpty(), "$lesson: the opponent waits for pages and playback")
                }
                if (now.tutorialCompletion != null) assertFalse(playbackShown(now), "$lesson: completion covers playback")
                now.tutorialCompletion != null
            }
            val done = h.state
            assertEquals(true, done.tutorial?.completed)
            assertEquals((1..scenario.chapterCount).toSet(), chapters, "$lesson skipped a chapter")
            assertNull(done.gameResult, "$lesson used the match result")
            assertFalse(done.showGameResultOverlay)
            assertTrue(h.timers.pending.isEmpty(), "$lesson kept the opponent running after completion")
            assertTrue(h.progress.value.isCompleted(lesson))
            assertNull(h.progress.value.current)

            // Completion holds the game: a late input leaves the board as it is.
            h.vm.finishTurn()
            assertEquals(done.boardState, h.state.boardState)
            assertEquals(done.hungerMarkers, h.state.hungerMarkers)
            assertTrue(h.timers.pending.isEmpty())

            h.vm.exitTutorial(TutorialExit.LESSONS)
            assertNull(h.state.tutorial)
            assertFalse(h.state.isGameStarted)
            assertEquals(true, h.state.tutorialMenu?.lessons?.single { it.lesson == lesson }?.completed)
            assertEquals(1, h.store.loads, "$lesson read the match save again")
            assertEquals(0, h.store.saves, "$lesson wrote the match save")
        }
    }

    // ---- The match save (none, ongoing, finished, damaged) is neither read again nor written ----

    /** Practice as a learner might: restart, switch lessons, finish one, try again and leave. */
    private fun practiseAndLeave(h: Harness) {
        h.vm.startTutorial(TutorialLessonId.BASIC, fromBeginning = true)
        h.vm.setBoardPiecesTransparent(true)
        assertTrue(h.state.boardPiecesTransparent)
        h.queue(solution(TutorialLessonId.BASIC).first())
        // The learner practises alone: the first turn ends with the health playback.
        h.runUntil { it.turnConsumptionAnimation != null }
        h.vm.reloadSavedGame()
        h.vm.restartTutorialChapter()
        h.vm.startTutorial(TutorialLessonId.DICE_ESCAPE, fromBeginning = true)
        h.play(solution(TutorialLessonId.DICE_ESCAPE).flatten())
        assertNotNull(h.state.tutorialCompletion)
        h.vm.startTutorial(TutorialLessonId.DICE_ESCAPE, fromBeginning = true)
        h.vm.exitTutorial()
    }

    private fun assertMatchSaveUntouched(h: Harness, before: GamePersistenceUiState, saved: SavedGame?) {
        assertEquals(1, h.store.loads, "practice read the match save again")
        assertEquals(0, h.store.saves, "practice wrote the match save")
        assertSame(saved, h.store.saved)
        assertEquals(before, h.state.persistence)
        assertFalse(h.state.isGameStarted)
        assertNull(h.state.tutorial)
        assertFalse(h.state.boardPiecesTransparent, "practice transparency leaked out")
    }

    private fun assertResumedLike(expected: AndroidGameUiState, actual: AndroidGameUiState) {
        assertTrue(actual.isGameStarted)
        assertNull(actual.tutorial)
        assertEquals(expected.playState, actual.playState)
        assertEquals(expected.boardState, actual.boardState)
        assertEquals(expected.hungerMarkers, actual.hungerMarkers)
        assertEquals(expected.logs, actual.logs)
        assertEquals(expected.lastMessage, actual.lastMessage)
        assertEquals(expected.boardPiecesTransparent, actual.boardPiecesTransparent)
        assertEquals(expected.gameResult, actual.gameResult)
        assertEquals(expected.showGameResultOverlay, actual.showGameResultOverlay)
    }

    private fun resumedReference(saved: SavedGame): AndroidGameUiState =
        AndroidGameViewModel(matchController(), MatchStore(saved)).apply { resumeSavedGame() }.uiState.value

    @Test
    fun `without a match save practice leaves none behind and the first match starts as usual`() {
        val h = Harness()
        val before = h.state.persistence
        assertFalse(before.showEntry)
        practiseAndLeave(h)
        assertMatchSaveUntouched(h, before, null)

        h.vm.startSelectedGame()
        assertTrue(h.state.isGameStarted)
        assertNull(h.state.tutorial)
        assertEquals(1, h.store.saves, "the first match starts without asking to overwrite anything")
    }

    @Test
    fun `an ongoing match save is kept through practice and resumes exactly as saved`() {
        val saved = ongoingSave()
        val h = Harness(MatchStore(saved))
        val before = h.state.persistence
        assertTrue(before.showEntry && before.hasSavedGame && !before.finished)
        practiseAndLeave(h)
        assertMatchSaveUntouched(h, before, saved)

        h.vm.resumeSavedGame()
        assertResumedLike(resumedReference(saved), h.state)
        assertTrue(h.state.boardPiecesTransparent, "the match keeps its own transparency")
    }

    @Test
    fun `a finished match save keeps its result through practice`() {
        val saved = finishedSave()
        val h = Harness(MatchStore(saved))
        val before = h.state.persistence
        assertTrue(before.showEntry && before.finished)
        practiseAndLeave(h)
        assertMatchSaveUntouched(h, before, saved)

        h.vm.resumeSavedGame()
        assertNotNull(h.state.gameResult)
        assertTrue(h.state.showGameResultOverlay)
        assertResumedLike(resumedReference(saved), h.state)
    }

    @Test
    fun `a damaged match save stays reported and protected through practice`() {
        val h = Harness(MatchStore(loadFailure = IllegalArgumentException("damaged")))
        val before = h.state.persistence
        assertEquals(GameSaveLoadIssue.CORRUPT, before.loadIssue)
        assertTrue(before.showEntry)
        practiseAndLeave(h)
        assertMatchSaveUntouched(h, before, null)

        h.vm.resumeSavedGame()
        assertFalse(h.state.isGameStarted)
        h.vm.chooseNewGame()
        h.vm.startSelectedGame()
        assertTrue(h.state.persistence.confirmOverwrite, "replacing the damaged save still needs confirmation")
        assertEquals(0, h.store.saves)
    }

    @Test
    fun `the match setup and its randomness come back unused after practice`() {
        fun seeded(): MoguraGameController {
            val random = Random(77)
            return MoguraGameController(
                object : DiceRoller { override fun roll() = random.nextInt(1, 7) },
                object : Shuffler { override fun <T> shuffle(list: List<T>) = list.shuffled(random) },
            )
        }
        fun configure(vm: AndroidGameViewModel) {
            vm.selectPlayerCount(3)
            vm.selectPlayerMole(0, MoguraGameController.moleOptions.last().playerId)
            vm.selectPlayerNest(0, MoguraGameController.nestPositions.last())
            vm.selectPlayerKind(2, true)
            vm.selectCpuDifficulty(2, CpuDifficulty.HARD)
            vm.selectStartPlayer(1)
        }
        fun setupOf(state: AndroidGameUiState) =
            Triple(state.selectedPlayerCount, state.setupPlayers, state.selectedStartPlayerIndex)

        val h = Harness(match = seeded())
        configure(h.vm)
        val setup = setupOf(h.state)
        h.vm.startTutorial(TutorialLessonId.DICE_ESCAPE, fromBeginning = true)
        h.play(solution(TutorialLessonId.DICE_ESCAPE).flatten())
        assertNotNull(h.state.tutorialCompletion)
        h.vm.exitTutorial(TutorialExit.SETUP)
        assertEquals(setup, setupOf(h.state))
        assertFalse(h.state.persistence.showEntry)

        h.vm.startSelectedGame()
        val reference = Harness(match = seeded()).also {
            configure(it.vm)
            it.vm.startSelectedGame()
        }
        val practised = requireNotNull(h.store.saved)
        val untouched = requireNotNull(reference.store.saved)
        assertEquals(untouched.game, practised.game, "practice used the match's dice or shuffle")
        assertEquals(untouched.cpuPlayerIds, practised.cpuPlayerIds)
        assertEquals(untouched.cpuDifficulties, practised.cpuDifficulties)
        assertEquals(CpuDifficulty.HARD, practised.cpuDifficulties.values.single())
        assertEquals(3, practised.game.engine.players.size)
    }

    // ---- Learning progress ----

    @Test
    fun `unreadable progress is listed as no record and replaced by the next progress save`() {
        val progress = ProgressStore().apply { loadFailure = IllegalArgumentException("damaged") }
        val h = Harness(MatchStore(ongoingSave()), progress)
        // Read in the same task, the match save is not affected.
        assertTrue(h.state.persistence.hasSavedGame)
        assertNull(h.state.persistence.loadIssue)
        h.vm.openTutorialMenu()
        val menu = requireNotNull(h.state.tutorialMenu)
        assertTrue(menu.progressLoadFailed)
        assertTrue(menu.lessons.none { it.completed || it.resumeChapter != null })

        h.vm.startTutorial(TutorialLessonId.BASIC, fromBeginning = true)
        h.vm.exitTutorial(TutorialExit.LESSONS)
        val basic = TutorialScenarios.of(TutorialLessonId.BASIC)
        assertEquals(TutorialProgress().started(basic, 0).left(), progress.value)
        val after = requireNotNull(h.state.tutorialMenu)
        assertFalse(after.progressLoadFailed)
        assertEquals(0, after.lessons.single { it.lesson == TutorialLessonId.BASIC }.resumeChapter)
        assertEquals(0, h.store.saves)
    }

    @Test
    fun `practice goes on when progress cannot be saved and a retry writes the newest record`() {
        val progress = ProgressStore().apply { failSaves = true }
        val h = Harness(progress = progress)
        val surface = TutorialScenarios.of(TutorialLessonId.SURFACE_HEALTH)
        h.vm.startTutorial(surface.lesson, fromBeginning = true)
        h.play(surface.solution.flatten())
        val completion = requireNotNull(h.state.tutorialCompletion) { "practice stopped when its record failed" }
        assertTrue(completion.progressSaveFailed)
        assertEquals(TutorialProgress(), progress.value)

        progress.failSaves = false
        h.vm.retryTutorialProgressSave()
        assertFalse(requireNotNull(h.state.tutorialCompletion).progressSaveFailed)
        assertEquals(TutorialProgress().completed(surface), progress.value)

        // Failing again: the lesson list explains it, apart from the match save's own state.
        progress.failSaves = true
        h.vm.startTutorial(TutorialLessonId.BASIC, fromBeginning = true)
        h.vm.exitTutorial(TutorialExit.LESSONS)
        assertTrue(requireNotNull(h.state.tutorialMenu).progressSaveFailed)
        assertFalse(h.state.persistence.saveFailed)
        progress.failSaves = false
        h.vm.retryTutorialProgressSave()
        assertFalse(requireNotNull(h.state.tutorialMenu).progressSaveFailed)
        val basic = TutorialScenarios.of(TutorialLessonId.BASIC)
        assertEquals(TutorialProgress().completed(surface).started(basic, 0).left(), progress.value)
        assertEquals(0, h.store.saves)
    }

    @Test
    fun `after the process ends the lesson resumes at its chapter start without replaying the rest`() {
        val store = MatchStore()
        val progress = ProgressStore()
        val lesson = TutorialLessonId.BASIC
        val solution = solution(lesson)
        val first = Harness(store, progress)
        first.vm.startTutorial(lesson, fromBeginning = true)
        first.play(solution[0] + solution[1])
        val chapterStart = first.state
        assertEquals(3, chapterStart.tutorial?.chapter)
        first.play(solution[2].take(2))
        assertNotEquals(chapterStart.boardState, first.state.boardState, "the chapter is left half done")

        // The process ends here, without leaving practice or finishing anything on screen.
        val second = Harness(store, progress)
        val offer = requireNotNull(second.state.tutorialResume)
        assertEquals(lesson, offer.lesson)
        assertEquals(2, offer.resumeChapter)
        second.vm.resumeInterruptedTutorial()
        second.settle()
        val resumed = second.state
        assertEquals(3, resumed.tutorial?.chapter)
        assertEquals(chapterStart.tutorial?.text, resumed.tutorial?.text)
        assertEquals(chapterStart.boardState, resumed.boardState)
        assertEquals(chapterStart.hungerMarkers, resumed.hungerMarkers)
        assertFalse(playbackShown(resumed))

        second.play(solution.drop(2).flatten())
        assertNotNull(second.state.tutorialCompletion)
        assertEquals(0, store.saves)
    }

    // ---- Leftover opponent timers and playback callbacks ----

    @Test
    fun `opponent timers left from before a restart or exit change nothing`() {
        val h = Harness()
        // Only the robbery lessons keep a scripted opponent.
        val lesson = TutorialLessonId.ROBBERY_DEFENSE
        val robbery = solution(lesson)
        val opponentReserved = { state: AndroidGameUiState -> state.tutorial?.opponentTurn == true && h.timers.pending.isNotEmpty() }
        h.vm.startTutorial(lesson, fromBeginning = true)
        h.queue(robbery[0])
        h.runUntil(opponentReserved)
        val beforeRestart = h.timers.pending.toList()

        h.vm.restartTutorialChapter()
        assertTrue(h.timers.pending.isEmpty(), "the restart cancels the reserved step")
        val restarted = h.state
        beforeRestart.forEach { it() }
        assertEquals(restarted, h.state)

        h.queue(robbery[0])
        h.runUntil(opponentReserved)
        val replayed = h.state
        val beforeExit = h.timers.pending.toList()
        beforeRestart.forEach { it() }
        assertEquals(replayed, h.state)
        assertEquals(beforeExit, h.timers.pending)

        h.vm.exitTutorial()
        assertTrue(h.timers.pending.isEmpty(), "leaving cancels the reserved step")
        val left = h.state
        (beforeRestart + beforeExit).forEach { it() }
        assertEquals(left, h.state)

        h.vm.startTutorial(lesson, fromBeginning = true)
        h.queue(robbery[0])
        h.runUntil(opponentReserved)
        val reopened = h.state
        (beforeRestart + beforeExit).forEach { it() }
        assertEquals(reopened, h.state)
        h.play(robbery.drop(1).flatten())
        assertNotNull(h.state.tutorialCompletion, "the opponent's live steps still play the lesson through")
        assertEquals(0, h.store.saves)
    }

    @Test
    fun `playback callbacks from before a restart cannot finish the replayed playback that reuses their IDs`() {
        val basic = TutorialScenarios.of(TutorialLessonId.BASIC)
        val h = Harness(progress = ProgressStore(TutorialProgress().started(basic, 2)))
        h.vm.startTutorial(TutorialLessonId.BASIC)
        assertEquals(3, h.state.tutorial?.chapter)
        val chapter = basic.solution[2]
        for (playback in Playback.entries) {
            h.queue(chapter)
            h.runUntil { playback.shownId(it) != null }
            val old = h.state
            val id = requireNotNull(playback.shownId(old))

            h.vm.restartTutorialChapter()
            val restarted = h.state
            assertNull(playback.shownId(restarted), "$playback")
            playback.finish(h.vm, id, old.playbackGeneration)
            assertEquals(restarted, h.state, "$playback finished after the restart")

            h.queue(chapter)
            h.runUntil { playback.shownId(it) != null }
            val replay = h.state
            assertEquals(id, playback.shownId(replay), "$playback replays with the same ID")
            playback.finish(h.vm, id, old.playbackGeneration)
            assertEquals(replay, h.state, "$playback of the earlier attempt finished the replay")
            playback.finish(h.vm, id, replay.playbackGeneration)
            assertNull(playback.shownId(h.state), "$playback of this attempt finishes")
            h.vm.restartTutorialChapter()
        }
    }

    @Test
    fun `playback callbacks from before leaving cannot finish the same playback in a reopened lesson`() {
        val h = Harness()
        val chapter = solution(TutorialLessonId.BASIC)[0]
        val playback = Playback.TURN_CONSUMPTION
        h.vm.startTutorial(TutorialLessonId.BASIC, fromBeginning = true)
        h.queue(chapter)
        h.runUntil { playback.shownId(it) != null }
        val old = h.state
        val id = requireNotNull(playback.shownId(old))

        h.vm.exitTutorial()
        val left = h.state
        playback.finish(h.vm, id, old.playbackGeneration)
        assertEquals(left, h.state)

        h.vm.startTutorial(TutorialLessonId.BASIC, fromBeginning = true)
        h.queue(chapter)
        h.runUntil { playback.shownId(it) != null }
        val reopened = h.state
        assertEquals(id, playback.shownId(reopened), "the new session numbers its playback from the start")
        playback.finish(h.vm, id, old.playbackGeneration)
        assertEquals(reopened, h.state)
        playback.finish(h.vm, id, reopened.playbackGeneration)
        assertNull(playback.shownId(h.state))
    }

    @Test
    fun `a dice landing from before a restart cannot resolve the replayed roll`() {
        val h = Harness()
        val untilLanding = solution(TutorialLessonId.DICE_ESCAPE)[0].takeWhile { it != TutorialAction.ResolveCapture }
        h.vm.startTutorial(TutorialLessonId.DICE_ESCAPE, fromBeginning = true)
        h.queue(untilLanding)
        h.runUntil { it.playState.diceRouletteResult != null }
        val old = h.state
        val eventId = requireNotNull(old.diceRouletteEventId)
        assertEquals(2, old.playState.diceRouletteResult, "the practice dice are fixed")

        h.vm.restartTutorialChapter()
        val restarted = h.state
        assertFalse(restarted.playState.diceRouletteActive)
        h.vm.finishDiceRoulette(old.playbackGeneration, eventId)
        assertEquals(restarted, h.state)

        h.queue(untilLanding)
        h.runUntil { it.playState.diceRouletteResult != null }
        val replay = h.state
        assertEquals(eventId, replay.diceRouletteEventId)
        h.vm.finishDiceRoulette(old.playbackGeneration, eventId)
        assertEquals(replay, h.state)
        h.vm.finishDiceRoulette(replay.playbackGeneration, eventId)
        assertNotNull(h.state.captureAnimation, "the landing of this attempt resolves the capture")
    }

    // ---- Escape and meal sounds belong to the attempt that played them ----

    /** Plays the first chapter of [sound]'s lesson from its start until the playback is on screen. */
    private fun playUntilSound(h: Harness, sound: Sound): AndroidGameUiState {
        h.queue(solution(sound.lesson).first())
        h.runUntil { sound.shownId(it) != null }
        return h.state
    }

    @Test
    fun `a restarted chapter plays its escape and meal sounds again once although it reuses their IDs`() {
        for (sound in Sound.entries) {
            val h = Harness()
            h.vm.startTutorial(sound.lesson, fromBeginning = true)
            val first = playUntilSound(h, sound)
            val id = requireNotNull(sound.shownId(first))
            assertEquals(sound.effect, sound.ask(h.vm, first), "$sound")
            // A recreated screen asks again for the playback it shows.
            assertNull(sound.ask(h.vm, first), "$sound played twice in one attempt")

            h.vm.restartTutorialChapter()
            val replay = playUntilSound(h, sound)
            assertEquals(id, sound.shownId(replay), "$sound replays with the same ID")
            assertNotEquals(first.playbackGeneration, replay.playbackGeneration)
            assertNull(sound.ask(h.vm, first), "$sound left from the earlier attempt played")
            assertEquals(sound.effect, sound.ask(h.vm, replay), "$sound of the restarted chapter was silenced")
            assertNull(sound.ask(h.vm, replay), "$sound played twice in the restarted chapter")
        }
    }

    @Test
    fun `sounds heard in one lesson do not silence the same IDs after switching lessons or reopening one`() {
        val h = Harness()
        val heard = Sound.entries.associateWith { sound ->
            h.vm.startTutorial(sound.lesson, fromBeginning = true)
            val shown = playUntilSound(h, sound)
            assertEquals(sound.effect, sound.ask(h.vm, shown), "$sound")
            requireNotNull(sound.shownId(shown))
        }
        // Each lesson opens again straight from the other one, whose playback is still on screen.
        for (sound in Sound.entries) {
            h.vm.startTutorial(sound.lesson, fromBeginning = true)
            val switched = playUntilSound(h, sound)
            assertEquals(heard[sound], sound.shownId(switched), "$sound replays with the same ID")
            assertEquals(sound.effect, sound.ask(h.vm, switched), "$sound was silenced after switching lessons")
            assertNull(sound.ask(h.vm, switched), "$sound played twice after switching lessons")
        }
        for (sound in Sound.entries) {
            h.vm.exitTutorial(TutorialExit.LESSONS)
            h.vm.startTutorial(sound.lesson, fromBeginning = true)
            val reopened = playUntilSound(h, sound)
            assertEquals(heard[sound], sound.shownId(reopened), "$sound replays with the same ID")
            assertEquals(sound.effect, sound.ask(h.vm, reopened), "$sound was silenced after reopening the lesson")
            assertNull(sound.ask(h.vm, reopened), "$sound played twice after reopening the lesson")
        }
        assertEquals(0, h.store.saves)
    }

    @Test
    fun `sounds heard in practice do not silence the same IDs in a match started afterwards`() {
        for (sound in Sound.entries) {
            // Every die shows 1: a centipede escapes to the upper left.
            val match = MoguraGameController(FixedDiceRoller(listOf(1)), FixedShuffler())
            val h = Harness(match = match)
            h.vm.startTutorial(sound.lesson, fromBeginning = true)
            val practised = playUntilSound(h, sound)
            assertEquals(sound.effect, sound.ask(h.vm, practised), "$sound")
            h.vm.exitTutorial(TutorialExit.SETUP)

            h.vm.startNewGame(2)
            val engine = requireNotNull(match.engine)
            val player = requireNotNull(match.currentPlayer)
            when (sound) {
                Sound.ESCAPE -> {
                    player.moveTo(Position(1, 1))
                    engine.placeFoodAt(player.position, FoodCard.createDummyCards(FoodType.CENTIPEDE).first())
                    engine.advancePhase()
                    engine.advancePhase()
                    h.vm.capture()
                    h.vm.stopDiceRoulette()
                    h.vm.finishDiceRoulette()
                }
                Sound.MEAL -> {
                    val source = Position(2, 2)
                    player.moveTo(source)
                    while (engine.foodsAt(source).isNotEmpty()) engine.removeFoodAt(source, 0)
                    engine.placeFoodAt(source, FoodCard.createDummyCards(FoodType.BEETLE_LARVA).first())
                    engine.advancePhase()
                    engine.advancePhase()
                    assertTrue(match.captureCurrentPositionImmediately().success)
                    h.vm.eat()
                }
            }
            val inMatch = h.state
            val id = requireNotNull(sound.shownId(inMatch)) { "$sound did not play in the match" }
            assertTrue(id <= requireNotNull(sound.shownId(practised)), "$sound: the match reuses an ID heard in practice")
            assertEquals(sound.effect, sound.ask(h.vm, inMatch), "$sound of the match was silenced by practice")
            assertNull(sound.ask(h.vm, inMatch), "$sound played twice in the match")
        }
    }

    // ---- Lessons practised alone ----

    @Test
    fun `lessons practised alone show no CPU turn and reserve no opponent step`() {
        for (lesson in SOLO_LESSONS) {
            val h = Harness()
            h.vm.startTutorial(lesson, fromBeginning = true)
            h.queue(solution(lesson).flatten())
            h.runUntil { now ->
                assertFalse(now.cpuTurn, "$lesson showed a CPU turn")
                assertNotEquals(true, now.tutorial?.opponentTurn, "$lesson waited for an opponent")
                assertTrue(h.timers.pending.isEmpty(), "$lesson reserved an opponent step")
                now.tutorialCompletion != null
            }
            assertTrue(h.progress.value.isCompleted(lesson))
            assertEquals(0, h.store.saves)
        }
    }

    /** A playback of a lesson practised alone: the inputs that bring it on screen and its completion callback. */
    private class SoloPlayback(
        val name: String,
        val lesson: TutorialLessonId,
        val inputs: List<TutorialAction>,
        val shown: (AndroidGameUiState) -> Boolean,
        val finish: (AndroidGameViewModel, AndroidGameUiState) -> Unit,
    )

    private fun soloPlaybacks(): List<SoloPlayback> {
        val basic = solution(TutorialLessonId.BASIC)
        val dice = solution(TutorialLessonId.DICE_ESCAPE).first()
        return listOf(
            SoloPlayback("dice landing", TutorialLessonId.DICE_ESCAPE, dice, { it.playState.diceRouletteResult != null }) { vm, shown ->
                vm.finishDiceRoulette(shown.playbackGeneration, shown.diceRouletteEventId)
            },
            SoloPlayback("capture", TutorialLessonId.DICE_ESCAPE, dice, { it.captureAnimation != null }) { vm, shown ->
                vm.finishCaptureAnimation(requireNotNull(shown.captureAnimation).event.id, shown.playbackGeneration)
            },
            SoloPlayback("meal", TutorialLessonId.BASIC, basic.take(3).flatten(), { it.eatAnimation != null }) { vm, shown ->
                vm.finishEatAnimation(requireNotNull(shown.eatAnimation).id, shown.playbackGeneration)
            },
            SoloPlayback("turn", TutorialLessonId.BASIC, basic.first(), { it.turnConsumptionAnimation != null }) { vm, shown ->
                vm.finishTurnConsumptionAnimation(requireNotNull(shown.turnConsumptionAnimation).id, shown.playbackGeneration)
            },
        )
    }

    @Test
    fun `practising alone holds playback completions behind a menu or in the background and applies each once`() {
        val covers = listOf<Pair<String, (AndroidGameViewModel, Boolean) -> Unit>>(
            "a menu" to { vm, covered -> vm.setCpuPlayScreenClear(!covered) },
            "the background" to { vm, covered -> vm.setCpuHostResumed(!covered) },
        )
        for ((cover, setCovered) in covers) for (playback in soloPlaybacks()) {
            val label = "${playback.name} behind $cover"
            val h = Harness()
            h.vm.startTutorial(playback.lesson, fromBeginning = true)
            h.queue(playback.inputs)
            h.runUntil(playback.shown)
            val shown = h.state

            setCovered(h.vm, true)
            playback.finish(h.vm, shown)
            assertTrue(playback.shown(h.state), "$label finished while covered")
            assertEquals(shown.playState, h.state.playState, label)
            assertEquals(shown.boardState, h.state.boardState, label)

            setCovered(h.vm, false)
            val resumed = h.state
            assertFalse(playback.shown(resumed), "$label was not applied when play resumed")
            playback.finish(h.vm, shown)
            assertEquals(resumed, h.state, "$label was applied twice")
            assertEquals(0, h.store.saves)
        }
    }

    private companion object {
        const val STEP_LIMIT = 1_000
        val SOLO_LESSONS = listOf(
            TutorialLessonId.BASIC, TutorialLessonId.DICE_ESCAPE, TutorialLessonId.SURFACE_HEALTH, TutorialLessonId.NEST_EAT,
        )

        fun matchController() = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())

        fun ongoingSave() = SavedGame(
            matchController().apply { startNewGame(2) }.exportSnapshot(), revision = 4, savedAtMillis = 1,
            boardPiecesTransparent = true, lastMessage = "前回の続き",
        )

        fun finishedSave(): SavedGame {
            val source = matchController().apply { startNewGame(2) }.exportSnapshot()
            val finished = source.copy(
                engine = source.engine.copy(
                    gameState = GameState.FINISHED, currentPhase = TurnPhase.END,
                    tileDiscardPile = source.engine.tileDiscardPile + listOfNotNull(source.pendingDigDrawnTile),
                    players = source.engine.players.mapIndexed { i, p -> if (i == 1) p.copy(health = 0) else p },
                ),
                pendingDigDrawnTile = null,
            )
            return SavedGame(finished, revision = 9, savedAtMillis = 1)
        }
    }
}
