package com.moguru.game.android

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.moguru.game.presenter.TURN_CONSUMPTION_ANIMATION_DURATION_MILLIS
import com.moguru.game.presenter.TurnConsumptionAnimationEvent
import com.moguru.game.presenter.turnConsumptionAnimationFrame

internal const val BOARD_TURN_CONSUMPTION_ANIMATION_Z = 94f
internal const val TURN_CONSUMPTION_INPUT_BLOCKER_TEST_TAG = "turn-consumption-input-blocker"
internal const val TURN_CONSUMPTION_LABEL_TEST_TAG = "turn-consumption-animation-label"
internal const val TURN_CONSUMPTION_MARKER_TEST_TAG = "turn-consumption-animated-hunger-marker"

@Composable
internal fun TurnConsumptionAnimationOverlay(
    state: AndroidGameUiState,
    event: TurnConsumptionAnimationEvent,
    maxWidth: Dp,
    maxHeight: Dp,
    onFinished: (Long) -> Unit,
) {
    val progress = remember(event.id) { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)
    val geometry = remember(event, state.hungerMarkers) {
        turnConsumptionAnimationGeometry(state, event)
    }
    val boardWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
    val boardHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

    LaunchedEffect(event.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = TURN_CONSUMPTION_ANIMATION_DURATION_MILLIS,
                easing = LinearEasing,
            ),
        )
        finish(event.id)
    }

    val frame = turnConsumptionAnimationFrame(progress.value)
    val markerHealth = event.healthBefore +
        (event.healthAfter - event.healthBefore) * frame.markerProgress
    val marker = hungerMarkerRect(markerHealth, geometry.layoutIndex)

    // The static marker is hidden while this identical marker moves to the consumed value.
    Box(
        modifier = Modifier
            .boardRect(maxWidth, maxHeight, geometry.markerStart)
            .zIndex(BOARD_TURN_CONSUMPTION_ANIMATION_Z)
            .testTag(TURN_CONSUMPTION_MARKER_TEST_TAG)
            .graphicsLayer {
                translationX = (marker.left - geometry.markerStart.left) * boardWidthPx
                translationY = (marker.top - geometry.markerStart.top) * boardHeightPx
            },
    ) {
        Image(
            painter = painterResource(playerRes(event.playerId)),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(999.dp))
                .border(2.dp, Color(0xFF2E2115), RoundedCornerShape(999.dp))
                .padding(1.dp)
                .border(1.dp, Color.White, RoundedCornerShape(999.dp)),
        )
    }

    val labelLeft = (geometry.markerEnd.left + geometry.markerEnd.width * 0.70f)
        .coerceIn(0.02f, 0.68f)
    val labelTop = (geometry.markerEnd.top - geometry.markerEnd.height * 0.30f)
        .coerceIn(0.01f, 0.92f)
    Box(
        modifier = Modifier
            .offset(x = maxWidth * labelLeft, y = maxHeight * labelTop)
            .zIndex(BOARD_TURN_CONSUMPTION_ANIMATION_Z + 1f)
            .testTag(TURN_CONSUMPTION_LABEL_TEST_TAG)
            .graphicsLayer {
                alpha = frame.labelAlpha
                scaleX = frame.labelScale
                scaleY = frame.labelScale
                translationY = -frame.labelLift * geometry.markerEnd.height * boardHeightPx
            }
            .background(Color(0xF6FFF4EA), RoundedCornerShape(8.dp))
            .border(1.5.dp, Color(0xFF9F241C), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = turnConsumptionLabel(event),
            color = Color(0xFF9F241C),
            fontSize = 20.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        )
    }
}

/** Prevent all board, action-bar and system-back input until the one-shot playback ends. */
@Composable
internal fun TurnConsumptionAnimationInputBlocker(event: TurnConsumptionAnimationEvent) {
    BackHandler {}
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
            .testTag(TURN_CONSUMPTION_INPUT_BLOCKER_TEST_TAG)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = turnConsumptionAnnouncement(event)
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

internal data class TurnConsumptionAnimationGeometry(
    val layoutIndex: Int,
    val markerStart: BoardRectSpec,
    val markerEnd: BoardRectSpec,
)

internal fun turnConsumptionAnimationGeometry(
    state: AndroidGameUiState,
    event: TurnConsumptionAnimationEvent,
): TurnConsumptionAnimationGeometry {
    val layoutIndex = state.hungerMarkers
        .firstOrNull { it.playerId == event.playerId }
        ?.layoutIndex
        ?: 0
    return TurnConsumptionAnimationGeometry(
        layoutIndex = layoutIndex,
        markerStart = hungerMarkerRect(event.healthBefore, layoutIndex),
        markerEnd = hungerMarkerRect(event.healthAfter, layoutIndex),
    )
}

internal fun turnConsumptionLabel(event: TurnConsumptionAnimationEvent): String =
    "-${event.actualConsumption}"

internal fun turnConsumptionAnnouncement(event: TurnConsumptionAnimationEvent): String =
    if (event.isOnSurface) {
        "地上で手番を終え、体力を${event.actualConsumption}消耗しました"
    } else {
        "手番終了で、体力を${event.actualConsumption}消耗しました"
    }
