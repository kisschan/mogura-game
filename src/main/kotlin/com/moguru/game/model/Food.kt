package com.moguru.game.model

/**
 * エサの逃走方向（8方向）
 */
/**
 * エサの逃走方向（8方向）。各方向に対応する座標オフセットを持つ。
 */
enum class EscapeDirection(val dc: Int, val dr: Int) {
    TOP(0, -1),
    TOP_RIGHT(1, -1),
    RIGHT(1, 0),
    BOTTOM_RIGHT(1, 1),
    BOTTOM(0, 1),
    BOTTOM_LEFT(-1, 1),
    LEFT(-1, 0),
    TOP_LEFT(-1, -1),
    ;

    /** 指定位置からこの方向に1マス移動した座標を返す */
    fun applyTo(pos: Position): Position = Position(pos.col + dc, pos.row + dr)
}

/**
 * エサの種類
 */
enum class FoodType(
    val points: Int,
    val recovery: Int,
    val escapeCount: Int,
    val cardCount: Int,
) {
    BEETLE_LARVA(points = 1, recovery = 1, escapeCount = 0, cardCount = 4),
    EARTHWORM(points = 2, recovery = 2, escapeCount = 2, cardCount = 3),
    MOLE_CRICKET(points = 2, recovery = 3, escapeCount = 3, cardCount = 3),
    CENTIPEDE(points = 3, recovery = 4, escapeCount = 4, cardCount = 2),
    FROG(points = 4, recovery = 5, escapeCount = 5, cardCount = 1),
}

/**
 * エサカード。逃走判定用のescapeMapを持つ。
 *
 * escapeMap: ダイス目 → 逃走方向。この目が出たら逃走。
 * 空マップ = 確定捕獲（カブトムシの幼虫）。
 */
data class FoodCard(
    val type: FoodType,
    val escapeMap: Map<Int, EscapeDirection>,
    val isFaceDown: Boolean = true,
) {
    companion object {
        /**
         * 指定タイプのダミーカードを生成する。
         * // TODO: 【未確定】12-1 各カードの逃走ダイス目・矢印方向は設計者確認待ち。ダミーデータで仮実装。
         */
        fun createDummyCards(type: FoodType): List<FoodCard> {
            val escapeMap = when (type) {
                FoodType.BEETLE_LARVA -> emptyMap()
                FoodType.EARTHWORM -> mapOf(
                    1 to EscapeDirection.TOP,
                    2 to EscapeDirection.RIGHT,
                )
                FoodType.MOLE_CRICKET -> mapOf(
                    1 to EscapeDirection.TOP,
                    2 to EscapeDirection.RIGHT,
                    3 to EscapeDirection.BOTTOM,
                )
                FoodType.CENTIPEDE -> mapOf(
                    1 to EscapeDirection.TOP,
                    2 to EscapeDirection.RIGHT,
                    3 to EscapeDirection.BOTTOM,
                    4 to EscapeDirection.LEFT,
                )
                FoodType.FROG -> mapOf(
                    1 to EscapeDirection.TOP,
                    2 to EscapeDirection.TOP_RIGHT,
                    3 to EscapeDirection.RIGHT,
                    4 to EscapeDirection.BOTTOM,
                    5 to EscapeDirection.LEFT,
                )
            }
            return List(type.cardCount) { FoodCard(type, escapeMap) }
        }

        /**
         * プレイ人数に応じたエサデッキを生成する。
         */
        fun createDeck(includeFrog: Boolean): List<FoodCard> {
            val types = if (includeFrog) FoodType.entries else FoodType.entries.filter { it != FoodType.FROG }
            return types.flatMap { createDummyCards(it) }
        }
    }
}
