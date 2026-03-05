package com.moguru.game.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DiceTest {

    @Test
    fun `ランダムダイスは1から6の範囲`() {
        val roller = RandomDiceRoller()
        repeat(100) {
            val result = roller.roll()
            assertTrue(result in 1..6, "ダイス目が1-6の範囲外: $result")
        }
    }

    @Test
    fun `固定ダイスは指定した値を順に返す`() {
        val roller = FixedDiceRoller(listOf(3, 5, 1))
        assertEquals(3, roller.roll())
        assertEquals(5, roller.roll())
        assertEquals(1, roller.roll())
    }

    @Test
    fun `固定ダイスは循環する`() {
        val roller = FixedDiceRoller(listOf(2, 4))
        assertEquals(2, roller.roll())
        assertEquals(4, roller.roll())
        assertEquals(2, roller.roll()) // 循環
    }

    @Test
    fun `ランダムシャッフラーはリストをシャッフルする`() {
        val shuffler = RandomShuffler()
        val original = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val shuffled = shuffler.shuffle(original)
        assertEquals(original.size, shuffled.size)
        assertTrue(shuffled.containsAll(original))
    }

    @Test
    fun `固定シャッフラーはリストをそのまま返す`() {
        val shuffler = FixedShuffler()
        val list = listOf(1, 2, 3)
        assertEquals(list, shuffler.shuffle(list))
    }
}
