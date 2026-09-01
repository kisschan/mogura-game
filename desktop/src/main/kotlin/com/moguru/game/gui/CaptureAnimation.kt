package com.moguru.game.gui

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodCard
import com.moguru.game.model.Position
import com.moguru.game.presenter.CaptureAnimationEvent
import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

/** Immutable positions keep the resolved board from replacing sprites during the animation. */
internal data class CaptureBoardSnapshot(
    val foods: Map<Position, List<FoodCard>>,
    val players: List<CapturePlayerSnapshot>,
    val phase: TurnPhase,
    val previousEventId: Long?,
)

internal data class CapturePlayerSnapshot(val id: Int, val name: String, val position: Position)

internal data class DesktopCaptureAnimation(
    val event: CaptureAnimationEvent,
    val board: CaptureBoardSnapshot,
    val destinationStackSize: Int,
    val destinationFoodIndex: Int,
    val startedAtNanos: Long,
    val onFinished: () -> Unit,
)

/** Interpolate centers, so shrinking the card never changes its intended flight path. */
internal fun captureSpriteRect(
    start: Rectangle,
    destination: Rectangle,
    travel: Float,
    scale: Float,
    liftPixels: Float = 0f,
): Rectangle {
    val centerX = start.centerX + (destination.centerX - start.centerX) * travel
    val centerY = start.centerY + (destination.centerY - start.centerY) * travel - liftPixels
    val width = ((start.width + (destination.width - start.width) * travel) * scale).roundToInt().coerceAtLeast(1)
    val height = ((start.height + (destination.height - start.height) * travel) * scale).roundToInt().coerceAtLeast(1)
    return Rectangle((centerX - width / 2.0).roundToInt(), (centerY - height / 2.0).roundToInt(), width, height)
}

/** Use a child graphics context: capture opacity and rotation must not leak into the board. */
internal fun drawCaptureSprite(
    graphics: Graphics2D,
    image: BufferedImage,
    rect: Rectangle,
    source: Rectangle = Rectangle(0, 0, image.width, image.height),
    rotationDegrees: Float = 0f,
    alpha: Float = 1f,
) {
    if (alpha <= 0f) return
    val g = graphics.create() as Graphics2D
    try {
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.coerceIn(0f, 1f))
        g.rotate(Math.toRadians(rotationDegrees.toDouble()), rect.centerX, rect.centerY)
        g.drawImage(
            image,
            rect.x, rect.y, rect.x + rect.width, rect.y + rect.height,
            source.x, source.y, source.x + source.width, source.y + source.height,
            null,
        )
    } finally {
        g.dispose()
    }
}
