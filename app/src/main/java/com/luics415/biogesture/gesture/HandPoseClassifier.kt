package com.luics415.biogesture.gesture

import kotlin.math.sqrt

data class HandPoseClassifierConfig(
    val extendedJointCosineMax: Float = -0.65f,
    val extendedRadialGainMin: Float = 0.08f,
    val foldedJointCosineMin: Float = -0.35f,
    val foldedRadialGainMax: Float = 0.02f,
    val victoryTipSeparationMin: Float = 0.30f,
    val thumbJointCosineMax: Float = -0.55f,
    val thumbRadialGainMin: Float = 0.08f,
    val thumbIndexSpreadMin: Float = 0.30f,
) {
    init {
        require(extendedJointCosineMax in -1f..1f)
        require(foldedJointCosineMin in -1f..1f)
        require(thumbJointCosineMax in -1f..1f)
        require(extendedRadialGainMin >= 0f)
        require(foldedRadialGainMax >= 0f)
        require(victoryTipSeparationMin >= 0f)
        require(thumbRadialGainMin >= 0f)
        require(thumbIndexSpreadMin >= 0f)
    }
}

enum class Finger {
    THUMB,
    INDEX,
    MIDDLE,
    RING,
    PINKY,
}

enum class FingerState {
    EXTENDED,
    FOLDED,
    UNKNOWN,
}

/**
 * Classifies static poses using joint angles and ratios to palm size.
 *
 * No rule depends on image up/down or left/right, so rotating or mirroring the camera frame does
 * not change the result. Camera-to-screen orientation is deliberately outside this class.
 */
class HandPoseClassifier(
    private val config: HandPoseClassifierConfig = HandPoseClassifierConfig(),
) {
    fun isVictory(pose: HandPose): Boolean {
        if (!hasUsableScale(pose)) return false

        val index = fingerState(pose, Finger.INDEX)
        val middle = fingerState(pose, Finger.MIDDLE)
        val ring = fingerState(pose, Finger.RING)
        val pinky = fingerState(pose, Finger.PINKY)
        val separation = pose.normalizedDistance(
            HandLandmark.INDEX_TIP,
            HandLandmark.MIDDLE_TIP,
        )

        return index == FingerState.EXTENDED &&
            middle == FingerState.EXTENDED &&
            ring == FingerState.FOLDED &&
            pinky == FingerState.FOLDED &&
            separation >= config.victoryTipSeparationMin
    }

    fun isThumbMenu(pose: HandPose): Boolean {
        if (!hasUsableScale(pose)) return false

        return fingerState(pose, Finger.THUMB) == FingerState.EXTENDED &&
            fingerState(pose, Finger.INDEX) == FingerState.FOLDED &&
            fingerState(pose, Finger.MIDDLE) == FingerState.FOLDED &&
            fingerState(pose, Finger.RING) == FingerState.FOLDED &&
            fingerState(pose, Finger.PINKY) == FingerState.FOLDED
    }

    fun fingerState(pose: HandPose, finger: Finger): FingerState {
        if (!hasUsableScale(pose)) return FingerState.UNKNOWN
        return if (finger == Finger.THUMB) thumbState(pose) else regularFingerState(pose, finger)
    }

    private fun thumbState(pose: HandPose): FingerState {
        val cosine = jointCosine(
            pose[HandLandmark.THUMB_MCP],
            pose[HandLandmark.THUMB_IP],
            pose[HandLandmark.THUMB_TIP],
        ) ?: return FingerState.UNKNOWN
        val radialGain = radialGain(
            pose,
            pose[HandLandmark.THUMB_IP],
            pose[HandLandmark.THUMB_TIP],
        )
        val indexSpread = pose.normalizedDistance(
            HandLandmark.THUMB_TIP,
            HandLandmark.INDEX_MCP,
        )

        if (
            cosine <= config.thumbJointCosineMax &&
            radialGain >= config.thumbRadialGainMin &&
            indexSpread >= config.thumbIndexSpreadMin
        ) {
            return FingerState.EXTENDED
        }

        if (cosine >= config.foldedJointCosineMin || radialGain <= config.foldedRadialGainMax) {
            return FingerState.FOLDED
        }
        return FingerState.UNKNOWN
    }

    private fun regularFingerState(pose: HandPose, finger: Finger): FingerState {
        val joints = when (finger) {
            Finger.INDEX -> FingerJoints(
                HandLandmark.INDEX_MCP,
                HandLandmark.INDEX_PIP,
                HandLandmark.INDEX_TIP,
            )
            Finger.MIDDLE -> FingerJoints(
                HandLandmark.MIDDLE_MCP,
                HandLandmark.MIDDLE_PIP,
                HandLandmark.MIDDLE_TIP,
            )
            Finger.RING -> FingerJoints(
                HandLandmark.RING_MCP,
                HandLandmark.RING_PIP,
                HandLandmark.RING_TIP,
            )
            Finger.PINKY -> FingerJoints(
                HandLandmark.PINKY_MCP,
                HandLandmark.PINKY_PIP,
                HandLandmark.PINKY_TIP,
            )
            Finger.THUMB -> error("Thumb has its own classifier")
        }

        val cosine = jointCosine(
            pose[joints.mcp],
            pose[joints.pip],
            pose[joints.tip],
        ) ?: return FingerState.UNKNOWN
        val radialGain = radialGain(pose, pose[joints.pip], pose[joints.tip])

        if (
            cosine <= config.extendedJointCosineMax &&
            radialGain >= config.extendedRadialGainMin
        ) {
            return FingerState.EXTENDED
        }
        if (cosine >= config.foldedJointCosineMin || radialGain <= config.foldedRadialGainMax) {
            return FingerState.FOLDED
        }
        return FingerState.UNKNOWN
    }

    private fun radialGain(pose: HandPose, innerJoint: Point3, tip: Point3): Float {
        val wrist = pose[HandLandmark.WRIST]
        return (wrist.distance2DTo(tip) - wrist.distance2DTo(innerJoint)) / pose.palmScale
    }

    private fun jointCosine(mcp: Point3, pip: Point3, tip: Point3): Float? {
        val firstX = mcp.x - pip.x
        val firstY = mcp.y - pip.y
        val secondX = tip.x - pip.x
        val secondY = tip.y - pip.y
        val firstLength = sqrt(firstX * firstX + firstY * firstY)
        val secondLength = sqrt(secondX * secondX + secondY * secondY)
        if (firstLength <= EPSILON || secondLength <= EPSILON) return null
        return ((firstX * secondX + firstY * secondY) / (firstLength * secondLength))
            .coerceIn(-1f, 1f)
    }

    private fun hasUsableScale(pose: HandPose): Boolean = pose.palmScale > EPSILON

    private data class FingerJoints(
        val mcp: HandLandmark,
        val pip: HandLandmark,
        val tip: HandLandmark,
    )

    companion object {
        private const val EPSILON = 1e-5f
    }
}
