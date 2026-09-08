package com.moguru.game.presenter

/** A completed end-of-turn health consumption, independent of later player switches. */
data class TurnConsumptionAnimationEvent(
    val id: Long,
    val playerId: Int,
    val requestedConsumption: Int,
    val actualConsumption: Int,
    val healthBefore: Int,
    val healthAfter: Int,
    val isOnSurface: Boolean,
)

const val TURN_CONSUMPTION_ANIMATION_DURATION_MILLIS = 500

/**
 * Dimensionless timing values shared by turn-consumption renderers.
 *
 * [markerProgress] interpolates the health marker from [TurnConsumptionAnimationEvent.healthBefore]
 * to [TurnConsumptionAnimationEvent.healthAfter]. [labelLift] is a normalized, renderer-owned
 * fixed offset. All curves are non-spring and intentionally have no bounce.
 */
data class TurnConsumptionAnimationFrame(
    val markerProgress: Float,
    val labelAlpha: Float,
    val labelScale: Float,
    val labelLift: Float,
)

/** Pure one-shot timing curve; callers own playback, cancellation and reduced-motion handling. */
fun turnConsumptionAnimationFrame(progress: Float): TurnConsumptionAnimationFrame {
    val p = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
    val labelEnter = consumptionEaseOut(consumptionUnit(p, 0.04f, 0.28f))
    val labelExit = consumptionSmooth(consumptionUnit(p, 0.80f, 1f))
    return TurnConsumptionAnimationFrame(
        markerProgress = consumptionSmooth(consumptionUnit(p, 0.20f, 0.72f)),
        labelAlpha = labelEnter * (1f - labelExit),
        labelScale = consumptionMix(
            consumptionMix(0.25f, 1f, labelEnter),
            0.96f,
            labelExit,
        ),
        labelLift = consumptionSmooth(consumptionUnit(p, 0.04f, 0.90f)),
    )
}

private fun consumptionUnit(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun consumptionSmooth(t: Float): Float = t * t * (3f - 2f * t)

private fun consumptionEaseOut(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

private fun consumptionMix(from: Float, to: Float, t: Float): Float = from + (to - from) * t
