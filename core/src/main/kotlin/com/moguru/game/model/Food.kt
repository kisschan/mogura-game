package com.moguru.game.model

/**
 * エサの逃走方向。各方向に対応する座標オフセットを持つ。
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

    /** 指定位置からこの方向へ1マス移動した座標を返す。 */
    fun applyTo(position: Position): Position = Position(position.col + dc, position.row + dr)
}

/**
 * エサの種類。
 */
enum class FoodType(
    val points: Int,
    val recovery: Int,
    val escapeCount: Int,
    val cardCount: Int,
) {
    BEETLE_LARVA(points = 1, recovery = 1, escapeCount = 0, cardCount = 4),
    EARTHWORM(points = 2, recovery = 2, escapeCount = 2, cardCount = 3),
    // 設計者確認済み: ケラは2面逃走。
    MOLE_CRICKET(points = 2, recovery = 3, escapeCount = 2, cardCount = 3),
    CENTIPEDE(points = 3, recovery = 4, escapeCount = 4, cardCount = 2),
    FROG(points = 4, recovery = 5, escapeCount = 5, cardCount = 1),
}

/**
 * エサカード。`escapeMap` はダイス目と逃走方向の対応表。
 */
data class FoodCard(
    val type: FoodType,
    val escapeMap: Map<Int, EscapeDirection>,
    val isFaceDown: Boolean = true,
) {
    companion object {
        /**
         * 指定タイプのダミーカードを生成する。
         *
         * 逃走ダイス目と方向は、確定一覧が届くまで `assets/images/foods` の
         * カード画像から読める情報を使う。
         */
        fun createDummyCards(type: FoodType): List<FoodCard> {
            // TODO: 【未確定】2-1 要件定義書12-1:
            //  13枚それぞれの逃走ダイス目と矢印方向を確定データへ更新する。
            val escapeMap = when (type) {
                FoodType.BEETLE_LARVA -> emptyMap()
                FoodType.EARTHWORM -> mapOf(
                    1 to EscapeDirection.TOP,
                    2 to EscapeDirection.BOTTOM,
                )
                FoodType.MOLE_CRICKET -> mapOf(
                    3 to EscapeDirection.TOP_RIGHT,
                    4 to EscapeDirection.BOTTOM_RIGHT,
                )
                FoodType.CENTIPEDE -> mapOf(
                    1 to EscapeDirection.TOP_RIGHT,
                    2 to EscapeDirection.BOTTOM_RIGHT,
                    3 to EscapeDirection.BOTTOM_LEFT,
                    4 to EscapeDirection.TOP_LEFT,
                )
                FoodType.FROG -> mapOf(
                    1 to EscapeDirection.LEFT,
                    2 to EscapeDirection.LEFT,
                    3 to EscapeDirection.TOP,
                    4 to EscapeDirection.RIGHT,
                    5 to EscapeDirection.RIGHT,
                )
            }

            return List(type.cardCount) { FoodCard(type, escapeMap) }
        }

        /** プレイ人数に応じたエサデッキを生成する。 */
        fun createDeck(includeFrog: Boolean): List<FoodCard> {
            val types = if (includeFrog) {
                FoodType.entries
            } else {
                FoodType.entries.filter { it != FoodType.FROG }
            }
            return types.flatMap(::createDummyCards)
        }
    }
}
