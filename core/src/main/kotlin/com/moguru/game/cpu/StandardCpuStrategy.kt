package com.moguru.game.cpu

import com.moguru.game.engine.BoardState
import com.moguru.game.engine.GameState
import com.moguru.game.engine.MovementEngine
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.*
import com.moguru.game.presenter.DigTileChoice
import com.moguru.game.presenter.FoodDecisionSource
import com.moguru.game.presenter.directionBetween

/** A bounded, deterministic one-move policy using only publicly visible information. */
class StandardCpuStrategy : CpuStrategy {
    override fun choose(observation: CpuObservation): CpuAction? {
        if (observation.gameState != GameState.PLAYING || observation.waitingForCapture) return null
        if (observation.legalActions.isEmpty() || observation.currentPlayer == null) return null
        val board = PublicBoard(observation)
        val selected = when (observation.phase) {
            TurnPhase.DIG -> chooseDig(observation)
            TurnPhase.MOVE -> chooseMove(observation, board)
            TurnPhase.CAPTURE -> observation.legalActions.filterIsInstance<CpuAction.Capture>()
                .maxByOrNull { foodRank(observation, board, it.foodIndex) } ?: CpuAction.Skip
            TurnPhase.DECIDE -> chooseFoodDecision(observation, board)
            TurnPhase.END -> CpuAction.FinishTurn
            null -> null
        }
        return selected?.takeIf { it in observation.legalActions } ?: observation.legalActions.firstOrNull()
    }

    private fun chooseDig(view: CpuObservation): CpuAction {
        val pending = view.pendingDig
        if (pending != null) {
            // Keep the current candidate/rotation first so equivalent previews need no extra operation.
            val choices = pending.candidates.keys.sortedBy { if (it == pending.selectedChoice) 0 else 1 }
            val rotations = Rotation.entries.sortedBy { if (it == pending.selectedRotation) 0 else 1 }
            val best = choices.flatMap { choice ->
                rotations.map { rotation -> CpuAction.PrepareDig(choice, rotation) }
                    .distinctBy { HoleTile(pending.candidates.getValue(choice)).rotate(it.rotation).openSides }
            }.maxByOrNull { action ->
                digRank(view, pending.position,
                    HoleTile(pending.candidates.getValue(action.choice)).rotate(action.rotation).flip())
            } ?: return CpuAction.ConfirmDig
            return if (best.choice == pending.selectedChoice && best.rotation == pending.selectedRotation) {
                CpuAction.ConfirmDig
            } else best
        }

        return view.legalActions.filterIsInstance<CpuAction.RevealDig>().maxByOrNull { action ->
            // Before revealing a location, only the drawn card and an already face-up tile are known.
            val shapes = listOfNotNull(view.drawnTile, view.tiles[action.position]?.shape).distinct()
            val best = shapes.flatMap { shape ->
                Rotation.entries.map { HoleTile(shape).rotate(it).flip() }.distinctBy { it.openSides }
            }.maxOfOrNull { digRank(view, action.position, it) } ?: Rank()
            best.then(if (view.tiles[action.position]?.shape == null) 1.0 else 0.0)
        } ?: CpuAction.Skip
    }

    private fun digRank(view: CpuObservation, position: Position, tile: HoleTile): Rank {
        val board = PublicBoard(view, position to tile)
        val positions = board.reachableIncludingCurrent()
        val bestMove = positions.maxOf { moveRank(view, board, it) }
        return bestMove.then(positions.size.toDouble())
    }

    private fun chooseMove(view: CpuObservation, board: PublicBoard): CpuAction {
        val player = requireNotNull(view.currentPlayer)
        val choices = buildList {
            if (CpuAction.Skip in view.legalActions) add(player.position to CpuAction.Skip)
            view.legalActions.filterIsInstance<CpuAction.Move>().forEach { add(it.position to it) }
        }
        return choices.maxByOrNull { moveRank(view, board, it.first) }?.second ?: CpuAction.Skip
    }

