package com.moguru.game.presenter

import com.moguru.game.model.FoodType
import kotlin.math.PI
import kotlin.math.sin

/** A completed eat action, emitted once by the controller action result. */
data class EatAnimationEvent(
    val id: Long,
    val playerId: Int,
    val foodType: FoodType,
    val requestedRecovery: Int,
    val actualRecovery: Int,
    val healthBefore: Int,
    val healthAfter: Int,
)

const val EAT_ANIMATION_DURATION_MILLIS = 800

/** Dimensionless values for one recovery icon travelling from the pawn to the health display. */
data class RecoveryIconAnimationFrame(
    val progress: Float,
    val scale: Float,
    val alpha: Float,
    val lift: Float,
)

/**
 * Shared timing values for the Android and desktop eat effects.
 *
 * Geometry stays in each renderer. [labelLift] is normalized from 0 to 1, while
 * each icon's [RecoveryIconAnimationFrame.lift] is a normalized arc height.
 */
data class EatAnimationFrame(
    val playerScale: Float,
    val labelAlpha: Float,
    val labelScale: Float,
    val labelLift: Float,
    val icons: List<RecoveryIconAnimationFrame>,
)

/** Pure one-shot timing curve; callers own playback, cancellation and reduced-motion handling. */
fun eatAnimationFrame(actualRecovery: Int, progress: Float): EatAnimationFrame {
    val p = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
    val labelEnter = smooth(eatUnit(p, 0.08f, 0.22f))
    val labelExit = smooth(eatUnit(p, 0.70f, 1f))
    val labelPop = easeOut(eatUnit(p, 0.08f, 0.22f))
    val labelSettle = smooth(eatUnit(p, 0.22f, 0.36f))

    return EatAnimationFrame(
        playerScale = playerEatScale(p),
        labelAlpha = labelEnter * (1f - labelExit),
        labelScale = eatMix(eatMix(0.86f, 1.06f, labelPop), 1f, labelSettle),
        labelLift = smooth(eatUnit(p, 0.08f, 0.88f)),
        icons = List(actualRecovery.coerceIn(0, MAX_RECOVERY_ICONS)) { index ->
            recoveryIconFrame(p, index)
        },
    )
}

private const val MAX_RECOVERY_ICONS = 5
private const val FIRST_ICON_START = 0.12f
private const val ICON_STAGGER = 0.075f
private const val ICON_DURATION = 0.44f

private fun recoveryIconFrame(animationProgress: Float, index: Int): RecoveryIconAnimationFrame {
    val start = FIRST_ICON_START + index * ICON_STAGGER
    val local = eatUnit(animationProgress, start, start + ICON_DURATION)
    val travel = smooth(local)
    val fadeIn = smooth(eatUnit(local, 0f, 0.16f))
    val fadeOut = smooth(eatUnit(local, 0.72f, 1f))
    val scale = when {
        local < 0.16f -> eatMix(0.25f, 1f, fadeIn)
        local < 0.72f -> 1f
        else -> eatMix(1f, 0.25f, fadeOut)
    }
    val lift = if (travel > 0f && travel < 1f) {
        sin(PI * travel).toFloat() * 0.18f
    } else {
        0f
    }
    return RecoveryIconAnimationFrame(
        progress = travel,
        scale = scale,
        alpha = fadeIn * (1f - fadeOut),
        lift = lift,
    )
}

private fun playerEatScale(p: Float): Float = when {
    p < 0.12f -> eatMix(1f, 0.94f, smooth(eatUnit(p, 0f, 0.12f)))
    p < 0.28f -> eatMix(0.94f, 1.08f, easeOut(eatUnit(p, 0.12f, 0.28f)))
    p < 0.56f -> eatMix(1.08f, 1f, smooth(eatUnit(p, 0.28f, 0.56f)))
    else -> 1f
}

private fun eatUnit(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

private fun eatMix(from: Float, to: Float, t: Float): Float = from + (to - from) * t
