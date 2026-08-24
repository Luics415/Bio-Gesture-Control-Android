package com.luics415.biogesture

import android.graphics.PointF
import android.graphics.RectF
import com.luics415.biogesture.gesture.Point3
import kotlin.math.hypot

/**
 * Single source of truth for converting MediaPipe's upright, normalized coordinates into
 * accessibility-screen coordinates. Camera rotation is handled before values arrive here;
 * mirroring is deliberately applied exactly once in this class.
 */
class ScreenCoordinateMapper(
    private var mirrorHorizontally: Boolean = true,
    private var inputBounds: InputCalibrationBounds = InputCalibrationBounds.DEFAULT,
) {
    private var safeBounds = RectF(0f, 0f, 1f, 1f)

    fun updateScreen(width: Int, height: Int, insets: ScreenInsets = ScreenInsets()) {
        safeBounds = RectF(
            insets.left.toFloat(),
            insets.top.toFloat(),
            (width - insets.right).coerceAtLeast(insets.left + 1).toFloat(),
            (height - insets.bottom).coerceAtLeast(insets.top + 1).toFloat(),
        )
    }

    fun setMirrored(mirrored: Boolean) {
        mirrorHorizontally = mirrored
    }

    fun updateInputBounds(bounds: InputCalibrationBounds) {
        inputBounds = bounds.sanitized()
    }

    fun map(point: Point3): PointF {
        val normalized = inputBounds.normalize(point.x, point.y, mirrorHorizontally)
        return PointF(
            safeBounds.left + normalized.x * safeBounds.width(),
            safeBounds.top + normalized.y * safeBounds.height(),
        )
    }

    fun bounds(): RectF = RectF(safeBounds)

    companion object {
        const val DEFAULT_INPUT_MARGIN = InputCalibrationBounds.DEFAULT_MARGIN
    }
}

data class ScreenInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

/** Velocity-aware low-pass filter: stable while aiming, responsive during deliberate movement. */
class AdaptivePointerFilter {
    private var previous: PointF? = null
    private var previousTimeMs = 0L

    fun filter(target: PointF, timestampMs: Long): PointF {
        val old = previous
        if (old == null || timestampMs <= previousTimeMs) {
            return PointF(target.x, target.y).also {
                previous = it
                previousTimeMs = timestampMs
            }
        }

        val deltaMs = (timestampMs - previousTimeMs).coerceAtLeast(1L)
        val speed = hypot(target.x - old.x, target.y - old.y) * 1000f / deltaMs
        val alpha = when {
            speed > 1_800f -> 0.78f
            speed > 800f -> 0.58f
            speed > 250f -> 0.38f
            else -> 0.22f
        }
        val filtered = PointF(
            old.x + (target.x - old.x) * alpha,
            old.y + (target.y - old.y) * alpha,
        )
        previous = filtered
        previousTimeMs = timestampMs
        return PointF(filtered.x, filtered.y)
    }

    fun reset(point: PointF? = null, timestampMs: Long = 0L) {
        previous = point?.let { PointF(it.x, it.y) }
        previousTimeMs = timestampMs
    }
}
