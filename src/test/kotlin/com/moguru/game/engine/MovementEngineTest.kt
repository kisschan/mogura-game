package com.moguru.game.engine

import com.moguru.game.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MovementEngineTest {

    private val board = Board()
    private val engine = MovementEngine(board)

    /**
     * 盤面にタイルを配置するヘルパー。
     * 指定位置に表向きで指定タイルを配置する。
     */
    private fun placeTile(
        boardState: BoardState,
        pos: Position,
        shape: TileShape,
        rotation: Rotation = Rotation.DEG_0,
    ) {
        val tile = HoleTile(shape).rotate(rotation).flip()
        boardState.placeTile(pos, tile)
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
        // (1,1)と(1,2)に直線タイル（上-下）を配置
        placeTile(state, Position(1, 1), TileShape.STRAIGHT)
        placeTile(state, Position(1, 2), TileShape.STRAIGHT)

        val reachable = engine.findReachablePositions(Position(1, 1), state, emptySet())
        assertTrue(Position(1, 2) in reachable)
    }

    @Test
    fun `道が繋がっていなければ移動不可`() {
        val state = BoardState(board)
        // (1,1)に直線（上-下）、(2,1)に直線（上-下）→ 横方向は繋がらない
        placeTile(state, Position(1, 1), TileShape.STRAIGHT)
        placeTile(state, Position(2, 1), TileShape.STRAIGHT)

        val reachable = engine.findReachablePositions(Position(1, 1), state, emptySet())
        assertFalse(Position(2, 1) in reachable)
    }

    @Test
    fun `L字タイルで曲がって移動可能`() {
        val state = BoardState(board)
        // (1,1)にL字（上-右）、(2,1)にL字180度（下-左）
        placeTile(state, Position(1, 1), TileShape.L_SHAPE) // 上-右
        placeTile(state, Position(2, 1), TileShape.L_SHAPE, Rotation.DEG_180) // 下-左

        val reachable = engine.findReachablePositions(Position(1, 1), state, emptySet())
        assertTrue(Position(2, 1) in reachable)
    }

    @Test
    fun `他プレイヤーがいるマスは通過可能だが停止不可`() {
        val state = BoardState(board)
        // 3マス縦に直線タイル
        placeTile(state, Position(1, 1), TileShape.STRAIGHT)
        placeTile(state, Position(1, 2), TileShape.STRAIGHT)
        placeTile(state, Position(1, 3), TileShape.STRAIGHT)

        val occupiedPositions = setOf(Position(1, 2))
        val reachable = engine.findReachablePositions(Position(1, 1), state, occupiedPositions)
        // (1,2)は通過のみ可能なので停止不可、(1,3)には到達可能
        assertFalse(Position(1, 2) in reachable)
        assertTrue(Position(1, 3) in reachable)
    }

    @Test
    fun `巣マスは全方向接続として扱う`() {
        // TODO: 【要確認】巣マスの接続ルールは仮実装
        val state = BoardState(board)
        // (0,1)は巣マス。隣の(1,1)に直線タイル（上-下）を配置
        // 巣は全方向接続なので、巣→(1,1)はLEFT辺で接続される
        // (1,1)の直線は上-下なので左辺がない→接続しない
        // 巣→(1,1) を接続させるには(1,1)がLEFT辺を持つ必要がある
        placeTile(state, Position(1, 1), TileShape.STRAIGHT, Rotation.DEG_90) // 左-右

        val reachable = engine.findReachablePositions(Position(0, 1), state, emptySet())
        assertTrue(Position(1, 1) in reachable)
    }

    @Test
    fun `道が長く繋がっていれば何マスでも移動可能`() {
        val state = BoardState(board)
        // row=1に横一列で直線タイル（左-右）を配置: col 1,2,3,4
        for (col in 1..4) {
            placeTile(state, Position(col, 1), TileShape.STRAIGHT, Rotation.DEG_90)
        }

        val reachable = engine.findReachablePositions(Position(1, 1), state, emptySet())
        assertTrue(Position(2, 1) in reachable)
        assertTrue(Position(3, 1) in reachable)
        assertTrue(Position(4, 1) in reachable)
    }

    @Test
    fun `十字タイルは全方向に接続`() {
        val state = BoardState(board)
        placeTile(state, Position(2, 2), TileShape.CROSS) // 全方向
        placeTile(state, Position(3, 2), TileShape.STRAIGHT, Rotation.DEG_90) // 左-右
        placeTile(state, Position(2, 3), TileShape.STRAIGHT) // 上-下

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
