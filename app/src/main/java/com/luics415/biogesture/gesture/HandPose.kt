package com.luics415.biogesture.gesture

import kotlin.math.max
import kotlin.math.sqrt

/** A MediaPipe landmark in normalized image space. */
data class Point3(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) {
            "Landmark coordinates must be finite"
        }
    }

    fun distance2DTo(other: Point3): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    fun weightedDistanceTo(other: Point3, zWeight: Float): Float {
        require(zWeight >= 0f && zWeight.isFinite()) { "zWeight must be finite and non-negative" }
        val dx = x - other.x
        val dy = y - other.y
        val dz = (z - other.z) * zWeight
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

/** MediaPipe Hand Landmarker indices. */
enum class HandLandmark(val index: Int) {
    WRIST(0),
    THUMB_CMC(1),
    THUMB_MCP(2),
    THUMB_IP(3),
    THUMB_TIP(4),
    INDEX_MCP(5),
    INDEX_PIP(6),
    INDEX_DIP(7),
    INDEX_TIP(8),
    MIDDLE_MCP(9),
    MIDDLE_PIP(10),
    MIDDLE_DIP(11),
    MIDDLE_TIP(12),
    RING_MCP(13),
    RING_PIP(14),
    RING_DIP(15),
    RING_TIP(16),
    PINKY_MCP(17),
    PINKY_PIP(18),
    PINKY_DIP(19),
    PINKY_TIP(20),
}

/**
 * Immutable observation consumed by [GestureEngine].
 *
 * [timestampMs] must come from a monotonic clock. The engine ignores observations older than the
 * last one it accepted so asynchronous inference cannot rewind a gesture state.
 */
class HandPose(
    landmarks: List<Point3>,
    val timestampMs: Long,
) {
    val landmarks: List<Point3> = landmarks.toList()

    init {
        require(this.landmarks.size == LANDMARK_COUNT) {
            "A hand pose must contain exactly $LANDMARK_COUNT landmarks"
        }
        require(timestampMs >= 0L) { "timestampMs must be non-negative" }
    }

    /** A rotation- and reflection-invariant reference length for normalized gesture distances. */
    val palmScale: Float = max(
        this[HandLandmark.WRIST].distance2DTo(this[HandLandmark.MIDDLE_MCP]),
        this[HandLandmark.INDEX_MCP].distance2DTo(this[HandLandmark.PINKY_MCP]),
    )

    operator fun get(landmark: HandLandmark): Point3 = landmarks[landmark.index]

    operator fun get(index: Int): Point3 = landmarks[index]

    fun normalizedDistance(
        first: HandLandmark,
        second: HandLandmark,
        zWeight: Float = 0f,
    ): Float {
        if (palmScale <= MIN_PALM_SCALE) return Float.POSITIVE_INFINITY
        return this[first].weightedDistanceTo(this[second], zWeight) / palmScale
    }

    companion object {
        const val LANDMARK_COUNT = 21
        private const val MIN_PALM_SCALE = 1e-5f
    }
}
