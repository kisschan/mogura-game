package com.moguru.game.android

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt

internal const val DEFAULT_ANDROID_BGM_VOLUME = 0.35f

internal data class AndroidMusicSettings(
    val volume: Float = DEFAULT_ANDROID_BGM_VOLUME,
    val muted: Boolean = false,
) {
    val normalizedVolume: Float
        get() = volume.coerceIn(MIN_VOLUME, MAX_VOLUME)

    val effectiveVolume: Float
        get() = if (muted) MIN_VOLUME else normalizedVolume

    val volumePercent: Int
        get() = (normalizedVolume * 100).roundToInt()

    fun normalized(): AndroidMusicSettings =
        copy(volume = normalizedVolume)

    companion object {
        const val MIN_VOLUME = 0f
        const val MAX_VOLUME = 1f
    }
}

internal class AndroidMusicSettingsRepository(
    private val store: AndroidMusicSettingsStore,
) {
    fun load(): AndroidMusicSettings =
        AndroidMusicSettings(
            volume = store.getFloat(KEY_BGM_VOLUME, DEFAULT_ANDROID_BGM_VOLUME),
            muted = store.getBoolean(KEY_BGM_MUTED, false),
        ).normalized()

    fun save(settings: AndroidMusicSettings): AndroidMusicSettings {
        val normalized = settings.normalized()
        store.putFloat(KEY_BGM_VOLUME, normalized.volume)
        store.putBoolean(KEY_BGM_MUTED, normalized.muted)
        return normalized
    }

    private companion object {
        const val KEY_BGM_VOLUME = "bgm_volume"
        const val KEY_BGM_MUTED = "bgm_muted"
    }
}

internal interface AndroidMusicSettingsStore {
    fun getFloat(key: String, defaultValue: Float): Float

    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    fun putFloat(key: String, value: Float)

    fun putBoolean(key: String, value: Boolean)
}

internal fun defaultAndroidMusicSettingsRepository(context: Context): AndroidMusicSettingsRepository =
    AndroidMusicSettingsRepository(
        SharedPreferencesAndroidMusicSettingsStore(
            context.applicationContext.getSharedPreferences(
                "mogura_audio_settings",
                Context.MODE_PRIVATE,
            ),
        ),
    )

private class SharedPreferencesAndroidMusicSettingsStore(
    private val preferences: SharedPreferences,
) : AndroidMusicSettingsStore {
    override fun getFloat(key: String, defaultValue: Float): Float =
        preferences.getFloat(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        preferences.getBoolean(key, defaultValue)

    override fun putFloat(key: String, value: Float) {
        preferences.edit().putFloat(key, value).apply()
    }

    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }
}
