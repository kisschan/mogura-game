package com.moguru.game.cpu

import com.moguru.game.engine.GameState
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.*

/** Local, deterministic decisions without evaluating future dig placements or planned raids. */
class EasyCpuStrategy : CpuStrategy {
    override fun choose(observation: CpuObservation): CpuAction? {
        val player = observation.currentPlayer ?: return null
        if (observation.gameState != GameState.PLAYING || observation.waitingForCapture) return null
        val board = PublicBoard(observation)
        val action = when (observation.phase) {
            TurnPhase.DIG -> if (observation.pendingDig != null) CpuAction.ConfirmDig else {
                val goals = if (player.carriedFood != null) listOf(player.nestPosition)
                    else observation.foods.filterValues { it.isNotEmpty() }.keys.toList()
                observation.legalActions.filterIsInstance<CpuAction.RevealDig>()
                    .minByOrNull { action -> goals.minOfOrNull { board.distance(action.position, it) } ?: 0 }
            }
            TurnPhase.MOVE -> {
                val choices = listOfNotNull(CpuAction.Skip.takeIf { it in observation.legalActions }) +
                    observation.legalActions.filterIsInstance<CpuAction.Move>()
                choices.maxByOrNull { choice ->
                    val position = (choice as? CpuAction.Move)?.position ?: player.position
                    val distance = observation.foods.filterValues { it.isNotEmpty() }.keys
                        .minOfOrNull { board.distance(position, it) } ?: 0
                    CpuRank(
                        if (player.health > board.turnCost(position)) 1.0 else 0.0,
                        if (player.carriedFood != null) -board.distance(position, player.nestPosition).toDouble()
                        else -distance.toDouble(),
                        -board.turnCost(position).toDouble(),
                    )
                }
            }
            TurnPhase.CAPTURE -> observation.legalActions.filterIsInstance<CpuAction.Capture>().maxByOrNull {
                expectedFood(observation, observation.foods[player.position].orEmpty()[it.foodIndex]) { type ->
                    board.captureChance(type, player.position)
                }
            } ?: CpuAction.Skip
            TurnPhase.DECIDE -> when {
                observation.pendingFood != null -> if (player.health <= 4) CpuAction.Eat else CpuAction.Carry
                observation.legalActions.any { it is CpuAction.Rob } -> observation.legalActions.first { it is CpuAction.Rob }
                player.health <= 2 && CpuAction.Eat in observation.legalActions -> CpuAction.Eat
                else -> CpuAction.Skip
            }
            TurnPhase.END -> CpuAction.FinishTurn
            null -> null
        }
        return action?.takeIf { it in observation.legalActions } ?: observation.legalActions.firstOrNull()
    }
}

/** Lexicographic evaluation; equal choices retain the stable public action order. */
internal class CpuRank(private vararg val values: Double) : Comparable<CpuRank> {
    fun then(value: Double) = CpuRank(*values, value)

    override fun compareTo(other: CpuRank): Int {
        for (index in 0 until minOf(values.size, other.values.size)) {
            val comparison = values[index].compareTo(other.values[index])
            if (comparison != 0) return comparison
        }
        return values.size.compareTo(other.values.size)
    }
}

/** Fixed public prior, never the real stock contents or order. */
internal fun expectedFood(view: CpuObservation, food: CpuFood, evaluate: (FoodType) -> Double): Double {
    food.type?.let { return evaluate(it) }
    val types = FoodType.entries.filter { view.players.size == 4 || it != FoodType.FROG }
    return types.sumOf { it.cardCount * evaluate(it) } / types.sumOf { it.cardCount }
}
