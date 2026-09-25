package com.moguru.game.android

import com.moguru.game.engine.*
import com.moguru.game.model.*
import com.moguru.game.persistence.*
import com.moguru.game.presenter.*
import org.json.JSONArray
import org.json.JSONObject

/** Explicit v1 schema. Enum names are persisted, never ordinal positions. */
internal object GameSaveCodec {
    private const val VERSION = 1

    fun encode(save: SavedGame): String = obj(
        "formatVersion" to VERSION, "revision" to save.revision, "savedAtMillis" to save.savedAtMillis,
        "selectedMovePosition" to save.selectedMovePosition?.let(::position),
        "boardPiecesTransparent" to save.boardPiecesTransparent, "lastMessage" to save.lastMessage,
        "game" to game(save.game),
    ).toString()

    fun decode(text: String): SavedGame {
        try {
            val root = JSONObject(text)
            if (root.int("formatVersion") != VERSION) throw InvalidGameSaveException(GameSaveLoadIssue.UNSUPPORTED)
            val snapshot = readGame(root.getJSONObject("game"))
            val restored = MoguraGameController.fromSnapshot(snapshot)
            val selectedMove = root.optionalObject("selectedMovePosition")?.let(::readPosition)
            if (selectedMove != null) {
                require(snapshot.engine.currentPhase == TurnPhase.MOVE)
                require(selectedMove in restored.moveTargets())
            }
            return SavedGame(
                snapshot, root.long("revision").also { require(it in 0 until Long.MAX_VALUE) },
                root.long("savedAtMillis").also { require(it >= 0) }, selectedMove,
                root.bool("boardPiecesTransparent"), root.optionalString("lastMessage"),
            )
        } catch (e: InvalidGameSaveException) {
            throw e
        } catch (e: Exception) {
            throw InvalidGameSaveException(GameSaveLoadIssue.CORRUPT, e)
        }
    }

    private fun game(s: GameSnapshot) = obj(
        "engine" to engine(s.engine),
        "lastCaptureResult" to s.lastCaptureResult?.let(::captureResult),
        "lastDiceRoll" to s.lastDiceRoll,
        "captureOutcome" to s.captureOutcome?.let {
            obj("kind" to it.kind.name, "diceRoll" to it.diceRoll, "message" to it.message)
        },
        "captureAnimationId" to s.captureAnimationId, "eatAnimationId" to s.eatAnimationId,
        "turnConsumptionAnimationId" to s.turnConsumptionAnimationId,
        "pendingDecision" to s.pendingDecision?.let {
            obj("food" to food(it.food), "source" to it.source.name, "victimPlayerId" to it.victimPlayerId)
        },
        "pendingCaptureRoll" to s.pendingCaptureRoll?.let {
            obj("position" to position(it.position), "foodIndex" to it.foodIndex, "food" to food(it.food), "roll" to it.roll)
        },
        "pendingDigPlacement" to s.pendingDigPlacement?.let {
            obj("position" to position(it.position), "revealedTile" to it.revealedTile?.let(::tile),
                "drawnTile" to it.drawnTile?.let(::tile))
        },
        "pendingDigDrawnTile" to s.pendingDigDrawnTile?.let(::tile),
        "pendingDigRotation" to s.pendingDigRotation?.name,
        "pendingDigTileChoice" to s.pendingDigTileChoice?.name,
        "pendingDigRotations" to array(s.pendingDigRotations.entries) {
            obj("choice" to it.key.name, "rotation" to it.value.name)
        },
        "selectedCaptureFoodIndex" to s.selectedCaptureFoodIndex,
        "selectedRobberyFoodIndex" to s.selectedRobberyFoodIndex,
        "robberyVisits" to array(s.robberyVisits.entries) {
            obj("playerId" to it.key, "nestPosition" to position(it.value.nestPosition), "eligible" to it.value.eligible)
        },
        "ownNestEatEligiblePlayers" to JSONArray(s.ownNestEatEligiblePlayers.toList()),
        "logs" to JSONArray(s.logs),
    )

