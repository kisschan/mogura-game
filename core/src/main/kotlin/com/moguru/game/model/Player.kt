package com.moguru.game.model

/**
 * プレイヤー。体力・得点・連行中エサ・巣の位置を管理する。
 */
class Player(
    val id: Int,
    val name: String,
    val nestPosition: Position,
) {
    companion object {
        const val MAX_HEALTH = 13
    }

    /** 現在位置。 */
    var position: Position = nestPosition
        private set

    /** 体力。0で脱落。 */
    var health: Int = MAX_HEALTH
        private set

    /** 連行中のエサ。 */
    var carriedFood: FoodCard? = null
        private set

    private val _storedFoods = mutableListOf<FoodCard>()
    val storedFoods: List<FoodCard> get() = _storedFoods.toList()

    /** 巣に持ち帰ったエサの合計得点。 */
    val score: Int get() = _storedFoods.sumOf { it.type.points }

    /** 脱落しているか。 */
    val isEliminated: Boolean get() = health <= 0

    /** エサを連行中か。 */
    val isCarrying: Boolean get() = carriedFood != null

    /** 地上なら2、地下なら1だけ体力を減らす。 */
    fun reduceHealth(isOnSurface: Boolean) {
        val amount = if (isOnSurface) 2 else 1
        health = (health - amount).coerceAtLeast(0)
    }

    /** 上限13まで体力を回復する。 */
    fun heal(amount: Int) {
        health = (health + amount).coerceAtMost(MAX_HEALTH)
    }

    /** エサを連行する。 */
    fun carryFood(food: FoodCard) {
        carriedFood = food
    }

    /** 連行中のエサを巣に貯蔵する。 */
    fun storeFood() {
        carriedFood?.let { food ->
            _storedFoods.add(food)
            carriedFood = null
        }
    }

    /** 巣にあるエサを1枚取り除く。 */
    fun removeStoredFood(food: FoodCard): Boolean = _storedFoods.remove(food)

    /** 位置を更新する。 */
    fun moveTo(position: Position) {
        this.position = position
    }
}
