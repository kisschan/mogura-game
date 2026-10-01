package com.moguru.game.android

import androidx.compose.ui.MotionDurationScale
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AndroidReducedMotionTest {
    private fun scale(value: Float) = object : MotionDurationScale {
        override val scaleFactor = value
    }

    @Test
    fun `disabled and reduced game animations reach their final state without requesting animation frames`() {
        for (value in listOf(0f, 0.5f)) {
            val progress = Animatable(0f)
            runBlocking(scale(value)) { playGameAnimation(progress, 900) }
            assertEquals(1f, progress.value)
        }
    }

    @Test
    fun `disabled and reduced dice show the saved face without cycling before completing once`() {
        for (value in listOf(0f, 0.5f)) {
            var frames = 0
            val faces = mutableListOf<Int>()
            var completions = 0
            runBlocking(scale(value)) {
                playDiceRoulettePresentation(6, true, { frames++ }, { faces.add(it) }, { completions++ })
            }
            assertEquals(0, frames)
            assertEquals(listOf(6), faces)
            assertEquals(1, completions)
        }
    }

    @Test
    fun `disabled and reduced unresolved dice finish presentation without committing a roll`() {
        for (value in listOf(0f, 0.5f)) {
            var frames = 0
            var results = 0
            var completions = 0
            runBlocking(scale(value)) {
                withTimeout(250) {
                    playDiceRoulettePresentation(null, true, { frames++ }, { results++ }, { completions++ })
                }
            }
            assertEquals(0, frames)
            assertEquals(0, results)
            assertEquals(0, completions)
        }
    }
}