    private fun readGame(j: JSONObject) = GameSnapshot(
        engine = readEngine(j.getJSONObject("engine")),
        lastCaptureResult = j.optionalObject("lastCaptureResult")?.let(::readCaptureResult),
        lastDiceRoll = j.optionalInt("lastDiceRoll"),
        captureOutcome = j.optionalObject("captureOutcome")?.let {
            CaptureOutcomeDisplay(it.enum("kind"), it.optionalInt("diceRoll"), it.string("message"))
        },
        captureAnimationId = j.long("captureAnimationId"), eatAnimationId = j.long("eatAnimationId"),
        turnConsumptionAnimationId = j.long("turnConsumptionAnimationId"),
        pendingDecision = j.optionalObject("pendingDecision")?.let {
            FoodDecisionSnapshot(readFood(it.getJSONObject("food")), it.enum("source"), it.optionalInt("victimPlayerId"))
        },
        pendingCaptureRoll = j.optionalObject("pendingCaptureRoll")?.let {
            PendingCaptureRoll(readPosition(it.getJSONObject("position")), it.int("foodIndex"),
                readFood(it.getJSONObject("food")), it.optionalInt("roll"))
        },
        pendingDigPlacement = j.optionalObject("pendingDigPlacement")?.let {
            PendingDigPlacement(readPosition(it.getJSONObject("position")),
                it.optionalObject("revealedTile")?.let(::readTile), it.optionalObject("drawnTile")?.let(::readTile))
        },
        pendingDigDrawnTile = j.optionalObject("pendingDigDrawnTile")?.let(::readTile),
        pendingDigRotation = j.optionalString("pendingDigRotation")?.let { enumValueOf<Rotation>(it) },
        pendingDigTileChoice = j.optionalString("pendingDigTileChoice")?.let { enumValueOf<DigTileChoice>(it) },
        pendingDigRotations = j.objects("pendingDigRotations") {
            it.enum<DigTileChoice>("choice") to it.enum<Rotation>("rotation")
        }.uniqueMap(),
        selectedCaptureFoodIndex = j.optionalInt("selectedCaptureFoodIndex"),
        selectedRobberyFoodIndex = j.optionalInt("selectedRobberyFoodIndex"),
        robberyVisits = j.objects("robberyVisits") {
            it.int("playerId") to RobberyVisitSnapshot(readPosition(it.getJSONObject("nestPosition")), it.bool("eligible"))
        }.uniqueMap(),
        ownNestEatEligiblePlayers = j.getJSONArray("ownNestEatEligiblePlayers").let { a ->
            (0 until a.length()).map { index ->
                val value = a.get(index)
                require(value is Int || value is Long)
                (value as Number).toLong().also { require(it in 0..3) }.toInt()
            }.also {
                require(it.distinct().size == it.size)
            }.toSet()
        },
        logs = j.getJSONArray("logs").let { a -> (0 until a.length()).map { a.get(it) as String } },
    )

    private fun engine(s: EngineSnapshot) = obj(
        "gameState" to s.gameState.name, "currentPhase" to s.currentPhase.name,
        "currentPlayerIndex" to s.currentPlayerIndex,
        "players" to array(s.players) {
            obj("id" to it.id, "name" to it.name, "nestPosition" to position(it.nestPosition),
                "position" to position(it.position), "health" to it.health,
                "carriedFood" to it.carriedFood?.let(::food), "storedFoods" to array(it.storedFoods, ::food))
        },
        "tiles" to array(s.tiles.entries) { obj("position" to position(it.key), "tile" to tile(it.value)) },
        "foods" to array(s.foods.entries) { obj("position" to position(it.key), "cards" to array(it.value, ::food)) },
        "tileDrawPile" to array(s.tileDrawPile, ::tile), "tileDiscardPile" to array(s.tileDiscardPile, ::tile),
        "foodStock" to array(s.foodStock, ::food), "foodDiscard" to array(s.foodDiscard, ::food),
        "lastCaptureSuccess" to s.lastCaptureSuccess,
    )

