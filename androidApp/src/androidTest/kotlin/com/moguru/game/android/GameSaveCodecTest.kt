package com.moguru.game.android

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.*
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class GameSaveCodecTest {
    private fun saved(): SavedGame {
        val game = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
        game.startNewGame(4)
        game.digAt(game.digTargets().first(), Rotation.DEG_0)
        game.setPendingDigRotation(Rotation.DEG_270)
        return SavedGame(game.exportSnapshot(), 42, 1_000, boardPiecesTransparent = true,
            lastMessage = "タイルを選択しました。\n「回転」")
    }

    @Test
    fun roundTripKeepsOrderedPilesPendingDigAndJapaneseHistory() {
        val expected = saved().let { it.copy(game = it.game.copy(logs = List(200) { n -> "記録 " + n })) }
        assertEquals(expected, GameSaveCodec.decode(GameSaveCodec.encode(expected)))
    }

    @Test
    fun captureCheckpointsKeepPendingRollDecisionAndEscapeResult() {
        val source = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
            .apply { startNewGame(2) }.exportSnapshot()
        val position = Position(2, 2)
        val ready = source.copy(
            engine = source.engine.copy(currentPhase = TurnPhase.CAPTURE,
                tileDiscardPile = source.engine.tileDiscardPile + listOfNotNull(source.pendingDigDrawnTile),
                players = source.engine.players.mapIndexed { i, p -> if (i == 0) p.copy(position = position) else p },
                foods = mapOf(position to listOf(FoodCard.createDummyCards(FoodType.EARTHWORM).first())),
                foodStock = source.engine.foodStock.drop(1) + source.engine.foods.values.flatten()),
            pendingDigDrawnTile = null,
        )
        for (roll in listOf(1, 6)) {
            val controller = MoguraGameController.fromSnapshot(ready, FixedDiceRoller(listOf(roll)), FixedShuffler())
            val actions = listOf(controller::captureCurrentPosition, controller::rollCaptureDice, controller::resolveCaptureRoll)
            for (action in actions) {
                assertTrue(action().success)
                val expected = SavedGame(controller.exportSnapshot(), 2, 1_000)
                assertEquals(expected, GameSaveCodec.decode(GameSaveCodec.encode(expected)))
            }
        }
    }

    @Test
    fun futureVersionIsDistinguishedFromCorruption() {
        val future = JSONObject(GameSaveCodec.encode(saved())).put("formatVersion", 2).toString()
        try { GameSaveCodec.decode(future); fail("accepted future version") }
        catch (e: InvalidGameSaveException) { assertEquals(GameSaveLoadIssue.UNSUPPORTED, e.issue) }
    }

    @Test
    fun malformedAndIncompleteSavesAreRejectedWithoutDefaults() {
        val missing = JSONObject(GameSaveCodec.encode(saved())).apply { remove("game") }.toString()
        val invalidPlayer = JSONObject(GameSaveCodec.encode(saved())).apply {
            getJSONObject("game").getJSONObject("engine").put("currentPlayerIndex", 99)
        }.toString()
        val invalidPhase = JSONObject(GameSaveCodec.encode(saved())).apply {
            getJSONObject("game").getJSONObject("engine").put("currentPhase", "UNKNOWN")
        }.toString()
        val invalidEscapes = listOf(
            JSONArray(),
            JSONArray().put(JSONObject().put("roll", 1).put("direction", "LEFT"))
                .put(JSONObject().put("roll", 2).put("direction", "BOTTOM")),
        ).map { escapeMap ->
            JSONObject(GameSaveCodec.encode(saved())).apply {
                getJSONObject("game").getJSONObject("engine").getJSONArray("foodStock").getJSONObject(0)
                    .put("type", "EARTHWORM").put("escapeMap", escapeMap)
            }.toString()
        }
        val extraFood = JSONObject(GameSaveCodec.encode(saved())).apply {
            val stock = getJSONObject("game").getJSONObject("engine").getJSONArray("foodStock")
            stock.put(stock.getJSONObject(0))
        }.toString()
        val missingTile = JSONObject(GameSaveCodec.encode(saved())).apply {
            val engine = getJSONObject("game").getJSONObject("engine")
            val tiles = engine.getJSONArray("tiles")
            engine.put("tiles", JSONArray().apply { for (index in 0 until tiles.length() - 1) put(tiles.get(index)) })
        }.toString()
        val extraTile = JSONObject(GameSaveCodec.encode(saved())).apply {
            val pile = getJSONObject("game").getJSONObject("engine").getJSONArray("tileDrawPile")
            pile.put(pile.getJSONObject(0))
        }.toString()
        val nestFood = JSONObject(GameSaveCodec.encode(saved())).apply {
            val engine = getJSONObject("game").getJSONObject("engine")
            engine.getJSONArray("foods").getJSONObject(0)
                .put("position", engine.getJSONArray("players").getJSONObject(0).getJSONObject("nestPosition"))
        }.toString()
        val opening = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler()).apply { startNewGame(2) }
        val unpreparedDig = JSONObject(GameSaveCodec.encode(SavedGame(opening.exportSnapshot(), 1, 1))).apply {
            val game = getJSONObject("game")
            game.getJSONObject("engine").getJSONArray("tileDiscardPile").put(game.getJSONObject("pendingDigDrawnTile"))
            game.put("pendingDigDrawnTile", JSONObject.NULL)
        }.toString()
        for (text in listOf("{", missing, invalidPlayer, invalidPhase, extraFood, missingTile, extraTile, nestFood, unpreparedDig) + invalidEscapes) {
            try { GameSaveCodec.decode(text); fail("accepted corrupt save") }
            catch (e: InvalidGameSaveException) { assertEquals(GameSaveLoadIssue.CORRUPT, e.issue) }
        }
    }
}
