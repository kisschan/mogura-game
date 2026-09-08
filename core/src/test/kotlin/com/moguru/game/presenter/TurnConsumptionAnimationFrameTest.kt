package com.moguru.game.presenter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TurnConsumptionAnimationFrameTest {
    @Test
    fun `animation has clean endpoints and a 500 millisecond duration`() {
        assertEquals(500, TURN_CONSUMPTION_ANIMATION_DURATION_MILLIS)

        assertEquals(
            TurnConsumptionAnimationFrame(
                markerProgress = 0f,
                labelAlpha = 0f,
                labelScale = 0.25f,
                labelLift = 0f,
            ),
            turnConsumptionAnimationFrame(0f),
        )
        assertEquals(
            TurnConsumptionAnimationFrame(
                markerProgress = 1f,
                labelAlpha = 0f,
                labelScale = 0.96f,
                labelLift = 1f,
            ),
            turnConsumptionAnimationFrame(1f),
        )
    }

    @Test
    fun `frame calculation clamps overshoot and treats NaN as the start`() {
        assertEquals(turnConsumptionAnimationFrame(0f), turnConsumptionAnimationFrame(-0.2f))
        assertEquals(turnConsumptionAnimationFrame(0f), turnConsumptionAnimationFrame(Float.NaN))
        assertEquals(turnConsumptionAnimationFrame(1f), turnConsumptionAnimationFrame(1.2f))
        assertEquals(turnConsumptionAnimationFrame(1f), turnConsumptionAnimationFrame(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `marker and label lift are monotonic without scale bounce`() {
        var previousMarkerProgress = 0f
        var previousLabelLift = 0f
        for (step in 0..100) {
            val frame = turnConsumptionAnimationFrame(step / 100f)
            assertTrue(frame.markerProgress >= previousMarkerProgress)
            assertTrue(frame.labelLift >= previousLabelLift)
            assertTrue(frame.labelAlpha in 0f..1f)
            assertTrue(frame.labelScale in 0.25f..1f)
            previousMarkerProgress = frame.markerProgress
            previousLabelLift = frame.labelLift
        }

        val held = turnConsumptionAnimationFrame(0.5f)
        val exiting = turnConsumptionAnimationFrame(0.9f)
        assertEquals(1f, held.labelAlpha)
        assertTrue(exiting.labelAlpha >= 0f && exiting.labelAlpha < held.labelAlpha)
    }
}
