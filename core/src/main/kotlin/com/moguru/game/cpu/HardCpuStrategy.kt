package com.moguru.game.cpu

import com.moguru.game.engine.GameState
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.*
import com.moguru.game.presenter.FoodDecisionSource

/** Public-route evaluation with score races, nest defense and two-player survival tactics. */
class HardCpuStrategy : CpuStrategy {
    override fun choose(observation: CpuObservation): CpuAction? {
        val player = observation.currentPlayer ?: return null
        if (observation.gameState != GameState.PLAYING || observation.waitingForCapture) return null
        val board = PublicBoard(observation)
        val tactics = OpponentAssessment(observation, board)
        val action = when (observation.phase) {
            TurnPhase.DIG -> chooseDig(observation, tactics)
            TurnPhase.MOVE -> {
                val actions = listOfNotNull(CpuAction.Skip.takeIf { it in observation.legalActions }) +
                    observation.legalActions.filterIsInstance<CpuAction.Move>()
                actions.maxByOrNull { moveRank(observation, board, (it as? CpuAction.Move)?.position ?: player.position, tactics) }
            }
            TurnPhase.CAPTURE -> observation.legalActions.filterIsInstance<CpuAction.Capture>().maxByOrNull {
                foodRank(observation, board, observation.foods.getValue(player.position)[it.foodIndex], player.position, tactics)
            } ?: CpuAction.Skip
            TurnPhase.DECIDE -> chooseDecision(observation, board, tactics)
            TurnPhase.END -> CpuAction.FinishTurn
            null -> null
        }
        return action?.takeIf { it in observation.legalActions } ?: observation.legalActions.firstOrNull()
    }

    private fun chooseDig(view: CpuObservation, intent: OpponentAssessment): CpuAction? {
        val pending = view.pendingDig
        if (pending != null) {
            val choices = pending.candidates.keys.sortedBy { if (it == pending.selectedChoice) 0 else 1 }
            val rotations = Rotation.entries.sortedBy { if (it == pending.selectedRotation) 0 else 1 }
            val best = choices.flatMap { choice -> rotations.map { CpuAction.PrepareDig(choice, it) } }
                .maxByOrNull { digRank(view, pending.position, HoleTile(pending.candidates.getValue(it.choice)).rotate(it.rotation).flip(), intent) }
                ?: return CpuAction.ConfirmDig
            return if (best.choice == pending.selectedChoice && best.rotation == pending.selectedRotation) CpuAction.ConfirmDig else best
        }
        return view.legalActions.filterIsInstance<CpuAction.RevealDig>().maxByOrNull { action ->
            listOfNotNull(view.drawnTile, view.tiles[action.position]?.shape).distinct().flatMap { shape ->
                Rotation.entries.map { digRank(view, action.position, HoleTile(shape).rotate(it).flip(), intent) }
            }.maxOrNull()?.then(flag(view.tiles[action.position]?.shape == null))
                ?: PublicBoard(view).let { board ->
                    moveRank(view, board, requireNotNull(view.currentPlayer).position, OpponentAssessment(view, board))
                }.then(0.0).then(0.0)
        }
    }

    private fun digRank(view: CpuObservation, position: Position, tile: HoleTile, intent: OpponentAssessment): CpuRank {
        val board = PublicBoard(view, position to tile)
        val positions = board.reachableIncludingCurrent()
        val tactics = OpponentAssessment(view, board, intent)
        return positions.maxOf { moveRank(view, board, it, tactics) }.then(positions.size.toDouble())
    }

