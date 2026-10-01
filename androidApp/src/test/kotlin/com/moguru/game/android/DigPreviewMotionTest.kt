package com.moguru.game.android

import com.moguru.game.model.Rotation
import com.moguru.game.model.HoleTile
import com.moguru.game.model.TileShape
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DigPreviewMotionTest {
    @Test
    fun `straight preview follows all selected angles even when its ports repeat`() {
        assertSelectedPreviewRotations(TileShape.STRAIGHT)
    }

    @Test
    fun `cross preview follows all selected angles even when its ports never change`() {
        assertSelectedPreviewRotations(TileShape.CROSS)
    }

    private fun assertSelectedPreviewRotations(shape: TileShape) {
        val controller = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
        val vm = AndroidGameViewModel(controller)
        vm.startNewGame(2)
        val position = controller.digTargets().single()
        controller.engine!!.boardState.placeTile(position, HoleTile(shape))
        vm.onCellClicked(position)
        listOf(Rotation.DEG_90, Rotation.DEG_180, Rotation.DEG_270, Rotation.DEG_0).forEach { rotation ->
            vm.selectRotation(rotation)
            val state = vm.uiState.value
            assertEquals(rotation, digPreviewRotationFor(state, state.boardState.cells.single { it.position == position }))
        }
    }

    @Test
    fun `initial rotation is shown without accumulating an extra turn`() {
        Rotation.entries.forEach { rotation ->
            val target = ClockwiseDigRotationTarget(rotation)
            assertEquals(rotation.steps * 90f, target.degrees)
            assertEquals(target.degrees, target.update(rotation))
        }
    }

    @Test
    fun `clockwise wrap advances from 270 to 360 instead of rotating backwards`() {
        val target = ClockwiseDigRotationTarget(Rotation.DEG_270)
        assertEquals(360f, target.update(Rotation.DEG_0))
        assertEquals(450f, target.update(Rotation.DEG_90))
    }

    @Test
    fun `rapid rotation changes accumulate clockwise toward the latest committed target`() {
        val target = ClockwiseDigRotationTarget(Rotation.DEG_0)
        val expected = listOf(90f, 180f, 270f, 360f, 450f, 540f, 630f, 720f)
        val rotations = listOf(Rotation.DEG_90, Rotation.DEG_180, Rotation.DEG_270, Rotation.DEG_0)
        assertEquals(expected, List(8) { target.update(rotations[it % rotations.size]) })
    }

    @Test
    fun `coalesced targets advance in the clockwise direction`() {
        val target = ClockwiseDigRotationTarget(Rotation.DEG_270)
        assertEquals(450f, target.update(Rotation.DEG_90))
    }
}
