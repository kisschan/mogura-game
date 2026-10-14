package com.moguru.game.gui

import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VictoryBackgroundMusicTest {
    @Test
    fun `switches tracks without overlap or restarting on repeated refreshes`() {
        val events = mutableListOf<String>()
        val music = SwitchingBackgroundMusicPlayer("game.mp3", "victory.mp3") { path ->
            events += "$path.create"
            object : BackgroundMusicPlayer {
                override fun playLooping() { events += "$path.play" }
                override fun close() { events += "$path.close" }
            }
        }

        music.playLooping()
        music.playLooping()
        music.playVictoryLooping()
        music.playVictoryLooping()
        music.playLooping()
        music.close()
        music.close()
        music.playVictoryLooping()

        assertEquals(listOf(
            "game.mp3.create", "game.mp3.play", "game.mp3.close",
            "victory.mp3.create", "victory.mp3.play", "victory.mp3.close",
            "game.mp3.create", "game.mp3.play", "game.mp3.close",
        ), events)
    }

    @Test
    fun `bundled victory mp3 decodes to audio samples`() {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("audio/the_champion_s_burrow.mp3"))
        stream.use {
            val bitstream = Bitstream(it)
            val decoder = Decoder()
            var frames = 0
            while (true) {
                val header = bitstream.readFrame() ?: break
                decoder.decodeFrame(header, bitstream)
                frames++
                bitstream.closeFrame()
            }
            assertTrue(frames > 0)
        }
    }
}