    private fun readEngine(j: JSONObject) = EngineSnapshot(
        j.enum("gameState"), j.enum("currentPhase"), j.int("currentPlayerIndex"),
        j.objects("players") {
            PlayerSnapshot(it.int("id"), it.string("name"), readPosition(it.getJSONObject("nestPosition")),
                readPosition(it.getJSONObject("position")), it.int("health"),
                it.optionalObject("carriedFood")?.let(::readFood), it.objects("storedFoods", ::readFood))
        },
        j.objects("tiles") { readPosition(it.getJSONObject("position")) to readTile(it.getJSONObject("tile")) }.uniqueMap(),
        j.objects("foods") { readPosition(it.getJSONObject("position")) to it.objects("cards", ::readFood) }.uniqueMap(),
        j.objects("tileDrawPile", ::readTile), j.objects("tileDiscardPile", ::readTile),
        j.objects("foodStock", ::readFood), j.objects("foodDiscard", ::readFood), j.bool("lastCaptureSuccess"),
    )

    private fun position(p: Position) = obj("col" to p.col, "row" to p.row)
    private fun readPosition(j: JSONObject) = Position(j.int("col"), j.int("row"))
    private fun tile(t: HoleTile) = obj(
        "shape" to t.shape.name, "openSides" to JSONArray(t.openSides.map { it.name }), "isFaceDown" to t.isFaceDown,
    )
    private fun readTile(j: JSONObject): HoleTile {
        val sides = j.getJSONArray("openSides").let { a ->
            (0 until a.length()).map { enumValueOf<Direction>(a.get(it) as String) }
        }
        require(sides.distinct().size == sides.size)
        return HoleTile(j.enum("shape"), sides.toSet(), j.bool("isFaceDown"))
    }
    private fun food(f: FoodCard) = obj(
        "type" to f.type.name, "isFaceDown" to f.isFaceDown,
        "escapeMap" to array(f.escapeMap.entries) { obj("roll" to it.key, "direction" to it.value.name) },
    )
    private fun readFood(j: JSONObject) = FoodCard(j.enum("type"),
        j.objects("escapeMap") { it.int("roll") to it.enum<EscapeDirection>("direction") }.uniqueMap(), j.bool("isFaceDown"))
    private fun captureResult(r: CaptureResult): JSONObject = when (r) {
        is CaptureResult.Success -> obj("kind" to "SUCCESS", "diceRoll" to r.diceRoll)
        is CaptureResult.Escaped -> obj("kind" to "ESCAPED", "diceRoll" to r.diceRoll,
            "direction" to r.direction.name, "to" to r.to?.let(::position))
    }
    private fun readCaptureResult(j: JSONObject): CaptureResult = when (j.string("kind")) {
        "SUCCESS" -> CaptureResult.Success(j.optionalInt("diceRoll"))
        "ESCAPED" -> CaptureResult.Escaped(j.enum("direction"), j.int("diceRoll"), j.optionalObject("to")?.let(::readPosition))
        else -> error("Unknown capture result")
    }
    private fun obj(vararg values: Pair<String, Any?>) = JSONObject().apply {
        values.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    }
    private fun <T> array(values: Iterable<T>, encode: (T) -> JSONObject) =
        JSONArray().apply { values.forEach { put(encode(it)) } }
    private fun <T> JSONObject.objects(key: String, decode: (JSONObject) -> T): List<T> =
        getJSONArray(key).let { a -> (0 until a.length()).map { decode(a.getJSONObject(it)) } }
    private fun <K, V> List<Pair<K, V>>.uniqueMap(): Map<K, V> = toMap().also { require(it.size == size) }
    private fun JSONObject.string(key: String): String = get(key) as String
    private fun JSONObject.bool(key: String): Boolean = get(key) as Boolean
    private fun JSONObject.long(key: String): Long = (get(key) as Number).let {
        require(it is Int || it is Long)
        it.toLong()
    }
    private fun JSONObject.int(key: String): Int = long(key).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
    private fun JSONObject.optionalObject(key: String): JSONObject? = get(key).let { if (it === JSONObject.NULL) null else it as JSONObject }
    private fun JSONObject.optionalString(key: String): String? = get(key).let { if (it === JSONObject.NULL) null else it as String }
    private fun JSONObject.optionalInt(key: String): Int? = if (get(key) === JSONObject.NULL) null else int(key)
    private inline fun <reified T : Enum<T>> JSONObject.enum(key: String): T = enumValueOf(string(key))
}
