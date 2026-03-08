package com.moguru.game.engine

import com.moguru.game.model.Board
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MovementEngineTest {

    private val board = Board()
    private val engine = MovementEngine(board)

    private fun placeTile(
        boardState: BoardState,
        position: Position,
        shape: TileShape,
        rotation: Rotation = Rotation.DEG_0,
    ) {
        val tile = HoleTile(shape).rotate(rotation).flip()
        boardState.placeTile(position, tile)
    }

    @Test
    fun `タイルがない場合は移動先がない`() {
        val state = BoardState(board)
        val reachable = engine.findReachablePositions(Position(1, 1), state, emptySet())
        assertTrue(reachable.isEmpty())
    }

    @Test
    fun `直線タイルが縦に2つ繋がっていれば移動可能`() {
        val state = BoardState(board)
        placeTile(state, Position(1, 1), TileShape.STRAIGHT)
        placeTile(state, Position(1, 2), TileShape.STRAIGHT)

        val reachable = engine.findReachablePositions(Position(1, 1), state, emptySet())
        assertTrue(Position(1, 2) in reachable)
    }

    @Test
    fun `道が繋がっていなければ移動不可`() {
        val state = BoardState(board)
        placeTile(state, Position(1, 1), TileShape.STRAIGHT)
        placeTile(state, Position(2, 1), TileShape.STRAIGHT)

        val reachable = engine.findReachablePositions(Position(1, 1), state, emptySet())
        assertFalse(Position(2, 1) in reachable)
    }

    @Test
    fun `L字タイルで曲がって移動可能`() {
        val state = BoardState(board)
        placeTile(state, Position(1, 1), TileShape.L_SHAPE)
        placeTile(state, Position(2, 1), TileShape.L_SHAPE, Rotation.DEG_180)

        val reachable = engine.findReachablePositions(Position(1, 1), state, emptySet())
        assertTrue(Position(2, 1) in reachable)
    }

    @Test
    fun `他プレイヤーがいるマスは通過可能だが停止不可`() {
        val state = BoardState(board)
        placeTile(state, Position(1, 1), TileShape.STRAIGHT)
        placeTile(state, Position(1, 2), TileShape.STRAIGHT)
        placeTile(state, Position(1, 3), TileShape.STRAIGHT)

        val reachable = engine.findReachablePositions(
            Position(1, 1),
            state,
            occupiedPositions = setOf(Position(1, 2)),
        )

        assertFalse(Position(1, 2) in reachable)
        assertTrue(Position(1, 3) in reachable)
    }

    @Test
    fun `巣マスは全方向接続として扱う`() {
        // TODO: 【要確認】3-4 巣マスの接続ルールは仮実装。
        val state = BoardState(board)
        placeTile(state, Position(1, 1), TileShape.STRAIGHT, Rotation.DEG_90)

        val reachable = engine.findReachablePositions(Position(0, 1), state, emptySet())
        assertTrue(Position(1, 1) in reachable)
    }

    @Test
    fun `道が長く繋がっていれば何マスでも移動可能`() {
        val state = BoardState(board)
        for (col in 1..4) {
            placeTile(state, Position(col, 1), TileShape.STRAIGHT, Rotation.DEG_90)
        }

        val reachable = engine.findReachablePositions(Position(1, 1), state, emptySet())
        assertTrue(Position(2, 1) in reachable)
        assertTrue(Position(3, 1) in reachable)
        assertTrue(Position(4, 1) in reachable)
    }

    @Test
    fun `十字タイルは全方向に接続する`() {
        val state = BoardState(board)
        placeTile(state, Position(2, 2), TileShape.CROSS)
        placeTile(state, Position(3, 2), TileShape.STRAIGHT, Rotation.DEG_90)
        placeTile(state, Position(2, 3), TileShape.STRAIGHT)

        val reachable = engine.findReachablePositions(Position(2, 2), state, emptySet())
        assertTrue(Position(3, 2) in reachable)
        assertTrue(Position(2, 3) in reachable)
    }

    @Test
    fun `開始位置自体は結果に含まない`() {
        val state = BoardState(board)
        placeTile(state, Position(1, 1), TileShape.STRAIGHT)

        val reachable = engine.findReachablePositions(Position(1, 1), state, emptySet())
        assertFalse(Position(1, 1) in reachable)
    }
}
