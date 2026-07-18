package com.moguru.game.android

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AndroidAudioSettingsRepositoryTest {
    @Test
    fun `loads default audio settings`() {
        val repository = AndroidAudioSettingsRepository(FakeAudioSettingsStore())

        assertEquals(AndroidAudioSettings(), repository.load())
    }

    @Test
    fun `saves and reloads bgm and sound effect settings independently`() {
        val store = FakeAudioSettingsStore()
        val repository = AndroidAudioSettingsRepository(store)

        val saved = repository.save(AndroidAudioSettings(bgmVolume = 0.72f, soundEffectVolume = 0.28f))

        assertEquals(AndroidAudioSettings(bgmVolume = 0.72f, soundEffectVolume = 0.28f), saved)
        assertEquals(AndroidAudioSettings(bgmVolume = 0.72f, soundEffectVolume = 0.28f), repository.load())
    }

    @Test
    fun `clamps stored volumes into supported range`() {
        val store = FakeAudioSettingsStore()
        val repository = AndroidAudioSettingsRepository(store)

        val saved = repository.save(AndroidAudioSettings(bgmVolume = 1.4f, soundEffectVolume = -0.2f))

        assertEquals(AndroidAudioSettings(bgmVolume = 1f, soundEffectVolume = 0f), saved)
        store.floatValues["bgm_volume"] = -0.2f
        store.floatValues["sound_effect_volume"] = 1.8f
        assertEquals(AndroidAudioSettings(bgmVolume = 0f, soundEffectVolume = 1f), repository.load())
    }

    private class FakeAudioSettingsStore : AndroidAudioSettingsStore {
        val floatValues = mutableMapOf<String, Float>()

        override fun getFloat(key: String, defaultValue: Float): Float =
            floatValues[key] ?: defaultValue

        override fun putFloat(key: String, value: Float) {
            floatValues[key] = value
        }
    }
}
