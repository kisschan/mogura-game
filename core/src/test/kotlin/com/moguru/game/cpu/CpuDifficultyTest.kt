package com.moguru.game.cpu

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.*
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CpuDifficultyTest {
    private fun caught(health: Int): CpuObservation {
        val game = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
        game.startNewGame(2)
        game.currentPlayer!!.moveTo(Position(1, 1))
        repeat(Player.MAX_HEALTH - health) { game.currentPlayer!!.reduceHealth(false) }
        game.engine!!.placeFoodAt(Position(1, 1), FoodCard.createDummyCards(FoodType.CENTIPEDE).first())
        game.engine!!.advancePhase()
        game.engine!!.advancePhase()
        check(game.captureCurrentPositionImmediately().success)
        return game.cpuObservation()
    }

    @Test fun `hard eats above four health when no public return route exists`() {
        val view = caught(5)
        assertEquals(CpuAction.Carry, StandardCpuStrategy().choose(view))
        assertEquals(CpuAction.Eat, HardCpuStrategy().choose(view))
    }

    @Test fun `hard does not waste a catch healing at maximum health`() {
        assertEquals(CpuAction.Carry, HardCpuStrategy().choose(caught(Player.MAX_HEALTH)))
    }

    @Test fun `public return route includes the future nest turn but not the current turn`() {
        val original = caught(3)
        assertNull(PublicBoard(original).returnRoute(Position(1, 1)))
        val view = original.copy(tiles = original.tiles +
            (Position(1, 1) to CpuTile(TileShape.CROSS, Direction.entries.toSet())))
        val board = PublicBoard(view)
        assertEquals(PublicBoard.ReturnRoute(1, 1), board.returnRoute(Position(1, 1)))
        assertEquals(PublicBoard.ReturnRoute(0, 0), board.returnRoute(view.currentPlayer!!.nestPosition))
    }

    @Test fun `hard carries a winning catch only with enough health to return alive`() {
        for (health in listOf(2, 3)) {
            val original = caught(health)
            val view = original.copy(
                players = original.players.mapIndexed { index, p ->
                    if (index == 0) p.copy(storedFoods = listOf(FoodType.BEETLE_LARVA)) else p
                },
                tiles = original.tiles + (Position(1, 1) to CpuTile(TileShape.CROSS, Direction.entries.toSet())),
            )
            assertEquals(if (health == 3) CpuAction.Carry else CpuAction.Eat, HardCpuStrategy().choose(view))
        }
    }

    @Test fun `normal delegates unchanged and every difficulty confirms its dig`() {
        for (difficulty in CpuDifficulty.entries) {
            val strategy = difficulty.createStrategy()
            val game = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
            game.startNewGame(2)
            val before = game.exportSnapshot()
            val action = strategy.choose(game.cpuObservation())!!
            assertEquals(before, game.exportSnapshot())
            assertEquals(action, strategy.choose(game.cpuObservation()))
            if (difficulty == CpuDifficulty.NORMAL) assertEquals(StandardCpuStrategy().choose(game.cpuObservation()), action)
            assertTrue(game.applyCpuAction(action).success)
            repeat(2) {
                if (game.engine!!.currentPhase == TurnPhase.DIG) {
                    assertTrue(game.applyCpuAction(strategy.choose(game.cpuObservation())!!).success)
                }
            }
            assertEquals(TurnPhase.MOVE, game.engine!!.currentPhase)
        }
    }
}
