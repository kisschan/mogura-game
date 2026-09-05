package com.moguru.game.gui

import java.io.BufferedInputStream
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

internal const val EAT_RECOVERY_SOUND_PATH = "assets/audio/sfx/eat_recovery.wav"

/** One-shot desktop sound used when a recovery animation starts. */
interface EatRecoverySoundPlayer : AutoCloseable {
    fun play()

    override fun close() = Unit
}

internal object NoOpEatRecoverySoundPlayer : EatRecoverySoundPlayer {
    override fun play() = Unit
}

fun defaultEatRecoverySoundPlayer(path: String = EAT_RECOVERY_SOUND_PATH): EatRecoverySoundPlayer =
    ShortWavEatRecoverySoundPlayer(path)

internal interface ShortAudioClip : AutoCloseable {
    fun playFromStart()
}

internal fun interface ShortAudioClipLoader {
    fun load(path: String): ShortAudioClip?
}

/** Lazily loads a short WAV and keeps its Clip open for low-latency replay. */
internal class ShortWavEatRecoverySoundPlayer(
    private val path: String,
    private val loader: ShortAudioClipLoader = JavaxShortAudioClipLoader(),
) : EatRecoverySoundPlayer {
    private var clip: ShortAudioClip? = null
    private var loadAttempted = false
    private var closed = false

    @Synchronized
    override fun play() {
        if (closed) return
        if (!loadAttempted) {
            loadAttempted = true
            clip = loader.load(path)
        }
        clip?.playFromStart()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        clip?.close()
        clip = null
    }
}

private class JavaxShortAudioClipLoader(
    private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
) : ShortAudioClipLoader {
    override fun load(path: String): ShortAudioClip? = try {
        val file = File(path)
        val audioInput = if (file.exists()) {
            AudioSystem.getAudioInputStream(file)
        } else {
            val resourcePath = path.removePrefix("assets/").replace('\\', '/')
            val stream = classLoader.getResourceAsStream(resourcePath) ?: return null
            AudioSystem.getAudioInputStream(BufferedInputStream(stream))
        }
        audioInput.use { input ->
            val clip = AudioSystem.getClip()
            try {
                clip.open(input)
                JavaxShortAudioClip(clip)
            } catch (exception: Exception) {
                clip.close()
                throw exception
            }
        }
    } catch (exception: Exception) {
        System.err.println("Failed to load eat recovery sound: ${exception.message}")
        null
    }
}

private class JavaxShortAudioClip(private val clip: Clip) : ShortAudioClip {
    override fun playFromStart() {
        if (clip.isRunning) clip.stop()
        clip.framePosition = 0
        clip.start()
    }

    override fun close() {
        if (clip.isRunning) clip.stop()
        clip.close()
    }
}
