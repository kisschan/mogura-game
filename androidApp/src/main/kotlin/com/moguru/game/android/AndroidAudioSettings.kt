package com.moguru.game.android

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt

internal const val DEFAULT_ANDROID_BGM_VOLUME = 0.35f
internal const val DEFAULT_ANDROID_SOUND_EFFECT_VOLUME = 0.45f

internal data class AndroidAudioSettings(
    val bgmVolume: Float = DEFAULT_ANDROID_BGM_VOLUME,
    val soundEffectVolume: Float = DEFAULT_ANDROID_SOUND_EFFECT_VOLUME,
) {
    val normalizedBgmVolume: Float
        get() = normalizeAndroidAudioVolume(bgmVolume)

    val normalizedSoundEffectVolume: Float
        get() = normalizeAndroidAudioVolume(soundEffectVolume)

    val bgmVolumePercent: Int
        get() = (normalizedBgmVolume * 100).roundToInt()

    val soundEffectVolumePercent: Int
        get() = (normalizedSoundEffectVolume * 100).roundToInt()

    fun normalized(): AndroidAudioSettings =
        copy(
            bgmVolume = normalizedBgmVolume,
            soundEffectVolume = normalizedSoundEffectVolume,
        )

    companion object {
        const val MIN_VOLUME = 0f
        const val MAX_VOLUME = 1f
    }
}

internal fun normalizeAndroidAudioVolume(volume: Float): Float =
    volume.coerceIn(AndroidAudioSettings.MIN_VOLUME, AndroidAudioSettings.MAX_VOLUME)

internal class AndroidAudioSettingsRepository(
    private val store: AndroidAudioSettingsStore,
) {
    fun load(): AndroidAudioSettings =
        AndroidAudioSettings(
            bgmVolume = store.getFloat(KEY_BGM_VOLUME, DEFAULT_ANDROID_BGM_VOLUME),
            soundEffectVolume = store.getFloat(KEY_SOUND_EFFECT_VOLUME, DEFAULT_ANDROID_SOUND_EFFECT_VOLUME),
        ).normalized()

    fun save(settings: AndroidAudioSettings): AndroidAudioSettings {
        val normalized = settings.normalized()
        store.putFloat(KEY_BGM_VOLUME, normalized.bgmVolume)
        store.putFloat(KEY_SOUND_EFFECT_VOLUME, normalized.soundEffectVolume)
        return normalized
    }

    private companion object {
        const val KEY_BGM_VOLUME = "bgm_volume"
        const val KEY_SOUND_EFFECT_VOLUME = "sound_effect_volume"
    }
}

internal interface AndroidAudioSettingsStore {
    fun getFloat(key: String, defaultValue: Float): Float

    fun putFloat(key: String, value: Float)
}

internal fun defaultAndroidAudioSettingsRepository(context: Context): AndroidAudioSettingsRepository =
    AndroidAudioSettingsRepository(
        SharedPreferencesAndroidAudioSettingsStore(
            context.applicationContext.getSharedPreferences(
                "mogura_audio_settings",
                Context.MODE_PRIVATE,
            ),
        ),
    )

private class SharedPreferencesAndroidAudioSettingsStore(
    private val preferences: SharedPreferences,
) : AndroidAudioSettingsStore {
    override fun getFloat(key: String, defaultValue: Float): Float =
        preferences.getFloat(key, defaultValue)

    override fun putFloat(key: String, value: Float) {
        preferences.edit().putFloat(key, value).apply()
    }
}
