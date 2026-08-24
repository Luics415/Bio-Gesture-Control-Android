package com.luics415.biogesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureFrameRateSelectorTest {
    private val commonRanges = listOf(
        CaptureFpsRange(15, 15),
        CaptureFpsRange(15, 30),
        CaptureFpsRange(30, 30),
    )

    @Test
    fun balancedBudgetCapsCaptureAtBestSupportedRangeBelowTarget() {
        assertEquals(CaptureFpsRange(15, 15), CaptureFrameRateSelector.select(20, commonRanges))
    }

    @Test
    fun precisionBudgetSelectsFixedThirtyWhenAvailable() {
        assertEquals(CaptureFpsRange(30, 30), CaptureFrameRateSelector.select(30, commonRanges))
    }

    @Test
    fun lowBudgetFallsBackToSmallestSupportedMaximum() {
        assertEquals(CaptureFpsRange(15, 15), CaptureFrameRateSelector.select(6, commonRanges))
    }

    @Test
    fun lowBudgetPrefersRangeWithLowestFloorWhenMaximumsTie() {
        val variableAndFixedThirty = listOf(
            CaptureFpsRange(15, 30),
            CaptureFpsRange(30, 30),
        )

        assertEquals(
            CaptureFpsRange(15, 30),
            CaptureFrameRateSelector.select(6, variableAndFixedThirty),
        )
    }

    @Test
    fun emptyCapabilitiesLeaveCameraConfigurationUntouched() {
        assertNull(CaptureFrameRateSelector.select(20, emptyList()))
    }
}