    private fun moveRank(view: CpuObservation, board: PublicBoard, position: Position): Rank {
        val player = requireNotNull(view.currentPlayer)
        val cost = board.turnCost(position)
        val atHome = position == player.nestPosition
        val surviving = player.health > cost
        if (player.carriedFood != null) {
            val wins = atHome && surviving && player.score + player.carriedFood.points >= view.winningScore
            return Rank(flag(surviving), flag(wins), flag(atHome))
                .plus(progressRank(board, position, listOf(player.nestPosition)))
                .then(-cost.toDouble())
        }

        val victim = view.players.firstOrNull {
            it.id != player.id && !it.isEliminated && it.nestPosition == position && it.position != position
        }
        val eligibleRobbery = victim != null && position == player.position && view.canRobAfterStaying
        val ownMeal = atHome && player.storedFoods.isNotEmpty() && player.score < view.winningScore &&
            (position != player.position || view.canEatStoredFoodAfterSkipping) && player.health <= NEST_EAT_HEALTH
        val food = view.foods[position].orEmpty().map { foodMetrics(view, board, it, position) }
        val survival = when {
            surviving -> 1.0
            ownMeal && player.health + player.storedFoods.first().recovery > cost -> 1.0
            eligibleRobbery && victim.storedFoods.any { player.health + it.recovery > cost } -> 1.0
            else -> food.maxOfOrNull { it.survivalChance } ?: 0.0
        }
        val winningFoodInNest = victim?.storedFoods?.any { player.score + it.points >= view.winningScore } == true
        val immediateWin = eligibleRobbery && winningFoodInNest && surviving
        val winningVisit = winningFoodInNest && player.health > cost + 1
        val tier = when {
            ownMeal -> 6.0
            eligibleRobbery -> 5.0
            winningVisit -> 4.5
            food.isNotEmpty() -> 4.0
            victim?.storedFoods?.isNotEmpty() == true -> 3.0
            else -> 1.0
        }
        val value = when {
            ownMeal -> effectiveRecovery(player, player.storedFoods.first()).toDouble()
            eligibleRobbery || winningVisit -> victim.storedFoods.maxOfOrNull {
                if (player.health <= LOW_HEALTH && !winningVisit) effectiveRecovery(player, it).toDouble()
                else it.points.toDouble()
            } ?: 0.0
            food.isNotEmpty() -> food.maxOf {
                if (player.health <= LOW_HEALTH) it.expectedHealing else it.expectedPoints
            }
            else -> 0.0
        }
        val goals = view.foods.filterValues { it.isNotEmpty() }.keys.toList().ifEmpty {
            view.players.filter { it.id != player.id && !it.isEliminated && it.position != it.nestPosition && it.storedFoods.isNotEmpty() }
                .map { it.nestPosition }.ifEmpty { listOf(player.nestPosition) }
        }
        return Rank(survival, flag(immediateWin), tier, value)
            .plus(progressRank(board, position, goals)).then(-cost.toDouble())
    }

    /** Favor a place from which the next dig approaches the goal, not merely the nearest dead end. */
    private fun progressRank(board: PublicBoard, position: Position, goals: List<Position>): Rank {
        val nextDigs = board.digTargetsFrom(position)
        return goals.maxOfOrNull { goal ->
            val distance = board.distance(position, goal)
            val frontier = nextDigs.minOfOrNull { board.distance(it, goal) } ?: (distance + 1)
            Rank(-frontier.toDouble(), -distance.toDouble())
        } ?: Rank()
    }

    private fun foodRank(view: CpuObservation, board: PublicBoard, index: Int): Rank {
        val player = requireNotNull(view.currentPlayer)
        val food = view.foods[player.position].orEmpty().getOrNull(index) ?: return Rank()
        val metrics = foodMetrics(view, board, food, player.position)
        return Rank(metrics.survivalChance,
            if (player.health <= LOW_HEALTH) metrics.expectedHealing else metrics.expectedPoints,
            metrics.captureChance)
    }

    private fun chooseFoodDecision(view: CpuObservation, board: PublicBoard): CpuAction {
        val player = requireNotNull(view.currentPlayer)
        val food = view.pendingFood
        if (food != null) {
            val cost = board.turnCost(player.position)
            val enoughPoints = player.score + food.points >= view.winningScore
            if (view.pendingFoodSource == FoodDecisionSource.ROBBERY && enoughPoints && player.health > cost) {
                return CpuAction.Carry
            }
            if (view.pendingFoodSource == FoodDecisionSource.CAPTURE && enoughPoints &&
                player.nestPosition in board.reachableIncludingCurrent() && player.health > cost + 1) {
                return CpuAction.Carry
            }
            return if (player.health <= LOW_HEALTH) CpuAction.Eat else CpuAction.Carry
        }
        val robbery = view.legalActions.filterIsInstance<CpuAction.Rob>()
        if (robbery.isNotEmpty()) {
            val victim = view.players.first { it.id != player.id && it.nestPosition == player.position }
            return robbery.maxByOrNull { action ->
                val target = victim.storedFoods[action.foodIndex]
                val win = player.health > board.turnCost(player.position) && player.score + target.points >= view.winningScore
                Rank(flag(win), if (player.health <= LOW_HEALTH) effectiveRecovery(player, target).toDouble() else target.points.toDouble())
            }!!
        }
        if (player.score >= view.winningScore && player.health > board.turnCost(player.position)) {
            return CpuAction.Skip
        }
        return if (CpuAction.Eat in view.legalActions && player.health <= NEST_EAT_HEALTH) CpuAction.Eat else CpuAction.Skip
    }

