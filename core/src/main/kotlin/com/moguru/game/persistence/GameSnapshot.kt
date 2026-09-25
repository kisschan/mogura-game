package com.moguru.game.persistence

import com.moguru.game.engine.CaptureResult
import com.moguru.game.engine.GameState
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.*
import com.moguru.game.presenter.*

/** Detached values only; no engines, random generators, or Android presentation frames. */
data class PlayerSnapshot(
    val id: Int, val name: String, val nestPosition: Position, val position: Position,
    val health: Int, val carriedFood: FoodCard?, val storedFoods: List<FoodCard>,
)

data class EngineSnapshot(
    val gameState: GameState, val currentPhase: TurnPhase, val currentPlayerIndex: Int,
    val players: List<PlayerSnapshot>, val tiles: Map<Position, HoleTile>,
    val foods: Map<Position, List<FoodCard>>,
    val tileDrawPile: List<HoleTile>, val tileDiscardPile: List<HoleTile>,
    val foodStock: List<FoodCard>, val foodDiscard: List<FoodCard>,
    val lastCaptureSuccess: Boolean,
)

data class FoodDecisionSnapshot(val food: FoodCard, val source: FoodDecisionSource, val victimPlayerId: Int?)
data class RobberyVisitSnapshot(val nestPosition: Position, val eligible: Boolean)

data class GameSnapshot(
    val engine: EngineSnapshot,
    val lastCaptureResult: CaptureResult?, val lastDiceRoll: Int?,
    val captureOutcome: CaptureOutcomeDisplay?,
    val captureAnimationId: Long, val eatAnimationId: Long, val turnConsumptionAnimationId: Long,
    val pendingDecision: FoodDecisionSnapshot?, val pendingCaptureRoll: PendingCaptureRoll?,
    val pendingDigPlacement: PendingDigPlacement?, val pendingDigDrawnTile: HoleTile?,
    val pendingDigRotation: Rotation?, val pendingDigTileChoice: DigTileChoice?,
    val pendingDigRotations: Map<DigTileChoice, Rotation>,
    val selectedCaptureFoodIndex: Int?, val selectedRobberyFoodIndex: Int?,
    val robberyVisits: Map<Int, RobberyVisitSnapshot>, val ownNestEatEligiblePlayers: Set<Int>,
    val logs: List<String>,
)

/** Copies collection-backed model values, so later game mutations cannot change a save. */
fun FoodCard.detached(): FoodCard = copy(escapeMap = escapeMap.toMap())
fun HoleTile.detached(): HoleTile = copy(openSides = openSides.toSet())

