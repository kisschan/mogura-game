package com.moguru.game.engine

import com.moguru.game.model.Board
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.util.Shuffler

/**
 * 穴タイル配置ロジック。
 */
class TilePlacementEngine(private val shuffler: Shuffler) {

    /** 山札。 */
    var drawPile: MutableList<HoleTile> = mutableListOf()

    /** 捨て札。 */
    var discardPile: MutableList<HoleTile> = mutableListOf()

    /**
     * 山札から1枚引く。山札が空なら捨て札を再構築する。
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
     * モグラに隣接する裏向きタイルを返す。
     *
     * TODO: 【要確認】3-1 隣接に裏向きタイルがない場合の掘る処理は仮実装。
     */
    fun getAdjacentFaceDownTiles(
        molePosition: Position,
        boardState: BoardState,
        board: Board,
    ): List<Pair<Position, HoleTile>> {
        return board.getValidNeighbors(molePosition).mapNotNull { position ->
            val tile = boardState.getTile(position)
            if (tile != null && tile.isFaceDown) position to tile else null
        }
    }

    /**
     * タイルを配置可能なマスを返す。
     *
     * TODO: 【要確認】3-2 配置先は隣接4方向から自由選択として仮実装。
     */
    fun getPlaceablePositions(
        molePosition: Position,
        boardState: BoardState,
        board: Board,
    ): List<Position> {
        return board.getValidNeighbors(molePosition).filter { position ->
            !boardState.hasTile(position) || boardState.isFaceDown(position)
        }
    }

    /** タイルを表向きで配置する。 */
    fun placeTile(
        position: Position,
        tile: HoleTile,
        rotation: Rotation,
        boardState: BoardState,
    ) {
        val placedTile = tile.rotate(rotation).flip()
        boardState.placeTile(position, placedTile)
    }

    /** タイルを捨て札に追加する。 */
    fun discard(tile: HoleTile) {
        discardPile.add(tile)
    }
}
