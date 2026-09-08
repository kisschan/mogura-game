package com.moguru.game.android

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.moguru.game.presenter.EAT_ANIMATION_DURATION_MILLIS
import com.moguru.game.presenter.EatAnimationEvent
import com.moguru.game.presenter.displayName
import com.moguru.game.presenter.eatAnimationFrame
import kotlin.math.PI
import kotlin.math.sin

internal const val BOARD_EAT_ANIMATION_Z = 90f
internal const val EAT_ANIMATION_INPUT_BLOCKER_TEST_TAG = "eat-animation-input-blocker"
internal const val EAT_ANIMATION_LABEL_TEST_TAG = "eat-animation-label"

@Composable
internal fun EatAnimationOverlay(
    state: AndroidGameUiState,
    event: EatAnimationEvent,
    maxWidth: Dp,
    maxHeight: Dp,
    pieceAlpha: Float,
    onFinished: (Long) -> Unit,
) {
    val progress = remember(event.id) { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)
    val geometry = remember(event, state.boardState, state.hungerMarkers) {
        eatAnimationGeometry(state, event)
    }
    val boardWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
    val boardHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

    LaunchedEffect(event.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = EAT_ANIMATION_DURATION_MILLIS,
                easing = LinearEasing,
            ),
        )
        finish(event.id)
    }

    val frame = eatAnimationFrame(event.actualRecovery, progress.value)

    // Replace the hidden board pawn with the same image so the small eating pulse has no duplicate.
    Box(
        modifier = Modifier
            .boardRect(maxWidth, maxHeight, geometry.player)
            .zIndex(BOARD_EAT_ANIMATION_Z)
            .testTag("eat-animated-player")
            .graphicsLayer {
                scaleX = frame.playerScale
                scaleY = frame.playerScale
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

    val markerProgress = eatHungerMarkerProgress(progress.value)
    val markerHealth = event.healthBefore + (event.healthAfter - event.healthBefore) * markerProgress
    val marker = hungerMarkerRect(
        health = markerHealth,
        index = geometry.markerIndex,
    )
    Box(
        modifier = Modifier
            .boardRect(maxWidth, maxHeight, geometry.markerStart)
            .zIndex(BOARD_EAT_ANIMATION_Z + 3f)
            .testTag("eat-animated-hunger-marker")
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
                .border(2.dp, Color(0xFF2E2115), RoundedCornerShape(999.dp))
                .padding(1.dp)
                .border(1.dp, Color.White, RoundedCornerShape(999.dp)),
        )
    }

    frame.icons.forEachIndexed { index, iconFrame ->
        val end = geometry.iconEnd.copy(
            left = geometry.iconEnd.left + (index - (frame.icons.lastIndex / 2f)) * geometry.iconEnd.width * 0.10f,
            top = geometry.iconEnd.top + (index % 2) * geometry.iconEnd.height * 0.08f,
        )
        val icon = interpolateCaptureRect(geometry.iconStart, end, iconFrame.progress).let { rect ->
            val direction = if (index % 2 == 0) 1f else -1f
            rect.copy(
                left = rect.left + sin(iconFrame.progress * PI).toFloat() * geometry.iconStart.width * 0.34f * direction,
                top = rect.top - iconFrame.lift * geometry.player.height,
            )
        }
        ProgrammaticMeatIcon(
            modifier = Modifier
                .boardRect(maxWidth, maxHeight, geometry.iconStart)
                .zIndex(BOARD_EAT_ANIMATION_Z + 2f + index * 0.01f)
                .testTag("eat-animation-icon-$index")
                .graphicsLayer {
                    translationX = (icon.left - geometry.iconStart.left) * boardWidthPx
                    translationY = (icon.top - geometry.iconStart.top) * boardHeightPx
                    scaleX = iconFrame.scale
                    scaleY = iconFrame.scale
                    alpha = iconFrame.alpha
                    rotationZ = -18f + index * 8f + iconFrame.progress * 14f
                },
        )
    }

    val labelLeft = (geometry.markerEnd.left + geometry.markerEnd.width * 0.78f).coerceIn(0.02f, 0.82f)
    val labelTop = (geometry.markerEnd.top - geometry.markerEnd.height * 0.35f).coerceIn(0.01f, 0.92f)
    Box(
        modifier = Modifier
            .offset(x = maxWidth * labelLeft, y = maxHeight * labelTop)
            .zIndex(BOARD_EAT_ANIMATION_Z + 4f)
            .testTag(EAT_ANIMATION_LABEL_TEST_TAG)
            .graphicsLayer {
                alpha = frame.labelAlpha
                scaleX = frame.labelScale
                scaleY = frame.labelScale
                translationY = -frame.labelLift * geometry.markerEnd.height * boardHeightPx
            }
            .background(Color(0xEEFFFBEA), RoundedCornerShape(8.dp))
            .border(1.5.dp, Color(0xFF075B2D), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = recoveryLabel(event.actualRecovery),
            color = Color(0xFF075B2D),
            fontSize = 16.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        )
    }
}

