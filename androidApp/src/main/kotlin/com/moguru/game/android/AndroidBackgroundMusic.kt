package com.moguru.game.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer

internal interface AndroidBackgroundMusicPlayer : AutoCloseable {
    fun playLooping()

    fun pause()

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

    override fun close()
}

internal interface AndroidAudioFocus {
    fun request(): Boolean

    fun abandon()
}

internal class AndroidBackgroundMusicController(
    private val player: AndroidBackgroundMusicPlayer,
) : AutoCloseable {
    private var isGameStarted = false
    private var isForeground = false
    private var isClosed = false

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

    private fun syncPlayback() {
        if (isForeground && isGameStarted) {
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
    private var hasFocus = false
    private var isClosed = false

    override fun playLooping() {
        if (isClosed || mediaPlayer.isPlaying) return
        if (!hasFocus && !audioFocus.request()) return

        hasFocus = true
        mediaPlayer.start()
    }

    override fun pause() {
        if (isClosed) return

        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
        abandonFocusIfHeld()
    }

    fun onAudioFocusLoss() {
        if (isClosed) return

        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
        abandonFocusIfHeld()
    }

    override fun close() {
        if (isClosed) return

        isClosed = true
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
        val audioFocus = AudioManagerAudioFocus(context) {
            focusAwarePlayer.onAudioFocusLoss()
        }
        focusAwarePlayer = AudioFocusBackgroundMusicPlayer(
            mediaPlayer = AndroidMediaPlayer(context, resourceId),
            audioFocus = audioFocus,
        )
        player = focusAwarePlayer
    }

    override fun playLooping() = player.playLooping()

    override fun pause() = player.pause()

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

    override fun close() {
        if (isClosed) return

        isClosed = true
        mediaPlayer.release()
    }
}

private class AudioManagerAudioFocus(
    context: Context,
    onFocusLoss: () -> Unit,
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
            if (focusChange.isFocusLoss()) {
                onFocusLoss()
            }
        }
        .build()

    override fun request(): Boolean =
        audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    override fun abandon() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}

private fun Int.isFocusLoss(): Boolean =
    this == AudioManager.AUDIOFOCUS_LOSS ||
        this == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
        this == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
