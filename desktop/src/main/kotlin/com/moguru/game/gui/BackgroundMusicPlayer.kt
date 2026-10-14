package com.moguru.game.gui

import javazoom.jl.player.Player
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

interface BackgroundMusicPlayer : AutoCloseable {
    fun playLooping()

    fun playVictoryLooping() = playLooping()

    override fun close() = Unit
}

fun defaultBackgroundMusicPlayer(path: String): BackgroundMusicPlayer =
    SwitchingBackgroundMusicPlayer(path, "assets/audio/the_champion_s_burrow.mp3") {
        JLayerBackgroundMusicPlayer(it)
    }

internal class SwitchingBackgroundMusicPlayer(
    private val gamePath: String,
    private val victoryPath: String,
    private val createPlayer: (String) -> BackgroundMusicPlayer,
) : BackgroundMusicPlayer {
    private var currentPath: String? = null
    private var player: BackgroundMusicPlayer? = null
    private var isClosed = false

    override fun playLooping() = playTrack(gamePath)

    override fun playVictoryLooping() = playTrack(victoryPath)

    private fun playTrack(path: String) {
        if (isClosed || currentPath == path) return

        player?.close()
        currentPath = path
        player = createPlayer(path).also { it.playLooping() }
    }

    override fun close() {
        if (isClosed) return

        isClosed = true
        player?.close()
        player = null
    }
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
    private val playbackLock = Any()

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
                synchronized(playbackLock) {
                    // A track can be switched while its stream is still opening.
                    if (!playing.get()) {
                        player.close()
                        return false
                    }
                    currentPlayer = player
                }
                try {
                    player.play()
                } finally {
                    player.close()
                }
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
        synchronized(playbackLock) {
            playing.set(false)
            currentPlayer?.close()
        }
        playbackThread?.interrupt()
    }
}
