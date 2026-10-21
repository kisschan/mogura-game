package com.moguru.game.android

import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.presenter.CaptureAnimationEvent
import com.moguru.game.presenter.CaptureOutcomeKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CaptureFailureSoundEffectTriggerTest {
    @Test
    fun `each escaped event triggers capture failure once`() {
        val trigger = CaptureFailureSoundEffectTrigger()
        val firstEscape = event(11L, CaptureOutcomeKind.ESCAPED)
        val nextEscape = event(12L, CaptureOutcomeKind.ESCAPED)

        assertEquals(AndroidSoundEffect.CAPTURE_FAILURE, trigger.soundEffectFor(firstEscape))
        assertNull(trigger.soundEffectFor(firstEscape))
        assertNull(trigger.soundEffectFor(null))
        assertNull(trigger.soundEffectFor(firstEscape))
        assertEquals(AndroidSoundEffect.CAPTURE_FAILURE, trigger.soundEffectFor(nextEscape))
        assertNull(trigger.soundEffectFor(nextEscape))
        assertNull(trigger.soundEffectFor(firstEscape))
    }

    @Test
    fun `captured events never trigger or consume capture failure sound`() {
        val trigger = CaptureFailureSoundEffectTrigger()

        assertNull(trigger.soundEffectFor(event(21L, CaptureOutcomeKind.CAPTURED)))
        assertEquals(
            AndroidSoundEffect.CAPTURE_FAILURE,
            trigger.soundEffectFor(event(21L, CaptureOutcomeKind.ESCAPED)),
        )
    }

    @Test
    fun `a later playback generation plays its reused IDs once and an earlier one stays silent`() {
        val trigger = CaptureFailureSoundEffectTrigger()
        val escape = event(11L, CaptureOutcomeKind.ESCAPED)
        val nextEscape = event(12L, CaptureOutcomeKind.ESCAPED)

        assertEquals(AndroidSoundEffect.CAPTURE_FAILURE, trigger.soundEffectFor(nextEscape, generation = 1L))
        assertNull(trigger.soundEffectFor(escape, generation = 1L))
        // A restarted chapter numbers its captures again.
        assertEquals(AndroidSoundEffect.CAPTURE_FAILURE, trigger.soundEffectFor(escape, generation = 2L))
        assertNull(trigger.soundEffectFor(escape, generation = 2L))
        assertNull(trigger.soundEffectFor(nextEscape, generation = 1L))
        assertNull(trigger.soundEffectFor(event(13L, CaptureOutcomeKind.ESCAPED), generation = 1L))
        assertNull(trigger.soundEffectFor(event(11L, CaptureOutcomeKind.CAPTURED), generation = 3L))
        assertEquals(AndroidSoundEffect.CAPTURE_FAILURE, trigger.soundEffectFor(nextEscape, generation = 2L))
    }

    private fun event(id: Long, kind: CaptureOutcomeKind): CaptureAnimationEvent =
        CaptureAnimationEvent(
            id = id,
            kind = kind,
            playerId = 0,
            foodType = FoodType.BEETLE_LARVA,
            source = Position(2, 2),
            foodIndex = 0,
            destination = if (kind == CaptureOutcomeKind.ESCAPED) Position(2, 1) else null,
        )
}
