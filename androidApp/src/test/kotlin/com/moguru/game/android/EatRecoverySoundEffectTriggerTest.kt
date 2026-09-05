package com.moguru.game.android

import com.moguru.game.model.FoodType
import com.moguru.game.presenter.EatAnimationEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EatRecoverySoundEffectTriggerTest {
    @Test
    fun `each eat event triggers the recovery sound exactly once`() {
        val trigger = EatRecoverySoundEffectTrigger()
        val first = event(11L)
        val next = event(12L)

        assertEquals(AndroidSoundEffect.EAT_RECOVERY, trigger.soundEffectFor(first))
        assertNull(trigger.soundEffectFor(first))
        assertNull(trigger.soundEffectFor(null))
        assertNull(trigger.soundEffectFor(first))
        assertEquals(AndroidSoundEffect.EAT_RECOVERY, trigger.soundEffectFor(next))
        assertNull(trigger.soundEffectFor(next))
        assertNull(trigger.soundEffectFor(first))
    }

    private fun event(id: Long): EatAnimationEvent =
        EatAnimationEvent(
            id = id,
            playerId = 0,
            foodType = FoodType.MOLE_CRICKET,
            requestedRecovery = 3,
            actualRecovery = 3,
            healthBefore = 8,
            healthAfter = 11,
        )
}
