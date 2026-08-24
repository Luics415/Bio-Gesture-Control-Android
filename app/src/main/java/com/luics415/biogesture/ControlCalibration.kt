package com.luics415.biogesture

import com.luics415.biogesture.gesture.Point3
import kotlin.math.roundToInt

data class NormalizedControlPoint(
    val x: Float,
    val y: Float,
)

/** Raw MediaPipe limits reached by the user during one orientation's calibration. */
data class InputCalibrationBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
) {
    fun sanitized(): InputCalibrationBounds {
        val safeMinX = minX.coerceIn(0f, 1f)
        val safeMaxX = maxX.coerceIn(0f, 1f)
        val safeMinY = minY.coerceIn(0f, 1f)
        val safeMaxY = maxY.coerceIn(0f, 1f)
        return if (
            safeMaxX - safeMinX >= MIN_SPAN &&
            safeMaxY - safeMinY >= MIN_SPAN
        ) {
            InputCalibrationBounds(safeMinX, safeMaxX, safeMinY, safeMaxY)
        } else {
            DEFAULT
        }
    }

    fun normalize(x: Float, y: Float, mirrored: Boolean): NormalizedControlPoint {
        val safe = sanitized()
        val rawX = normalizeAxis(x, safe.minX, safe.maxX)
        return NormalizedControlPoint(
            x = if (mirrored) 1f - rawX else rawX,
            y = normalizeAxis(y, safe.minY, safe.maxY),
        )
    }

    private fun normalizeAxis(value: Float, minimum: Float, maximum: Float): Float =
        ((value - minimum) / (maximum - minimum).coerceAtLeast(MIN_SPAN)).coerceIn(0f, 1f)

    companion object {
        const val DEFAULT_MARGIN = 0.08f
        private const val MIN_SPAN = 0.20f
        val DEFAULT = InputCalibrationBounds(
            minX = DEFAULT_MARGIN,
            maxX = 1f - DEFAULT_MARGIN,
            minY = DEFAULT_MARGIN,
            maxY = 1f - DEFAULT_MARGIN,
        )
    }
}

data class ObservedCalibrationBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
)

/** Collects samples and uses percentiles so one bad landmark cannot redefine the whole screen. */
class CalibrationAccumulator(
    val startedAtMs: Long,
    val landscape: Boolean,
) {
    private val xSamples = ArrayList<Float>()
    private val ySamples = ArrayList<Float>()

    val sampleCount: Int
        get() = minOf(xSamples.size, ySamples.size)

    fun include(point: Point3) {
        xSamples += point.x.coerceIn(0f, 1f)
        ySamples += point.y.coerceIn(0f, 1f)
    }

    fun robustBounds(outlierFraction: Float): ObservedCalibrationBounds? {
        require(outlierFraction in 0f..0.49f)
        if (sampleCount == 0) return null
        val sortedX = xSamples.sorted()
        val sortedY = ySamples.sorted()
        val lowerIndex = ((sampleCount - 1) * outlierFraction).roundToInt()
            .coerceIn(0, sampleCount - 1)
        val upperIndex = ((sampleCount - 1) * (1f - outlierFraction)).roundToInt()
            .coerceIn(lowerIndex, sampleCount - 1)
        return ObservedCalibrationBounds(
            minX = sortedX[lowerIndex],
            maxX = sortedX[upperIndex],
            minY = sortedY[lowerIndex],
            maxY = sortedY[upperIndex],
        )
    }
}
