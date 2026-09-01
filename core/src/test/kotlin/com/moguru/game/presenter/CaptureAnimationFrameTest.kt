package com.moguru.game.presenter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CaptureAnimationFrameTest {
    @Test
    fun `both animations start at rest and return the pawn to rest`() {
        CaptureOutcomeKind.entries.forEach { kind ->
            assertEquals(CaptureAnimationFrame(), captureAnimationFrame(kind, 0f))
            val end = captureAnimationFrame(kind, 1f)
            assertEquals(0f, end.playerApproach, 0.0001f)
            assertEquals(1f, end.playerScale, 0.0001f)
            assertEquals(0f, end.playerRotationDegrees, 0.0001f)
            assertEquals(0f, end.foodLift, 0.0001f)
            assertEquals(1f, end.foodTravel, 0.0001f)
        }
        val caught = captureAnimationFrame(CaptureOutcomeKind.CAPTURED, 1f)
        assertEquals(0f, caught.foodAlpha)
        assertTrue(caught.foodScale < 1f)
        val escaped = captureAnimationFrame(CaptureOutcomeKind.ESCAPED, 1f)
        assertEquals(1f, escaped.foodAlpha)
        assertEquals(1f, escaped.foodScale)
    }

    @Test
    fun `success winds up and holds both images together before gathering the food`() {
        assertTrue(captureAnimationFrame(CaptureOutcomeKind.CAPTURED, 0.12f).playerApproach < 0f)
        val firstContact = captureAnimationFrame(CaptureOutcomeKind.CAPTURED, 0.36f)
        val lastContact = captureAnimationFrame(CaptureOutcomeKind.CAPTURED, 0.42f)
        assertEquals(firstContact, lastContact, "Keep the contact still briefly to convey a firm grab")
        assertEquals(1f, firstContact.playerApproach)
        assertEquals(1f, firstContact.foodAlpha)
        val gathering = captureAnimationFrame(CaptureOutcomeKind.CAPTURED, 0.7f)
        assertTrue(gathering.playerApproach < firstContact.playerApproach)
        assertTrue(gathering.foodTravel > firstContact.foodTravel)
        assertTrue(gathering.foodScale < firstContact.foodScale)
        assertTrue(gathering.foodAlpha < firstContact.foodAlpha)
    }

    @Test
    fun `escaping food leaves before the pawn lands and moves toward its destination without fading`() {
        val contact = captureAnimationFrame(CaptureOutcomeKind.ESCAPED, 0.34f)
        assertEquals(1f, contact.playerApproach)
        assertTrue(contact.foodTravel > 0f)
        assertTrue(contact.foodLift > 0f)
        var previousTravel = 0f
        for (step in 0..100) {
            val frame = captureAnimationFrame(CaptureOutcomeKind.ESCAPED, step / 100f)
            assertTrue(frame.foodTravel >= previousTravel)
            assertEquals(1f, frame.foodAlpha)
            assertTrue(frame.foodLift >= 0f)
            previousTravel = frame.foodTravel
        }
    }

    @Test
    fun `frame calculation clamps timer overshoot and tolerates invalid initial progress`() {
        CaptureOutcomeKind.entries.forEach { kind ->
            assertEquals(captureAnimationFrame(kind, 0f), captureAnimationFrame(kind, -0.2f))
            assertEquals(captureAnimationFrame(kind, 0f), captureAnimationFrame(kind, Float.NaN))
            assertEquals(captureAnimationFrame(kind, 1f), captureAnimationFrame(kind, 1.2f))
            assertEquals(captureAnimationFrame(kind, 1f), captureAnimationFrame(kind, Float.POSITIVE_INFINITY))
        }
    }
}
