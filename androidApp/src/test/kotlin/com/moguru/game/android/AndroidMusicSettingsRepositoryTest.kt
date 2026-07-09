package com.moguru.game.android

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AndroidMusicSettingsRepositoryTest {
    @Test
    fun `loads default quiet music settings`() {
        val repository = AndroidMusicSettingsRepository(FakeMusicSettingsStore())

        assertEquals(AndroidMusicSettings(), repository.load())
    }

    @Test
    fun `saves and reloads music settings`() {
        val store = FakeMusicSettingsStore()
        val repository = AndroidMusicSettingsRepository(store)

        val saved = repository.save(AndroidMusicSettings(volume = 0.72f, muted = true))

        assertEquals(AndroidMusicSettings(volume = 0.72f, muted = true), saved)
        assertEquals(AndroidMusicSettings(volume = 0.72f, muted = true), repository.load())
    }

    @Test
    fun `clamps stored volume into supported range`() {
        val store = FakeMusicSettingsStore()
        val repository = AndroidMusicSettingsRepository(store)

        val saved = repository.save(AndroidMusicSettings(volume = 1.4f, muted = false))

        assertEquals(AndroidMusicSettings(volume = 1f, muted = false), saved)
        store.floatValues["bgm_volume"] = -0.2f
        assertEquals(AndroidMusicSettings(volume = 0f, muted = false), repository.load())
    }

    private class FakeMusicSettingsStore : AndroidMusicSettingsStore {
        val floatValues = mutableMapOf<String, Float>()
        private val booleanValues = mutableMapOf<String, Boolean>()

        override fun getFloat(key: String, defaultValue: Float): Float =
            floatValues[key] ?: defaultValue

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            booleanValues[key] ?: defaultValue

        override fun putFloat(key: String, value: Float) {
            floatValues[key] = value
        }

        override fun putBoolean(key: String, value: Boolean) {
            booleanValues[key] = value
        }
    }
}
