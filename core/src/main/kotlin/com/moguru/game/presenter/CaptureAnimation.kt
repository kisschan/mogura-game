package com.moguru.game.presenter

import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import kotlin.math.PI
import kotlin.math.sin

/** A resolved board action, independent of how often a screen is redrawn. */
data class CaptureAnimationEvent(
    val id: Long,
    val kind: CaptureOutcomeKind,
    val playerId: Int,
    val foodType: FoodType,
    val source: Position,
    val foodIndex: Int,
    val destination: Position?,
)

const val CAPTURE_SUCCESS_DURATION_MILLIS = 720
const val CAPTURE_ESCAPE_DURATION_MILLIS = 680

/**
 * Transform values shared by the Android and desktop board renderers.
 *
 * [playerApproach] runs from the pawn's resting position (0) to the food (1),
 * with negative values for the initial wind-up. [foodTravel] runs from the
 * food's original position (0) to the pawn on success or escape destination (1).
 * [foodLift] is an upward offset expressed as a fraction of a board cell's height.
 */
data class CaptureAnimationFrame(
    val playerApproach: Float = 0f,
    val playerScale: Float = 1f,
    val playerRotationDegrees: Float = 0f,
    val foodTravel: Float = 0f,
    val foodScale: Float = 1f,
    val foodRotationDegrees: Float = 0f,
    val foodAlpha: Float = 1f,
    val foodLift: Float = 0f,
)

/** Pure timing curve; callers own playback, cancellation and reduced-motion handling. */
fun captureAnimationFrame(kind: CaptureOutcomeKind, progress: Float): CaptureAnimationFrame {
    val p = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
    return when (kind) {
        CaptureOutcomeKind.CAPTURED -> capturedFrame(p)
        CaptureOutcomeKind.ESCAPED -> escapedFrame(p)
    }
}

private fun capturedFrame(p: Float): CaptureAnimationFrame {
    val pawn = when {
        p < 0.16f -> {
            val t = smooth(unit(p, 0f, 0.16f))
            CaptureAnimationFrame(
                playerApproach = mix(0f, -0.18f, t),
                playerScale = mix(1f, 0.95f, t),
                playerRotationDegrees = mix(0f, -8f, t),
            )
        }
        p < 0.34f -> {
            val t = unit(p, 0.16f, 0.34f).let { it * it }
            CaptureAnimationFrame(
                playerApproach = mix(-0.18f, 1f, t),
                playerScale = mix(0.95f, 1.08f, t),
                playerRotationDegrees = mix(-8f, 8f, t),
            )
        }
        // A 72 ms contact hold makes the existing still images feel like a firm grab.
        p < 0.44f -> CaptureAnimationFrame(
            playerApproach = 1f,
            playerScale = 1.08f,
            playerRotationDegrees = 8f,
        )
        else -> {
            val t = easeOut(unit(p, 0.44f, 0.92f))
            CaptureAnimationFrame(
                playerApproach = mix(1f, 0f, t),
                playerScale = mix(1.08f, 1f, t),
                playerRotationDegrees = mix(8f, 0f, t),
            )
        }
    }
    val contact = smooth(unit(p, 0.29f, 0.34f))
    val gathered = smooth(unit(p, 0.44f, 0.90f))
    return pawn.copy(
        foodTravel = gathered,
        foodScale = mix(mix(1f, 0.78f, contact), 0.24f, gathered),
        foodRotationDegrees = mix(mix(0f, -8f, contact), 0f, gathered),
        foodAlpha = 1f - smooth(unit(p, 0.60f, 0.90f)),
    )
}

private fun escapedFrame(p: Float): CaptureAnimationFrame {
    val pawn = when {
        p < 0.14f -> {
            val t = smooth(unit(p, 0f, 0.14f))
            CaptureAnimationFrame(
                playerApproach = mix(0f, -0.15f, t),
                playerScale = mix(1f, 0.96f, t),
                playerRotationDegrees = mix(0f, -7f, t),
            )
        }
        p < 0.34f -> {
            val t = unit(p, 0.14f, 0.34f).let { it * it }
            CaptureAnimationFrame(
                playerApproach = mix(-0.15f, 1f, t),
                playerScale = mix(0.96f, 1.05f, t),
                playerRotationDegrees = mix(-7f, 12f, t),
            )
        }
        p < 0.60f -> {
            val t = easeOut(unit(p, 0.34f, 0.60f))
            CaptureAnimationFrame(
                playerApproach = mix(1f, -0.08f, t),
                playerScale = mix(1.05f, 0.98f, t),
                playerRotationDegrees = mix(12f, -4f, t),
            )
        }
        else -> {
            val t = smooth(unit(p, 0.60f, 0.92f))
            CaptureAnimationFrame(
                playerApproach = mix(-0.08f, 0f, t),
                playerScale = mix(0.98f, 1f, t),
                playerRotationDegrees = mix(-4f, 0f, t),
            )
        }
    }
    val travel = smooth(unit(p, 0.27f, 0.91f))
    val hop = if (travel > 0f && travel < 1f) sin(PI * travel).toFloat() else 0f
    return pawn.copy(
        foodTravel = travel,
        foodRotationDegrees = sin(PI * 4 * travel).toFloat() * 9f * hop,
        foodLift = hop * 0.16f,
    )
}

private fun unit(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

private fun mix(from: Float, to: Float, t: Float): Float = from + (to - from) * t
