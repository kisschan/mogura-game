package com.moguru.game.android

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

enum class AndroidSoundEffect {
    BUTTON_PRESS,
    TILE_ROTATE,
}

interface AndroidSoundEffectPlayer : AutoCloseable {
    fun play(effect: AndroidSoundEffect)

    fun setVolume(volume: Float)

    override fun close()
}

object NoOpAndroidSoundEffectPlayer : AndroidSoundEffectPlayer {
    override fun play(effect: AndroidSoundEffect) = Unit

    override fun setVolume(volume: Float) = Unit

    override fun close() = Unit
}

internal fun defaultAndroidSoundEffectPlayer(
    context: Context,
    initialVolume: Float = DEFAULT_ANDROID_SOUND_EFFECT_VOLUME,
): AndroidSoundEffectPlayer =
    VolumeControlledAndroidSoundEffectPlayer(
        output = SoundPoolAndroidSoundEffectOutput(
            context = context.applicationContext,
            resourceIds = mapOf(
                AndroidSoundEffect.BUTTON_PRESS to R.raw.button_press,
                AndroidSoundEffect.TILE_ROTATE to R.raw.tile_rotate,
            ),
        ),
        initialVolume = initialVolume,
    )

internal interface AndroidSoundEffectOutput : AutoCloseable {
    fun play(effect: AndroidSoundEffect, volume: Float)

    override fun close()
}

internal class VolumeControlledAndroidSoundEffectPlayer(
    private val output: AndroidSoundEffectOutput,
    initialVolume: Float = DEFAULT_ANDROID_SOUND_EFFECT_VOLUME,
) : AndroidSoundEffectPlayer {
    private var volume = normalizeAndroidAudioVolume(initialVolume)
    private var isClosed = false

    override fun play(effect: AndroidSoundEffect) {
        synchronized(this) {
            if (isClosed || volume <= AndroidAudioSettings.MIN_VOLUME) return

            output.play(effect, volume)
        }
    }

    override fun setVolume(volume: Float) {
        synchronized(this) {
            if (isClosed) return

            this.volume = normalizeAndroidAudioVolume(volume)
        }
    }

    override fun close() {
        synchronized(this) {
            if (isClosed) return

            isClosed = true
            output.close()
        }
    }
}

private class SoundPoolAndroidSoundEffectOutput(
    context: Context,
    resourceIds: Map<AndroidSoundEffect, Int>,
) : AndroidSoundEffectOutput {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(resourceIds.size)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val soundIds = mutableMapOf<AndroidSoundEffect, Int>()
    private val loadedSoundIds = mutableSetOf<Int>()
    private var isClosed = false

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            synchronized(this) {
                if (!isClosed && status == 0) {
                    loadedSoundIds += sampleId
                }
            }
        }
        resourceIds.forEach { (effect, resourceId) ->
            soundIds[effect] = soundPool.load(context, resourceId, 1)
        }
    }

    override fun play(effect: AndroidSoundEffect, volume: Float) {
        val soundId = synchronized(this) {
            if (isClosed) return
            soundIds[effect]?.takeIf(loadedSoundIds::contains)
        } ?: return

        soundPool.play(
            soundId,
            volume,
            volume,
            1,
            0,
            1f,
        )
    }

    override fun close() {
        synchronized(this) {
            if (isClosed) return

            isClosed = true
            soundIds.clear()
            loadedSoundIds.clear()
        }
        soundPool.release()
    }
}
