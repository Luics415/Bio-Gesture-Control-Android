package com.luics415.biogesture.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureEngineTest {
    @Test
    fun primaryPinch_releasedBefore300ms_emitsExactlyOneTapAtInitialAnchor() {
        val engine = GestureEngine()
        val initial = HandPoseFixtures.primaryPinch(0L)

        assertTrue(engine.onHandPose(initial).ofType<GestureEffect.Tap>().isEmpty())
        assertEquals(GestureInteractionPhase.PRIMARY_CANDIDATE, engine.snapshot.interaction)
        assertTrue(engine.onHandPose(HandPoseFixtures.primaryPinch(50L)).ofType<GestureEffect.Tap>().isEmpty())

        val firstOpenSample = engine.onHandPose(HandPoseFixtures.open(100L))
        assertTrue(firstOpenSample.ofType<GestureEffect.Tap>().isEmpty())
        assertEquals(GestureInteractionPhase.PRIMARY_CANDIDATE, engine.snapshot.interaction)
        assertTrue(
            engine.onHandPose(HandPoseFixtures.open(209L))
                .ofType<GestureEffect.Tap>()
                .isEmpty(),
        )

        val releaseEffects = engine.onHandPose(HandPoseFixtures.open(210L))
        assertEquals(
            listOf(GestureEffect.Tap(initial[HandLandmark.INDEX_TIP])),
            releaseEffects.ofType<GestureEffect.Tap>(),
        )
        assertEquals(GestureInteractionPhase.NEUTRAL, engine.snapshot.interaction)

        assertTrue(engine.onHandPose(HandPoseFixtures.open(211L)).ofType<GestureEffect.Tap>().isEmpty())
        assertEquals(GestureInteractionPhase.NEUTRAL, engine.snapshot.interaction)
    }

    @Test
    fun primaryPinch_shorterThanConfirmation_isIgnored() {
        val engine = GestureEngine()

        engine.onHandPose(HandPoseFixtures.primaryPinch(0L))
        val firstOpenSample = engine.onHandPose(HandPoseFixtures.open(40L))
        assertTrue(firstOpenSample.ofType<GestureEffect.Tap>().isEmpty())
        val effects = engine.onHandPose(HandPoseFixtures.open(150L))

        assertTrue(effects.ofType<GestureEffect.Tap>().isEmpty())
        assertEquals(GestureInteractionPhase.NEUTRAL, engine.snapshot.interaction)
    }

    @Test
    fun primaryPinch_at300ms_startsDragAndNeverAlsoClicks() {
        val engine = GestureEngine()
        val initial = HandPoseFixtures.primaryPinch(0L)

        engine.onHandPose(initial)
        assertTrue(
            engine.onHandPose(HandPoseFixtures.primaryPinch(299L, indexOffsetX = 0.05f))
                .ofType<GestureEffect.DragStarted>()
                .isEmpty(),
        )

        val startEffects = engine.onHandPose(
            HandPoseFixtures.primaryPinch(300L, indexOffsetX = 0.10f),
        )
        assertEquals(
            listOf(GestureEffect.DragStarted(initial[HandLandmark.INDEX_TIP])),
            startEffects.ofType<GestureEffect.DragStarted>(),
        )
        assertEquals(1, startEffects.ofType<GestureEffect.DragMoved>().size)
        assertTrue(startEffects.ofType<GestureEffect.Tap>().isEmpty())
        assertEquals(GestureInteractionPhase.DRAGGING, engine.snapshot.interaction)

        val moveEffects = engine.onHandPose(
            HandPoseFixtures.primaryPinch(350L, indexOffsetX = 0.20f),
        )
        assertEquals(1, moveEffects.ofType<GestureEffect.DragMoved>().size)

        assertTrue(
            engine.onHandPose(HandPoseFixtures.open(400L))
                .ofType<GestureEffect.DragEnded>()
                .isEmpty(),
        )
        assertTrue(
            engine.onHandPose(HandPoseFixtures.open(509L))
                .ofType<GestureEffect.DragEnded>()
                .isEmpty(),
        )
        val releaseEffects = engine.onHandPose(HandPoseFixtures.open(510L))
        assertEquals(DragEndReason.RELEASED, releaseEffects.singleDragEnd().reason)
        assertTrue(releaseEffects.ofType<GestureEffect.Tap>().isEmpty())
    }

    @Test
    fun primaryPinch_hysteresisKeepsCandidateBetweenCloseAndReleaseThresholds() {
        val engine = GestureEngine()

        engine.onHandPose(HandPoseFixtures.primaryPinch(0L))
        val jitterEffects = engine.onHandPose(
            HandPoseFixtures.primaryAtIntermediateDistance(150L, ratio = 0.26f),
        )

        assertTrue(jitterEffects.ofType<GestureEffect.Tap>().isEmpty())
        assertEquals(GestureInteractionPhase.PRIMARY_CANDIDATE, engine.snapshot.interaction)
        assertTrue(engine.onHandPose(HandPoseFixtures.open(200L)).ofType<GestureEffect.Tap>().isEmpty())
        assertEquals(1, engine.onHandPose(HandPoseFixtures.open(310L)).ofType<GestureEffect.Tap>().size)
    }

    @Test
    fun singleOpenSample_duringClickCandidateDoesNotTapOrResetThePinch() {
        val engine = GestureEngine()

        engine.onHandPose(HandPoseFixtures.primaryPinch(0L))
        val noisyOpen = engine.onHandPose(HandPoseFixtures.open(100L))
        assertTrue(noisyOpen.ofType<GestureEffect.Tap>().isEmpty())
        assertEquals(GestureInteractionPhase.PRIMARY_CANDIDATE, engine.snapshot.interaction)

        val closedAgain = engine.onHandPose(HandPoseFixtures.primaryPinch(150L))
        assertTrue(closedAgain.ofType<GestureEffect.Tap>().isEmpty())
        assertEquals(GestureInteractionPhase.PRIMARY_CANDIDATE, engine.snapshot.interaction)

        engine.onHandPose(HandPoseFixtures.open(200L))
        assertTrue(engine.onHandPose(HandPoseFixtures.open(309L)).ofType<GestureEffect.Tap>().isEmpty())
        assertEquals(1, engine.onHandPose(HandPoseFixtures.open(310L)).ofType<GestureEffect.Tap>().size)
    }

    @Test
    fun singleOpenSample_duringDragDoesNotLiftVirtualContact() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.primaryPinch(0L))
        engine.onHandPose(HandPoseFixtures.primaryPinch(300L))

        val noisyOpen = engine.onHandPose(HandPoseFixtures.open(350L))
        assertTrue(noisyOpen.ofType<GestureEffect.DragEnded>().isEmpty())
        assertEquals(GestureInteractionPhase.DRAGGING, engine.snapshot.interaction)

        val closedAgain = engine.onHandPose(
            HandPoseFixtures.primaryPinch(400L, indexOffsetX = 0.1f),
        )
        assertEquals(1, closedAgain.ofType<GestureEffect.DragMoved>().size)
        assertTrue(closedAgain.ofType<GestureEffect.DragEnded>().isEmpty())

        engine.onHandPose(HandPoseFixtures.open(500L))
        assertTrue(engine.onHandPose(HandPoseFixtures.open(609L)).ofType<GestureEffect.DragEnded>().isEmpty())
        assertEquals(
            DragEndReason.RELEASED,
            engine.onHandPose(HandPoseFixtures.open(610L)).singleDragEnd().reason,
        )
    }

    @Test
    fun primaryPinch_depthSeparation_doesNotCountAsClosed() {
        val engine = GestureEngine()

        engine.onHandPose(HandPoseFixtures.primaryPinch(0L, zDifference = 1f))

        assertEquals(GestureInteractionPhase.NEUTRAL, engine.snapshot.interaction)
    }

    @Test
    fun contextPinch_confirmsOnceThenLatchesUntilRelease() {
        val engine = GestureEngine()
        val initial = HandPoseFixtures.contextPinch(0L)

        engine.onHandPose(initial)
        assertTrue(
            engine.onHandPose(HandPoseFixtures.contextPinch(99L))
                .ofType<GestureEffect.ContextClick>()
                .isEmpty(),
        )

        val confirmed = engine.onHandPose(HandPoseFixtures.contextPinch(100L))
        assertEquals(
            listOf(GestureEffect.ContextClick(initial[HandLandmark.INDEX_TIP])),
            confirmed.ofType<GestureEffect.ContextClick>(),
        )
        assertEquals(GestureInteractionPhase.CONTEXT_LATCHED, engine.snapshot.interaction)
        assertTrue(
            engine.onHandPose(HandPoseFixtures.contextPinch(500L))
                .ofType<GestureEffect.ContextClick>()
                .isEmpty(),
        )

        engine.onHandPose(HandPoseFixtures.open(510L))
        assertEquals(GestureInteractionPhase.CONTEXT_LATCHED, engine.snapshot.interaction)
        engine.onHandPose(HandPoseFixtures.open(620L))
        assertEquals(GestureInteractionPhase.NEUTRAL, engine.snapshot.interaction)
    }

    @Test
    fun contextLatch_requiresStableReleaseBeforeItCanRearm() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.contextPinch(0L))
        assertEquals(
            1,
            engine.onHandPose(HandPoseFixtures.contextPinch(100L))
                .ofType<GestureEffect.ContextClick>()
                .size,
        )

        engine.onHandPose(HandPoseFixtures.open(200L))
        val noisyReclose = engine.onHandPose(HandPoseFixtures.contextPinch(250L))
        assertTrue(noisyReclose.ofType<GestureEffect.ContextClick>().isEmpty())
        assertEquals(GestureInteractionPhase.CONTEXT_LATCHED, engine.snapshot.interaction)

        engine.onHandPose(HandPoseFixtures.open(300L))
        engine.onHandPose(HandPoseFixtures.open(410L))
        assertEquals(GestureInteractionPhase.NEUTRAL, engine.snapshot.interaction)

        engine.onHandPose(HandPoseFixtures.contextPinch(420L))
        assertEquals(
            1,
            engine.onHandPose(HandPoseFixtures.contextPinch(520L))
                .ofType<GestureEffect.ContextClick>()
                .size,
        )
    }

    @Test
    fun simultaneousPinches_enterAmbiguousAndDoNothingUntilBothRelease() {
        val engine = GestureEngine()

        engine.onHandPose(HandPoseFixtures.ambiguousPinch(0L))
        assertEquals(GestureInteractionPhase.AMBIGUOUS, engine.snapshot.interaction)

        val oneStillClosed = engine.onHandPose(HandPoseFixtures.primaryPinch(400L))
        assertNoActions(oneStillClosed)
        assertEquals(GestureInteractionPhase.AMBIGUOUS, engine.snapshot.interaction)

        engine.onHandPose(HandPoseFixtures.open(500L))
        assertEquals(GestureInteractionPhase.AMBIGUOUS, engine.snapshot.interaction)
        engine.onHandPose(HandPoseFixtures.open(610L))
        assertEquals(GestureInteractionPhase.NEUTRAL, engine.snapshot.interaction)

        engine.onHandPose(HandPoseFixtures.primaryPinch(700L))
        engine.onHandPose(HandPoseFixtures.open(800L))
        val validTap = engine.onHandPose(HandPoseFixtures.open(910L))
        assertEquals(1, validTap.ofType<GestureEffect.Tap>().size)
    }

    @Test
    fun secondPinchJoiningPrimaryCandidate_cancelsCandidateAsAmbiguous() {
        val engine = GestureEngine()

        engine.onHandPose(HandPoseFixtures.primaryPinch(0L))
        val effects = engine.onHandPose(HandPoseFixtures.ambiguousPinch(100L))
        assertNoActions(effects)
        assertEquals(GestureInteractionPhase.AMBIGUOUS, engine.snapshot.interaction)

        assertNoActions(engine.onHandPose(HandPoseFixtures.primaryPinch(400L)))
        assertNoActions(engine.onHandPose(HandPoseFixtures.open(500L)))
    }

    @Test
    fun establishedDrag_ownsGestureEvenIfContextPinchAlsoCloses() {
        val engine = GestureEngine()

        engine.onHandPose(HandPoseFixtures.primaryPinch(0L))
        engine.onHandPose(HandPoseFixtures.primaryPinch(300L))

        val ambiguousLookingFrame = engine.onHandPose(HandPoseFixtures.ambiguousPinch(350L))
        assertEquals(1, ambiguousLookingFrame.ofType<GestureEffect.DragMoved>().size)
        assertTrue(ambiguousLookingFrame.ofType<GestureEffect.ContextClick>().isEmpty())
        assertEquals(GestureInteractionPhase.DRAGGING, engine.snapshot.interaction)
    }

    @Test
    fun victoryHeldForThreeSeconds_pausesAndCannotOscillateWhileStillHeld() {
        val engine = GestureEngine()

        engine.onHandPose(HandPoseFixtures.victory(0L))
        assertFalse(engine.snapshot.paused)
        assertTrue(
            engine.onHandPose(HandPoseFixtures.victory(2_999L))
                .filter { it == GestureEffect.PauseActivated }
                .isEmpty(),
        )

        val pausedEffects = engine.onHandPose(HandPoseFixtures.victory(3_000L))
        assertTrue(pausedEffects.contains(GestureEffect.PauseActivated))
        assertTrue(engine.snapshot.paused)
        assertTrue(engine.snapshot.waitingForVictoryRelease)

        val stillHeld = engine.onHandPose(HandPoseFixtures.victory(20_000L))
        assertFalse(stillHeld.contains(GestureEffect.PauseDeactivated))
        assertTrue(engine.snapshot.paused)
    }

    @Test
    fun briefFalsePose_duringVictoryHoldKeepsTimerAndCannotBecomeClick() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.victory(0L))
        engine.onHandPose(HandPoseFixtures.victory(1_000L))

        val falsePinchFrame = engine.onHandPose(HandPoseFixtures.primaryPinch(1_500L))
        assertTrue(falsePinchFrame.ofType<GestureEffect.CursorMoved>().isEmpty())
        assertNoActions(falsePinchFrame)
        assertEquals(GestureInteractionPhase.NEUTRAL, engine.snapshot.interaction)

        engine.onHandPose(HandPoseFixtures.victory(1_600L))
        val activation = engine.onHandPose(HandPoseFixtures.victory(3_000L))
        assertTrue(activation.contains(GestureEffect.PauseActivated))
        assertTrue(engine.snapshot.paused)
    }

    @Test
    fun sustainedFalsePose_expiresVictoryGraceBeforeItCanStartAnotherGesture() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.victory(0L))

        assertNoActions(engine.onHandPose(HandPoseFixtures.primaryPinch(1_000L)))
        assertNoActions(engine.onHandPose(HandPoseFixtures.primaryPinch(1_249L)))
        engine.onHandPose(HandPoseFixtures.primaryPinch(1_250L))
        assertEquals(GestureInteractionPhase.PRIMARY_CANDIDATE, engine.snapshot.interaction)

        engine.onHandPose(HandPoseFixtures.open(1_350L))
        val tap = engine.onHandPose(HandPoseFixtures.open(1_460L))
        assertEquals(1, tap.ofType<GestureEffect.Tap>().size)
        assertFalse(engine.snapshot.paused)
    }

    @Test
    fun oneMissingHandSample_duringVictoryHoldIsTolerated() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.victory(0L))
        engine.onHandPose(HandPoseFixtures.victory(1_000L))

        engine.onHandLost(1_500L)
        engine.onHandPose(HandPoseFixtures.victory(1_667L))
        val activation = engine.onHandPose(HandPoseFixtures.victory(3_000L))

        assertTrue(activation.contains(GestureEffect.PauseActivated))
        assertTrue(engine.snapshot.paused)
    }

    @Test
    fun victoryReturningAfterDropoutGrace_restartsThreeSecondHold() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.victory(0L))
        engine.onHandPose(HandPoseFixtures.open(1_000L))

        engine.onHandPose(HandPoseFixtures.victory(1_250L))
        assertFalse(
            engine.onHandPose(HandPoseFixtures.victory(3_000L))
                .contains(GestureEffect.PauseActivated),
        )
        assertFalse(engine.snapshot.paused)

        val activation = engine.onHandPose(HandPoseFixtures.victory(4_250L))
        assertTrue(activation.contains(GestureEffect.PauseActivated))
        assertTrue(engine.snapshot.paused)
    }

    @Test
    fun pausedMode_ignoresPinches_thenRequiresReleaseAndSecondThreeSecondVictory() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.victory(0L))
        engine.onHandPose(HandPoseFixtures.victory(3_000L))

        val ignoredPinch1 = engine.onHandPose(HandPoseFixtures.primaryPinch(3_100L))
        val ignoredPinch2 = engine.onHandPose(HandPoseFixtures.primaryPinch(3_300L))
        assertNoActions(ignoredPinch1)
        assertNoActions(ignoredPinch2)
        assertTrue(engine.snapshot.paused)
        assertFalse(engine.snapshot.waitingForVictoryRelease)

        engine.onHandPose(HandPoseFixtures.victory(4_000L))
        assertTrue(
            engine.onHandPose(HandPoseFixtures.victory(6_999L))
                .filter { it == GestureEffect.PauseDeactivated }
                .isEmpty(),
        )
        val resumed = engine.onHandPose(HandPoseFixtures.victory(7_000L))
        assertTrue(resumed.contains(GestureEffect.PauseDeactivated))
        assertFalse(engine.snapshot.paused)
        assertTrue(engine.snapshot.waitingForVictoryRelease)

        // The same held V cannot immediately arm a new pause or generate pointer effects.
        assertTrue(engine.onHandPose(HandPoseFixtures.victory(20_000L)).isEmpty())
        assertFalse(engine.snapshot.paused)
    }

    @Test
    fun noHandCountsAsVictoryReleaseWhilePaused() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.victory(0L))
        engine.onHandPose(HandPoseFixtures.victory(3_000L))

        engine.onHandLost(3_100L)
        engine.onHandLost(3_300L)
        assertFalse(engine.snapshot.waitingForVictoryRelease)

        engine.onHandPose(HandPoseFixtures.victory(4_000L))
        val resumed = engine.onHandPose(HandPoseFixtures.victory(7_000L))
        assertTrue(resumed.contains(GestureEffect.PauseDeactivated))
    }

    @Test
    fun victoryPose_reservesControlsAndEndsAnActiveDrag() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.primaryPinch(0L))
        engine.onHandPose(HandPoseFixtures.primaryPinch(300L))

        val victoryStart = engine.onHandPose(HandPoseFixtures.victory(400L))

        assertEquals(DragEndReason.VICTORY_GESTURE, victoryStart.singleDragEnd().reason)
        assertTrue(victoryStart.ofType<GestureEffect.Tap>().isEmpty())
        assertFalse(engine.snapshot.paused)

        val pause = engine.onHandPose(HandPoseFixtures.victory(3_400L))
        assertTrue(pause.contains(GestureEffect.PauseActivated))
    }

    @Test
    fun lostHand_duringDragEndsOnlyAfterGracePeriod() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.primaryPinch(0L))
        engine.onHandPose(HandPoseFixtures.primaryPinch(300L, indexOffsetX = 0.1f))

        val firstMissing = engine.onHandLost(400L)
        assertFalse(firstMissing.contains(GestureEffect.HandTrackingLost))
        assertTrue(firstMissing.ofType<GestureEffect.DragEnded>().isEmpty())
        assertEquals(GestureInteractionPhase.DRAGGING, engine.snapshot.interaction)

        assertTrue(engine.onHandLost(749L).ofType<GestureEffect.DragEnded>().isEmpty())
        val expired = engine.onHandLost(750L)
        assertTrue(expired.contains(GestureEffect.HandTrackingLost))
        assertEquals(DragEndReason.HAND_LOST, expired.singleDragEnd().reason)
        assertEquals(GestureInteractionPhase.NEUTRAL, engine.snapshot.interaction)
        assertTrue(engine.onHandLost(600L).ofType<GestureEffect.DragEnded>().isEmpty())
    }

    @Test
    fun handRecoveredWithinGrace_continuesExistingDrag() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.primaryPinch(0L))
        engine.onHandPose(HandPoseFixtures.primaryPinch(300L))
        engine.onHandLost(400L)

        val recovered = engine.onHandPose(
            HandPoseFixtures.primaryPinch(500L, indexOffsetX = 0.1f),
        )
        assertFalse(recovered.contains(GestureEffect.HandTrackingResumed))
        assertEquals(1, recovered.ofType<GestureEffect.DragMoved>().size)
        assertTrue(recovered.ofType<GestureEffect.DragEnded>().isEmpty())

        assertTrue(
            engine.onHandPose(HandPoseFixtures.open(600L))
                .ofType<GestureEffect.DragEnded>()
                .isEmpty(),
        )
        assertEquals(
            DragEndReason.RELEASED,
            engine.onHandPose(HandPoseFixtures.open(710L)).singleDragEnd().reason,
        )
    }

    @Test
    fun handLoss_cancelsUncommittedClickCandidateWithoutTap() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.primaryPinch(0L))

        assertNoActions(engine.onHandLost(50L))
        assertEquals(GestureInteractionPhase.NEUTRAL, engine.snapshot.interaction)
        engine.onHandPose(HandPoseFixtures.primaryPinch(100L))
        assertTrue(engine.onHandPose(HandPoseFixtures.open(120L)).ofType<GestureEffect.Tap>().isEmpty())
    }

    @Test
    fun thumbMenu_requiresStableHold_thenRemainsOpenDuringNaturalNavigation() {
        val engine = GestureEngine()

        engine.onHandPose(HandPoseFixtures.thumbMenu(0L))
        assertEquals(GestureInteractionPhase.MENU_OPENING, engine.snapshot.interaction)
        assertTrue(
            engine.onHandPose(HandPoseFixtures.thumbMenu(799L))
                .ofType<GestureEffect.MenuOpened>()
                .isEmpty(),
        )

        val opened = engine.onHandPose(HandPoseFixtures.thumbMenu(800L))
        assertEquals(1, opened.ofType<GestureEffect.MenuOpened>().size)
        assertEquals(GestureInteractionPhase.MENU_ACTIVE, engine.snapshot.interaction)

        assertEquals(
            1,
            engine.onHandPose(HandPoseFixtures.thumbMenu(850L))
                .ofType<GestureEffect.MenuPointerMoved>()
                .size,
        )
        val navigating = engine.onHandPose(HandPoseFixtures.open(1_200L))
        assertEquals(1, navigating.ofType<GestureEffect.MenuPointerMoved>().size)
        assertTrue(navigating.ofType<GestureEffect.MenuClosed>().isEmpty())
        assertEquals(GestureInteractionPhase.MENU_ACTIVE, engine.snapshot.interaction)

        engine.finishMenuInteraction(1_210L)
        assertEquals(GestureInteractionPhase.AWAITING_RELEASE, engine.snapshot.interaction)
        engine.onHandPose(HandPoseFixtures.open(1_211L))
        engine.onHandPose(HandPoseFixtures.open(1_321L))
        assertEquals(GestureInteractionPhase.NEUTRAL, engine.snapshot.interaction)
    }

    @Test
    fun briefMenuPoseDropout_doesNotResetOpeningOrCloseAnActiveMenu() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.thumbMenu(0L))
        engine.onHandPose(HandPoseFixtures.thumbMenu(400L))

        assertTrue(
            engine.onHandPose(HandPoseFixtures.open(500L))
                .ofType<GestureEffect.MenuOpened>()
                .isEmpty(),
        )
        engine.onHandPose(HandPoseFixtures.thumbMenu(600L))
        assertEquals(
            1,
            engine.onHandPose(HandPoseFixtures.thumbMenu(800L))
                .ofType<GestureEffect.MenuOpened>()
                .size,
        )

        assertTrue(
            engine.onHandPose(HandPoseFixtures.open(900L))
                .ofType<GestureEffect.MenuClosed>()
                .isEmpty(),
        )
        val recovered = engine.onHandPose(HandPoseFixtures.thumbMenu(950L))
        assertEquals(1, recovered.ofType<GestureEffect.MenuPointerMoved>().size)
        assertTrue(recovered.ofType<GestureEffect.MenuClosed>().isEmpty())
        assertEquals(GestureInteractionPhase.MENU_ACTIVE, engine.snapshot.interaction)
    }

    @Test
    fun longOpeningDropoutRestartsButAnActiveMenuIgnoresPoseChanges() {
        val openingEngine = GestureEngine()
        openingEngine.onHandPose(HandPoseFixtures.thumbMenu(0L))
        openingEngine.onHandPose(HandPoseFixtures.open(400L))
        assertTrue(
            openingEngine.onHandPose(HandPoseFixtures.thumbMenu(620L))
                .ofType<GestureEffect.MenuOpened>()
                .isEmpty(),
        )
        assertEquals(GestureInteractionPhase.MENU_OPENING, openingEngine.snapshot.interaction)
        assertTrue(
            openingEngine.onHandPose(HandPoseFixtures.thumbMenu(1_419L))
                .ofType<GestureEffect.MenuOpened>()
                .isEmpty(),
        )
        assertEquals(
            1,
            openingEngine.onHandPose(HandPoseFixtures.thumbMenu(1_420L))
                .ofType<GestureEffect.MenuOpened>()
                .size,
        )

        val activeEngine = GestureEngine()
        activeEngine.onHandPose(HandPoseFixtures.thumbMenu(0L))
        activeEngine.onHandPose(HandPoseFixtures.thumbMenu(800L))
        val movedWithoutPose = activeEngine.onHandPose(HandPoseFixtures.open(1_020L))
        assertEquals(1, movedWithoutPose.ofType<GestureEffect.MenuPointerMoved>().size)
        assertTrue(movedWithoutPose.ofType<GestureEffect.MenuClosed>().isEmpty())
        assertEquals(GestureInteractionPhase.MENU_ACTIVE, activeEngine.snapshot.interaction)
    }

    @Test
    fun handLoss_closesActiveMenu() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.thumbMenu(0L))
        engine.onHandPose(HandPoseFixtures.thumbMenu(800L))

        assertTrue(engine.onHandLost(810L).ofType<GestureEffect.MenuClosed>().isEmpty())
        assertTrue(engine.onHandLost(1_159L).ofType<GestureEffect.MenuClosed>().isEmpty())
        val effects = engine.onHandLost(1_160L)

        assertEquals(MenuCloseReason.HAND_LOST, effects.singleMenuClose().reason)
        assertEquals(GestureInteractionPhase.NEUTRAL, engine.snapshot.interaction)
    }

    @Test
    fun staleObservation_isIgnoredWithoutRewindingState() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.primaryPinch(100L))

        assertTrue(engine.onHandPose(HandPoseFixtures.open(90L)).isEmpty())
        assertEquals(GestureInteractionPhase.PRIMARY_CANDIDATE, engine.snapshot.interaction)
        assertTrue(engine.onHandPose(HandPoseFixtures.open(200L)).ofType<GestureEffect.Tap>().isEmpty())
        assertEquals(1, engine.onHandPose(HandPoseFixtures.open(310L)).ofType<GestureEffect.Tap>().size)
    }

    @Test
    fun externalCancellation_endsDragAndResetsState() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.primaryPinch(0L))
        engine.onHandPose(HandPoseFixtures.primaryPinch(300L))

        val effects = engine.cancelActiveInteraction(350L)

        assertEquals(DragEndReason.EXTERNAL_CANCEL, effects.singleDragEnd().reason)
        assertEquals(GestureInteractionPhase.NEUTRAL, engine.snapshot.interaction)
    }

    @Test
    fun trackingReset_marksLossAndFirstRecoveredPoseAsResumed() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.open(0L))

        val reset = engine.resetTracking(10L)
        assertTrue(reset.contains(GestureEffect.HandTrackingLost))
        assertFalse(engine.snapshot.handTracked)

        val recovered = engine.onHandPose(HandPoseFixtures.open(20L))
        assertTrue(recovered.contains(GestureEffect.HandTrackingResumed))
        assertTrue(engine.snapshot.handTracked)
    }

    @Test
    fun trackingResetWhilePaused_stillAnnouncesRecoveredHand() {
        val engine = GestureEngine()
        engine.onHandPose(HandPoseFixtures.victory(0L))
        engine.onHandPose(HandPoseFixtures.victory(3_000L))
        engine.resetTracking(3_100L)

        val recovered = engine.onHandPose(HandPoseFixtures.open(3_200L))

        assertTrue(recovered.contains(GestureEffect.HandTrackingResumed))
        assertTrue(engine.snapshot.paused)
    }

    private inline fun <reified T : GestureEffect> List<GestureEffect>.ofType(): List<T> =
        filterIsInstance<T>()

    private fun List<GestureEffect>.singleDragEnd(): GestureEffect.DragEnded =
        ofType<GestureEffect.DragEnded>().single()

    private fun List<GestureEffect>.singleMenuClose(): GestureEffect.MenuClosed =
        ofType<GestureEffect.MenuClosed>().single()

    private fun assertNoActions(effects: List<GestureEffect>) {
        assertTrue(effects.ofType<GestureEffect.Tap>().isEmpty())
        assertTrue(effects.ofType<GestureEffect.DragStarted>().isEmpty())
        assertTrue(effects.ofType<GestureEffect.DragEnded>().isEmpty())
        assertTrue(effects.ofType<GestureEffect.ContextClick>().isEmpty())
        assertTrue(effects.ofType<GestureEffect.MenuOpened>().isEmpty())
    }
}
