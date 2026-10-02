package com.moguru.game.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** A shortened system duration requests a static presentation, rather than faster motion. */
internal fun gameMotionEnabled(context: CoroutineContext): Boolean =
    (context[MotionDurationScale]?.scaleFactor ?: 1f) >= 1f

@Composable
internal fun gameMotionEnabled(): Boolean = gameMotionEnabled(rememberCoroutineScope().coroutineContext)

/** Shared playback for the existing capture, meal and turn-consumption presentations. */
internal suspend fun playGameAnimation(progress: Animatable<Float, AnimationVector1D>, durationMillis: Int) {
    if (gameMotionEnabled(coroutineContext)) {
        progress.animateTo(1f, tween(durationMillis = durationMillis, easing = LinearEasing))
    } else {
        progress.snapTo(1f)
    }
}
