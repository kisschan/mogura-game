package com.moguru.game.android

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moguru.game.model.Position
import com.moguru.game.presenter.CAPTURE_ESCAPE_DURATION_MILLIS
import com.moguru.game.presenter.CAPTURE_SUCCESS_DURATION_MILLIS
import com.moguru.game.presenter.CaptureAnimationEvent
import com.moguru.game.presenter.CaptureOutcomeKind
import com.moguru.game.presenter.captureAnimationFrame
import com.moguru.game.presenter.displayName

internal const val BOARD_CAPTURE_ANIMATION_Z = 90f

/** No extra artwork: only the same card and cropped player image used by the board. */
@Composable
internal fun CaptureAnimationOverlay(
    animation: AndroidCaptureAnimationUiState,
    maxWidth: Dp,
    maxHeight: Dp,
    pieceAlpha: Float,
    onFinished: (Long) -> Unit,
) {
    val event = animation.event
    val progress = remember(event.id) { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)
    val geometry = remember(animation) { captureAnimationGeometry(animation) }
    val boardWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
    val boardHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

    LaunchedEffect(event.id) {
        // Animatable also respects the system animation duration scale, including zero.
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (event.kind == CaptureOutcomeKind.CAPTURED) {
                    CAPTURE_SUCCESS_DURATION_MILLIS
                } else {
                    CAPTURE_ESCAPE_DURATION_MILLIS
                },
                easing = LinearEasing,
            ),
        )
        finish(event.id)
    }

    val frame = captureAnimationFrame(event.kind, progress.value)
    val player = interpolateCaptureRect(geometry.playerStart, geometry.playerContact, frame.playerApproach)
    val food = interpolateCaptureRect(geometry.foodStart, geometry.foodEnd, frame.foodTravel).let {
        it.copy(top = it.top - geometry.cellHeight * frame.foodLift)
    }

    Box(
        modifier = Modifier
            .boardRect(maxWidth, maxHeight, geometry.playerStart)
            .zIndex(BOARD_CAPTURE_ANIMATION_Z)
            .testTag("capture-animated-player")
            .graphicsLayer {
                translationX = (player.left - geometry.playerStart.left) * boardWidthPx
                translationY = (player.top - geometry.playerStart.top) * boardHeightPx
                scaleX = frame.playerScale
                scaleY = frame.playerScale
                rotationZ = frame.playerRotationDegrees
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(999.dp))
                .border(2.dp, playerAccentColor(event.playerId), RoundedCornerShape(999.dp))
                .padding(2.dp),
        ) {
            BoardPlayerImage(
                playerId = event.playerId,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = pieceAlpha },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(3.dp, Color(0xFF2E2115), RoundedCornerShape(999.dp))
                .padding(2.dp)
                .border(2.dp, Color.White, RoundedCornerShape(999.dp)),
        )
    }
    Image(
        painter = painterResource(foodRes(event.foodType)),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .boardRect(maxWidth, maxHeight, geometry.foodStart)
            .zIndex(BOARD_CAPTURE_ANIMATION_Z + 1f)
            .testTag("capture-animated-food")
            .graphicsLayer {
                translationX = (food.left - geometry.foodStart.left) * boardWidthPx
                translationY = (food.top - geometry.foodStart.top) * boardHeightPx
                scaleX = frame.foodScale
                scaleY = frame.foodScale
                rotationZ = frame.foodRotationDegrees
                alpha = frame.foodAlpha * pieceAlpha
            },
    )
}

/** A transparent sibling blocks touch without putting gesture handlers on the images. */
@Composable
internal fun CaptureAnimationInputBlocker(event: CaptureAnimationEvent) {
    BackHandler {}
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
            .testTag("capture-animation-input-blocker")
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = if (event.kind == CaptureOutcomeKind.CAPTURED) {
                    "${event.foodType.displayName()}を捕獲しました"
                } else {
                    "${event.foodType.displayName()}が逃げました"
                }
            }
            .pointerInput(event.id) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            },
    )
}

internal fun isAnimatedCaptureFood(
    animation: AndroidCaptureAnimationUiState?,
    position: Position,
    index: Int,
): Boolean = animation?.event?.let { it.source == position && it.foodIndex == index } == true

internal data class CaptureAnimationGeometry(
    val playerStart: BoardRectSpec,
    val playerContact: BoardRectSpec,
    val foodStart: BoardRectSpec,
    val foodEnd: BoardRectSpec,
    val cellHeight: Float,
)

internal fun captureAnimationGeometry(animation: AndroidCaptureAnimationUiState): CaptureAnimationGeometry {
    val event = animation.event
    val source = animation.boardBefore.cells.firstOrNull { it.position == event.source }
    val playerIndex = source?.players?.indexOfFirst { it.playerId == event.playerId }?.coerceAtLeast(0) ?: 0
    val player = playerRect(event.source, playerIndex, source?.players?.size ?: 1)
    val food = foodRect(event.source, 0.76f, event.foodIndex, source?.foods?.size ?: 1)
    val cell = cellRect(event.source, 1f)
    val contact = player.copy(
        left = food.left + food.width / 2f - player.width / 2f + cell.width * 0.14f,
        top = food.top + food.height / 2f - player.height / 2f + cell.height * 0.10f,
    )
    val destination = event.destination
    val foodEnd = if (event.kind == CaptureOutcomeKind.ESCAPED && destination != null) {
        // Escape appends one revealed card. Use its index, not the first card of that type.
        val beforeCount = animation.boardBefore.cells.firstOrNull { it.position == destination }?.foods?.size ?: 0
        val afterCount = animation.boardAfter.cells.firstOrNull { it.position == destination }?.foods?.size ?: beforeCount + 1
        foodRect(destination, 0.76f, beforeCount, afterCount)
    } else {
        food.copy(
            left = player.left + player.width / 2f - food.width / 2f,
            top = player.top + player.height * 0.70f - food.height / 2f,
        )
    }
    return CaptureAnimationGeometry(player, contact, food, foodEnd, cell.height)
}

internal fun interpolateCaptureRect(start: BoardRectSpec, end: BoardRectSpec, progress: Float): BoardRectSpec =
    BoardRectSpec(
        left = start.left + (end.left - start.left) * progress,
        top = start.top + (end.top - start.top) * progress,
        width = start.width + (end.width - start.width) * progress,
        height = start.height + (end.height - start.height) * progress,
    )
