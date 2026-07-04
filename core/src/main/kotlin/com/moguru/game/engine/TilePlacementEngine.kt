package com.moguru.game.engine

import com.moguru.game.model.Board
import com.moguru.game.model.CellType
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
     * 隣接する既存タイルが1枚もない場合は、掘る処理を行わず移動へ進む。
     */
    fun getAdjacentFaceDownTiles(
        molePosition: Position,
        boardState: BoardState,
        board: Board,
    ): List<Pair<Position, HoleTile>> {
        return board.getValidNeighbors(molePosition).mapNotNull { position ->
            if (board.getCell(position)?.type == CellType.GROUND) return@mapNotNull null
            val tile = boardState.getTile(position)
            if (tile != null && tile.isFaceDown) position to tile else null
        }
    }

    /** モグラに隣接する既存の穴タイルを返す。 */
    fun getAdjacentHoleTiles(
        molePosition: Position,
        boardState: BoardState,
        board: Board,
    ): List<Pair<Position, HoleTile>> {
        return board.getValidNeighbors(molePosition).mapNotNull { position ->
            if (board.getCell(position)?.type == CellType.GROUND) return@mapNotNull null
            boardState.getTile(position)?.let { tile -> position to tile }
        }
    }

    fun getDiggableAdjacentPositions(
        molePosition: Position,
        boardState: BoardState,
        board: Board,
    ): List<Position> {
        return board.getValidNeighbors(molePosition).filter { position ->
            val tile = boardState.getTile(position)
            val cell = board.getCell(position)
            cell?.type != CellType.GROUND && tile != null
        }
    }

    /**
     * タイルを配置可能なマスを返す。
     *
     * 配置先はモグラに隣接する、既存穴タイルのある地中マスに限る。
     */
    fun getPlaceablePositions(
        molePosition: Position,
        boardState: BoardState,
        board: Board,
    ): List<Position> {
        return board.getValidNeighbors(molePosition).filter { position ->
            board.getCell(position)?.type != CellType.GROUND &&
                (!boardState.hasTile(position) || boardState.isFaceDown(position))
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
        discardPile.add(HoleTile(tile.shape))
    }
}