/** Validate before creating any live game. Never repair a damaged save by dropping state. */
fun GameSnapshot.validate() {
    val board = Board()
    fun validPosition(position: Position) =
        board.getCell(position)?.type?.let { it != CellType.INVALID } == true
    fun food(card: FoodCard) {
        require(card.escapeMap == FoodCard.createDummyCards(card.type).first().escapeMap)
        require(engine.players.size == 4 || card.type != FoodType.FROG)
    }
    fun tile(value: HoleTile) {
        require(Rotation.entries.any { HoleTile(value.shape).rotate(it).openSides == value.openSides })
    }
    val players = engine.players
    require(players.size in 2..4)
    require(players.map { it.id }.distinct().size == players.size)
    require(players.map { it.nestPosition }.distinct().size == players.size)
    require(engine.currentPlayerIndex in players.indices)
    require(engine.gameState != GameState.SETUP)
    players.forEach {
        require(it.id in 0..3 && it.name.isNotBlank())
        require(it.nestPosition in Board.NEST_POSITIONS && validPosition(it.position))
        require(it.health in 0..Player.MAX_HEALTH)
        it.carriedFood?.let(::food)
        it.storedFoods.forEach(::food)
    }
    val ids = players.map { it.id }.toSet()
    engine.tiles.forEach { (position, value) ->
        require(board.getCell(position)?.type in setOf(CellType.UNDERGROUND, CellType.HOT_ZONE))
        tile(value)
    }
    engine.foods.forEach { (position, cards) ->
        require(validPosition(position) && cards.isNotEmpty())
        cards.forEach(::food)
    }
    (engine.tileDrawPile + engine.tileDiscardPile).forEach(::tile)
    (engine.foodStock + engine.foodDiscard).forEach(::food)
    require(lastDiceRoll == null || lastDiceRoll in 1..6)
    when (val result = lastCaptureResult) {
        is CaptureResult.Success -> require(result.diceRoll == null || result.diceRoll in 1..6)
        is CaptureResult.Escaped -> {
            require(result.diceRoll in 1..6)
            require(result.to == null || validPosition(result.to))
        }
        null -> Unit
    }
    captureOutcome?.let { require(it.diceRoll == null || it.diceRoll in 1..6) }
    require(listOf(captureAnimationId, eatAnimationId, turnConsumptionAnimationId).all { it >= 0 })
    require(ownNestEatEligiblePlayers.all { it in ids })
    robberyVisits.forEach { (id, visit) ->
        require(id in ids && players.any { it.id != id && it.nestPosition == visit.nestPosition })
    }
    selectedCaptureFoodIndex?.let {
        require(it >= 0 && it < engine.foods[players[engine.currentPlayerIndex].position].orEmpty().size)
    }
    selectedRobberyFoodIndex?.let {
        val owner = players.firstOrNull { owner -> owner.nestPosition == players[engine.currentPlayerIndex].position }
        require(owner != null && it in owner.storedFoods.indices)
    }
    pendingDecision?.let {
        require(engine.currentPhase == TurnPhase.DECIDE)
        require(pendingCaptureRoll == null)
        require(!it.food.isFaceDown && players[engine.currentPlayerIndex].carriedFood == null)
        food(it.food)
        require(if (it.source == FoodDecisionSource.ROBBERY)
            it.victimPlayerId in ids && it.victimPlayerId != players[engine.currentPlayerIndex].id
        else it.victimPlayerId == null)
    }
    pendingCaptureRoll?.let {
        require(engine.currentPhase == TurnPhase.CAPTURE)
        require(it.position == players[engine.currentPlayerIndex].position)
        require(engine.foods[it.position]?.getOrNull(it.foodIndex) == it.food)
        require(it.roll == null || it.roll in 1..6)
        require(it.roll == null || it.roll == lastDiceRoll)
        food(it.food)
    }
    pendingDigDrawnTile?.let {
        require(engine.currentPhase == TurnPhase.DIG && pendingDigPlacement == null)
        tile(it)
    }
    pendingDigPlacement?.let {
        require(engine.currentPhase == TurnPhase.DIG && pendingDigDrawnTile == null)
        require(board.getCell(it.position)?.type in setOf(CellType.UNDERGROUND, CellType.HOT_ZONE))
        require(it.revealedTile != null || it.drawnTile != null)
        it.revealedTile?.let(::tile)
        it.drawnTile?.let(::tile)
        val choice = requireNotNull(pendingDigTileChoice)
        val selected = requireNotNull(if (choice == DigTileChoice.REVEALED) it.revealedTile else it.drawnTile)
        val rotation = requireNotNull(pendingDigRotation)
        require(pendingDigRotations[choice] == rotation)
        require(engine.tiles[it.position] == selected.rotate(rotation).flip())
        require(pendingDigRotations.keys.all { key ->
            if (key == DigTileChoice.REVEALED) it.revealedTile != null else it.drawnTile != null
        })
    }
    if (pendingDigPlacement == null) {
        require(pendingDigRotation == null && pendingDigTileChoice == null && pendingDigRotations.isEmpty())
    }
    if (engine.gameState == GameState.FINISHED) {
        require(pendingDecision == null && pendingCaptureRoll == null && pendingDigPlacement == null)
    }
}
