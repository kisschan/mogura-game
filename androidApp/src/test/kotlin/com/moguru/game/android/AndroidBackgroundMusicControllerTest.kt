package com.moguru.game.android

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AndroidBackgroundMusicControllerTest {
    @Test
    fun `sets default quiet volume on creation`() {
        val player = FakeBackgroundMusicPlayer()

        AndroidBackgroundMusicController(player)

        assertEquals(listOf(DEFAULT_ANDROID_BGM_VOLUME), player.volumeEvents)
    }

    @Test
    fun `does not play before game starts`() {
        val player = FakeBackgroundMusicPlayer()
        val controller = AndroidBackgroundMusicController(player)

        controller.onForegrounded()

        assertEquals(0, player.playCount)
    }

    @Test
    fun `plays when game starts in foreground`() {
        val player = FakeBackgroundMusicPlayer()
        val controller = AndroidBackgroundMusicController(player)

        controller.onForegrounded()
        player.resetCounts()
        controller.onGameStartedChanged(true)

        assertEquals(1, player.playCount)
        assertEquals(0, player.pauseCount)
    }

    @Test
    fun `pauses on background and resumes started game on foreground`() {
        val player = FakeBackgroundMusicPlayer()
        val controller = AndroidBackgroundMusicController(player)

        controller.onForegrounded()
        controller.onGameStartedChanged(true)
        player.resetCounts()

        controller.onBackgrounded()
        controller.onForegrounded()

        assertEquals(1, player.pauseCount)
        assertEquals(1, player.playCount)
    }

    @Test
    fun `pauses when returning to setup`() {
        val player = FakeBackgroundMusicPlayer()
        val controller = AndroidBackgroundMusicController(player)

        controller.onForegrounded()
        controller.onGameStartedChanged(true)
        player.resetCounts()

        controller.onGameStartedChanged(false)

        assertEquals(0, player.playCount)
        assertEquals(1, player.pauseCount)
    }

    @Test
    fun `pauses and drops volume when muted`() {
        val player = FakeBackgroundMusicPlayer()
        val controller = AndroidBackgroundMusicController(player)
        controller.onForegrounded()
        controller.onGameStartedChanged(true)
        player.resetCounts()

        controller.onMusicSettingsChanged(AndroidMusicSettings(muted = true))

        assertEquals(listOf(0f), player.volumeEvents)
        assertEquals(0, player.playCount)
        assertEquals(1, player.pauseCount)
    }

    @Test
    fun `does not start when volume is zero`() {
        val player = FakeBackgroundMusicPlayer()
        val controller = AndroidBackgroundMusicController(
            player = player,
            initialSettings = AndroidMusicSettings(volume = 0f),
        )
        player.resetCounts()

        controller.onForegrounded()
        controller.onGameStartedChanged(true)

        assertEquals(0, player.playCount)
    }

    @Test
    fun `resumes a started foreground game after unmuting`() {
        val player = FakeBackgroundMusicPlayer()
        val controller = AndroidBackgroundMusicController(player)
        controller.onForegrounded()
        controller.onGameStartedChanged(true)
        controller.onMusicSettingsChanged(AndroidMusicSettings(muted = true))
        player.resetCounts()

        controller.onMusicSettingsChanged(AndroidMusicSettings(volume = 0.2f, muted = false))

        assertEquals(listOf(0.2f), player.volumeEvents)
        assertEquals(1, player.playCount)
        assertEquals(0, player.pauseCount)
    }

    @Test
    fun `closes player once`() {
        val player = FakeBackgroundMusicPlayer()
        val controller = AndroidBackgroundMusicController(player)

        controller.close()
        controller.close()

        assertEquals(1, player.closeCount)
    }

    private class FakeBackgroundMusicPlayer : AndroidBackgroundMusicPlayer {
        var playCount = 0
            private set
        var pauseCount = 0
            private set
        var closeCount = 0
            private set
        val volumeEvents = mutableListOf<Float>()

        override fun playLooping() {
            playCount += 1
        }

        override fun pause() {
            pauseCount += 1
        }

        override fun setVolume(volume: Float) {
            volumeEvents += volume
        }

        override fun close() {
            closeCount += 1
        }

        fun resetCounts() {
            playCount = 0
            pauseCount = 0
            closeCount = 0
            volumeEvents.clear()
        }
    }
}
