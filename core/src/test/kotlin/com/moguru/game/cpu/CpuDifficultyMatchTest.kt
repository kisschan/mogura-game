package com.moguru.game.cpu

import com.moguru.game.engine.GameState
import com.moguru.game.engine.TurnPhase
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.DiceRoller
import com.moguru.game.util.Shuffler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class CpuDifficultyMatchTest {
    private data class Outcome(val winner: CpuDifficulty?, val eliminated: List<CpuDifficulty>, val turns: Int)

    private fun play(difficulties: List<CpuDifficulty>, seed: Int): Outcome {
        val dice = Random(seed)
        val cards = Random(seed)
        val game = MoguraGameController(
            object : DiceRoller { override fun roll() = dice.nextInt(1, 7) },
            object : Shuffler { override fun <T> shuffle(list: List<T>) = list.shuffled(cards) },
        )
        game.startNewGame(difficulties.size)
        val policies = difficulties.map { it.createStrategy() }
        var steps = 0
        var turns = 0
        while (game.engine!!.gameState == GameState.PLAYING && steps++ < 2000) {
            val before = game.exportSnapshot()
            val result = if (game.pendingCaptureRoll != null) {
                val pending = game.pendingCaptureRoll!!
                if (pending.roll == null && pending.food.escapeMap.isNotEmpty()) game.rollCaptureDice()
                else game.resolveCaptureRoll()
            } else {
                val observation = game.cpuObservation()
                val policy = policies[game.engine!!.currentPlayerIndex]
                val action = policy.choose(observation)
                assertNotNull(action, "seed=$seed difficulties=$difficulties")
                assertTrue(action in observation.legalActions)
                assertEquals(action, policy.choose(MoguraGameController.fromSnapshot(before).cpuObservation()))
                if (observation.phase == TurnPhase.END) turns++
                game.applyCpuAction(action!!)
            }
            assertTrue(result.success, result.message)
            assertNotEquals(before, game.exportSnapshot())
        }
        assertEquals(GameState.FINISHED, game.engine!!.gameState, "seed=$seed difficulties=$difficulties")
        val players = game.engine!!.players
        return Outcome(game.engine!!.checkWinCondition()?.let { difficulties[players.indexOf(it)] },
            players.mapIndexedNotNull { index, p -> difficulties[index].takeIf { p.isEliminated } }, turns)
    }

    @Test fun `mixed difficulties complete two to four player games`() {
        for (count in 2..4) for (seed in 0..3) {
            play(List(count) { CpuDifficulty.entries[(it + seed) % 3] }, seed)
        }
    }

    @Test fun `report paired seeded matches with reversed seats`() {
        for ((first, second) in listOf(CpuDifficulty.EASY to CpuDifficulty.NORMAL,
            CpuDifficulty.NORMAL to CpuDifficulty.HARD, CpuDifficulty.EASY to CpuDifficulty.HARD)) {
            val results = (0..47).flatMap { seed ->
                listOf(play(listOf(first, second), seed), play(listOf(second, first), seed))
            }
            println("CPU comparison $first/$second: wins=${results.count { it.winner == first }}/${results.count { it.winner == second }}, " +
                "noWinner=${results.count { it.winner == null }}, eliminated=${results.sumOf { it.eliminated.count { d -> d == first } }}/" +
                "${results.sumOf { it.eliminated.count { d -> d == second } }}, averageTurns=${results.map { it.turns }.average()}")
        }
    }

    @Test fun `report independent seeds and all multiplayer seats against normal`() {
        for (count in 2..4) {
            val seeds = if (count == 2) 96..191 else 96..127
            val results = seeds.flatMap { seed -> (0 until count).map { hardSeat ->
                play(List(count) { if (it == hardSeat) CpuDifficulty.HARD else CpuDifficulty.NORMAL }, seed)
            } }
            println("Independent $count-player: hardWins=${results.count { it.winner == CpuDifficulty.HARD }}/${results.size}, " +
                "noWinner=${results.count { it.winner == null }}, averageTurns=${results.map { it.turns }.average()}")
        }
    }
}
