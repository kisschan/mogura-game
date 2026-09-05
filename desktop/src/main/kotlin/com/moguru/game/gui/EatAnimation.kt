package com.moguru.game.gui

import com.moguru.game.model.Position
import com.moguru.game.presenter.EatAnimationEvent
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import kotlin.math.roundToInt

internal data class DesktopEatAnimation(
    val event: EatAnimationEvent,
    val playerPosition: Position,
    val startedAtNanos: Long,
    val onFinished: () -> Unit,
)

/** Keeps the marker on the U-shaped meter route throughout recovery. */
internal fun hungerMeterRecoveryCenter(
    healthBefore: Int,
    healthAfter: Int,
    progress: Float,
    maxHealth: Int,
    meterRect: Rectangle,
): Point {
    val eased = easeOutCubic(progress.coerceIn(0f, 1f))
    val health = healthBefore + (healthAfter - healthBefore) * eased.toDouble()
    return hungerMeterMarkerCenter(health, maxHealth, meterRect)
}

internal fun eatRecoveryIconRect(
    start: Point,
    destination: Point,
    travel: Float,
    scale: Float,
    lift: Float,
    iconSize: Int,
): Rectangle {
    val t = travel.coerceIn(0f, 1f)
    val centerX = start.x + (destination.x - start.x) * t
    val directY = start.y + (destination.y - start.y) * t
    val arc = lift.coerceAtLeast(0f) * iconSize * 2.6
    val size = (iconSize * scale.coerceAtLeast(0f)).roundToInt().coerceAtLeast(1)
    return Rectangle(
        (centerX - size / 2.0).roundToInt(),
        (directY - arc - size / 2.0).roundToInt(),
        size,
        size,
    )
}

internal fun scaleRectAroundCenter(rect: Rectangle, scale: Float): Rectangle {
    val width = (rect.width * scale.coerceAtLeast(0f)).roundToInt().coerceAtLeast(1)
    val height = (rect.height * scale.coerceAtLeast(0f)).roundToInt().coerceAtLeast(1)
    return Rectangle(
        (rect.centerX - width / 2.0).roundToInt(),
        (rect.centerY - height / 2.0).roundToInt(),
        width,
        height,
    )
}

/** Keeps the floating recovery label inside the illustrated board at every meter position. */
internal fun recoveryLabelAnchor(
    destination: Point,
    boardRect: Rectangle,
    fontSize: Float,
    avoidanceDistance: Int = 0,
): Point {
    val horizontalMargin = fontSize.roundToInt().coerceAtLeast(1)
    val minX = boardRect.x + horizontalMargin
    val maxX = (boardRect.x + boardRect.width - horizontalMargin).coerceAtLeast(minX)
    val boardCenterX = boardRect.x + boardRect.width / 2
    val preferredX = if (destination.x <= boardCenterX) {
        destination.x + avoidanceDistance
    } else {
        destination.x - avoidanceDistance
    }
    val baseY = maxOf(
        destination.y + (fontSize * 2.25f).roundToInt(),
        boardRect.y + (fontSize * 2.4f).roundToInt(),
    )
    return Point(
        preferredX.coerceIn(minX, maxX),
        baseY.coerceAtMost(boardRect.y + boardRect.height - horizontalMargin),
    )
}

/** Draws a compact bone-in meat symbol without depending on another image asset. */
internal fun drawRecoveryMeatIcon(
    graphics: Graphics2D,
    rect: Rectangle,
    alpha: Float,
) {
    if (alpha <= 0f || rect.width <= 0 || rect.height <= 0) return
    val g = graphics.create() as Graphics2D
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.coerceIn(0f, 1f))
        g.translate(rect.centerX, rect.centerY)
        g.rotate(Math.toRadians(-18.0))
        val size = minOf(rect.width, rect.height).toDouble()

        val boneStroke = BasicStroke((size * 0.17).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val outlineStroke = BasicStroke((size * 0.24).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.stroke = outlineStroke
        g.color = Color(0x3A2518)
        g.drawLine((size * 0.02).roundToInt(), 0, (size * 0.38).roundToInt(), 0)
        g.stroke = boneStroke
        g.color = Color(0xFFF4D6)
        g.drawLine((size * 0.02).roundToInt(), 0, (size * 0.38).roundToInt(), 0)

        val knobSize = size * 0.20
        listOf(-0.10, 0.10).forEach { offset ->
            val knob = Ellipse2D.Double(
                size * 0.31,
                size * offset - knobSize / 2,
                knobSize,
                knobSize,
            )
            g.color = Color(0x3A2518)
            g.fill(knob)
            val inset = size * 0.035
            g.color = Color(0xFFF4D6)
            g.fill(Ellipse2D.Double(knob.x + inset, knob.y + inset, knob.width - inset * 2, knob.height - inset * 2))
        }

        val meat = Area(Ellipse2D.Double(-size * 0.45, -size * 0.31, size * 0.50, size * 0.62)).apply {
            add(Area(Ellipse2D.Double(-size * 0.19, -size * 0.27, size * 0.40, size * 0.54)))
        }
        g.color = Color(0x3A2518)
        g.fill(meat)
        val inset = size * 0.045
        val meatFill = Area(
            Ellipse2D.Double(
                -size * 0.45 + inset,
                -size * 0.31 + inset,
                size * 0.50 - inset * 2,
                size * 0.62 - inset * 2,
            ),
        ).apply {
            add(
                Area(
                    Ellipse2D.Double(
                        -size * 0.19 + inset,
                        -size * 0.27 + inset,
                        size * 0.40 - inset * 2,
                        size * 0.54 - inset * 2,
                    ),
                ),
            )
        }
        g.color = Color(0xF28B35)
        g.fill(meatFill)
        g.color = Color(0xFFD08A)
        g.fill(Ellipse2D.Double(-size * 0.32, -size * 0.20, size * 0.17, size * 0.10))
    } finally {
        g.dispose()
    }
}

/** Green recovery text with a dark outline remains legible over the illustrated board. */
internal fun drawRecoveryLabel(
    graphics: Graphics2D,
    text: String,
    anchor: Point,
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
        val bounds = glyph.visualBounds
        val transform = AffineTransform().apply {
            translate(anchor.x.toDouble(), (anchor.y - fontSize * (0.65f + lift * 0.45f)).toDouble())
            scale(scale.toDouble(), scale.toDouble())
            translate(-bounds.centerX, -bounds.centerY)
        }
        val shape = transform.createTransformedShape(glyph.outline)
        g.color = Color(0x2A1B12)
        g.stroke = BasicStroke((fontSize * 0.16f).coerceAtLeast(2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(shape)
        g.color = Color(0x36D267)
        g.fill(shape)
    } finally {
        g.dispose()
    }
}

private fun easeOutCubic(value: Float): Float {
    val inverse = 1f - value
    return 1f - inverse * inverse * inverse
}
