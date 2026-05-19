package com.moguru.game.gui

import com.moguru.game.model.Rotation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DigRotationDisplayTest {

    @Test
    fun `rotation display resets to zero when no dig tile is pending`() {
        val selected = rotationSelectionForPendingDig(hasPendingDig = false, pendingRotation = Rotation.DEG_180)

        assertEquals(Rotation.DEG_0, selected)
    }

    @Test
    fun `rotation display follows pending dig rotation while tile is pending`() {
        val selected = rotationSelectionForPendingDig(hasPendingDig = true, pendingRotation = Rotation.DEG_180)

        assertEquals(Rotation.DEG_180, selected)
    }

    @Test
    fun `rotation display defaults to zero when pending rotation is absent`() {
        val selected = rotationSelectionForPendingDig(hasPendingDig = true, pendingRotation = null)

        assertEquals(Rotation.DEG_0, selected)
    }
}
