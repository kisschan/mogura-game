package com.moguru.game.engine

import com.moguru.game.model.Board
import com.moguru.game.model.Direction
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
    private val directionCases = listOf(
        DirectionCase(Direction.TOP, Position(2, 2), Position(2, 1)),
        DirectionCase(Direction.RIGHT, Position(2, 2), Position(3, 2)),
        DirectionCase(Direction.BOTTOM, Position(2, 1), Position(2, 2)),
        DirectionCase(Direction.LEFT, Position(3, 2), Position(2, 2)),
    )

    private data class DirectionCase(
        val direction: Direction,
        val from: Position,
        val to: Position,
    )

    private fun placeTile(
        boardState: BoardState,
        position: Position,
        shape: TileShape,
        rotation: Rotation = Rotation.DEG_0,
    ) {
        val tile = HoleTile(shape).rotate(rotation).flip()
        boardState.placeTile(position, tile)
    }

    private fun openTile(vararg directions: Direction): HoleTile =
        HoleTile(TileShape.CROSS, directions.toSet(), isFaceDown = false)

    @Test
    fun `タイルがない場合は移動先がない`() {
        val state = BoardState(board)
        val reachable = engine.findReachablePositions(Position(1, 1), state, emptySet())
        assertTrue(reachable.isEmpty())
    }

    @Test
    fun `隣接2マスは両側の辺が開いている方向だけ移動可能`() {
        directionCases.forEach { case ->
            val state = BoardState(board)
            state.placeTile(case.from, openTile(case.direction))
            state.placeTile(case.to, openTile(case.direction.opposite()))

            val reachable = engine.findReachablePositions(case.from, state, emptySet())

            assertTrue(case.to in reachable, "${case.direction} should connect ${case.from} to ${case.to}")
        }
    }

    @Test
    fun `隣接2マスは片側の辺だけ開いていても移動不可`() {
        directionCases.forEach { case ->
            val fromClosed = BoardState(board)
            fromClosed.placeTile(case.from, openTile())
            fromClosed.placeTile(case.to, openTile(case.direction.opposite()))

            val toClosed = BoardState(board)
            toClosed.placeTile(case.from, openTile(case.direction))
            toClosed.placeTile(case.to, openTile())

            assertFalse(
                case.to in engine.findReachablePositions(case.from, fromClosed, emptySet()),
                "${case.direction} should be blocked when source side is closed",
            )
            assertFalse(
                case.to in engine.findReachablePositions(case.from, toClosed, emptySet()),
                "${case.direction} should be blocked when target side is closed",
            )
        }
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
        placeTile(state, Position(1, 1), TileShape.L_SHAPE, Rotation.DEG_270)
        placeTile(state, Position(2, 1), TileShape.L_SHAPE, Rotation.DEG_90)

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
    fun `巣の右隣に左へ開いたL字タイルがあれば移動可能`() {
        val state = BoardState(board)
        placeTile(state, Position(1, 1), TileShape.L_SHAPE)

        val reachable = engine.findReachablePositions(Position(0, 1), state, emptySet())

        assertTrue(Position(1, 1) in reachable)
    }

    @Test
    fun `T字タイルの右隣に横向き直線タイルがあれば移動可能`() {
        val state = BoardState(board)
        placeTile(state, Position(1, 1), TileShape.T_SHAPE)
        placeTile(state, Position(2, 1), TileShape.STRAIGHT, Rotation.DEG_90)

        val reachable = engine.findReachablePositions(Position(1, 1), state, emptySet())

        assertTrue(Position(2, 1) in reachable)
    }

    @Test
    fun `右巣の左隣に90度の直線タイルがあれば移動可能`() {
        val state = BoardState(board)
        placeTile(state, Position(4, 1), TileShape.STRAIGHT, Rotation.DEG_90)

        val reachable = engine.findReachablePositions(Position(5, 1), state, emptySet())

        assertTrue(Position(4, 1) in reachable)
    }

    @Test
    fun `巣から地上へは直接移動できない`() {
        val state = BoardState(board)
        placeTile(state, Position(0, 0), TileShape.STRAIGHT)

        val reachable = engine.findReachablePositions(Position(0, 1), state, emptySet())

        assertFalse(Position(0, 0) in reachable)
    }

    @Test
    fun `地下と地上は道が繋がっていれば移動できる`() {
        val state = BoardState(board)
        placeTile(state, Position(1, 1), TileShape.STRAIGHT)
        placeTile(state, Position(1, 0), TileShape.STRAIGHT)

        val reachable = engine.findReachablePositions(Position(1, 1), state, emptySet())

        assertTrue(Position(1, 0) in reachable)
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
