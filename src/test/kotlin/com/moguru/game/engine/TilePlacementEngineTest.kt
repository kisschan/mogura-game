package com.moguru.game.engine

import com.moguru.game.model.Board
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Position
import com.moguru.game.model.TileShape
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TilePlacementEngineTest {

    private val board = Board()
    private val shuffler = FixedShuffler()

    @Test
    fun `山札から1枚引ける`() {
        val engine = TilePlacementEngine(shuffler)
        engine.drawPile = HoleTile.createFullSet().toMutableList()

        val drawn = engine.drawFromPile()
        assertNotNull(drawn)
        assertEquals(25, engine.drawPile.size)
    }

    @Test
    fun `山札が空の時は捨て札を再構築する`() {
        val engine = TilePlacementEngine(shuffler)
        engine.discardPile = mutableListOf(
            HoleTile(TileShape.STRAIGHT),
            HoleTile(TileShape.L_SHAPE),
        )

        val drawn = engine.drawFromPile()
        assertNotNull(drawn)
        assertEquals(1, engine.drawPile.size)
        assertTrue(engine.discardPile.isEmpty())
    }

    @Test
    fun `山札も捨て札も空の時はnullを返す`() {
        val engine = TilePlacementEngine(shuffler)
        val drawn = engine.drawFromPile()
        assertNull(drawn)
    }

    @Test
    fun `隣接する裏向きタイルを取得できる`() {
        val state = BoardState(board)
        val engine = TilePlacementEngine(shuffler)

        state.placeTile(Position(1, 0), HoleTile(TileShape.STRAIGHT))
        state.placeTile(Position(1, 2), HoleTile(TileShape.L_SHAPE))

        val faceDownNeighbors = engine.getAdjacentFaceDownTiles(Position(1, 1), state, board)
        assertEquals(2, faceDownNeighbors.size)
    }

    @Test
    fun `隣接する表向きタイルは取得されない`() {
        val state = BoardState(board)
        val engine = TilePlacementEngine(shuffler)

        state.placeTile(Position(1, 2), HoleTile(TileShape.L_SHAPE).flip())

        val faceDownNeighbors = engine.getAdjacentFaceDownTiles(Position(1, 1), state, board)
        assertTrue(faceDownNeighbors.isEmpty())
    }

    @Test
    fun `タイルを捨て札に追加できる`() {
        val engine = TilePlacementEngine(shuffler)
        engine.discard(HoleTile(TileShape.STRAIGHT))
        assertEquals(1, engine.discardPile.size)
    }

    @Test
    fun `配置可能なマスはモグラの隣接マスのみ`() {
        // TODO: 【要確認】3-2 配置先ルールは仮実装。
        val state = BoardState(board)
        val engine = TilePlacementEngine(shuffler)
        val molePosition = Position(1, 1)

        val placeable = engine.getPlaceablePositions(molePosition, state, board)
        val expected = board.getValidNeighbors(molePosition)
        assertTrue(placeable.all { it in expected })
    }

    @Test
    fun `既に表向きタイルが配置済みのマスには置けない`() {
        val state = BoardState(board)
        val engine = TilePlacementEngine(shuffler)
        val molePosition = Position(2, 2)

        state.placeTile(Position(3, 2), HoleTile(TileShape.STRAIGHT).flip())

        val placeable = engine.getPlaceablePositions(molePosition, state, board)
        assertFalse(Position(3, 2) in placeable)
    }
}