/** A transparent sibling blocks every game control, including system back, during playback. */
@Composable
internal fun EatAnimationInputBlocker(event: EatAnimationEvent) {
    BackHandler {}
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
            .testTag(EAT_ANIMATION_INPUT_BLOCKER_TEST_TAG)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = recoveryAnnouncement(event)
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

internal data class EatAnimationGeometry(
    val player: BoardRectSpec,
    val markerIndex: Int,
    val markerStart: BoardRectSpec,
    val markerEnd: BoardRectSpec,
    val iconStart: BoardRectSpec,
    val iconEnd: BoardRectSpec,
)

internal fun eatAnimationGeometry(
    state: AndroidGameUiState,
    event: EatAnimationEvent,
): EatAnimationGeometry {
    val markerIndex = state.hungerMarkers
        .firstOrNull { it.playerId == event.playerId }
        ?.layoutIndex
        ?: 0
    val markerStart = hungerMarkerRect(event.healthBefore, markerIndex)
    val markerEnd = hungerMarkerRect(event.healthAfter, markerIndex)
    val playerCell = state.boardState.cells.firstOrNull { cell ->
        cell.players.any { it.playerId == event.playerId }
    }
    val playerIndex = playerCell?.players?.indexOfFirst { it.playerId == event.playerId }?.coerceAtLeast(0) ?: 0
    val player = playerCell?.let { cell ->
        playerRect(cell.position, playerIndex, cell.players.size)
    } ?: markerStart
    val iconSize = (minOf(player.width, player.height) * 0.42f).coerceAtLeast(0.026f)
    val iconStart = centeredBoardRect(player, iconSize)
    val iconEnd = centeredBoardRect(markerEnd, iconSize)
    return EatAnimationGeometry(player, markerIndex, markerStart, markerEnd, iconStart, iconEnd)
}

internal fun recoveryLabel(actualRecovery: Int): String =
    if (actualRecovery > 0) "+$actualRecovery" else "満腹"

internal fun recoveryAnnouncement(event: EatAnimationEvent): String =
    if (event.actualRecovery > 0) {
        "${event.foodType.displayName()}を食べて、体力が${event.actualRecovery}回復しました"
    } else {
        "${event.foodType.displayName()}を食べました。満腹です"
    }

internal fun eatHungerMarkerProgress(progress: Float): Float {
    val normalized = (progress / 0.72f).coerceIn(0f, 1f)
    return normalized * normalized * (3f - 2f * normalized)
}

private fun centeredBoardRect(container: BoardRectSpec, size: Float): BoardRectSpec =
    BoardRectSpec(
        left = container.left + (container.width - size) / 2f,
        top = container.top + (container.height - size) / 2f,
        width = size,
        height = size,
    )

@Composable
private fun ProgrammaticMeatIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val outline = Color(0xFF4A2417)
        val bone = Color(0xFFFFE5AD)
        val meat = Color(0xFFF2994A)
        val highlight = Color(0xFFFFC66D)
        val boneStroke = size.minDimension * 0.24f

        drawLine(
            color = outline,
            start = Offset(size.width * 0.23f, size.height * 0.79f),
            end = Offset(size.width * 0.78f, size.height * 0.24f),
            strokeWidth = boneStroke + size.minDimension * 0.09f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = bone,
            start = Offset(size.width * 0.23f, size.height * 0.79f),
            end = Offset(size.width * 0.78f, size.height * 0.24f),
            strokeWidth = boneStroke,
            cap = StrokeCap.Round,
        )
        // Two overlapping orange lobes echo the food-card symbol without reusing its artwork.
        drawOval(
            color = outline,
            topLeft = Offset(size.width * 0.27f, size.height * 0.18f),
            size = Size(size.width * 0.52f, size.height * 0.48f),
        )
        drawOval(
            color = meat,
            topLeft = Offset(size.width * 0.31f, size.height * 0.22f),
            size = Size(size.width * 0.44f, size.height * 0.40f),
        )
        drawOval(
            color = outline,
            topLeft = Offset(size.width * 0.44f, size.height * 0.09f),
            size = Size(size.width * 0.46f, size.height * 0.50f),
        )
        drawOval(
            color = meat,
            topLeft = Offset(size.width * 0.48f, size.height * 0.13f),
            size = Size(size.width * 0.38f, size.height * 0.42f),
        )
        drawOval(
            color = highlight,
            topLeft = Offset(size.width * 0.50f, size.height * 0.19f),
            size = Size(size.width * 0.20f, size.height * 0.10f),
        )
        drawCircle(bone, size.minDimension * 0.12f, Offset(size.width * 0.20f, size.height * 0.82f))
        drawCircle(outline, size.minDimension * 0.15f, Offset(size.width * 0.20f, size.height * 0.82f), style = Stroke(size.minDimension * 0.06f))
        drawCircle(bone, size.minDimension * 0.10f, Offset(size.width * 0.82f, size.height * 0.18f))
        drawCircle(outline, size.minDimension * 0.13f, Offset(size.width * 0.82f, size.height * 0.18f), style = Stroke(size.minDimension * 0.05f))
    }
}
