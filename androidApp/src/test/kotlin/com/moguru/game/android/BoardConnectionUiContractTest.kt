package com.moguru.game.android

import com.moguru.game.model.CellType
import com.moguru.game.model.Direction
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoardConnectionUiContractTest {
    @Test
    fun `face up tiles expose rotated connection ports`() {
        assertEquals(
            setOf(Direction.TOP, Direction.LEFT),
            androidTileOpenSides(TileShape.L_SHAPE, Rotation.DEG_90, isFaceDown = false),
        )
    }

    @Test
    fun `face down tiles hide connection ports`() {
        assertEquals(
            emptySet<Direction>(),
            androidTileOpenSides(TileShape.CROSS, Rotation.DEG_0, isFaceDown = true),
        )
    }

    @Test
    fun `current tile classifies connected and blocked open edges`() {
        assertEquals(
            AndroidConnectionTone.CONNECTED,
            connectionToneFor(
                currentOpenSides = setOf(Direction.RIGHT),
                currentCellType = CellType.UNDERGROUND,
                neighborOpenSides = setOf(Direction.LEFT),
                neighborCellType = CellType.UNDERGROUND,
                direction = Direction.RIGHT,
                isCurrentCell = true,
            ),
        )
        assertEquals(
            AndroidConnectionTone.BLOCKED,
            connectionToneFor(
                currentOpenSides = setOf(Direction.RIGHT),
                currentCellType = CellType.UNDERGROUND,
                neighborOpenSides = setOf(Direction.TOP),
                neighborCellType = CellType.UNDERGROUND,
                direction = Direction.RIGHT,
                isCurrentCell = true,
            ),
        )
        assertNull(
            connectionToneFor(
                currentOpenSides = setOf(Direction.RIGHT),
                currentCellType = CellType.UNDERGROUND,
                neighborOpenSides = setOf(Direction.LEFT),
                neighborCellType = CellType.UNDERGROUND,
                direction = Direction.TOP,
                isCurrentCell = true,
            ),
        )
    }

    @Test
    fun `non current tiles show open ports without judging neighbors`() {
        assertEquals(
            AndroidConnectionTone.OPEN,
            connectionToneFor(
                currentOpenSides = setOf(Direction.BOTTOM),
                currentCellType = CellType.UNDERGROUND,
                neighborOpenSides = null,
                neighborCellType = CellType.UNDERGROUND,
                direction = Direction.BOTTOM,
                isCurrentCell = false,
            ),
        )
    }

    @Test
    fun `current nest edge follows movement cell rules`() {
        assertEquals(
            AndroidConnectionTone.BLOCKED,
            connectionToneFor(
                currentOpenSides = emptySet(),
                currentCellType = CellType.NEST,
                neighborOpenSides = setOf(Direction.BOTTOM),
                neighborCellType = CellType.GROUND,
                direction = Direction.TOP,
                isCurrentCell = true,
            ),
        )
        assertEquals(
            AndroidConnectionTone.CONNECTED,
            connectionToneFor(
                currentOpenSides = emptySet(),
                currentCellType = CellType.NEST,
                neighborOpenSides = setOf(Direction.LEFT),
                neighborCellType = CellType.UNDERGROUND,
                direction = Direction.RIGHT,
                isCurrentCell = true,
            ),
        )
    }

    @Test
    fun `connected and blocked ports are visually stronger than open ports`() {
        assertTrue(connectionPortStrokeWidth(AndroidConnectionTone.CONNECTED) > connectionPortStrokeWidth(AndroidConnectionTone.OPEN))
        assertTrue(connectionPortStrokeWidth(AndroidConnectionTone.BLOCKED) > connectionPortStrokeWidth(AndroidConnectionTone.OPEN))
    }
}
