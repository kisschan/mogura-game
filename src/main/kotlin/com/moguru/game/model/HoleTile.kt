package com.moguru.game.model

/**
 * 穴タイルの辺方向。
 */
enum class Direction {
    TOP,
    RIGHT,
    BOTTOM,
    LEFT,
    ;

    /** 対向方向を返す。 */
    fun opposite(): Direction = when (this) {
        TOP -> BOTTOM
        RIGHT -> LEFT
        BOTTOM -> TOP
        LEFT -> RIGHT
    }

    /** 時計回りに90度回転した方向を返す。 */
    fun rotateClockwise(): Direction = when (this) {
        TOP -> RIGHT
        RIGHT -> BOTTOM
        BOTTOM -> LEFT
        LEFT -> TOP
    }
}

/**
 * 回転量。90度単位。
 */
enum class Rotation(val steps: Int) {
    DEG_0(0),
    DEG_90(1),
    DEG_180(2),
    DEG_270(3),
}

/**
 * 穴タイルの形状。
 */
enum class TileShape(val defaultOpenSides: Set<Direction>, val count: Int) {
    STRAIGHT(setOf(Direction.TOP, Direction.BOTTOM), 10),
    L_SHAPE(setOf(Direction.TOP, Direction.RIGHT), 10),
    T_SHAPE(setOf(Direction.TOP, Direction.LEFT, Direction.RIGHT), 4),
    CROSS(setOf(Direction.TOP, Direction.BOTTOM, Direction.LEFT, Direction.RIGHT), 2),
}

/**
 * 穴タイル。4辺のどこに道があるかで定義する。
 */
data class HoleTile(
    val shape: TileShape,
    val openSides: Set<Direction> = shape.defaultOpenSides,
    val isFaceDown: Boolean = true,
) {
    /** 指定した辺に道があるか。 */
    fun hasOpenSide(direction: Direction): Boolean = direction in openSides

    /** 指定した回転量で回転した新しいタイルを返す。 */
    fun rotate(rotation: Rotation): HoleTile {
        var rotated = openSides
        repeat(rotation.steps) {
            rotated = rotated.map { it.rotateClockwise() }.toSet()
        }
        return copy(openSides = rotated)
    }

    /** タイルを表向きにする。 */
    fun flip(): HoleTile = copy(isFaceDown = false)

    companion object {
        /** 全26枚のタイルセットを生成する。 */
        fun createFullSet(): List<HoleTile> = TileShape.entries.flatMap { shape ->
            List(shape.count) { HoleTile(shape) }
        }
    }
}
