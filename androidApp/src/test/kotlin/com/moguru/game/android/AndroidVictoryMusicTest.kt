package com.moguru.game.android

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AndroidVictoryMusicTest {
    private val events = mutableListOf<String>()
    private fun controller(volume: Float = 0.3f) = AndroidBackgroundMusicController(
        createPlayer = { track ->
            events += "$track.create"
            object : AndroidBackgroundMusicPlayer {
                override fun playLooping() { events += "$track.play" }
                override fun pause() { events += "$track.pause" }
                override fun setVolume(volume: Float) { events += "$track.volume:$volume" }
                override fun close() { events += "$track.close" }
            }
        },
        initialSettings = AndroidAudioSettings(bgmVolume = volume),
    )

    @Test
    fun `victory releases normal music before playing victory at the configured volume`() {
        val controller = controller()
        controller.onForegrounded()
        controller.onGameStateChanged(started = true)
        events.clear()

        controller.onGameStateChanged(started = true, hasWinner = true)

        assertEquals(listOf("GAME.close", "VICTORY.create", "VICTORY.volume:0.3", "VICTORY.play"), events)
    }

    @Test
    fun `result updates and foregrounding keep the same victory player`() {
        val controller = controller()
        controller.onForegrounded()
        controller.onGameStateChanged(started = true, hasWinner = true)
        events.clear()

        controller.onGameStateChanged(started = true, hasWinner = true)
        controller.onBackgrounded()
        controller.onForegrounded()

        assertEquals(listOf("VICTORY.volume:0.3", "VICTORY.play", "VICTORY.pause", "VICTORY.volume:0.3", "VICTORY.play"), events)
    }

    @Test
    fun `muted background victory waits until foreground and unmuted`() {
        val controller = controller(volume = 0f)
        events.clear()
        controller.onGameStateChanged(started = true, hasWinner = true)
        controller.onForegrounded()
        assertEquals(0, events.count { it.endsWith(".play") })

        events.clear()
        controller.onAudioSettingsChanged(AndroidAudioSettings(bgmVolume = 0.5f))
        assertEquals(listOf("VICTORY.volume:0.5", "VICTORY.play"), events)
    }

    @Test
    fun `setup stops victory and the next game returns to normal music`() {
        val controller = controller()
        controller.onForegrounded()
        controller.onGameStateChanged(started = true, hasWinner = true)
        events.clear()

        controller.onGameStateChanged(started = false)
        controller.onGameStateChanged(started = true)

        assertEquals(listOf("VICTORY.close", "GAME.create", "GAME.volume:0.3", "GAME.pause", "GAME.volume:0.3", "GAME.play"), events)
    }

    @Test
    fun `a game without a winner keeps normal music and closed controller ignores late results`() {
        val controller = controller()
        controller.onForegrounded()
        events.clear()
        controller.onGameStateChanged(started = true, hasWinner = false)
        assertEquals(listOf("GAME.volume:0.3", "GAME.play"), events)

        controller.close()
        events.clear()
        controller.onGameStateChanged(started = true, hasWinner = true)
        controller.onForegrounded()
        controller.close()
        assertEquals(emptyList<String>(), events)
    }
}
