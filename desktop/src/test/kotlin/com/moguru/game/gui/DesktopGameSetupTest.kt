package com.moguru.game.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DesktopGameSetupTest {
    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2, 3, 4, 5])
    fun `cancelling any setup dialog leaves the new game unconfirmed`(cancelledDialog: Int) {
        // Count, two mole/nest pairs, then starting player.
        var dialog = 0
        val setup = collectDesktopGameSetup { _, _, choices ->
            if (dialog++ == cancelledDialog) null else choices.first()
        }
        assertNull(setup, "Cancel at dialog $cancelledDialog must abort setup")
    }

    @Test
    fun `confirmed setup preserves chosen order nests and starting player`() {
        val setup = collectDesktopGameSetup { _, title, choices ->
            when (title) {
                "新しいゲーム" -> "4"
                else -> choices.last()
            }
        }
        assertNotNull(setup)
        setup!!
        assertEquals(4, setup.players.size)
        assertEquals(4, setup.players.map { it.playerId }.distinct().size)
        assertEquals(4, setup.players.map { it.nestPosition }.distinct().size)
        assertEquals(3, setup.startPlayerIndex)
    }
}
