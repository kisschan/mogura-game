package com.moguru.game.cpu

/** Stable names are persisted; display labels belong to the Android UI. */
enum class CpuDifficulty {
    EASY, NORMAL, HARD;

    fun createStrategy(): CpuStrategy = when (this) {
        EASY -> EasyCpuStrategy()
        NORMAL -> StandardCpuStrategy()
        HARD -> HardCpuStrategy()
    }
}
