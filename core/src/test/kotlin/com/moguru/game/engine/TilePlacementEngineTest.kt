package com.moguru.game.engine

import com.moguru.game.model.Board
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
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

        state.placeTile(Position(2, 1), HoleTile(TileShape.STRAIGHT))
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
    fun `discard resets rotated tile to canonical face down orientation`() {
        val engine = TilePlacementEngine(shuffler)
        val rotatedTile = HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip()

        engine.discard(rotatedTile)
        val drawn = engine.drawFromPile()

        assertNotNull(drawn)
        assertTrue(drawn!!.isFaceDown)
        assertEquals(TileShape.STRAIGHT.defaultOpenSides, drawn.openSides)
    }

    @Test
    fun `配置可能なマスはモグラに隣接する既存穴タイルのみ`() {
        val state = BoardState(board)
        val engine = TilePlacementEngine(shuffler)
        val molePosition = Position(1, 1)
        val leftNest = Position(0, 1)
        val rightUnderground = Position(2, 1)
        val bottomUnderground = Position(1, 2)
        val ground = Position(1, 0)

        state.placeTile(leftNest, HoleTile(TileShape.CROSS))
        state.placeTile(rightUnderground, HoleTile(TileShape.STRAIGHT).flip())
        state.placeTile(bottomUnderground, HoleTile(TileShape.L_SHAPE))
        state.placeTile(ground, HoleTile(TileShape.T_SHAPE))
        val placeable = engine.getPlaceablePositions(molePosition, state, board)

        assertEquals(setOf(rightUnderground, bottomUnderground), placeable.toSet())
    }

    @Test
    fun `地上マスには穴タイルを配置できない`() {
        val state = BoardState(board)
        val engine = TilePlacementEngine(shuffler)
        val molePosition = Position(1, 1)

        val diggable = engine.getDiggableAdjacentPositions(molePosition, state, board)
        val placeable = engine.getPlaceablePositions(molePosition, state, board)

        assertFalse(Position(1, 0) in diggable)
        assertFalse(Position(1, 0) in placeable)
    }

    @Test
    fun `既に表向きタイルが配置済みのマスも置き換え対象にできる`() {
        val state = BoardState(board)
        val engine = TilePlacementEngine(shuffler)
        val molePosition = Position(2, 2)
        val target = Position(3, 2)

        state.placeTile(target, HoleTile(TileShape.STRAIGHT).flip())

        val placeable = engine.getPlaceablePositions(molePosition, state, board)
        assertTrue(target in placeable)
    }
}
