package com.luics415.biogesture

import com.luics415.biogesture.gesture.Point3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LandmarkCoordinateTransformTest {
    private val point = Point3(0.2f, 0.3f, -0.4f)

    @Test
    fun zeroDegrees_keepsCoordinates() {
        assertPoint(Point3(0.2f, 0.3f, -0.4f), LandmarkCoordinateTransform.toDisplay(point, 0))
    }

    @Test
    fun ninetyDegrees_rotatesClockwise() {
        assertPoint(Point3(0.7f, 0.2f, -0.4f), LandmarkCoordinateTransform.toDisplay(point, 90))
    }

    @Test
    fun oneHundredEightyDegrees_rotatesBothAxes() {
        assertPoint(Point3(0.8f, 0.7f, -0.4f), LandmarkCoordinateTransform.toDisplay(point, 180))
    }

    @Test
    fun twoHundredSeventyDegrees_rotatesCounterClockwise() {
        assertPoint(Point3(0.3f, 0.8f, -0.4f), LandmarkCoordinateTransform.toDisplay(point, 270))
    }

    @Test
    fun unsupportedRotation_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            LandmarkCoordinateTransform.toDisplay(point, 45)
        }
    }

    @Test
    fun handednessLabelsPreserveMediaPipeClassification() {
        assertEquals("Derecha", HandednessLabel.inSpanish("Right"))
        assertEquals("Izquierda", HandednessLabel.inSpanish("Left"))
        assertEquals("Mano", HandednessLabel.inSpanish(""))
    }

    private fun assertPoint(expected: Point3, actual: Point3) {
        assertEquals(expected.x, actual.x, 0.0001f)
        assertEquals(expected.y, actual.y, 0.0001f)
        assertEquals(expected.z, actual.z, 0.0001f)
    }
}
