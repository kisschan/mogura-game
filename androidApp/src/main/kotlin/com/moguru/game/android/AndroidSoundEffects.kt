package com.moguru.game.android

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.moguru.game.presenter.CaptureAnimationEvent
import com.moguru.game.presenter.CaptureOutcomeKind
import com.moguru.game.presenter.EatAnimationEvent

enum class AndroidSoundEffect {
    BUTTON_PRESS,
    TILE_ROTATE,
    CAPTURE_FAILURE,
    EAT_RECOVERY,
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
            resourceIds = androidSoundEffectResourceIds(),
        ),
        initialVolume = initialVolume,
    )

internal fun androidSoundEffectResourceIds(): Map<AndroidSoundEffect, Int> =
    mapOf(
        AndroidSoundEffect.BUTTON_PRESS to R.raw.button_press,
        AndroidSoundEffect.TILE_ROTATE to R.raw.tile_rotate,
        AndroidSoundEffect.CAPTURE_FAILURE to R.raw.capture_failure,
        AndroidSoundEffect.EAT_RECOVERY to R.raw.eat_recovery,
    )

/**
 * The last event that played, in the playback generation it was shown in. A restarted chapter,
 * another lesson or a new match numbers its events again, so IDs are compared within one
 * generation only, and an earlier generation never plays again.
 */
private data class PlayedSoundEvent(val generation: Long, val eventId: Long) {
    fun covers(generation: Long, eventId: Long): Boolean =
        generation < this.generation || (generation == this.generation && eventId <= this.eventId)
}

internal class CaptureFailureSoundEffectTrigger {
    private var lastPlayedEscape: PlayedSoundEvent? = null

    fun soundEffectFor(event: CaptureAnimationEvent?, generation: Long = 0L): AndroidSoundEffect? {
        if (
            event == null ||
            event.kind != CaptureOutcomeKind.ESCAPED ||
            lastPlayedEscape?.covers(generation, event.id) == true
        ) {
            return null
        }

        lastPlayedEscape = PlayedSoundEvent(generation, event.id)
        return AndroidSoundEffect.CAPTURE_FAILURE
    }
}

internal class EatRecoverySoundEffectTrigger {
    private var lastPlayed: PlayedSoundEvent? = null

    fun soundEffectFor(event: EatAnimationEvent?, generation: Long = 0L): AndroidSoundEffect? {
        if (event == null || lastPlayed?.covers(generation, event.id) == true) return null

        lastPlayed = PlayedSoundEvent(generation, event.id)
        return AndroidSoundEffect.EAT_RECOVERY
    }
}

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
