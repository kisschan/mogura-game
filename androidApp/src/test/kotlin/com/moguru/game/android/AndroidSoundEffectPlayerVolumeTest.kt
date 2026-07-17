package com.moguru.game.android

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AndroidSoundEffectPlayerVolumeTest {
    @Test
    fun `plays effects at the configured volume`() {
        val output = FakeSoundEffectOutput()
        val player = VolumeControlledAndroidSoundEffectPlayer(output, initialVolume = 0.45f)

        player.play(AndroidSoundEffect.BUTTON_PRESS)
        player.setVolume(0.72f)
        player.play(AndroidSoundEffect.TILE_ROTATE)

        assertEquals(
            listOf(
                PlayedSoundEffect(AndroidSoundEffect.BUTTON_PRESS, 0.45f),
                PlayedSoundEffect(AndroidSoundEffect.TILE_ROTATE, 0.72f),
            ),
            output.played,
        )
    }

    @Test
    fun `zero volume suppresses sound effect playback`() {
        val output = FakeSoundEffectOutput()
        val player = VolumeControlledAndroidSoundEffectPlayer(output)

        player.setVolume(0f)
        player.play(AndroidSoundEffect.BUTTON_PRESS)

        assertEquals(emptyList<PlayedSoundEffect>(), output.played)
    }

    @Test
    fun `sound effect volume is clamped before playback`() {
        val output = FakeSoundEffectOutput()
        val player = VolumeControlledAndroidSoundEffectPlayer(output, initialVolume = 1.4f)

        player.play(AndroidSoundEffect.BUTTON_PRESS)
        player.setVolume(-0.2f)
        player.play(AndroidSoundEffect.TILE_ROTATE)

        assertEquals(
            listOf(PlayedSoundEffect(AndroidSoundEffect.BUTTON_PRESS, 1f)),
            output.played,
        )
    }

    @Test
    fun `close releases output once and ignores later calls`() {
        val output = FakeSoundEffectOutput()
        val player = VolumeControlledAndroidSoundEffectPlayer(output)

        player.close()
        player.close()
        player.setVolume(0.8f)
        player.play(AndroidSoundEffect.BUTTON_PRESS)

        assertEquals(1, output.closeCount)
        assertEquals(emptyList<PlayedSoundEffect>(), output.played)
    }

    private data class PlayedSoundEffect(
        val effect: AndroidSoundEffect,
        val volume: Float,
    )

    private class FakeSoundEffectOutput : AndroidSoundEffectOutput {
        val played = mutableListOf<PlayedSoundEffect>()
        var closeCount = 0
            private set

        override fun play(effect: AndroidSoundEffect, volume: Float) {
            played += PlayedSoundEffect(effect, volume)
        }

        override fun close() {
            closeCount += 1
        }
    }
}
