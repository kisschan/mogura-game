package com.moguru.game.android

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidSoundEffectResourceTest {
    @Test
    fun `every sound effect has a generated raw resource`() {
        val resources = androidSoundEffectResourceIds()

        assertEquals(AndroidSoundEffect.entries.toSet(), resources.keys)
        assertEquals(R.raw.capture_failure, resources[AndroidSoundEffect.CAPTURE_FAILURE])
        assertTrue(resources.values.all { it != 0 })
    }
}
