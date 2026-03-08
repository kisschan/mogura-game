package com.moguru.game.engine

import com.moguru.game.model.Board
import com.moguru.game.model.CellType
import com.moguru.game.model.Direction
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Position

/**
 * 盤面上のタイル配置状態を管理する。
 */
class BoardState(val board: Board) {
    private val tiles = mutableMapOf<Position, HoleTile>()

    fun getTile(position: Position): HoleTile? = tiles[position]

    fun placeTile(position: Position, tile: HoleTile) {
        tiles[position] = tile
    }

    fun hasTile(position: Position): Boolean = position in tiles

    fun isFaceDown(position: Position): Boolean = tiles[position]?.isFaceDown ?: false

    fun clear() {
        tiles.clear()
    }
}

/**
 * 移動経路探索エンジン。BFSで到達可能マスを返す。
 */
class MovementEngine(private val board: Board) {

    /**
     * 開始位置から到達可能な停止マス一覧を返す。
     */
    fun findReachablePositions(
        start: Position,
        boardState: BoardState,
        occupiedPositions: Set<Position>,
    ): Set<Position> {
        val reachable = mutableSetOf<Position>()
        val visited = mutableSetOf(start)
        val queue = ArrayDeque<Position>()
        queue.add(start)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (neighbor in board.getValidNeighbors(current)) {
                if (neighbor in visited) continue
                if (!isConnected(current, neighbor, boardState)) continue

                visited.add(neighbor)
                queue.add(neighbor)

                if (neighbor !in occupiedPositions) {
                    reachable.add(neighbor)
                }
            }
        }

        return reachable
    }

    /**
     * 隣接する2マス間が接続されているか判定する。
     */
    private fun isConnected(from: Position, to: Position, boardState: BoardState): Boolean {
        val directionFromTo = getDirection(from, to) ?: return false
        val directionToFrom = directionFromTo.opposite()

        val fromCell = board.getCell(from) ?: return false
        val toCell = board.getCell(to) ?: return false

        val fromHasPath = when (fromCell.type) {
            // TODO: 【要確認】3-4 巣マスは全方向接続として仮実装。
            CellType.NEST -> true
            else -> boardState.getTile(from)?.let { tile ->
                !tile.isFaceDown && tile.hasOpenSide(directionFromTo)
            } ?: false
        }

        val toHasPath = when (toCell.type) {
            CellType.NEST -> true
            else -> boardState.getTile(to)?.let { tile ->
                !tile.isFaceDown && tile.hasOpenSide(directionToFrom)
            } ?: false
        }

        return fromHasPath && toHasPath
    }

    /**
     * `from` から `to` への方向を返す。隣接していなければ `null`。
     */
    private fun getDirection(from: Position, to: Position): Direction? {
        val dc = to.col - from.col
        val dr = to.row - from.row
        return when {
            dc == 0 && dr == -1 -> Direction.TOP
            dc == 0 && dr == 1 -> Direction.BOTTOM
            dc == -1 && dr == 0 -> Direction.LEFT
            dc == 1 && dr == 0 -> Direction.RIGHT
            else -> null
        }
    }
}
