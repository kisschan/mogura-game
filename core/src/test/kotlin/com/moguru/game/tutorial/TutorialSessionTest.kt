package com.moguru.game.tutorial

import com.moguru.game.engine.GameState
import com.moguru.game.model.Board
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.HoleTile
import com.moguru.game.model.TileShape
import com.moguru.game.persistence.GameSnapshot
import com.moguru.game.persistence.validate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TutorialSessionTest {
    private val scenario = TutorialScenarios.basic

    private fun driver(startChapter: Int = 0) = TutorialDriver(TutorialSession(scenario, startChapter))

    /** Learner health, opponent health and learner score, as in [BasicLesson.chapterStartStats]. */
    private fun GameSnapshot.stats() =
        Triple(player(LEARNER_ID).health, player(OPPONENT_ID).health, player(LEARNER_ID).score)

    /** Food types the learner has already seen face up somewhere. */
    private fun GameSnapshot.publicFoodTypes(): Set<FoodType> = buildSet {
        engine.foods.values.flatten().filter { !it.isFaceDown }.forEach { add(it.type) }
        engine.players.forEach { player ->
            player.carriedFood?.let { add(it.type) }
            player.storedFoods.forEach { add(it.type) }
        }
        pendingDecision?.let { add(it.food.type) }
        pendingCaptureRoll?.let { add(it.food.type) }
    }

    @Test
    fun `basic lesson starts from the fixed layout with the regulation tiles and food`() {
        val session = TutorialSession(scenario)
        val start = session.controller.exportSnapshot()
        start.validate()
        assertEquals(TutorialLessonId.BASIC, scenario.lesson)
        assertEquals(BasicLesson.chapters.size, scenario.chapterCount)
        assertEquals(BasicLesson.chapters, scenario.solution)
        assertEquals(0, session.chapterIndex)
        assertEquals(LEARNER_ID, session.learnerPlayerId)
        assertEquals(OPPONENT_ID, session.opponentPlayerId)

        val engine = start.engine
        assertEquals(listOf(LEARNER_ID, OPPONENT_ID), engine.players.map { it.id })
        assertEquals(LEARNER_ID, engine.players[engine.currentPlayerIndex].id, "the learner moves first")
        start.player(LEARNER_ID).let {
            assertEquals(LEARNER_NEST, it.nestPosition)
            assertEquals(LEARNER_NEST, it.position)
        }
        start.player(OPPONENT_ID).let {
            assertEquals(OPPONENT_NEST, it.nestPosition)
            assertEquals(OPPONENT_NEST, it.position)
        }
        assertEquals(BasicLesson.chapterStartStats[0], start.stats())

        val s = TileShape.STRAIGHT
        val l = TileShape.L_SHAPE
        val t = TileShape.T_SHAPE
        val x = TileShape.CROSS
        val layout = listOf(
            listOf(s, s, l, l),
            listOf(l, l, s, t),
            listOf(s, l, s, t),
            listOf(s, l, s, s),
        )
        val expectedTiles = layout.flatMapIndexed { row, shapes ->
            shapes.mapIndexed { col, shape -> p(col + 1, row + 1) to shape }
        }.toMap()
        assertEquals(expectedTiles, engine.tiles.mapValues { it.value.shape })
        assertTrue(engine.tiles.values.all { it.isFaceDown })
        // Both piles are drawn from the front. The game opens by drawing the learner's first tile
        // (an L), so it waits as the prepared dig tile and nine tiles remain in the pile.
        assertEquals(l, start.pendingDigDrawnTile?.shape)
        assertEquals(listOf(t, l, x, s, t, l, x, s, l), engine.tileDrawPile.map { it.shape })
        assertTrue(engine.tileDiscardPile.isEmpty())
        assertEquals(
            HoleTile.createFullSet().groupingBy { it.shape }.eachCount(),
            (engine.tiles.values + engine.tileDrawPile + listOfNotNull(start.pendingDigDrawnTile))
                .groupingBy { it.shape }.eachCount(),
        )

        assertEquals(Board.HOT_ZONE_POSITIONS, engine.foods.keys)
        engine.foods.forEach { (position, cards) ->
            val card = cards.single()
            assertEquals(FoodType.BEETLE_LARVA, card.type, "$position")
            assertTrue(card.isFaceDown, "$position")
        }
        assertEquals(
            List(3) { FoodType.EARTHWORM } + List(3) { FoodType.MOLE_CRICKET } + List(2) { FoodType.CENTIPEDE },
            engine.foodStock.map { it.type },
        )
        assertTrue(engine.foodDiscard.isEmpty())
        assertEquals(
            FoodCard.createDeck(includeFrog = false).groupingBy { it.type }.eachCount(),
            (engine.foods.values.flatten() + engine.foodStock).groupingBy { it.type }.eachCount(),
        )
    }

    @Test
    fun `basic lesson is completed with legal moves and shows eating, turn cost and scoring`() {
        val driver = driver()
        driver.finish(BasicLesson.chapters)
        assertEquals(TutorialStatus.COMPLETED, driver.session.status)
        assertTrue(driver.opponentActions.isNotEmpty())

        assertEquals((0 until scenario.chapterCount).toList(), driver.chapterStarts.keys.toList())
        driver.chapterStarts.forEach { (chapter, start) ->
            assertEquals(BasicLesson.chapterStartStats[chapter], start.stats(), "chapter $chapter")
            assertEquals(OPPONENT_NEST, start.player(OPPONENT_ID).position, "the opponent stays in its nest")
            assertEquals(scenario.chapterStartSnapshot(chapter).engine, start.engine, "chapter $chapter")
        }

        val eat = driver.learnerSteps.single { it.action == TutorialAction.Eat }
        assertEquals(2, eat.chapter)
        assertEquals(11, eat.before.player(LEARNER_ID).health)
        assertEquals(12, eat.afterAction.player(LEARNER_ID).health)
        assertTrue(eat.afterAction.player(LEARNER_ID).storedFoods.isEmpty(), "eaten food scores nothing")

        val consumptions = driver.learnerConsumptions().take(5)
        assertEquals(
            listOf(13 to 12, 12 to 11, 12 to 11, 11 to 10, 10 to 9),
            consumptions.map { it.healthBefore to it.healthAfter },
        )
        assertTrue(consumptions.all { it.actualConsumption == 1 })

        val homecoming = driver.learnerSteps.single { it.action == TutorialAction.Move(LEARNER_NEST) }
        assertEquals(4, homecoming.chapter)
        assertEquals(FoodType.BEETLE_LARVA, homecoming.before.player(LEARNER_ID).carriedFood?.type)
        homecoming.afterAction.player(LEARNER_ID).let {
            assertNull(it.carriedFood)
            assertEquals(listOf(FoodType.BEETLE_LARVA), it.storedFoods.map { food -> food.type })
            assertEquals(1, it.score)
        }
    }

    @Test
    fun `first point neither wins the game nor completes the lesson`() {
        val driver = driver()
        val session = driver.session
        driver.play(BasicLesson.chapters.take(4).flatten() + BasicLesson.chapter4.dropLast(1))
        assertEquals(4, session.chapterIndex)
        assertEquals(1, driver.learner().score)
        assertNotEquals(TutorialStatus.COMPLETED, session.status)
        // The lesson explains that eating from the nest loses the point instead of allowing it.
        assertRefusedByTutorial(session, TutorialAction.Eat)

        driver.play(listOf(TutorialAction.FinishTurn))
        driver.reachLearnerAction()
        assertEquals(5, session.chapterIndex)
        assertEquals(TutorialStatus.LEARNER_ACTION, session.status)
        assertEquals(BasicLesson.chapterStartStats[5], driver.snapshot().stats())
        val engine = requireNotNull(session.controller.engine)
        assertEquals(GameState.PLAYING, engine.gameState)
        assertNull(engine.checkWinCondition())
    }

    @Test
    fun `operations outside the guidance are refused without touching the game`() {
        val driver = driver()
        val session = driver.session
        assertEquals(TutorialStatus.AWAITING_ACKNOWLEDGEMENT, session.status)
        assertRefusedByTutorial(session, BasicLesson.chapter0.first())
        val waiting = driver.snapshot()
        assertNull(session.advanceAutomatic(), "an open explanation holds the game")
        assertEquals(waiting, driver.snapshot())

        driver.reachLearnerAction()
        val stage = session.guidance.stage
        assertFalse(session.acknowledge(), "a repeated acknowledgement is ignored")
        assertEquals(waiting, driver.snapshot())
        assertEquals(stage, session.guidance.stage)
        listOf(
            TutorialAction.SelectDigCell(p(2, 1)),
            TutorialAction.SelectMoveTarget(p(1, 1)),
            TutorialAction.FinishTurn,
            TutorialAction.Skip,
        ).forEach { assertRefusedByTutorial(session, it) }

        driver.play(BasicLesson.chapter0 + BasicLesson.chapter1 + BasicLesson.chapter2.dropLast(1))
        driver.reachLearnerAction()
        assertNotNull(driver.snapshot().pendingDecision, "carrying is a legal game move here")
        assertRefusedByTutorial(session, TutorialAction.Carry)
        assertRefusedByTutorial(session, TutorialAction.FinishTurn)
        driver.perform(TutorialAction.Eat)
        assertEquals(12, driver.learner().health)
    }

    @Test
    fun `every chapter starts from its recorded state and can be finished from there`() {
        assertEquals(scenario.newController().exportSnapshot(), scenario.chapterStartSnapshot(0))
        for (chapter in 0 until scenario.chapterCount) {
            val expected = scenario.chapterStartSnapshot(chapter)
            expected.validate()
            assertEquals(expected, scenario.chapterStartSnapshot(chapter), "chapter $chapter is reproducible")
            assertEquals(BasicLesson.chapterStartStats[chapter], expected.stats(), "chapter $chapter")

            val driver = driver(chapter)
            assertEquals(chapter, driver.session.chapterIndex)
            assertEquals(expected, driver.snapshot(), "chapter $chapter")
            driver.finish(BasicLesson.chapters.drop(chapter))
            assertEquals(TutorialStatus.COMPLETED, driver.session.status, "chapter $chapter")
        }
    }

    @Test
    fun `restarting a chapter restores its start and starts a new attempt`() {
        val driver = driver()
        val session = driver.session
        driver.play(BasicLesson.chapter0 + BasicLesson.chapter1 + BasicLesson.chapter2.take(5))
        assertEquals(2, session.chapterIndex)
        assertNotEquals(scenario.chapterStartSnapshot(2), driver.snapshot())

        val generation = session.generation
        session.restartChapter()
        assertEquals(2, session.chapterIndex)
        assertTrue(session.generation > generation)
        assertEquals(scenario.chapterStartSnapshot(2), driver.snapshot())

        driver.finish(BasicLesson.chapters.drop(2))
        assertEquals(TutorialStatus.COMPLETED, session.status)
    }

    @Test
    fun `guidance points at face-down food without naming it`() {
        // The driver also checks every guidance while playing; this pins down that the check had cases.
        val driver = driver()
        driver.finish(BasicLesson.chapters)

        // Hidden is decided per card. Types the learner has already seen elsewhere stay checked.
        val seen = mutableSetOf<FoodType>()
        val repeatedTypes = mutableListOf<LearnerStep>()
        val guidedHidden = driver.learnerSteps.mapNotNull { step ->
            seen += step.before.publicFoodTypes()
            val hidden = hiddenGuidedCards(step.guidance, step.before)
            val repeated = hidden.any { it.type in seen }
            seen += step.afterAction.publicFoodTypes()
            if (hidden.isEmpty()) return@mapNotNull null
            val shown = step.guidance.shownText()
            hidden.flatMap { it.type.hiddenNames() }.forEach { name ->
                assertFalse(name in shown, "chapter ${step.chapter} ${step.action} names hidden food: $shown")
            }
            if (repeated) repeatedTypes += step
            step
        }

        val captures = driver.learnerSteps.filter { it.action == TutorialAction.Capture }
        assertEquals(listOf(2, 3), captures.map { it.chapter })
        captures.forEach { step ->
            assertTrue(step in guidedHidden, "the capture guidance in chapter ${step.chapter} points at the hidden card")
        }
        // The second larva is still hidden although the first one was revealed and eaten.
        assertTrue(captures.last() in repeatedTypes)
    }

    @Test
    fun `capture reveals the card only after the attempt, and the guidance may then name it`() {
        val driver = driver()
        val session = driver.session
        driver.play(BasicLesson.chapter0 + BasicLesson.chapter1 + BasicLesson.chapter2.take(5))
        driver.reachLearnerAction()
        assertTrue(hiddenGuidedCards(session.guidance, driver.snapshot()).isNotEmpty())
        driver.perform(TutorialAction.Capture)
        driver.reachLearnerAction()
        assertTrue(hiddenGuidedCards(session.guidance, driver.snapshot()).isEmpty())
        // After the attempt the lesson explains the larva and that other food needs the dice.
        assertTrue(driver.shownGuidance.any { "幼虫" in it.shownText() && "ダイス" in it.shownText() })
    }
}
