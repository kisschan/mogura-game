package com.moguru.game.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.presenter.DigTileChoice

internal class ClockwiseDigRotationTarget(initialRotation: Rotation) {
    private var previousRotation = initialRotation
    var degrees = initialRotation.steps * 90f
        private set

    fun degreesFor(rotation: Rotation): Float =
        degrees + ((rotation.steps - previousRotation.steps + 4) % 4) * 90f

    fun update(rotation: Rotation): Float {
        degrees = degreesFor(rotation)
        previousRotation = rotation
        return degrees
    }
}

/** Symmetric ports lose angle information; preview the player's complete selected rotation. */
internal fun digPreviewRotationFor(state: AndroidGameUiState, cell: AndroidBoardCellUiState): Rotation =
    if (state.digPreviewPosition == cell.position) state.playState.selectedRotation
    else cell.tile?.rotation ?: Rotation.DEG_0

@Composable
internal fun rememberDigPreviewRotation(
    position: Position,
    shape: TileShape,
    choice: DigTileChoice?,
    generation: Long,
    rotation: Rotation,
): State<Float> = key(position, shape, choice, generation) {
    val target = remember { ClockwiseDigRotationTarget(rotation) }
    val degrees = target.degreesFor(rotation)
    // Only an applied composition advances the rotation history.
    SideEffect { target.update(rotation) }
    if (gameMotionEnabled()) {
        animateFloatAsState(degrees, tween(durationMillis = 140), label = "dig-clockwise-preview")
    } else {
        rememberUpdatedState(rotation.steps * 90f)
    }
}
