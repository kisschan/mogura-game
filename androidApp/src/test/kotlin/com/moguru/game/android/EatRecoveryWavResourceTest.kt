package com.moguru.game.android

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EatRecoveryWavResourceTest {
    @Test
    fun `desktop and Android package the same recovery sound`() {
        val root = repositoryRoot()

        assertArrayEquals(
            Files.readAllBytes(root.resolve(DESKTOP_WAV)),
            Files.readAllBytes(root.resolve(ANDROID_WAV)),
            "Both platforms must package the same mixed recovery sound",
        )
    }

    @Test
    fun `recovery sound is audible short mono PCM with headroom and no clipped samples`() {
        val wav = readWave(Files.readAllBytes(repositoryRoot().resolve(ANDROID_WAV)))

        assertEquals(1, wav.format.audioFormat, "The SoundPool asset must use uncompressed PCM")
        assertEquals(1, wav.format.channels, "The short recovery effect must be mono")
        assertEquals(44_100, wav.format.sampleRate)
        assertEquals(16, wav.format.bitsPerSample)
        assertEquals(2, wav.format.blockAlign)
        assertEquals(88_200, wav.format.byteRate)
        assertTrue(wav.data.isNotEmpty(), "The WAV must contain audio samples")
        assertEquals(0, wav.data.size % wav.format.blockAlign, "The last PCM frame must be complete")

        val sampleCount = wav.data.size / 2
        assertTrue(sampleCount <= 35_280, "The effect must finish within the 800 ms eating animation")
        val pcm = ByteBuffer.wrap(wav.data).order(ByteOrder.LITTLE_ENDIAN)
        val samples = IntArray(sampleCount) { pcm.getShort(it * 2).toInt() }
        assertTrue(
            samples.none { it == Short.MIN_VALUE.toInt() || it == Short.MAX_VALUE.toInt() },
            "No PCM sample may be clipped at the signed 16-bit limits",
        )
        val peak = samples.maxOf { abs(it) } / 32_768.0
        val rms = sqrt(samples.sumOf { sample ->
            val normalized = sample / 32_768.0
            normalized * normalized
        } / sampleCount)
        assertTrue(peak <= 0.95, "Peak $peak must retain at least 5% full-scale headroom")
        assertTrue(rms > 0.001, "RMS $rms must exceed -60 dBFS so an accidentally silent mix fails")
    }

    /** Accept Gradle's module directory and an IDE/test runner launched at the repository root. */
    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        val root = when {
            Files.isRegularFile(workingDirectory.resolve(ANDROID_WAV)) -> workingDirectory
            Files.isRegularFile(workingDirectory.resolve("src/main/res/raw/eat_recovery.wav")) ->
                workingDirectory.parent
            else -> throw AssertionError("Cannot find the packaged recovery WAV from $workingDirectory")
        }
        assertTrue(Files.isRegularFile(root.resolve(DESKTOP_WAV)), "The desktop recovery WAV must exist")
        return root
    }

    /** Parse RIFF chunks instead of assuming that every WAV has a fixed 44-byte header. */
    private fun readWave(bytes: ByteArray): PcmWave {
        assertTrue(bytes.size >= 12, "A WAV must contain the RIFF/WAVE header")
        assertEquals("RIFF", ascii(bytes, 0))
        assertEquals("WAVE", ascii(bytes, 8))
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val riffSize = unsignedInt(buffer, 4)
        assertEquals(bytes.size.toLong(), riffSize + 8L, "The RIFF size must match the complete file")

        var format: WaveFormat? = null
        var data: ByteArray? = null
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val chunkId = ascii(bytes, offset)
            val chunkSize = unsignedInt(buffer, offset + 4)
            val contentOffset = offset + 8
            val contentEnd = contentOffset.toLong() + chunkSize
            assertTrue(contentEnd <= bytes.size, "The $chunkId chunk must not run past the RIFF file")
            when (chunkId) {
                "fmt " -> {
                    assertTrue(format == null, "The WAV must have one format chunk")
                    assertTrue(chunkSize >= 16, "The PCM format chunk must contain its standard fields")
                    format = WaveFormat(
                        audioFormat = unsignedShort(buffer, contentOffset),
                        channels = unsignedShort(buffer, contentOffset + 2),
                        sampleRate = buffer.getInt(contentOffset + 4),
                        byteRate = buffer.getInt(contentOffset + 8),
                        blockAlign = unsignedShort(buffer, contentOffset + 12),
                        bitsPerSample = unsignedShort(buffer, contentOffset + 14),
                    )
                }
                "data" -> {
                    assertTrue(data == null, "The WAV must have one PCM data chunk")
                    data = bytes.copyOfRange(contentOffset, contentEnd.toInt())
                }
            }
            offset = (contentEnd + (chunkSize and 1L)).toInt()
        }
        assertEquals(bytes.size, offset, "Every RIFF chunk and its padding must be complete")
        return PcmWave(
            format = format ?: throw AssertionError("The WAV is missing its format chunk"),
            data = data ?: throw AssertionError("The WAV is missing its PCM data chunk"),
        )
    }

    private fun ascii(bytes: ByteArray, offset: Int): String =
        String(bytes, offset, 4, Charsets.US_ASCII)

    private fun unsignedInt(buffer: ByteBuffer, offset: Int): Long =
        buffer.getInt(offset).toLong() and 0xffff_ffffL

    private fun unsignedShort(buffer: ByteBuffer, offset: Int): Int =
        buffer.getShort(offset).toInt() and 0xffff

    private data class WaveFormat(
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val byteRate: Int,
        val blockAlign: Int,
        val bitsPerSample: Int,
    )

    private data class PcmWave(val format: WaveFormat, val data: ByteArray)

    private companion object {
        const val ANDROID_WAV = "androidApp/src/main/res/raw/eat_recovery.wav"
        const val DESKTOP_WAV = "assets/audio/sfx/eat_recovery.wav"
    }
}
