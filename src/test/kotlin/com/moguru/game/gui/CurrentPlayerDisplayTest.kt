package com.moguru.game.gui

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.Player
import com.moguru.game.model.Position
import java.awt.Rectangle
import java.awt.image.BufferedImage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CurrentPlayerDisplayTest {

    @Test
    fun `current player display formats player status`() {
        val player = Player(id = 1, name = "モグタ", nestPosition = Position(5, 1))
        repeat(2) { player.reduceHealth(isOnSurface = false) }
        player.carryFood(FoodCard(FoodType.EARTHWORM, emptyMap(), isFaceDown = false))

        val display = currentPlayerDisplay(player, TurnPhase.MOVE)

        assertEquals(1, display.playerId)
        assertEquals("モグタ の番", display.titleText)
        assertEquals("フェーズ: 移動", display.phaseText)
        assertEquals("体力: 11/13", display.healthText)
        assertEquals("点: 0", display.scoreText)
        assertEquals("所持: ミミズ", display.carriedFoodText)
    }

    @Test
    fun `current player display shows dash when no food is carried`() {
        val player = Player(id = 0, name = "モグオ", nestPosition = Position(0, 1))

        val display = currentPlayerDisplay(player, TurnPhase.DIG)

        assertEquals("所持: -", display.carriedFoodText)
    }

    @Test
    fun `current player display shows dash when player is absent`() {
        val display = currentPlayerDisplay(player = null, phase = null)

        assertEquals(null, display.playerId)
        assertEquals("-", display.titleText)
        assertEquals("フェーズ: -", display.phaseText)
        assertEquals("体力: -", display.healthText)
        assertEquals("点: -", display.scoreText)
        assertEquals("所持: -", display.carriedFoodText)
    }

    @Test
    fun `current player portrait is large enough to read card text`() {
        assertTrue(
            currentPlayerPortraitDrawSize() >= 100,
            "現在プレイヤー画像はカード内の文字が読めるよう100px以上で描画する",
        )
    }

    @Test
    fun `current player portrait crops transparent image padding`() {
        val image = BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB)
        for (y in 5..12) {
            for (x in 7..12) {
                image.setRGB(x, y, 0xFF334455.toInt())
            }
        }

        val bounds = visibleImageBounds(image)

        assertEquals(Rectangle(7, 5, 6, 8), bounds)
    }
}
