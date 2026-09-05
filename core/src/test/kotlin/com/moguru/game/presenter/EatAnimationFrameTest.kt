package com.moguru.game.presenter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EatAnimationFrameTest {
    @Test
    fun `animation has clean endpoints and an 800 millisecond duration`() {
        assertEquals(800, EAT_ANIMATION_DURATION_MILLIS)

        val start = eatAnimationFrame(actualRecovery = 5, progress = 0f)
        assertEquals(1f, start.playerScale)
        assertEquals(0f, start.labelAlpha)
        assertEquals(0f, start.labelLift)
        assertEquals(5, start.icons.size)
        start.icons.forEach { icon ->
            assertEquals(0f, icon.progress)
            assertEquals(0.25f, icon.scale)
            assertEquals(0f, icon.alpha)
            assertEquals(0f, icon.lift)
        }

        val end = eatAnimationFrame(actualRecovery = 5, progress = 1f)
        assertEquals(1f, end.playerScale)
        assertEquals(0f, end.labelAlpha)
        assertEquals(1f, end.labelScale)
        assertEquals(1f, end.labelLift)
        end.icons.forEach { icon ->
            assertEquals(1f, icon.progress)
            assertEquals(0.25f, icon.scale)
            assertEquals(0f, icon.alpha)
            assertEquals(0f, icon.lift)
        }
    }

    @Test
    fun `one to five recovery icons launch in order with a visible stagger`() {
        val frame = eatAnimationFrame(actualRecovery = 5, progress = 0.35f)

        assertEquals(5, frame.icons.size)
        frame.icons.zipWithNext().forEach { (earlier, later) ->
            assertTrue(earlier.progress > later.progress)
        }
        assertTrue(frame.icons.first().progress > 0f)
        assertEquals(0f, frame.icons.last().progress)
    }

    @Test
    fun `zero recovery has no icons while the label still enters and exits softly`() {
        assertTrue(eatAnimationFrame(actualRecovery = 0, progress = 0.4f).icons.isEmpty())
        assertTrue(eatAnimationFrame(actualRecovery = -1, progress = 0.4f).icons.isEmpty())
        assertEquals(5, eatAnimationFrame(actualRecovery = 8, progress = 0.4f).icons.size)

        val entered = eatAnimationFrame(actualRecovery = 0, progress = 0.4f)
        val exiting = eatAnimationFrame(actualRecovery = 0, progress = 0.85f)
        assertEquals(1f, entered.labelAlpha, 0.0001f)
        assertTrue(exiting.labelAlpha in 0f..1f)
        assertTrue(exiting.labelAlpha < entered.labelAlpha)
        assertEquals(0f, eatAnimationFrame(actualRecovery = 0, progress = 1f).labelAlpha)
    }

    @Test
    fun `frame calculation clamps timer overshoot and treats NaN as the start`() {
        assertEquals(eatAnimationFrame(3, 0f), eatAnimationFrame(3, -0.2f))
        assertEquals(eatAnimationFrame(3, 0f), eatAnimationFrame(3, Float.NaN))
        assertEquals(eatAnimationFrame(3, 1f), eatAnimationFrame(3, 1.2f))
        assertEquals(eatAnimationFrame(3, 1f), eatAnimationFrame(3, Float.POSITIVE_INFINITY))
    }
}
