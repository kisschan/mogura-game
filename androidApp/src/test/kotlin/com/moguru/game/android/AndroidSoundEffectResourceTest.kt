package com.moguru.game.android

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidSoundEffectResourceTest {
    @Test
    fun `button press raw resource is generated`() {
        assertTrue(R.raw.button_press != 0)
    }

    @Test
    fun `tile rotate raw resource is generated`() {
        assertTrue(R.raw.tile_rotate != 0)
    }
}
