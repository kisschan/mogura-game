package com.moguru.game.android

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidBackgroundMusicResourceTest {
    @Test
    fun `background music raw resource is generated`() {
        assertTrue(R.raw.burrowed_logic != 0)
    }
}