    private fun moveRank(view: CpuObservation, board: PublicBoard, position: Position, tactics: OpponentAssessment): CpuRank {
        val player = requireNotNull(view.currentPlayer)
        val cost = board.turnCost(position)
        val route = board.returnRoute(position)
        val safe = route != null && player.health > cost + route.healthCost
        val home = position == player.nestPosition
        val survival = player.health > cost
        if (player.carriedFood != null) {
            return CpuRank(flag(survival), flag(home && survival && player.score + player.carriedFood.points >= view.winningScore),
                -tactics.winningThreatsAfter(position).toDouble(), nextTurnSurvival(view, board, position),
                flag(safe), flag(home), -(route?.healthCost ?: 100).toDouble(),
                progress(board, position, listOf(player.nestPosition)), -cost.toDouble())
        }
        val victim = view.players.firstOrNull {
            it.id != player.id && !it.isEliminated && it.nestPosition == position && it.position != position
        }
        val canRob = victim != null && position == player.position && view.canRobAfterStaying
        val ownMeal = home && player.storedFoods.isNotEmpty() &&
            (position != player.position || view.canEatStoredFoodAfterSkipping) && player.health <= 2 && player.score < view.winningScore
        val foods = view.foods[position].orEmpty()
        val survivalChance = if (survival) 1.0 else when {
            ownMeal -> flag(player.health + player.storedFoods.first().recovery > cost)
            canRob -> flag(victim.storedFoods.any { player.health + it.recovery > cost })
            else -> foods.maxOfOrNull { food -> expectedFood(view, food) {
                if (player.health + it.recovery > cost) board.captureChance(it, position) else 0.0
            } } ?: 0.0
        }
        val immediateWin = canRob && survival && victim.storedFoods.any { player.score + it.points >= view.winningScore }
        val winningVisit = victim != null && player.health > cost * 2 &&
            victim.storedFoods.any { player.score + it.points >= view.winningScore }
        val healing = needsRecovery(player, board, position, tactics)
        val foodValue = foods.maxOfOrNull { food -> expectedFood(view, food) {
            board.captureChance(it, position) * if (healing) minOf(Player.MAX_HEALTH - player.health, it.recovery).toDouble()
                else it.points.toDouble() / (1 + (route?.turns ?: 2))
        } } ?: 0.0
        // Arrival, a later robbery, and departure are separate turns. Robbed points go home immediately.
        val robberyCost = if (canRob) cost else cost * 2
        val raidValue = if (victim != null && player.health > robberyCost) victim.storedFoods.maxOfOrNull {
            if (healing) minOf(Player.MAX_HEALTH - player.health, it.recovery).toDouble() / (if (canRob) 1 else 2)
            else tactics.raidValue(victim, it, canRob)
        } ?: 0.0 else 0.0
        val value = maxOf(foodValue + tactics.contestedRecoveryValue(position), raidValue, tactics.defenseValue(position))
        val goals = listOf(tactics.goal)
        val winningCatch = if (safe) foods.maxOfOrNull { food -> expectedFood(view, food) {
            if (player.score + it.points >= view.winningScore) board.captureChance(it, position) else 0.0
        } } ?: 0.0 else 0.0
        val outlastValue = if (tactics.outlast) {
            val healingValue = foods.maxOfOrNull { food -> expectedFood(view, food) {
                board.captureChance(it, position) * minOf(Player.MAX_HEALTH - player.health, it.recovery)
            } } ?: 0.0
            maxOf(healingValue, if (ownMeal) player.storedFoods.first().recovery.toDouble() else 0.0,
                if (canRob) raidValue else 0.0) - cost - if (tactics.enablesRecoveryAfter(position)) 4.0 else 0.0
        } else nextTurnSurvival(view, board, position)
        return CpuRank(survivalChance, flag(immediateWin), -tactics.winningThreatsAfter(position).toDouble(), outlastValue, flag(ownMeal),
            maxOf(if (winningVisit) tactics.raidOpportunity(requireNotNull(victim), canRob) else 0.0, winningCatch), value,
            progress(board, position, goals), -cost.toDouble())
    }

    private fun foodRank(view: CpuObservation, board: PublicBoard, food: CpuFood, position: Position, tactics: OpponentAssessment): CpuRank {
        val player = requireNotNull(view.currentPlayer)
        val route = board.returnRoute(position)
        val healing = needsRecovery(player, board, position, tactics)
        val safe = route != null && player.health > board.turnCost(position) + route.healthCost
        return CpuRank(
            if (player.health > board.turnCost(position)) 1.0 else expectedFood(view, food) {
                if (player.health + it.recovery > board.turnCost(position)) board.captureChance(it, position) else 0.0
            },
            if (safe) expectedFood(view, food) {
                if (player.score + it.points >= view.winningScore) board.captureChance(it, position) else 0.0
            } else 0.0,
            expectedFood(view, food) {
                board.captureChance(it, position) * if (healing) minOf(Player.MAX_HEALTH - player.health, it.recovery).toDouble()
                    else it.points.toDouble() / (1 + (route?.turns ?: 2))
            },
            expectedFood(view, food) { board.captureChance(it, position) },
        )
    }

