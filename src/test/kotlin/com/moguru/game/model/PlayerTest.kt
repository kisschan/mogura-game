package com.moguru.game.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PlayerTest {

    @Test
    fun `初期体力は13`() {
        val player = Player(id = 0, name = "モグオ", nestPosition = Position(0, 1))
        assertEquals(13, player.health)
    }

    @Test
    fun `地下で体力1減少`() {
        val player = Player(id = 0, name = "モグオ", nestPosition = Position(0, 1))
        player.reduceHealth(isOnSurface = false)
        assertEquals(12, player.health)
    }

    @Test
    fun `地上で体力2減少`() {
        val player = Player(id = 0, name = "モグオ", nestPosition = Position(0, 1))
        player.reduceHealth(isOnSurface = true)
        assertEquals(11, player.health)
    }

    @Test
    fun `体力0で脱落`() {
        val player = Player(id = 0, name = "モグオ", nestPosition = Position(0, 1))
        repeat(13) { player.reduceHealth(isOnSurface = false) }
        assertEquals(0, player.health)
        assertTrue(player.isEliminated)
    }

    @Test
    fun `体力は0未満にならない`() {
        val player = Player(id = 0, name = "モグオ", nestPosition = Position(0, 1))
        repeat(20) { player.reduceHealth(isOnSurface = false) }
        assertEquals(0, player.health)
    }

    @Test
    fun `体力回復は上限13を超えない`() {
        val player = Player(id = 0, name = "モグオ", nestPosition = Position(0, 1))
        player.reduceHealth(isOnSurface = false) // 12
        player.heal(5)
        assertEquals(13, player.health)
    }

    @Test
    fun `体力回復量が正しい`() {
        val player = Player(id = 0, name = "モグオ", nestPosition = Position(0, 1))
        repeat(5) { player.reduceHealth(isOnSurface = false) } // 8
        player.heal(3)
        assertEquals(11, player.health)
    }

    @Test
    fun `エサを連行できる`() {
        val player = Player(id = 0, name = "モグオ", nestPosition = Position(0, 1))
        val food = FoodCard(FoodType.EARTHWORM, emptyMap())
        player.carryFood(food)
        assertNotNull(player.carriedFood)
        assertEquals(FoodType.EARTHWORM, player.carriedFood?.type)
    }

    @Test
    fun `連行中は別のエサを持てない`() {
        val player = Player(id = 0, name = "モグオ", nestPosition = Position(0, 1))
        player.carryFood(FoodCard(FoodType.EARTHWORM, emptyMap()))
        assertTrue(player.isCarrying)
    }

    @Test
    fun `巣にエサを持ち帰ると得点加算`() {
        val player = Player(id = 0, name = "モグオ", nestPosition = Position(0, 1))
        val food = FoodCard(FoodType.MOLE_CRICKET, emptyMap()) // 2点
        player.carryFood(food)
        player.storeFood()
        assertEquals(2, player.score)
        assertNull(player.carriedFood)
    }

    @Test
    fun `巣に貯蔵されたエサ一覧を取得できる`() {
        val player = Player(id = 0, name = "モグオ", nestPosition = Position(0, 1))
        val food1 = FoodCard(FoodType.BEETLE_LARVA, emptyMap())
        val food2 = FoodCard(FoodType.EARTHWORM, emptyMap())
        player.carryFood(food1)
        player.storeFood()
        player.carryFood(food2)
        player.storeFood()
        assertEquals(2, player.storedFoods.size)
        assertEquals(3, player.score) // 1 + 2
    }

    @Test
    fun `初期位置は巣`() {
        val player = Player(id = 0, name = "モグオ", nestPosition = Position(0, 1))
        assertEquals(Position(0, 1), player.position)
    }
}
