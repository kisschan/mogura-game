package com.moguru.game.engine

import com.moguru.game.model.*

/**
 * 盤面上のタイル配置状態を管理する。
 */
class BoardState(val board: Board) {

    /** 各マスに配置された穴タイル（null = 未配置） */
    private val tiles = mutableMapOf<Position, HoleTile>()

    /** 指定位置のタイルを取得 */
    fun getTile(pos: Position): HoleTile? = tiles[pos]

    /** タイルを配置する */
    fun placeTile(pos: Position, tile: HoleTile) {
        tiles[pos] = tile
    }

    /** 指定位置にタイルがあるか */
    fun hasTile(pos: Position): Boolean = pos in tiles

    /** 指定位置のタイルが裏向きか */
    fun isFaceDown(pos: Position): Boolean = tiles[pos]?.isFaceDown ?: false
}

/**
 * 移動経路探索エンジン。
 * BFSで到達可能なマス一覧を返す。
 */
class MovementEngine(private val board: Board) {

    /**
     * 指定位置から到達可能なマスの一覧を返す（BFS）。
     *
     * @param start 開始位置
     * @param boardState 盤面のタイル配置状態
     * @param occupiedPositions 他プレイヤーがいるマス（通過可能・停止不可）
     * @return 停止可能なマスの集合（開始位置は含まない）
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

                // 他プレイヤーがいるマスは通過のみ（停止不可）
                if (neighbor !in occupiedPositions) {
                    reachable.add(neighbor)
                }
            }
        }

        return reachable
    }

    /**
     * 隣接する2マス間が接続されているか判定する。
     *
     * 条件:
     * - 巣マスは全方向に道があるものとして扱う（仮実装）
     * - 通常マス: 現マスのタイルの該当辺に道があり、かつ隣マスのタイルの対向辺に道がある
     */
    private fun isConnected(from: Position, to: Position, boardState: BoardState): Boolean {
        val directionFromTo = getDirection(from, to) ?: return false
        val directionToFrom = directionFromTo.opposite()

        val fromCell = board.getCell(from) ?: return false
        val toCell = board.getCell(to) ?: return false

        // from側の辺チェック
        val fromHasPath = when (fromCell.type) {
            // TODO: 【要確認】13-4 巣マスは全方向接続として仮実装
            CellType.NEST -> true
            else -> boardState.getTile(from)?.let {
                !it.isFaceDown && it.hasOpenSide(directionFromTo)
            } ?: false
        }

        // to側の辺チェック
        val toHasPath = when (toCell.type) {
            CellType.NEST -> true
            else -> boardState.getTile(to)?.let {
                !it.isFaceDown && it.hasOpenSide(directionToFrom)
            } ?: false
        }

        return fromHasPath && toHasPath
    }

    /**
     * from→toの方向を返す。隣接していなければnull。
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