    private fun chooseDecision(view: CpuObservation, board: PublicBoard, tactics: OpponentAssessment): CpuAction {
        val player = requireNotNull(view.currentPlayer)
        val cost = board.turnCost(player.position)
        view.pendingFood?.let { food ->
            val robbery = view.pendingFoodSource == FoodDecisionSource.ROBBERY
            val route = board.returnRoute(player.position)
            val safe = player.health > cost && (robbery || route != null && player.health > cost + route.healthCost)
            if (safe && player.score + food.points >= view.winningScore) return CpuAction.Carry
            val needsHealing = if (robbery) robberyNeedsHealing(player, cost, tactics)
                else needsRecovery(player, board, player.position, tactics)
            return if (needsHealing && player.health < Player.MAX_HEALTH) CpuAction.Eat else CpuAction.Carry
        }
        val robbery = view.legalActions.filterIsInstance<CpuAction.Rob>()
        if (robbery.isNotEmpty()) {
            val victim = view.players.first { it.id != player.id && it.nestPosition == player.position }
            return robbery.maxByOrNull {
                val food = victim.storedFoods[it.foodIndex]
                CpuRank(flag(player.health + food.recovery > cost),
                    flag(player.health > cost && player.score + food.points >= view.winningScore),
                    if (robberyNeedsHealing(player, board.turnCost(player.position), tactics)) minOf(Player.MAX_HEALTH - player.health, food.recovery).toDouble()
                    else tactics.raidValue(victim, food, eligibleNow = true))
            }!!
        }
        if (player.score >= view.winningScore && player.health > cost) return CpuAction.Skip
        return if (CpuAction.Eat in view.legalActions && player.health <= 2) CpuAction.Eat else CpuAction.Skip
    }

    private fun robberyNeedsHealing(player: CpuPlayer, cost: Int, tactics: OpponentAssessment): Boolean =
        tactics.outlast || player.health <= (if (tactics.racing) cost + 1 else 4)

    /** Unknown routes stay uncertain; reserve four health for opening a path and returning. */
    private fun needsRecovery(player: CpuPlayer, board: PublicBoard, position: Position, tactics: OpponentAssessment): Boolean {
        val route = board.returnRoute(position)
        if (tactics.outlast) return true
        val reserve = if (tactics.racing && route != null) 1 else 0
        val minimum = if (tactics.racing && route != null) 3 else 4
        return player.health <= maxOf(minimum, board.turnCost(position) + (route?.healthCost ?: 4) + reserve)
    }

    /** Avoid a stop that survives now but needs an uncertain catch just to survive the following turn. */
    private fun nextTurnSurvival(view: CpuObservation, board: PublicBoard, position: Position): Double {
        val player = requireNotNull(view.currentPlayer)
        val remaining = player.health - board.turnCost(position)
        if (remaining >= 2) return 1.0
        if (position == player.nestPosition) {
            val meal = player.storedFoods.firstOrNull() ?: player.carriedFood
            if (meal != null && remaining + meal.recovery > 1) return 1.0
        }
        if (player.carriedFood != null) return 0.0
        val stops = board.reachableFor(player, position)
        return stops.maxOfOrNull { stop ->
            if (stop == player.nestPosition && player.storedFoods.firstOrNull()?.let { remaining + it.recovery > 1 } == true) 1.0
            else view.foods[stop].orEmpty().maxOfOrNull { food -> expectedFood(view, food) {
                if (remaining + it.recovery > board.turnCost(stop)) board.captureChance(it, stop) else 0.0
            } } ?: 0.0
        } ?: 0.0
    }

    private fun progress(board: PublicBoard, position: Position, goals: List<Position>): Double =
        goals.maxOfOrNull { goal ->
            val distance = board.distance(position, goal)
            val frontier = board.digTargetsFrom(position).minOfOrNull { board.distance(it, goal) } ?: distance + 1
            -frontier * 100.0 - distance
        } ?: 0.0

    private fun flag(value: Boolean) = if (value) 1.0 else 0.0
}
