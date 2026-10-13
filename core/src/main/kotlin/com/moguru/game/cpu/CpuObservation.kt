package com.moguru.game.cpu

import com.moguru.game.engine.GameState
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.*
import com.moguru.game.presenter.DigTileChoice
import com.moguru.game.presenter.FoodDecisionSource

/** A hidden tile has a position, but no shape or openings. */
data class CpuTile(val shape: TileShape?, val openSides: Set<Direction> = emptySet())

/** A hidden food retains its stack index, but never its type. */
data class CpuFood(val type: FoodType?)

data class CpuPlayer(
    val id: Int,
    val position: Position,
    val nestPosition: Position,
    val health: Int,
    val carriedFood: FoodType?,
    val storedFoods: List<FoodType>,
) {
    val score: Int get() = storedFoods.sumOf { it.points }
    val isEliminated: Boolean get() = health <= 0
}

data class CpuDigPlacement(
    val position: Position,
    val candidates: Map<DigTileChoice, TileShape>,
    val selectedChoice: DigTileChoice,
    val selectedRotation: Rotation,
)

/** Detached public information only: no engine, save, deck order, or unrevealed card values. */
data class CpuObservation(
    val gameState: GameState,
    val phase: TurnPhase?,
    val currentPlayerId: Int?,
    val players: List<CpuPlayer>,
    val tiles: Map<Position, CpuTile>,
    val foods: Map<Position, List<CpuFood>>,
    val drawnTile: TileShape?,
    val pendingDig: CpuDigPlacement?,
    val pendingFood: FoodType?,
    val pendingFoodSource: FoodDecisionSource?,
    val waitingForCapture: Boolean,
    val canRobAfterStaying: Boolean,
    val canEatStoredFoodAfterSkipping: Boolean,
    val legalActions: List<CpuAction>,
) {
    val currentPlayer: CpuPlayer? get() = players.firstOrNull { it.id == currentPlayerId }
    val winningScore: Int get() = if (players.size == 4) 5 else 4
}
