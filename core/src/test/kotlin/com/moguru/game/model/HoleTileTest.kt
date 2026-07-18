package com.moguru.game.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HoleTileTest {

    @Test
    fun `直線タイルのデフォルト接続は上と下`() {
        val tile = HoleTile(TileShape.STRAIGHT)
        assertEquals(setOf(Direction.TOP, Direction.BOTTOM), tile.openSides)
    }

    @Test
    fun `L字タイルのデフォルト接続は左と下`() {
        val tile = HoleTile(TileShape.L_SHAPE)
        assertEquals(setOf(Direction.LEFT, Direction.BOTTOM), tile.openSides)
    }

    @Test
    fun `T字タイルのデフォルト接続は上と左と右`() {
        val tile = HoleTile(TileShape.T_SHAPE)
        assertEquals(setOf(Direction.TOP, Direction.LEFT, Direction.RIGHT), tile.openSides)
    }

    @Test
    fun `十字タイルのデフォルト接続は全方向`() {
        val tile = HoleTile(TileShape.CROSS)
        assertEquals(setOf(Direction.TOP, Direction.BOTTOM, Direction.LEFT, Direction.RIGHT), tile.openSides)
    }

    @Test
    fun `全タイルの全回転が画像基準の接続方向と一致する`() {
        val expected = mapOf(
            TileShape.STRAIGHT to mapOf(
                Rotation.DEG_0 to setOf(Direction.TOP, Direction.BOTTOM),
                Rotation.DEG_90 to setOf(Direction.LEFT, Direction.RIGHT),
                Rotation.DEG_180 to setOf(Direction.TOP, Direction.BOTTOM),
                Rotation.DEG_270 to setOf(Direction.LEFT, Direction.RIGHT),
            ),
            TileShape.L_SHAPE to mapOf(
                Rotation.DEG_0 to setOf(Direction.LEFT, Direction.BOTTOM),
                Rotation.DEG_90 to setOf(Direction.LEFT, Direction.TOP),
                Rotation.DEG_180 to setOf(Direction.TOP, Direction.RIGHT),
                Rotation.DEG_270 to setOf(Direction.RIGHT, Direction.BOTTOM),
            ),
            TileShape.T_SHAPE to mapOf(
                Rotation.DEG_0 to setOf(Direction.TOP, Direction.LEFT, Direction.RIGHT),
                Rotation.DEG_90 to setOf(Direction.TOP, Direction.RIGHT, Direction.BOTTOM),
                Rotation.DEG_180 to setOf(Direction.RIGHT, Direction.BOTTOM, Direction.LEFT),
                Rotation.DEG_270 to setOf(Direction.BOTTOM, Direction.LEFT, Direction.TOP),
            ),
            TileShape.CROSS to Rotation.entries.associateWith {
                setOf(Direction.TOP, Direction.RIGHT, Direction.BOTTOM, Direction.LEFT)
            },
        )

        expected.forEach { (shape, expectedByRotation) ->
            expectedByRotation.forEach { (rotation, openSides) ->
                assertEquals(openSides, HoleTile(shape).rotate(rotation).openSides, "$shape $rotation")
            }
        }
    }

    @Test
    fun `現在の開口方向から基準形状に対する回転角を復元できる`() {
        assertEquals(
            Rotation.DEG_270,
            HoleTile(TileShape.L_SHAPE).rotate(Rotation.DEG_270).canonicalRotation(),
        )
        assertEquals(
            Rotation.DEG_180,
            HoleTile(TileShape.T_SHAPE).rotate(Rotation.DEG_180).canonicalRotation(),
        )
        assertEquals(
            Rotation.DEG_90,
            HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_270).canonicalRotation(),
            "回転対称な形状は同じ開口方向になる最初の角度へ正規化する",
        )
        assertEquals(
            Rotation.DEG_0,
            HoleTile(TileShape.CROSS).rotate(Rotation.DEG_180).canonicalRotation(),
        )
    }

    @Test
    fun `直線タイルを90度回転すると左と右`() {
        val tile = HoleTile(TileShape.STRAIGHT)
        val rotated = tile.rotate(Rotation.DEG_90)
        assertEquals(setOf(Direction.LEFT, Direction.RIGHT), rotated.openSides)
    }

    @Test
    fun `直線タイルを180度回転しても上と下のまま`() {
        val tile = HoleTile(TileShape.STRAIGHT)
        val rotated = tile.rotate(Rotation.DEG_180)
        assertEquals(setOf(Direction.TOP, Direction.BOTTOM), rotated.openSides)
    }

    @Test
    fun `L字タイルを90度回転すると左と上`() {
        val tile = HoleTile(TileShape.L_SHAPE)
        val rotated = tile.rotate(Rotation.DEG_90)
        assertEquals(setOf(Direction.LEFT, Direction.TOP), rotated.openSides)
    }

    @Test
    fun `L字タイルを180度回転すると上と右`() {
        val tile = HoleTile(TileShape.L_SHAPE)
        val rotated = tile.rotate(Rotation.DEG_180)
        assertEquals(setOf(Direction.TOP, Direction.RIGHT), rotated.openSides)
    }

    @Test
    fun `L字タイルを270度回転すると右と下`() {
        val tile = HoleTile(TileShape.L_SHAPE)
        val rotated = tile.rotate(Rotation.DEG_270)
        assertEquals(setOf(Direction.RIGHT, Direction.BOTTOM), rotated.openSides)
    }

    @Test
    fun `T字タイルを90度回転すると上と右と下`() {
        val tile = HoleTile(TileShape.T_SHAPE)
        val rotated = tile.rotate(Rotation.DEG_90)
        assertEquals(setOf(Direction.TOP, Direction.RIGHT, Direction.BOTTOM), rotated.openSides)
    }

    @Test
    fun `十字タイルはどの回転でも全方向`() {
        val tile = HoleTile(TileShape.CROSS)
        Rotation.entries.forEach { rotation ->
            val rotated = tile.rotate(rotation)
            assertEquals(
                setOf(Direction.TOP, Direction.BOTTOM, Direction.LEFT, Direction.RIGHT),
                rotated.openSides,
            )
        }
    }

    @Test
    fun `穴タイル全26枚を生成できる`() {
        val allTiles = HoleTile.createFullSet()
        assertEquals(26, allTiles.size)
        assertEquals(10, allTiles.count { it.shape == TileShape.STRAIGHT })
        assertEquals(10, allTiles.count { it.shape == TileShape.L_SHAPE })
        assertEquals(4, allTiles.count { it.shape == TileShape.T_SHAPE })
        assertEquals(2, allTiles.count { it.shape == TileShape.CROSS })
    }

    @Test
    fun `対向方向を取得できる`() {
        assertEquals(Direction.BOTTOM, Direction.TOP.opposite())
        assertEquals(Direction.TOP, Direction.BOTTOM.opposite())
        assertEquals(Direction.RIGHT, Direction.LEFT.opposite())
        assertEquals(Direction.LEFT, Direction.RIGHT.opposite())
    }

    @Test
    fun `タイルの指定辺に道があるか判定できる`() {
        val tile = HoleTile(TileShape.L_SHAPE)
        assertTrue(tile.hasOpenSide(Direction.BOTTOM))
        assertTrue(tile.hasOpenSide(Direction.LEFT))
        assertFalse(tile.hasOpenSide(Direction.TOP))
        assertFalse(tile.hasOpenSide(Direction.RIGHT))
    }

    @Test
    fun `タイルは初期状態で裏向き`() {
        val tile = HoleTile(TileShape.STRAIGHT)
        assertTrue(tile.isFaceDown)
    }

    @Test
    fun `タイルを表向きにできる`() {
        val tile = HoleTile(TileShape.STRAIGHT)
        val flipped = tile.flip()
        assertFalse(flipped.isFaceDown)
    }
}
