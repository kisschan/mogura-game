package com.moguru.game.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class HoleTileTest {

    @Test
    fun `直線タイルのデフォルト接続は上と下`() {
        val tile = HoleTile(TileShape.STRAIGHT)
        assertEquals(setOf(Direction.TOP, Direction.BOTTOM), tile.openSides)
    }

    @Test
    fun `L字タイルのデフォルト接続は上と右`() {
        val tile = HoleTile(TileShape.L_SHAPE)
        assertEquals(setOf(Direction.TOP, Direction.RIGHT), tile.openSides)
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
    fun `L字タイルを90度回転すると右と下`() {
        val tile = HoleTile(TileShape.L_SHAPE)
        val rotated = tile.rotate(Rotation.DEG_90)
        assertEquals(setOf(Direction.RIGHT, Direction.BOTTOM), rotated.openSides)
    }

    @Test
    fun `L字タイルを180度回転すると下と左`() {
        val tile = HoleTile(TileShape.L_SHAPE)
        val rotated = tile.rotate(Rotation.DEG_180)
        assertEquals(setOf(Direction.BOTTOM, Direction.LEFT), rotated.openSides)
    }

    @Test
    fun `L字タイルを270度回転すると左と上`() {
        val tile = HoleTile(TileShape.L_SHAPE)
        val rotated = tile.rotate(Rotation.DEG_270)
        assertEquals(setOf(Direction.LEFT, Direction.TOP), rotated.openSides)
    }

    @Test
    fun `T字タイルを90度回転すると上と右と下`() {
        val tile = HoleTile(TileShape.T_SHAPE)
        val rotated = tile.rotate(Rotation.DEG_90)
        assertEquals(setOf(Direction.RIGHT, Direction.BOTTOM, Direction.TOP), rotated.openSides)
    }

    @Test
    fun `十字タイルはどう回転しても全方向`() {
        val tile = HoleTile(TileShape.CROSS)
        Rotation.entries.forEach { rotation ->
            val rotated = tile.rotate(rotation)
            assertEquals(
                setOf(Direction.TOP, Direction.BOTTOM, Direction.LEFT, Direction.RIGHT),
                rotated.openSides
            )
        }
    }

    @Test
    fun `穴タイル全26枚の内訳`() {
        val allTiles = HoleTile.createFullSet()
        assertEquals(26, allTiles.size)
        assertEquals(10, allTiles.count { it.shape == TileShape.STRAIGHT })
        assertEquals(10, allTiles.count { it.shape == TileShape.L_SHAPE })
        assertEquals(4, allTiles.count { it.shape == TileShape.T_SHAPE })
        assertEquals(2, allTiles.count { it.shape == TileShape.CROSS })
    }

    @Test
    fun `対向方向の取得`() {
        assertEquals(Direction.BOTTOM, Direction.TOP.opposite())
        assertEquals(Direction.TOP, Direction.BOTTOM.opposite())
        assertEquals(Direction.RIGHT, Direction.LEFT.opposite())
        assertEquals(Direction.LEFT, Direction.RIGHT.opposite())
    }

    @Test
    fun `タイルの指定辺に道があるか判定`() {
        val tile = HoleTile(TileShape.L_SHAPE) // 上-右
        assertTrue(tile.hasOpenSide(Direction.TOP))
        assertTrue(tile.hasOpenSide(Direction.RIGHT))
        assertFalse(tile.hasOpenSide(Direction.BOTTOM))
        assertFalse(tile.hasOpenSide(Direction.LEFT))
    }

    @Test
    fun `タイルは初期状態で裏向き`() {
        val tile = HoleTile(TileShape.STRAIGHT)
        assertTrue(tile.isFaceDown)
    }

    @Test
    fun `タイルを表にできる`() {
        val tile = HoleTile(TileShape.STRAIGHT)
        val flipped = tile.flip()
        assertFalse(flipped.isFaceDown)
    }
}
