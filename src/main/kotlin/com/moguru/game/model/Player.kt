package com.moguru.game.model

/**
 * プレイヤー（モグラ）。体力・得点・連行中エサ・巣の情報を管理する。
 */
class Player(
    val id: Int,
    val name: String,
    val nestPosition: Position,
) {
    companion object {
        const val MAX_HEALTH = 13
    }

    /** 現在位置 */
    var position: Position = nestPosition
        private set

    /** 体力（0で脱落） */
    var health: Int = MAX_HEALTH
        private set

    /** 連行中のエサ（null = 連行なし） */
    var carriedFood: FoodCard? = null
        private set

    /** 巣に貯蔵されたエサ一覧 */
    private val _storedFoods = mutableListOf<FoodCard>()
    val storedFoods: List<FoodCard> get() = _storedFoods.toList()

    /** 得点（巣に持ち帰ったエサの得点合計） */
    val score: Int get() = _storedFoods.sumOf { it.type.points }

    /** 脱落しているか */
    val isEliminated: Boolean get() = health <= 0

    /** エサを連行中か */
    val isCarrying: Boolean get() = carriedFood != null

    /** 体力を減少させる */
    fun reduceHealth(isOnSurface: Boolean) {
        val amount = if (isOnSurface) 2 else 1
        health = (health - amount).coerceAtLeast(0)
    }

    /** 体力を回復する（上限13） */
    fun heal(amount: Int) {
        health = (health + amount).coerceAtMost(MAX_HEALTH)
    }

    /** エサを連行する */
    fun carryFood(food: FoodCard) {
        carriedFood = food
    }

    /** 連行中のエサを巣に貯蔵して得点化する */
    fun storeFood() {
        carriedFood?.let {
            _storedFoods.add(it)
            carriedFood = null
        }
    }

    /** 巣から指定のエサを奪われる */
    fun removeStoredFood(food: FoodCard): Boolean = _storedFoods.remove(food)

    /** 位置を移動する */
    fun moveTo(pos: Position) {
        position = pos
    }
}
