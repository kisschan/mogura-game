package com.moguru.game.engine

import com.moguru.game.model.*
import com.moguru.game.util.Shuffler

/**
 * 穴タイル配置ロジック。
 * 毎ターンの「掘る」フェーズで使用する。
 */
class TilePlacementEngine(private val shuffler: Shuffler) {

    /** 山札 */
    var drawPile: MutableList<HoleTile> = mutableListOf()

    /** 捨て札 */
    var discardPile: MutableList<HoleTile> = mutableListOf()

    /**
     * 山札から1枚引く。山札が空なら捨て札をシャッフルして再構成。
     * 両方空ならnull。
     */
    fun drawFromPile(): HoleTile? {
        if (drawPile.isEmpty()) {
            if (discardPile.isEmpty()) return null
            drawPile = shuffler.shuffle(discardPile).toMutableList()
            discardPile.clear()
        }
        return drawPile.removeFirst()
    }

    /**
     * モグラの隣接マスにある裏向きタイルの位置とタイルのペアを返す。
     *
     * // TODO: 【要確認】13-1 隣接に裏向きタイルがない場合、山札の1枚だけを配置する（仮実装）
     */
    fun getAdjacentFaceDownTiles(
        molePosition: Position,
        boardState: BoardState,
        board: Board,
    ): List<Pair<Position, HoleTile>> {
        return board.getValidNeighbors(molePosition).mapNotNull { pos ->
            val tile = boardState.getTile(pos)
            if (tile != null && tile.isFaceDown) pos to tile else null
        }
    }

    /**
     * タイルを配置可能なマス（モグラの隣接で未配置のマス）を返す。
     *
     * // TODO: 【要確認】13-2 配置先は隣接4方向のどこでも自由に選べる（仮実装）
     */
    fun getPlaceablePositions(
        molePosition: Position,
        boardState: BoardState,
        board: Board,
    ): List<Position> {
        return board.getValidNeighbors(molePosition).filter { pos ->
            !boardState.hasTile(pos) || boardState.isFaceDown(pos)
        }
    }

    /**
     * タイルを指定位置に表向きで配置する。
     */
    fun placeTile(
        position: Position,
        tile: HoleTile,
        rotation: Rotation,
        boardState: BoardState,
    ) {
        val placed = tile.rotate(rotation).flip()
        boardState.placeTile(position, placed)
    }

    /** タイルを捨て札に追加する */
    fun discard(tile: HoleTile) {
        discardPile.add(tile)
    }
}
