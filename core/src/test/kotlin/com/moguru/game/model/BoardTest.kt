package com.moguru.game.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoardTest {

    private val board = Board()

    @Test
    fun `有効マス数は26`() {
        val validCells = board.cells.values.filter { it.type != CellType.INVALID }
        assertEquals(26, validCells.size)
    }

    @Test
    fun `地上マスはrow0の6マス`() {
        val groundCells = board.cells.values.filter { it.type == CellType.GROUND }
        assertEquals(6, groundCells.size)
        groundCells.forEach { assertEquals(0, it.position.row) }
    }

    @Test
    fun `巣は4か所`() {
        val nests = board.cells.values.filter { it.type == CellType.NEST }
        assertEquals(4, nests.size)
        assertEquals(
            setOf(Position(0, 1), Position(5, 1), Position(0, 4), Position(5, 4)),
            nests.map { it.position }.toSet(),
        )
    }

    @Test
    fun `ホットゾーンは中央4マス`() {
        val hotZones = board.cells.values.filter { it.type == CellType.HOT_ZONE }
        assertEquals(4, hotZones.size)
        assertEquals(
            setOf(Position(2, 2), Position(3, 2), Position(2, 3), Position(3, 3)),
            hotZones.map { it.position }.toSet(),
        )
    }

    @Test
    fun `無効マスは4か所`() {
        val invalidCells = board.cells.values.filter { it.type == CellType.INVALID }
        assertEquals(4, invalidCells.size)
        assertEquals(
            setOf(Position(0, 2), Position(5, 2), Position(0, 3), Position(5, 3)),
            invalidCells.map { it.position }.toSet(),
        )
    }

    @Test
    fun `地下マスは12マス`() {
        val undergroundCells = board.cells.values.filter { it.type == CellType.UNDERGROUND }
        assertEquals(12, undergroundCells.size)
    }

    @Test
    fun `隣接マス取得は無効マスを除外する`() {
        val neighbors = board.getValidNeighbors(Position(1, 1))
        assertEquals(4, neighbors.size)
    }

    @Test
    fun `盤面端のマスは隣接が少ない`() {
        val neighbors = board.getValidNeighbors(Position(0, 0))
        assertEquals(2, neighbors.size)
    }

    @Test
    fun `無効マスに隣接するマスは無効マスを含まない`() {
        val neighbors = board.getValidNeighbors(Position(1, 2))
        assertEquals(3, neighbors.size)
        assertTrue(neighbors.none { board.getCell(it)?.type == CellType.INVALID })
    }

    @Test
    fun `盤面サイズは6列5行`() {
        assertEquals(6, Board.COLS)
        assertEquals(5, Board.ROWS)
    }

    @Test
    fun `全セル数は30`() {
        assertEquals(30, board.cells.size)
    }
}
