package com.moguru.game.model

/**
 * 盤面上の座標（col=列, row=行）。0-indexed。
 */
data class Position(val col: Int, val row: Int) {
    /** 上下左右4方向の隣接座標を返す */
    fun neighbors(): List<Position> = listOf(
        Position(col, row - 1),  // 上
        Position(col, row + 1),  // 下
        Position(col - 1, row),  // 左
        Position(col + 1, row),  // 右
    )
}

/**
 * マスの種類
 */
enum class CellType {
    GROUND,      // 地上（row=0）
    NEST,        // 巣
    UNDERGROUND, // 地下
    HOT_ZONE,    // ホットゾーン（エサ配置場所）
    INVALID,     // 無効マス（存在しない）
}

/**
 * 盤面上の1マス
 */
data class Cell(
    val position: Position,
    val type: CellType,
)

/**
 * 6列×5行の盤面。有効マス26。
 */
class Board {

    companion object {
        const val COLS = 6
        const val ROWS = 5

        val NEST_POSITIONS = setOf(
            Position(0, 1), Position(5, 1),
            Position(0, 4), Position(5, 4),
        )

        val HOT_ZONE_POSITIONS = setOf(
            Position(2, 2), Position(3, 2),
            Position(2, 3), Position(3, 3),
        )

        val INVALID_POSITIONS = setOf(
            Position(0, 2), Position(5, 2),
            Position(0, 3), Position(5, 3),
        )
    }

    /** 全セル（無効マス含む） */
    val cells: Map<Position, Cell> = buildMap {
        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                val pos = Position(col, row)
                val type = when {
                    pos in INVALID_POSITIONS -> CellType.INVALID
                    pos in NEST_POSITIONS -> CellType.NEST
                    pos in HOT_ZONE_POSITIONS -> CellType.HOT_ZONE
                    row == 0 -> CellType.GROUND
                    else -> CellType.UNDERGROUND
                }
                put(pos, Cell(pos, type))
            }
        }
    }

    /** 指定座標のセルを返す。盤面外ならnull。 */
    fun getCell(position: Position): Cell? = cells[position]

    /** 指定座標の有効な隣接マス（INVALID・盤面外を除く）を返す */
    fun getValidNeighbors(position: Position): List<Position> =
        position.neighbors().filter { pos ->
            val cell = cells[pos]
            cell != null && cell.type != CellType.INVALID
        }
}
