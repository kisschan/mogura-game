package com.moguru.game.engine

import com.moguru.game.model.*
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TilePlacementEngineTest {

    private val board = Board()
    private val shuffler = FixedShuffler()

    @Test
    fun `山札から1枚引ける`() {
        val engine = TilePlacementEngine(shuffler)
        val tiles = HoleTile.createFullSet().toMutableList()
        engine.drawPile = tiles.toMutableList()

        val drawn = engine.drawFromPile()
        assertNotNull(drawn)
        assertEquals(25, engine.drawPile.size)
    }

    @Test
    fun `山札が空の時は捨て札をシャッフルして再構成`() {
        val engine = TilePlacementEngine(shuffler)
        engine.drawPile = mutableListOf()
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
        engine.drawPile = mutableListOf()
        engine.discardPile = mutableListOf()

        val drawn = engine.drawFromPile()
        assertNull(drawn)
    }

    @Test
    fun `隣接する裏向きタイルを取得できる`() {
        val state = BoardState(board)
        val engine = TilePlacementEngine(shuffler)

        // (1,1)の周りに裏向きタイルを配置
        state.placeTile(Position(1, 0), HoleTile(TileShape.STRAIGHT)) // 表ではない（裏向き）
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
        val tile = HoleTile(TileShape.STRAIGHT)
        engine.discard(tile)
        assertEquals(1, engine.discardPile.size)
    }

    @Test
    fun `配置可能なマスはモグラの隣接マスのみ`() {
        // TODO: 【要確認】13-2 配置先は隣接4方向のどこでも自由に選べるか（仮: 自由選択）
        val state = BoardState(board)
        val engine = TilePlacementEngine(shuffler)
        val molePosition = Position(1, 1)

        val placeable = engine.getPlaceablePositions(molePosition, state, board)
        // 隣接有効マスのうちまだタイルが置かれていない場所
        val expected = board.getValidNeighbors(molePosition)
        assertTrue(placeable.all { it in expected })
    }

    @Test
    fun `既にタイルが配置済みのマスには置けない`() {
        val state = BoardState(board)
        val engine = TilePlacementEngine(shuffler)
        val molePosition = Position(2, 2)

        // (3,2)にタイル配置済み
        state.placeTile(Position(3, 2), HoleTile(TileShape.STRAIGHT).flip())

        val placeable = engine.getPlaceablePositions(molePosition, state, board)
        assertFalse(Position(3, 2) in placeable)
    }
}
