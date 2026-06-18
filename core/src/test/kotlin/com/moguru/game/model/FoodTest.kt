package com.moguru.game.model

import com.moguru.game.presenter.displayName
import com.moguru.game.presenter.shortLabel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FoodTest {

    @Test
    fun `カブトムシの幼虫は得点1回復1`() {
        val type = FoodType.BEETLE_LARVA
        assertEquals(1, type.points)
        assertEquals(1, type.recovery)
    }

    @Test
    fun `カブトムシの幼虫は表示名もカード短縮名も正しい`() {
        val card = FoodCard(FoodType.BEETLE_LARVA, emptyMap())

        assertEquals("カブトムシの幼虫", FoodType.BEETLE_LARVA.displayName())
        assertEquals("カブトムシの幼虫", card.shortLabel())
        assertTrue("ダンゴムシ" !in FoodType.BEETLE_LARVA.displayName())
        assertTrue("ダンゴムシ" !in card.shortLabel())
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
    fun `カブトムシの幼虫は逃走しない`() {
        val card = FoodCard.createDummyCards(FoodType.BEETLE_LARVA).first()
        assertTrue(card.escapeMap.isEmpty())
    }

    @Test
    fun `ミミズは画像通り1で上2で下へ逃走`() {
        val card = FoodCard.createDummyCards(FoodType.EARTHWORM).first()
        assertEquals(
            mapOf(
                1 to EscapeDirection.TOP,
                2 to EscapeDirection.BOTTOM,
            ),
            card.escapeMap,
        )
    }

    @Test
    fun `ケラは画像で読める3で右上4で右下へ逃走として仮実装`() {
        val card = FoodCard.createDummyCards(FoodType.MOLE_CRICKET).first()
        assertEquals(
            mapOf(
                3 to EscapeDirection.TOP_RIGHT,
                4 to EscapeDirection.BOTTOM_RIGHT,
            ),
            card.escapeMap,
        )
    }

    @Test
    fun `ムカデは画像通り1右上2右下3左下4左上へ逃走`() {
        val card = FoodCard.createDummyCards(FoodType.CENTIPEDE).first()
        assertEquals(
            mapOf(
                1 to EscapeDirection.TOP_RIGHT,
                2 to EscapeDirection.BOTTOM_RIGHT,
                3 to EscapeDirection.BOTTOM_LEFT,
                4 to EscapeDirection.TOP_LEFT,
            ),
            card.escapeMap,
        )
    }

    @Test
    fun `カエルは画像通り1と2左3上4と5右へ逃走`() {
        val card = FoodCard.createDummyCards(FoodType.FROG).first()
        assertEquals(
            mapOf(
                1 to EscapeDirection.LEFT,
                2 to EscapeDirection.LEFT,
                3 to EscapeDirection.TOP,
                4 to EscapeDirection.RIGHT,
                5 to EscapeDirection.RIGHT,
            ),
            card.escapeMap,
        )
    }

    @Test
    fun `逃走数メタデータは逃走マップ数と一致する`() {
        FoodType.entries.forEach { type ->
            val card = FoodCard.createDummyCards(type).first()
            assertEquals(type.escapeCount, card.escapeMap.size, "$type の逃走数が一致しない")
        }
    }

    @Test
    fun `2人プレイ時はカエルなしの12枚`() {
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