    private fun foodMetrics(view: CpuObservation, board: PublicBoard, food: CpuFood, position: Position): FoodMetrics {
        val player = requireNotNull(view.currentPlayer)
        // This fixed prior never uses the contents of the actual stock or any unrevealed card.
        val distribution = food.type?.let { listOf(it to 1.0) } ?: run {
            val types = FoodType.entries.filter { view.players.size == 4 || it != FoodType.FROG }
            val count = types.sumOf { it.cardCount }.toDouble()
            types.map { it to it.cardCount / count }
        }
        var chance = 0.0
        var healing = 0.0
        var points = 0.0
        var survival = 0.0
        for ((type, weight) in distribution) {
            val capture = board.captureChance(type, position)
            chance += weight * capture
            healing += weight * capture * effectiveRecovery(player, type)
            points += weight * capture * type.points
            survival += weight * when {
                player.health > board.turnCost(position) -> 1.0
                player.health + type.recovery > board.turnCost(position) -> capture
                else -> 0.0
            }
        }
        return FoodMetrics(chance, healing, points, survival)
    }

    private fun effectiveRecovery(player: CpuPlayer, food: FoodType): Int =
        minOf(Player.MAX_HEALTH - player.health, food.recovery)

    private fun flag(value: Boolean) = if (value) 1.0 else 0.0

    private data class FoodMetrics(
        val captureChance: Double, val expectedHealing: Double, val expectedPoints: Double, val survivalChance: Double,
    )

    /** Lexicographic priorities avoid arbitrary mixtures of score, health and geometric distance. */
    private class Rank(private vararg val values: Double) : Comparable<Rank> {
        fun then(value: Double) = Rank(*values, value)
        fun plus(other: Rank) = Rank(*values, *other.values)
        override fun compareTo(other: Rank): Int {
            for (index in 0 until minOf(values.size, other.values.size)) {
                val compared = values[index].compareTo(other.values[index])
                if (compared != 0) return compared
            }
            return values.size.compareTo(other.values.size)
        }
    }

    companion object {
        private const val LOW_HEALTH = 4
        private const val NEST_EAT_HEALTH = 2
    }
}

