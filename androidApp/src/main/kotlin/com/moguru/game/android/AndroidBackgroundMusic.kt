package com.moguru.game.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer

internal interface AndroidBackgroundMusicPlayer : AutoCloseable {
    fun playLooping()

    fun pause()

    fun setVolume(volume: Float)

    override fun close()
}

internal fun defaultAndroidBackgroundMusicPlayer(context: Context): AndroidBackgroundMusicPlayer =
    MediaPlayerBackgroundMusicPlayer(
        context = context.applicationContext,
        resourceId = R.raw.burrowed_logic,
    )

internal interface AndroidLoopingMediaPlayer : AutoCloseable {
    val isPlaying: Boolean

    fun start()

    fun pause()

    fun setVolume(volume: Float)

    override fun close()
}

internal interface AndroidAudioFocus {
    fun request(): Boolean

    fun abandon()
}

internal class AndroidBackgroundMusicController(
    private val player: AndroidBackgroundMusicPlayer,
    initialSettings: AndroidAudioSettings = AndroidAudioSettings(),
) : AutoCloseable {
    private var isGameStarted = false
    private var isForeground = false
    private var isClosed = false
    private var audioSettings = initialSettings.normalized()

    init {
        player.setVolume(audioSettings.normalizedBgmVolume)
    }

    fun onGameStartedChanged(started: Boolean) {
        if (isClosed) return

        isGameStarted = started
        syncPlayback()
    }

    fun onForegrounded() {
        if (isClosed) return

        isForeground = true
        syncPlayback()
    }

    fun onBackgrounded() {
        if (isClosed) return

        isForeground = false
        player.pause()
    }

    fun onAudioSettingsChanged(settings: AndroidAudioSettings) {
        if (isClosed) return

        audioSettings = settings.normalized()
        syncPlayback()
    }

    private fun syncPlayback() {
        val volume = audioSettings.normalizedBgmVolume
        player.setVolume(volume)
        if (isForeground && isGameStarted && volume > AndroidAudioSettings.MIN_VOLUME) {
            player.playLooping()
        } else {
            player.pause()
        }
    }

    override fun close() {
        if (isClosed) return

        isClosed = true
        player.close()
    }
}

internal class AudioFocusBackgroundMusicPlayer(
    private val mediaPlayer: AndroidLoopingMediaPlayer,
    private val audioFocus: AndroidAudioFocus,
) : AndroidBackgroundMusicPlayer {
    private var playbackRequested = false
    private var hasFocus = false
    private var temporarilyPausedForFocus = false
    private var isClosed = false

    override fun playLooping() {
        if (isClosed) return

        playbackRequested = true
        if (mediaPlayer.isPlaying) return
        if (temporarilyPausedForFocus) return
        if (!hasFocus && !audioFocus.request()) return

        hasFocus = true
        mediaPlayer.start()
    }

    override fun pause() {
        if (isClosed) return

        playbackRequested = false
        temporarilyPausedForFocus = false
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
        abandonFocusIfHeld()
    }

    override fun setVolume(volume: Float) {
        if (isClosed) return

        mediaPlayer.setVolume(normalizeAndroidAudioVolume(volume))
    }

    fun onPermanentAudioFocusLoss() {
        if (isClosed) return

        playbackRequested = false
        temporarilyPausedForFocus = false
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
        abandonFocusIfHeld()
    }

    fun onTransientAudioFocusLoss() {
        if (isClosed) return
        if (!playbackRequested || !hasFocus) return

        temporarilyPausedForFocus = true
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
    }

    fun onAudioFocusGain() {
        if (isClosed) return

        temporarilyPausedForFocus = false
        if (!playbackRequested || mediaPlayer.isPlaying) return

        hasFocus = true
        mediaPlayer.start()
    }

    override fun close() {
        if (isClosed) return

        isClosed = true
        playbackRequested = false
        temporarilyPausedForFocus = false
        abandonFocusIfHeld()
        mediaPlayer.close()
    }

    private fun abandonFocusIfHeld() {
        if (!hasFocus) return

        hasFocus = false
        audioFocus.abandon()
    }
}

private class MediaPlayerBackgroundMusicPlayer(
    context: Context,
    resourceId: Int,
) : AndroidBackgroundMusicPlayer {
    private val player: AudioFocusBackgroundMusicPlayer

    init {
        lateinit var focusAwarePlayer: AudioFocusBackgroundMusicPlayer
        val audioFocus = AudioManagerAudioFocus(context) { focusChange ->
            when (focusChange) {
                AndroidAudioFocusChange.GAIN -> focusAwarePlayer.onAudioFocusGain()
                AndroidAudioFocusChange.LOSS -> focusAwarePlayer.onPermanentAudioFocusLoss()
                AndroidAudioFocusChange.TRANSIENT_LOSS -> focusAwarePlayer.onTransientAudioFocusLoss()
            }
        }
        focusAwarePlayer = AudioFocusBackgroundMusicPlayer(
            mediaPlayer = AndroidMediaPlayer(context, resourceId),
            audioFocus = audioFocus,
        )
        player = focusAwarePlayer
    }

    override fun playLooping() = player.playLooping()

    override fun pause() = player.pause()

    override fun setVolume(volume: Float) = player.setVolume(volume)

    override fun close() = player.close()
}

private class AndroidMediaPlayer(
    context: Context,
    resourceId: Int,
) : AndroidLoopingMediaPlayer {
    private val mediaPlayer = checkNotNull(MediaPlayer.create(context, resourceId)) {
        "Failed to create background music player."
    }.apply { isLooping = true }
    private var isClosed = false

    override val isPlaying: Boolean
        get() = !isClosed && mediaPlayer.isPlaying

    override fun start() {
        if (isClosed) return
        mediaPlayer.start()
    }

    override fun pause() {
        if (isClosed || !mediaPlayer.isPlaying) return

        mediaPlayer.pause()
    }

    override fun setVolume(volume: Float) {
        if (isClosed) return

        val normalizedVolume = normalizeAndroidAudioVolume(volume)
        mediaPlayer.setVolume(normalizedVolume, normalizedVolume)
    }

    override fun close() {
        if (isClosed) return

        isClosed = true
        mediaPlayer.release()
    }
}

private class AudioManagerAudioFocus(
    context: Context,
    onFocusChange: (AndroidAudioFocusChange) -> Unit,
) : AndroidAudioFocus {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setOnAudioFocusChangeListener { focusChange ->
            focusChange.asAndroidAudioFocusChange()?.let(onFocusChange)
        }
        .build()

    override fun request(): Boolean =
        audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    override fun abandon() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}

private enum class AndroidAudioFocusChange {
    GAIN,
    LOSS,
    TRANSIENT_LOSS,
}

private fun Int.asAndroidAudioFocusChange(): AndroidAudioFocusChange? =
    when (this) {
        AudioManager.AUDIOFOCUS_GAIN -> AndroidAudioFocusChange.GAIN
        AudioManager.AUDIOFOCUS_LOSS -> AndroidAudioFocusChange.LOSS
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
        -> AndroidAudioFocusChange.TRANSIENT_LOSS
        else -> null
    }
