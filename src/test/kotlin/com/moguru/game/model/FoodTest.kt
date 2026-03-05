package com.moguru.game.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class FoodTest {

    @Test
    fun `カブトムシの幼虫は得点1回復1`() {
        val type = FoodType.BEETLE_LARVA
        assertEquals(1, type.points)
        assertEquals(1, type.recovery)
    }

    @Test
    fun `ミミズは得点2回復2`() {
        val type = FoodType.EARTHWORM
        assertEquals(2, type.points)
        assertEquals(2, type.recovery)
    }

    @Test
    fun `ケラは得点2回復3`() {
        val type = FoodType.MOLE_CRICKET
        assertEquals(2, type.points)
        assertEquals(3, type.recovery)
    }

    @Test
    fun `ムカデは得点3回復4`() {
        val type = FoodType.CENTIPEDE
        assertEquals(3, type.points)
        assertEquals(4, type.recovery)
    }

    @Test
    fun `カエルは得点4回復5`() {
        val type = FoodType.FROG
        assertEquals(4, type.points)
        assertEquals(5, type.recovery)
    }

    @Test
    fun `カブトムシの幼虫は逃走しない（確定捕獲）`() {
        val card = FoodCard.createDummyCards(FoodType.BEETLE_LARVA).first()
        assertTrue(card.escapeMap.isEmpty())
    }

    @Test
    fun `ミミズは6面中2面で逃走`() {
        val card = FoodCard.createDummyCards(FoodType.EARTHWORM).first()
        assertEquals(2, card.escapeMap.size)
    }

    @Test
    fun `ケラは6面中3面で逃走`() {
        val card = FoodCard.createDummyCards(FoodType.MOLE_CRICKET).first()
        assertEquals(3, card.escapeMap.size)
    }

    @Test
    fun `ムカデは6面中4面で逃走`() {
        val card = FoodCard.createDummyCards(FoodType.CENTIPEDE).first()
        assertEquals(4, card.escapeMap.size)
    }

    @Test
    fun `カエルは6面中5面で逃走`() {
        val card = FoodCard.createDummyCards(FoodType.FROG).first()
        assertEquals(5, card.escapeMap.size)
    }

    @Test
    fun `2〜3人プレイ時はカエルなしの12枚`() {
        val cards = FoodCard.createDeck(includeFrog = false)
        assertEquals(12, cards.size)
        assertTrue(cards.none { it.type == FoodType.FROG })
    }

    @Test
    fun `4人プレイ時はカエルありの13枚`() {
        val cards = FoodCard.createDeck(includeFrog = true)
        assertEquals(13, cards.size)
        assertEquals(1, cards.count { it.type == FoodType.FROG })
    }

    @Test
    fun `逃走方向は8方向のいずれか`() {
        val card = FoodCard.createDummyCards(FoodType.CENTIPEDE).first()
        card.escapeMap.values.forEach { direction ->
            assertTrue(direction in EscapeDirection.entries)
        }
    }
}
