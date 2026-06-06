package com.moguru.game.gui

import javazoom.jl.player.Player
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

interface BackgroundMusicPlayer : AutoCloseable {
    fun playLooping()

    override fun close() = Unit
}

fun defaultBackgroundMusicPlayer(path: String): BackgroundMusicPlayer =
    JLayerBackgroundMusicPlayer(path)

private object NoOpBackgroundMusicPlayer : BackgroundMusicPlayer {
    override fun playLooping() = Unit
}

/**
 * Desktop playback adapter for the Swing app. An Android UI should provide its
 * own BackgroundMusicPlayer implementation backed by android.media.MediaPlayer.
 */
private class JLayerBackgroundMusicPlayer(
    private val path: String,
    private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
) : BackgroundMusicPlayer {
    private val playing = AtomicBoolean(false)

    @Volatile
    private var currentPlayer: Player? = null

    @Volatile
    private var playbackThread: Thread? = null

    override fun playLooping() {
        if (!playing.compareAndSet(false, true)) return

        playbackThread = Thread(::runLoop, "mogura-background-music").apply {
            isDaemon = true
            start()
        }
    }

    private fun runLoop() {
        try {
            while (playing.get()) {
                if (!playOnce()) {
                    playing.set(false)
                }
            }
        } finally {
            currentPlayer = null
            playbackThread = null
        }
    }

    private fun playOnce(): Boolean {
        val stream = openStream() ?: return false
        return try {
            stream.use { input ->
                val player = Player(input)
                currentPlayer = player
                player.play()
            }
            true
        } catch (exception: Exception) {
            if (playing.get()) {
                System.err.println("Failed to play background music: ${exception.message}")
            }
            false
        } finally {
            currentPlayer = null
        }
    }

    private fun openStream(): InputStream? {
        val file = File(path)
        if (file.exists()) return file.inputStream()

        val resourcePath = path.removePrefix("assets/").replace('\\', '/')
        return classLoader.getResourceAsStream(resourcePath)
    }

    override fun close() {
        playing.set(false)
        currentPlayer?.close()
        playbackThread?.interrupt()
    }
}
