package com.moguru.game.gui

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodType
import java.awt.Point
import java.awt.Rectangle
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FoodDisplayTest {

    @Test
    fun `food card is small while route visibility matters`() {
        assertEquals(0.42, foodCardScaleForPhase(TurnPhase.DIG))
        assertEquals(0.42, foodCardScaleForPhase(TurnPhase.MOVE))
    }

    @Test
    fun `food card keeps large display outside dig and move`() {
        assertEquals(0.75, foodCardScaleForPhase(TurnPhase.CAPTURE))
        assertEquals(0.75, foodCardScaleForPhase(TurnPhase.DECIDE))
        assertEquals(0.75, foodCardScaleForPhase(TurnPhase.END))
    }

    @Test
    fun `food image paths use the five canonical jpg assets`() {
        val expected = mapOf(
            FoodType.BEETLE_LARVA to "assets/images/foods/food_beetle_larva.jpg",
            FoodType.EARTHWORM to "assets/images/foods/food_earthworm.jpg",
            FoodType.MOLE_CRICKET to "assets/images/foods/food_mole_cricket.jpg",
            FoodType.CENTIPEDE to "assets/images/foods/food_centipede.jpg",
            FoodType.FROG to "assets/images/foods/food_frog.jpg",
        )

        assertEquals(expected, FoodType.entries.associateWith(::foodImagePath))
        assertFalse(foodImagePath(FoodType.BEETLE_LARVA).contains("dango", ignoreCase = true))
    }

    @Test
    fun `food images are the exact 1280 square canonical originals`() {
        val expectedHashes = mapOf(
            FoodType.BEETLE_LARVA to "E5654A2884A67B4CAFC0A0C6F28FA8998250CD2B7411D15C51695EB573D70F57",
            FoodType.EARTHWORM to "986F3B76B2904FDC1837464B293EE4F10F16C9D30BE0509F366861DCD0C50390",
            FoodType.MOLE_CRICKET to "ECD2733D8AC587E5A4B3FBB06B81D564FB60835E7E355B6766B298B5A7CED86F",
            FoodType.CENTIPEDE to "B57625EF1E7213837F075E92A158BE7999209AD707F8B6D035E5E81CAC211666",
            FoodType.FROG to "D61812ACDB2207B2F8282DEFA54AB08905857355E1226171796C8F47CEA9CB5C",
        )
        val assets = GuiAssets()

        expectedHashes.forEach { (type, expectedHash) ->
            val image = assets.foodImage(type)
            assertNotNull(image, "$type の正本画像を読み込めること")
            assertEquals(1280, image!!.width, "$type の正本画像幅")
            assertEquals(1280, image.height, "$type の正本画像高")

            val resourcePath = foodImagePath(type).removePrefix("assets/")
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            assertNotNull(stream, "$type の正本画像がリソースに含まれること")
            val actualHash = stream!!.use { input ->
                MessageDigest.getInstance("SHA-256")
                    .digest(input.readBytes())
                    .joinToString("") { byte -> "%02X".format(byte.toInt() and 0xFF) }
            }
            assertEquals(expectedHash, actualHash, "$type の正本画像ハッシュ")
        }
    }

    @Test
    fun `food preview is larger than the board cell`() {
        val cell = Rectangle(100, 100, 80, 80)
        val board = Rectangle(0, 0, 400, 400)

        val preview = foodPreviewRect(cell, board)

        assertTrue(preview.width > cell.width)
        assertTrue(preview.height > cell.height)
    }

    @Test
    fun `food preview stays inside board image`() {
        val cell = Rectangle(330, 330, 60, 60)
        val board = Rectangle(0, 0, 400, 400)

        val preview = foodPreviewRect(cell, board)

        assertTrue(board.contains(preview))
    }

    @Test
    fun `food card hover area is only the drawn compact food card`() {
        val cell = Rectangle(10, 20, 100, 80)

        val card = foodCardRect(cell, foodCardScaleForPhase(TurnPhase.DIG))

        assertEquals(Rectangle(73, 63, 34, 34), card)
        assertTrue(card.contains(Point(90, 80)))
        assertTrue(cell.contains(Point(20, 30)))
        assertFalse(card.contains(Point(20, 30)))
    }

    @Test
    fun `stacked food cards offset older visible cards up and left`() {
        val cell = Rectangle(10, 20, 100, 80)

        val front = foodCardRect(cell, 0.5, stackIndex = 0, stackSize = 2)
        val back = foodCardRect(cell, 0.5, stackIndex = 1, stackSize = 2)

        assertEquals(Rectangle(61, 51, 40, 40), front)
        assertEquals(Rectangle(67, 57, 40, 40), back)
    }
}
