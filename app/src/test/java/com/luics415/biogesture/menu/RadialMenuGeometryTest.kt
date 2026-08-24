package com.luics415.biogesture.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadialMenuGeometryTest {
    private val geometry = RadialMenuGeometry()

    @Test
    fun `portrait layout stays inside safe bounds near a corner`() {
        val viewport = ScalarViewport(
            width = 1080.0,
            height = 2400.0,
            insets = ScalarInsets(left = 0.0, top = 90.0, right = 0.0, bottom = 120.0),
        )
        val layout = geometry.layout(ScalarPoint(4.0, 94.0), viewport)

        assertCircleInsideSafeBounds(layout)
    }

    @Test
    fun `landscape layout stays inside safe bounds near a corner`() {
        val viewport = ScalarViewport(
            width = 2400.0,
            height = 1080.0,
            insets = ScalarInsets(left = 80.0, top = 0.0, right = 120.0, bottom = 60.0),
        )
        val layout = geometry.layout(ScalarPoint(2390.0, 1070.0), viewport)

        assertCircleInsideSafeBounds(layout)
    }

    @Test
    fun `radius and deadzone scale with the safe short side`() {
        val small = geometry.layout(
            ScalarPoint(500.0, 500.0),
            ScalarViewport(1000.0, 1600.0),
        )
        val large = geometry.layout(
            ScalarPoint(1000.0, 1000.0),
            ScalarViewport(2000.0, 3200.0),
        )

        assertEquals(small.radius * 2.0, large.radius, 0.0001)
        assertEquals(small.deadZoneRadius * 2.0, large.deadZoneRadius, 0.0001)
        assertEquals(small.radius * 0.32, small.deadZoneRadius, 0.0001)
    }

    @Test
    fun `sectors start at top and proceed clockwise`() {
        val layout = geometry.layout(
            ScalarPoint(500.0, 500.0),
            ScalarViewport(1000.0, 1000.0),
        )

        for (index in 0 until 8) {
            assertEquals(index, layout.sectorAt(layout.pointForSector(index, 8), 8))
        }
        assertEquals(null, layout.sectorAt(layout.center, 8))
    }

    private fun assertCircleInsideSafeBounds(layout: RadialMenuLayout) {
        val viewport = layout.viewport
        assertTrue(layout.center.x - layout.radius >= viewport.safeLeft + layout.edgeMargin - 0.0001)
        assertTrue(layout.center.x + layout.radius <= viewport.safeRight - layout.edgeMargin + 0.0001)
        assertTrue(layout.center.y - layout.radius >= viewport.safeTop + layout.edgeMargin - 0.0001)
        assertTrue(layout.center.y + layout.radius <= viewport.safeBottom - layout.edgeMargin + 0.0001)
    }
}
