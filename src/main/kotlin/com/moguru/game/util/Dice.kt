package com.moguru.game.util

/**
 * サイコロのインターフェース。テスト時に差し替え可能。
 */
interface DiceRoller {
    /** 1〜6のダイス目を返す */
    fun roll(): Int
}

/**
 * ランダムなサイコロ実装
 */
class RandomDiceRoller : DiceRoller {
    override fun roll(): Int = (1..6).random()
}

/**
 * テスト用の固定値サイコロ。指定した値を順に返し、末尾に達したら先頭に戻る。
 */
class FixedDiceRoller(private val values: List<Int>) : DiceRoller {
    private var index = 0
    override fun roll(): Int {
        val value = values[index % values.size]
        index++
        return value
    }
}

/**
 * シャッフルのインターフェース。テスト時に差し替え可能。
 */
interface Shuffler {
    fun <T> shuffle(list: List<T>): List<T>
}

/**
 * ランダムなシャッフル実装
 */
class RandomShuffler : Shuffler {
    override fun <T> shuffle(list: List<T>): List<T> = list.shuffled()
}

/**
 * テスト用の固定シャッフラー。リストをそのまま返す。
 */
class FixedShuffler : Shuffler {
    override fun <T> shuffle(list: List<T>): List<T> = list
}
