package com.moguru.game.android

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoardLayerZIndexTest {
    @Test
    fun `food cards render below player tokens on occupied cells`() {
        assertTrue(
            BOARD_TILE_Z < BOARD_FOOD_Z,
            "Food cards should remain above board tiles.",
        )
        assertTrue(
            BOARD_FOOD_Z < BOARD_PLAYER_BASE_Z,
            "Food cards must render below player tokens so occupied food cells stay readable.",
        )
        assertTrue(
            BOARD_PLAYER_BASE_Z < BOARD_CURRENT_PLAYER_OUTLINE_Z,
            "Current-player outline should stay above player tokens.",
        )
        assertTrue(
            BOARD_CURRENT_PLAYER_OUTLINE_Z < BOARD_CLICK_TARGET_Z,
            "Clickable cell overlay should remain the top board layer.",
        )
    }
}
