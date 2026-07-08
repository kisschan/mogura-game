package com.moguru.game.android

import android.content.Context
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

private class MediaPlayerBackgroundMusicPlayer(
    context: Context,
    resourceId: Int,
) : AndroidBackgroundMusicPlayer {
    private val mediaPlayer = checkNotNull(MediaPlayer.create(context, resourceId)) {
        "Failed to create background music player."
    }.apply {
        isLooping = true
    }
    private var isClosed = false

    override fun playLooping() {
        if (isClosed || mediaPlayer.isPlaying) return

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
