package com.moguru.game.gui

import com.moguru.game.model.Position
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodCard
import com.moguru.game.presenter.TurnConsumptionAnimationEvent
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import kotlin.math.roundToInt

internal data class DesktopTurnConsumptionAnimation(
    val event: TurnConsumptionAnimationEvent,
    val board: TurnConsumptionBoardSnapshot,
    val playerPosition: Position,
    val startedAtNanos: Long,
    val onFinished: () -> Unit,
)

internal data class TurnConsumptionBoardSnapshot(
    val foods: Map<Position, List<FoodCard>>,
    val players: List<CapturePlayerSnapshot>,
    val phase: TurnPhase,
    val currentPlayerId: Int,
)

internal fun turnConsumptionLabel(event: TurnConsumptionAnimationEvent): String {
    return "-${event.actualConsumption}"
}

/** Interpolates the consumed player's marker from the captured pre-cost health to post-cost health. */
internal fun turnConsumptionMarkerCenter(
    event: TurnConsumptionAnimationEvent,
    markerProgress: Float,
    maxHealth: Int,
    meterRect: Rectangle,
): Point {
    val progress = markerProgress.coerceIn(0f, 1f)
    val health = event.healthBefore + (event.healthAfter - event.healthBefore) * progress.toDouble()
    return hungerMeterMarkerCenter(health, maxHealth, meterRect)
}

/** Draws the red cost label with a dark outline and clamps the complete glyph inside the board. */
internal fun drawTurnConsumptionLabel(
    graphics: Graphics2D,
    text: String,
    anchor: Point,
    boardRect: Rectangle,
    fontSize: Float,
    alpha: Float,
    scale: Float,
    lift: Float,
) {
    if (alpha <= 0f || scale <= 0f) return
    val g = graphics.create() as Graphics2D
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.coerceIn(0f, 1f))
        g.font = Font(Font.SANS_SERIF, Font.BOLD, fontSize.roundToInt().coerceAtLeast(1))
        val glyph = g.font.createGlyphVector(g.fontRenderContext, text)
        val glyphBounds = glyph.visualBounds
        val transform = AffineTransform().apply {
            translate(anchor.x.toDouble(), (anchor.y - fontSize * (0.42f + lift * 0.28f)).toDouble())
            scale(scale.toDouble(), scale.toDouble())
            translate(-glyphBounds.centerX, -glyphBounds.centerY)
        }
        var shape = transform.createTransformedShape(glyph.outline)
        val bounds = shape.bounds2D
        val margin = (fontSize * 0.18f).roundToInt().coerceAtLeast(4)
        val minX = boardRect.x + margin
        val maxX = boardRect.x + boardRect.width - margin
        val minY = boardRect.y + margin
        val maxY = boardRect.y + boardRect.height - margin
        val translateX = when {
            bounds.minX < minX -> minX - bounds.minX
            bounds.maxX > maxX -> maxX - bounds.maxX
            else -> 0.0
        }
        val translateY = when {
            bounds.minY < minY -> minY - bounds.minY
            bounds.maxY > maxY -> maxY - bounds.maxY
            else -> 0.0
        }
        if (translateX != 0.0 || translateY != 0.0) {
            shape = AffineTransform.getTranslateInstance(translateX, translateY).createTransformedShape(shape)
        }

        g.color = Color(0x2A1B12)
        g.stroke = BasicStroke((fontSize * 0.15f).coerceAtLeast(2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(shape)
        g.color = Color(0xF04C4C)
        g.fill(shape)
    } finally {
        g.dispose()
    }
}
