package com.luics415.biogesture

import com.luics415.biogesture.gesture.Point3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlCalibrationTest {
    @Test
    fun asymmetricBoundsMapEveryObservedEdgeToTheControlArea() {
        val bounds = InputCalibrationBounds(
            minX = 0.10f,
            maxX = 0.70f,
            minY = 0.20f,
            maxY = 0.80f,
        )

        assertPoint(0f, 0f, bounds.normalize(0.10f, 0.20f, mirrored = false))
        assertPoint(1f, 1f, bounds.normalize(0.70f, 0.80f, mirrored = false))
        assertPoint(0.5f, 0.5f, bounds.normalize(0.40f, 0.50f, mirrored = false))
    }

    @Test
    fun mirrorChangesOnlyTheHorizontalAxisAndAppliesExactlyOnce() {
        val bounds = InputCalibrationBounds(0.10f, 0.70f, 0.20f, 0.80f)

        assertPoint(1f, 0f, bounds.normalize(0.10f, 0.20f, mirrored = true))
        assertPoint(0f, 1f, bounds.normalize(0.70f, 0.80f, mirrored = true))
    }

    @Test
    fun invalidOrTooNarrowBoundsFallBackToSafeDefaults() {
        val invalid = InputCalibrationBounds(0.4f, 0.5f, 0.6f, 0.65f)

        assertEquals(InputCalibrationBounds.DEFAULT, invalid.sanitized())
    }

    @Test
    fun robustAccumulatorRejectsFivePercentExtremeOutliers() {
        val accumulator = CalibrationAccumulator(startedAtMs = 10L, landscape = true)
        repeat(5) { accumulator.include(Point3(0f, 0f)) }
        repeat(45) { accumulator.include(Point3(0.20f, 0.30f)) }
        repeat(45) { accumulator.include(Point3(0.80f, 0.70f)) }
        repeat(5) { accumulator.include(Point3(1f, 1f)) }

        val bounds = requireNotNull(accumulator.robustBounds(0.05f))
        assertEquals(100, accumulator.sampleCount)
        assertEquals(true, accumulator.landscape)
        assertEquals(0.20f, bounds.minX, EPSILON)
        assertEquals(0.80f, bounds.maxX, EPSILON)
        assertEquals(0.30f, bounds.minY, EPSILON)
        assertEquals(0.70f, bounds.maxY, EPSILON)
    }

    @Test
    fun emptyAccumulatorHasNoBounds() {
        assertNull(CalibrationAccumulator(0L, landscape = false).robustBounds(0.05f))
    }

    private fun assertPoint(expectedX: Float, expectedY: Float, actual: NormalizedControlPoint) {
        assertEquals(expectedX, actual.x, EPSILON)
        assertEquals(expectedY, actual.y, EPSILON)
    }

    companion object {
        private const val EPSILON = 0.0001f
    }
}
