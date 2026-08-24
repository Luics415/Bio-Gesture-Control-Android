package com.luics415.biogesture.gesture

import kotlin.math.cos
import kotlin.math.sin

internal object HandPoseFixtures {
    fun open(timestampMs: Long): HandPose = HandPose(openPoints(), timestampMs)

    fun victory(timestampMs: Long): HandPose {
        val points = openPoints().toMutableList()
        foldFinger(points, HandLandmark.RING_MCP, HandLandmark.RING_PIP,
            HandLandmark.RING_DIP, HandLandmark.RING_TIP, 0.34f, 0.75f)
        foldFinger(points, HandLandmark.PINKY_MCP, HandLandmark.PINKY_PIP,
            HandLandmark.PINKY_DIP, HandLandmark.PINKY_TIP, 0.65f, 0.62f)
        return HandPose(points, timestampMs)
    }

    fun thumbMenu(timestampMs: Long): HandPose {
        val points = openPoints().toMutableList()
        foldFinger(points, HandLandmark.INDEX_MCP, HandLandmark.INDEX_PIP,
            HandLandmark.INDEX_DIP, HandLandmark.INDEX_TIP, -0.35f, 0.75f)
        foldFinger(points, HandLandmark.MIDDLE_MCP, HandLandmark.MIDDLE_PIP,
            HandLandmark.MIDDLE_DIP, HandLandmark.MIDDLE_TIP, 0f, 0.82f)
        foldFinger(points, HandLandmark.RING_MCP, HandLandmark.RING_PIP,
            HandLandmark.RING_DIP, HandLandmark.RING_TIP, 0.34f, 0.75f)
        foldFinger(points, HandLandmark.PINKY_MCP, HandLandmark.PINKY_PIP,
            HandLandmark.PINKY_DIP, HandLandmark.PINKY_TIP, 0.65f, 0.62f)
        return HandPose(points, timestampMs)
    }

    fun primaryPinch(
        timestampMs: Long,
        indexOffsetX: Float = 0f,
        distanceRatio: Float = 0.08f,
        zDifference: Float = 0f,
    ): HandPose {
        val points = openPoints().toMutableList()
        val originalIndex = points[HandLandmark.INDEX_TIP.index]
        val index = originalIndex.copy(x = originalIndex.x + indexOffsetX)
        points[HandLandmark.INDEX_TIP.index] = index
        points[HandLandmark.THUMB_TIP.index] = Point3(
            x = index.x + distanceRatio * palmScale(points),
            y = index.y,
            z = index.z + zDifference,
        )
        return HandPose(points, timestampMs)
    }

    fun primaryAtIntermediateDistance(timestampMs: Long, ratio: Float): HandPose =
        primaryPinch(timestampMs = timestampMs, distanceRatio = ratio)

    fun contextPinch(timestampMs: Long, distanceRatio: Float = 0.08f): HandPose {
        val points = openPoints().toMutableList()
        val middle = points[HandLandmark.MIDDLE_TIP.index]
        points[HandLandmark.THUMB_TIP.index] = Point3(
            x = middle.x + distanceRatio * palmScale(points),
            y = middle.y,
            z = middle.z,
        )
        return HandPose(points, timestampMs)
    }

    fun ambiguousPinch(timestampMs: Long): HandPose {
        val points = openPoints().toMutableList()
        val index = points[HandLandmark.INDEX_TIP.index]
        val scale = palmScale(points)
        val middle = index.copy(x = index.x + 0.08f * scale)
        points[HandLandmark.MIDDLE_TIP.index] = middle
        points[HandLandmark.THUMB_TIP.index] = index.copy(x = index.x + 0.04f * scale)
        return HandPose(points, timestampMs)
    }

    fun transform(
        pose: HandPose,
        timestampMs: Long = pose.timestampMs,
        degrees: Float = 0f,
        mirrorX: Boolean = false,
    ): HandPose {
        val radians = Math.toRadians(degrees.toDouble())
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()
        val transformed = pose.landmarks.map { point ->
            val mirroredX = if (mirrorX) -point.x else point.x
            Point3(
                x = mirroredX * cosine - point.y * sine,
                y = mirroredX * sine + point.y * cosine,
                z = point.z,
            )
        }
        return HandPose(transformed, timestampMs)
    }

    fun degenerate(timestampMs: Long): HandPose = HandPose(
        landmarks = List(HandPose.LANDMARK_COUNT) { Point3(0.5f, 0.5f, 0f) },
        timestampMs = timestampMs,
    )

    private fun openPoints(): List<Point3> = listOf(
        Point3(0f, 0f), // 0 wrist
        Point3(-0.20f, 0.25f),
        Point3(-0.38f, 0.45f),
        Point3(-0.58f, 0.62f),
        Point3(-0.78f, 0.78f), // 4 thumb tip
        Point3(-0.35f, 0.75f),
        Point3(-0.38f, 1.20f),
        Point3(-0.40f, 1.65f),
        Point3(-0.42f, 2.10f), // 8 index tip
        Point3(0f, 0.82f),
        Point3(0f, 1.35f),
        Point3(0f, 1.85f),
        Point3(0f, 2.35f), // 12 middle tip
        Point3(0.34f, 0.75f),
        Point3(0.38f, 1.20f),
        Point3(0.40f, 1.62f),
        Point3(0.42f, 2.02f), // 16 ring tip
        Point3(0.65f, 0.62f),
        Point3(0.72f, 1.00f),
        Point3(0.78f, 1.36f),
        Point3(0.84f, 1.70f), // 20 pinky tip
    )

    private fun foldFinger(
        points: MutableList<Point3>,
        mcpLandmark: HandLandmark,
        pipLandmark: HandLandmark,
        dipLandmark: HandLandmark,
        tipLandmark: HandLandmark,
        mcpX: Float,
        mcpY: Float,
    ) {
        points[mcpLandmark.index] = Point3(mcpX, mcpY)
        points[pipLandmark.index] = Point3(mcpX + 0.04f, mcpY + 0.40f)
        points[dipLandmark.index] = Point3(mcpX - 0.04f, mcpY + 0.20f)
        points[tipLandmark.index] = Point3(mcpX - 0.12f, mcpY - 0.05f)
    }

    private fun palmScale(points: List<Point3>): Float = maxOf(
        points[HandLandmark.WRIST.index].distance2DTo(points[HandLandmark.MIDDLE_MCP.index]),
        points[HandLandmark.INDEX_MCP.index].distance2DTo(points[HandLandmark.PINKY_MCP.index]),
    )
}
