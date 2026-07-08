package com.moguru.game.android

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AndroidBackgroundMusicPlayerAudioFocusTest {
    @Test
    fun `requests audio focus before starting playback`() {
        val events = mutableListOf<String>()
        val mediaPlayer = FakeLoopingMediaPlayer(events)
        val audioFocus = FakeAndroidAudioFocus(events)
        val player = AudioFocusBackgroundMusicPlayer(mediaPlayer, audioFocus)

        player.playLooping()

        assertEquals(listOf("focus.request", "media.start"), events)
    }

    @Test
    fun `does not start playback when audio focus is denied`() {
        val events = mutableListOf<String>()
        val mediaPlayer = FakeLoopingMediaPlayer(events)
        val audioFocus = FakeAndroidAudioFocus(events, requestGranted = false)
        val player = AudioFocusBackgroundMusicPlayer(mediaPlayer, audioFocus)

        player.playLooping()

        assertEquals(listOf("focus.request"), events)
    }

    @Test
    fun `pause abandons audio focus after pausing playback`() {
        val events = mutableListOf<String>()
        val mediaPlayer = FakeLoopingMediaPlayer(events)
        val audioFocus = FakeAndroidAudioFocus(events)
        val player = AudioFocusBackgroundMusicPlayer(mediaPlayer, audioFocus)
        player.playLooping()
        events.clear()

        player.pause()

        assertEquals(listOf("media.pause", "focus.abandon"), events)
    }

    @Test
    fun `permanent audio focus loss pauses playback and abandons focus`() {
        val events = mutableListOf<String>()
        val mediaPlayer = FakeLoopingMediaPlayer(events)
        val audioFocus = FakeAndroidAudioFocus(events)
        val player = AudioFocusBackgroundMusicPlayer(mediaPlayer, audioFocus)
        player.playLooping()
        events.clear()

        player.onPermanentAudioFocusLoss()

        assertEquals(listOf("media.pause", "focus.abandon"), events)
    }

    @Test
    fun `transient audio focus loss pauses playback without abandoning focus`() {
        val events = mutableListOf<String>()
        val mediaPlayer = FakeLoopingMediaPlayer(events)
        val audioFocus = FakeAndroidAudioFocus(events)
        val player = AudioFocusBackgroundMusicPlayer(mediaPlayer, audioFocus)
        player.playLooping()
        events.clear()

        player.onTransientAudioFocusLoss()

        assertEquals(listOf("media.pause"), events)
    }

    @Test
    fun `audio focus gain resumes playback after transient loss`() {
        val events = mutableListOf<String>()
        val mediaPlayer = FakeLoopingMediaPlayer(events)
        val audioFocus = FakeAndroidAudioFocus(events)
        val player = AudioFocusBackgroundMusicPlayer(mediaPlayer, audioFocus)
        player.playLooping()
        player.onTransientAudioFocusLoss()
        events.clear()

        player.onAudioFocusGain()

        assertEquals(listOf("media.start"), events)
    }

    @Test
    fun `audio focus gain does not resume after user requested pause`() {
        val events = mutableListOf<String>()
        val mediaPlayer = FakeLoopingMediaPlayer(events)
        val audioFocus = FakeAndroidAudioFocus(events)
        val player = AudioFocusBackgroundMusicPlayer(mediaPlayer, audioFocus)
        player.playLooping()
        player.onTransientAudioFocusLoss()
        player.pause()
        events.clear()

        player.onAudioFocusGain()

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `close abandons focus and closes media player once`() {
        val events = mutableListOf<String>()
        val mediaPlayer = FakeLoopingMediaPlayer(events)
        val audioFocus = FakeAndroidAudioFocus(events)
        val player = AudioFocusBackgroundMusicPlayer(mediaPlayer, audioFocus)
        player.playLooping()
        events.clear()

        player.close()
        player.close()

        assertEquals(listOf("focus.abandon", "media.close"), events)
    }

    private class FakeLoopingMediaPlayer(
        private val events: MutableList<String>,
    ) : AndroidLoopingMediaPlayer {
        override var isPlaying = false
            private set

        override fun start() {
            events += "media.start"
            isPlaying = true
        }

        override fun pause() {
            events += "media.pause"
            isPlaying = false
        }

        override fun close() {
            events += "media.close"
        }
    }

    private class FakeAndroidAudioFocus(
        private val events: MutableList<String>,
        private val requestGranted: Boolean = true,
    ) : AndroidAudioFocus {
        override fun request(): Boolean {
            events += "focus.request"
            return requestGranted
        }

        override fun abandon() {
            events += "focus.abandon"
        }
    }
}