/** A disposable board for evaluation, containing no hidden values and no live model objects. */
internal class PublicBoard(
    private val view: CpuObservation,
    replacement: Pair<Position, HoleTile>? = null,
) {
    private val player = requireNotNull(view.currentPlayer)
    private val board = Board()
    private val tiles = view.tiles.toMutableMap().apply {
        replacement?.let { (position, tile) -> put(position, CpuTile(tile.shape, tile.openSides.toSet())) }
    }
    private val state = BoardState(board).apply {
        tiles.forEach { (position, tile) ->
            tile.shape?.let { placeTile(position, HoleTile(it, tile.openSides.toSet(), isFaceDown = false)) }
        }
    }
    private val otherPlayers = view.players.filter { it.id != player.id && !it.isEliminated }
    private val occupied = otherPlayers.map { it.position }.filter { it != player.nestPosition }.toSet()
    private val blocked = otherPlayers.filter { it.position == it.nestPosition }.map { it.nestPosition }.toSet()
    private val distances = mutableMapOf<Position, Map<Position, Int>>()
    private val returnRoutes = mutableMapOf<Position, ReturnRoute?>()
    private val opponentReachability = mutableMapOf<Pair<Int, Position>, Set<Position>>()
    private val possibleDigReachability = mutableMapOf<Int, Set<Position>>()

    /** Exact public movement after the evaluated move, including immediate nest eviction. */
    fun projectedPosition(actor: CpuPlayer, movingPlayerPosition: Position): Position = when {
        actor.id == player.id -> movingPlayerPosition
        movingPlayerPosition == player.nestPosition && actor.position == player.nestPosition ->
            Board.NEST_EVICTION_DESTINATIONS.getValue(player.nestPosition)
        else -> actor.position
    }

    fun reachableFor(actor: CpuPlayer, movingPlayerPosition: Position = player.position): Set<Position> =
        opponentReachability.getOrPut(actor.id to movingPlayerPosition) {
            val others = view.players.filter { it.id != actor.id && !it.isEliminated }
            val occupied = others.map { projectedPosition(it, movingPlayerPosition) }.filter { it != actor.nestPosition }.toSet()
            val blocked = others.filter { projectedPosition(it, movingPlayerPosition) == it.nestPosition }.map { it.nestPosition }.toSet()
            val start = projectedPosition(actor, movingPlayerPosition)
            setOf(start) + MovementEngine(board).findReachablePositions(start, state, occupied, blocked)
        }

    /** Conservative possibility only: a CROSS could open an adjacent dig; no hidden draw is inspected. */
    fun possiblyReachableAfterDig(actor: CpuPlayer): Set<Position> = possibleDigReachability.getOrPut(actor.id) {
        val others = view.players.filter { it.id != actor.id && !it.isEliminated }
        val occupied = others.map { it.position }.filter { it != actor.nestPosition }.toSet()
        val blocked = others.filter { it.position == it.nestPosition }.map { it.nestPosition }.toSet()
        val type = board.getCell(actor.position)?.type
        val directions = if (type == CellType.NEST || type == CellType.GROUND) Direction.entries.toSet()
            else tiles[actor.position]?.openSides.orEmpty()
        buildSet {
            addAll(reachableFor(actor))
            for (dig in board.getValidNeighbors(actor.position)) {
                if (dig !in tiles || others.any { it.position == dig } || directionBetween(actor.position, dig) !in directions) continue
                val possible = BoardState(board).apply {
                    tiles.forEach { (position, tile) ->
                        tile.shape?.let { placeTile(position, HoleTile(it, tile.openSides, isFaceDown = false)) }
                    }
                    placeTile(dig, HoleTile(TileShape.CROSS).flip())
                }
                addAll(MovementEngine(board).findReachablePositions(actor.position, possible, occupied, blocked))
            }
        }
    }

    data class ReturnRoute(val turns: Int, val healthCost: Int)

    /** Stops use the real movement rules. Costs exclude the turn ending at [from]. */
    fun returnRoute(from: Position): ReturnRoute? {
        if (from in returnRoutes) return returnRoutes[from]
        return findReturnRoute(from).also { returnRoutes[from] = it }
    }

    private fun findReturnRoute(from: Position): ReturnRoute? {
        val best = mutableMapOf(from to ReturnRoute(0, 0))
        val pending = mutableSetOf(from)
        while (pending.isNotEmpty()) {
            val position = pending.minWith(compareBy<Position> { best.getValue(it).healthCost }.thenBy { best.getValue(it).turns })
            pending.remove(position)
            val route = best.getValue(position)
            if (position == player.nestPosition) return route
            for (next in MovementEngine(board).findReachablePositions(position, state, occupied, blocked)) {
                val candidate = ReturnRoute(route.turns + 1, route.healthCost + turnCost(next))
                val old = best[next]
                if (old == null || candidate.healthCost < old.healthCost ||
                    candidate.healthCost == old.healthCost && candidate.turns < old.turns) {
                    best[next] = candidate
                    pending.add(next)
                }
            }
        }
        return null
    }

    fun reachableIncludingCurrent(): List<Position> = listOf(player.position) +
        MovementEngine(board).findReachablePositions(player.position, state, occupied, blocked)
            .sortedWith(compareBy({ it.row }, { it.col }))

    fun turnCost(position: Position): Int = if (board.getCell(position)?.type == CellType.GROUND) 2 else 1

    fun digTargetsFrom(position: Position): List<Position> {
        val type = board.getCell(position)?.type
        val directions = if (type == CellType.NEST || type == CellType.GROUND) Direction.entries.toSet()
        else tiles[position]?.openSides.orEmpty()
        return board.getValidNeighbors(position).filter { neighbor ->
            neighbor in tiles && otherPlayers.none { it.position == neighbor } &&
                directionBetween(position, neighbor) in directions
        }
    }

    /** Terrain distance is a progress hint, not an estimate of remaining turns. */
    fun distance(from: Position, goal: Position): Int = distances.getOrPut(goal) {
        val result = mutableMapOf(goal to 0)
        val queue = ArrayDeque<Position>().apply { add(goal) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentType = board.getCell(current)?.type
            if (current != goal && currentType == CellType.NEST) continue
            for (next in board.getValidNeighbors(current)) {
                if (next in result || next in blocked) continue
                val nextType = board.getCell(next)?.type
                if ((currentType == CellType.NEST && nextType == CellType.GROUND) ||
                    (currentType == CellType.GROUND && nextType == CellType.NEST)) continue
                result[next] = result.getValue(current) + 1
                queue.add(next)
            }
        }
        result
    }[from] ?: (Board.COLS * Board.ROWS + 1)

    fun captureChance(type: FoodType, position: Position): Double {
        val escape = ESCAPE_MAPS.getValue(type)
        return (1..6).count { roll ->
            val direction = escape[roll]
            if (direction == null) true else {
                board.getCell(direction.applyTo(position))?.type.let {
                    it == null || it == CellType.INVALID || it == CellType.NEST
                }
            }
        } / 6.0
    }

    companion object {
        private val ESCAPE_MAPS = FoodType.entries.associateWith { FoodCard.createDummyCards(it).first().escapeMap }
    }
}
