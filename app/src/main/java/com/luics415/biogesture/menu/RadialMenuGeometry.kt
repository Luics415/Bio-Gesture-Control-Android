package com.luics415.biogesture.menu

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

data class ScalarPoint(
    val x: Double,
    val y: Double,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "Point coordinates must be finite." }
    }
}

data class ScalarInsets(
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0,
) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it >= 0.0 }) {
            "Insets must be finite and non-negative."
        }
    }
}

data class ScalarViewport(
    val width: Double,
    val height: Double,
    val insets: ScalarInsets = ScalarInsets(),
) {
    init {
        require(width.isFinite() && height.isFinite() && width > 0.0 && height > 0.0) {
            "Viewport dimensions must be finite and positive."
        }
        require(insets.left + insets.right < width && insets.top + insets.bottom < height) {
            "Insets must leave a positive safe viewport."
        }
    }

    val safeLeft: Double get() = insets.left
    val safeTop: Double get() = insets.top
    val safeRight: Double get() = width - insets.right
    val safeBottom: Double get() = height - insets.bottom
    val safeWidth: Double get() = safeRight - safeLeft
    val safeHeight: Double get() = safeBottom - safeTop
}

data class RadialMenuGeometryPolicy(
    val radiusFractionOfShortSide: Double = 0.28,
    val edgeMarginFractionOfShortSide: Double = 0.02,
    val deadZoneFractionOfRadius: Double = 0.32,
    val itemDistanceFractionOfRadius: Double = 0.70,
) {
    init {
        require(radiusFractionOfShortSide in 0.05..0.48)
        require(edgeMarginFractionOfShortSide in 0.0..0.20)
        require(deadZoneFractionOfRadius in 0.05..0.80)
        require(itemDistanceFractionOfRadius in deadZoneFractionOfRadius..1.0)
    }
}

data class RadialMenuLayout(
    val viewport: ScalarViewport,
    val requestedAnchor: ScalarPoint,
    val center: ScalarPoint,
    val radius: Double,
    val deadZoneRadius: Double,
    val itemDistance: Double,
    val edgeMargin: Double,
) {
    fun isInsideDeadZone(point: ScalarPoint): Boolean =
        hypot(point.x - center.x, point.y - center.y) <= deadZoneRadius

    fun pointForSector(index: Int, sectorCount: Int, distanceFraction: Double = 0.78): ScalarPoint {
        require(sectorCount > 0)
        require(index in 0 until sectorCount)
        require(distanceFraction.isFinite() && distanceFraction >= 0.0)
        val angle = -PI / 2.0 + index * (2.0 * PI / sectorCount)
        val distance = radius * distanceFraction
        return ScalarPoint(
            x = center.x + cos(angle) * distance,
            y = center.y + sin(angle) * distance,
        )
    }

    /** Item zero is centered at the top and subsequent sectors run clockwise. */
    fun sectorAt(point: ScalarPoint, sectorCount: Int): Int? {
        require(sectorCount > 0)
        val dx = point.x - center.x
        val dy = point.y - center.y
        if (hypot(dx, dy) <= deadZoneRadius) return null

        val fullTurn = 2.0 * PI
        val sweep = fullTurn / sectorCount
        val clockwiseFromTop = normalizeRadians(atan2(dy, dx) + PI / 2.0)
        return floor((clockwiseFromTop + sweep / 2.0) / sweep).toInt() % sectorCount
    }

    private fun normalizeRadians(value: Double): Double {
        val fullTurn = 2.0 * PI
        return ((value % fullTurn) + fullTurn) % fullTurn
    }
}

class RadialMenuGeometry(
    private val policy: RadialMenuGeometryPolicy = RadialMenuGeometryPolicy(),
) {
    fun layout(requestedAnchor: ScalarPoint, viewport: ScalarViewport): RadialMenuLayout {
        val shortSide = min(viewport.safeWidth, viewport.safeHeight)
        val edgeMargin = shortSide * policy.edgeMarginFractionOfShortSide
        val maximumRadius = (shortSide / 2.0 - edgeMargin).coerceAtLeast(0.0)
        val radius = min(shortSide * policy.radiusFractionOfShortSide, maximumRadius)

        val minimumCenterX = viewport.safeLeft + edgeMargin + radius
        val maximumCenterX = viewport.safeRight - edgeMargin - radius
        val minimumCenterY = viewport.safeTop + edgeMargin + radius
        val maximumCenterY = viewport.safeBottom - edgeMargin - radius
        val center = ScalarPoint(
            x = requestedAnchor.x.coerceIn(minimumCenterX, maximumCenterX),
            y = requestedAnchor.y.coerceIn(minimumCenterY, maximumCenterY),
        )

        return RadialMenuLayout(
            viewport = viewport,
            requestedAnchor = requestedAnchor,
            center = center,
            radius = radius,
            deadZoneRadius = radius * policy.deadZoneFractionOfRadius,
            itemDistance = radius * policy.itemDistanceFractionOfRadius,
            edgeMargin = edgeMargin,
        )
    }
}
