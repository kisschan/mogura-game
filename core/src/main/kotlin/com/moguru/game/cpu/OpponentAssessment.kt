package com.moguru.game.cpu

import com.moguru.game.model.FoodType
import com.moguru.game.model.Position

/** Opponent-sensitive priorities derived solely from the current public board. */
internal class OpponentAssessment(
    private val view: CpuObservation,
    private val board: PublicBoard,
    intent: OpponentAssessment? = null,
) {
    private val self = requireNotNull(view.currentPlayer)
    private val opponents = view.players.filter { it.id != self.id && !it.isEliminated }

    // Sole survival is a win only in a game that started with two participants.
    val outlast: Boolean = intent?.outlast ?: (view.players.size == 2 && opponents.singleOrNull()?.let {
        it.health <= 3 && self.health > it.health + 2 && it.carriedFood == null &&
            !hasRecovery(it, allowDig = true) && winningThreatsAfter(self.position) == 0
    } == true)

    val racing: Boolean = intent?.racing ?: (!outlast && opponents.any {
        val bankable = it.score + if (it.nestPosition in board.reachableFor(it)) (it.carriedFood?.points ?: 0) else 0
        it.health > board.turnCost(it.position) && bankable >= view.winningScore - 2 && bankable >= self.score
    })

    /** One objective per observation; hypothetical dig candidates share it to avoid inconsistent comparisons. */
    val goal: Position = intent?.goal ?: chooseGoal()

    fun enablesRecoveryAfter(position: Position): Boolean = opponents.any { hasRecovery(it, position) }

    /** A visible win on the rival's next turn; future digging/captures are deliberately uncertain. */
    fun winningThreatsAfter(position: Position): Int = opponents.count { rival ->
        val location = board.projectedPosition(rival, position)
        val winningReturn = rival.carriedFood?.let {
            rival.score + it.points >= view.winningScore && rival.health > board.turnCost(rival.nestPosition) &&
                rival.nestPosition in board.reachableFor(rival, position)
        } == true
        val victim = view.players.firstOrNull {
            it.id != rival.id && !it.isEliminated && it.nestPosition == location &&
                board.projectedPosition(it, position) != location
        }
        winningReturn || (rival.carriedFood == null && rival.health > board.turnCost(location) &&
            victim?.storedFoods?.any { rival.score + it.points >= view.winningScore } == true)
    }

    /** Defending also prevents ordinary loss of stored points, even before it would lose the game. */
    fun defenseValue(position: Position): Double {
        if (position != self.nestPosition || self.storedFoods.isEmpty()) return 0.0
        val intruder = opponents.firstOrNull { it.position == self.nestPosition } ?: return 0.0
        return self.storedFoods.maxOf { it.points }.toDouble() * (1.0 + pressure(intruder))
    }

    fun raidValue(victim: CpuPlayer, food: FoodType, eligibleNow: Boolean): Double {
        // In multiplayer, denying a trailing rival mostly helps the other opponents.
        val denialWeight = if (view.players.size == 2) 1.0
            else if (victim.score == opponents.maxOfOrNull { it.score }) 0.7 else 0.25
        val denial = food.points * denialWeight * (1.0 + 2.0 * pressure(victim))
        return (food.points + denial) * raidOpportunity(victim, eligibleNow) / if (eligibleNow) 1.0 else 2.0
    }

    /** A tactical confidence weight, not a claim about the opponent's actual policy. */
    fun raidOpportunity(victim: CpuPlayer, eligibleNow: Boolean): Double {
        if (eligibleNow) return 1.0
        if (victim.health <= board.turnCost(victim.position) && !hasRecovery(victim)) return 0.0
        // A carried catch is a strong public reason for the owner to return and evict us first.
        val canDefend = victim.health > 1 && victim.nestPosition in board.reachableFor(victim)
        return if (canDefend && victim.carriedFood != null) 0.15 else if (canDefend) 0.6 else 1.0
    }

    fun contestedRecoveryValue(position: Position): Double = opponents.sumOf { rival ->
        if (rival.carriedFood == null && rival.health <= 3 && position in board.reachableFor(rival) &&
            view.foods[position].orEmpty().isNotEmpty()) (4 - rival.health) * 0.25 else 0.0
    }

    private fun chooseGoal(): Position {
        if (self.carriedFood != null) return self.nestPosition
        val targets = view.foods.filterValues { it.isNotEmpty() }.map { (position, foods) ->
            position to (foods.maxOf { food -> expectedFood(view, food) {
                board.captureChance(it, position) * if (self.health <= 4 || outlast) it.recovery.toDouble() else it.points.toDouble()
            } } + contestedRecoveryValue(position))
        } + opponents.filter { it.position != it.nestPosition && it.storedFoods.isNotEmpty() }.map { rival ->
            rival.nestPosition to rival.storedFoods.maxOf { raidValue(rival, it, eligibleNow = false) }
        }
        return targets.maxByOrNull { (position, value) ->
            value / (1 + board.distance(self.position, position))
        }?.first ?: self.nestPosition
    }

    private fun pressure(rival: CpuPlayer): Double {
        val potential = rival.score + (rival.carriedFood?.points ?: 0)
        val proximity = (potential.toDouble() / view.winningScore).coerceAtMost(1.0)
        return proximity * proximity * if (rival.health <= 2 && !hasRecovery(rival)) 0.25 else 1.0
    }

    private fun hasRecovery(rival: CpuPlayer, position: Position = self.position, allowDig: Boolean = false): Boolean {
        val stops = if (allowDig) board.possiblyReachableAfterDig(rival) else board.reachableFor(rival, position)
        return stops.any { location ->
            location == rival.nestPosition && (rival.storedFoods.isNotEmpty() || rival.carriedFood != null) ||
                rival.carriedFood == null && view.foods[location].orEmpty().any { food ->
                    expectedFood(view, food) { board.captureChance(it, location) * it.recovery } > 0.0
                } || rival.carriedFood == null && view.players.any {
                    it.id != rival.id && !it.isEliminated && it.nestPosition == location && board.projectedPosition(it, position) != location &&
                        it.storedFoods.isNotEmpty() && (location == board.projectedPosition(rival, position) || rival.health > board.turnCost(location))
                }
        }
    }
}
