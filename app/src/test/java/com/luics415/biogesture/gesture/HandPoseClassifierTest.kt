package com.luics415.biogesture.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandPoseClassifierTest {
    private val classifier = HandPoseClassifier()

    @Test
    fun victory_requires_indexAndMiddleExtended_withRingAndPinkyFolded() {
        assertTrue(classifier.isVictory(HandPoseFixtures.victory(0L)))
        assertFalse(classifier.isVictory(HandPoseFixtures.open(0L)))
        assertFalse(classifier.isVictory(HandPoseFixtures.thumbMenu(0L)))
    }

    @Test
    fun victory_isInvariantToRotationAndMirror() {
        val victory = HandPoseFixtures.victory(0L)

        for (angle in listOf(0f, 90f, 180f, 270f, 37f)) {
            assertTrue(
                "Victory should survive a $angle degree rotation",
                classifier.isVictory(HandPoseFixtures.transform(victory, degrees = angle)),
            )
            assertTrue(
                "Victory should survive mirror plus $angle degree rotation",
                classifier.isVictory(
                    HandPoseFixtures.transform(victory, degrees = angle, mirrorX = true),
                ),
            )
        }
    }

    @Test
    fun thumbMenu_requiresExtendedThumbAndFourFoldedFingers() {
        assertTrue(classifier.isThumbMenu(HandPoseFixtures.thumbMenu(0L)))
        assertFalse(classifier.isThumbMenu(HandPoseFixtures.open(0L)))
        assertFalse(classifier.isThumbMenu(HandPoseFixtures.victory(0L)))
    }

    @Test
    fun thumbMenu_isInvariantToRotationAndMirror() {
        val menu = HandPoseFixtures.thumbMenu(0L)

        for (angle in listOf(0f, 90f, 180f, 270f, 123f)) {
            assertTrue(classifier.isThumbMenu(HandPoseFixtures.transform(menu, degrees = angle)))
            assertTrue(
                classifier.isThumbMenu(
                    HandPoseFixtures.transform(menu, degrees = angle, mirrorX = true),
                ),
            )
        }
    }

    @Test
    fun degenerateLandmarks_areUnknownAndNeverMatchStaticPoses() {
        val pose = HandPoseFixtures.degenerate(0L)

        assertEquals(FingerState.UNKNOWN, classifier.fingerState(pose, Finger.INDEX))
        assertEquals(FingerState.UNKNOWN, classifier.fingerState(pose, Finger.THUMB))
        assertFalse(classifier.isVictory(pose))
        assertFalse(classifier.isThumbMenu(pose))
    }

    @Test
    fun normalizedPinchDistance_usesDepthWithoutDependingOnOrientation() {
        val flat = HandPoseFixtures.primaryPinch(0L, zDifference = 0f)
        val separatedInDepth = HandPoseFixtures.primaryPinch(0L, zDifference = 1f)
        val flatDistance = flat.normalizedDistance(
            HandLandmark.THUMB_TIP,
            HandLandmark.INDEX_TIP,
            zWeight = 0.35f,
        )
        val depthDistance = separatedInDepth.normalizedDistance(
            HandLandmark.THUMB_TIP,
            HandLandmark.INDEX_TIP,
            zWeight = 0.35f,
        )

        assertTrue(flatDistance < depthDistance)
        assertEquals(
            flatDistance,
            HandPoseFixtures.transform(flat, degrees = 90f, mirrorX = true)
                .normalizedDistance(
                    HandLandmark.THUMB_TIP,
                    HandLandmark.INDEX_TIP,
                    zWeight = 0.35f,
                ),
            0.0001f,
        )
    }
}
