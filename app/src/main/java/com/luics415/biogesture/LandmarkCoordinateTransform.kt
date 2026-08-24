package com.luics415.biogesture

import com.luics415.biogesture.gesture.Point3

/** Converts MediaPipe landmarks from the camera buffer into the display-oriented image space. */
object LandmarkCoordinateTransform {
    fun toDisplay(point: Point3, rotationDegrees: Int): Point3 = when (rotationDegrees) {
        0 -> point
        90 -> Point3(1f - point.y, point.x, point.z)
        180 -> Point3(1f - point.x, 1f - point.y, point.z)
        270 -> Point3(point.y, 1f - point.x, point.z)
        else -> throw IllegalArgumentException("rotationDegrees must be 0, 90, 180 or 270")
    }
}

object HandednessLabel {
    fun inSpanish(rawCategory: String): String = when (rawCategory.lowercase()) {
        "left" -> "Izquierda"
        "right" -> "Derecha"
        else -> "Mano"
    }
}
