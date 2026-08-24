package com.luics415.biogesture.gesture

data class GestureThresholds(
    val pinchCloseRatio: Float = 0.20f,
    val pinchReleaseRatio: Float = 0.34f,
    val pinchZWeight: Float = 0.35f,
    val pinchReleaseConfirmMs: Long = 110L,
    val primaryClickMinMs: Long = 60L,
    val primaryDragHoldMs: Long = 300L,
    val contextConfirmMs: Long = 100L,
    val victoryHoldMs: Long = 3_000L,
    val victoryDropoutGraceMs: Long = 250L,
    val victoryReleaseMs: Long = 200L,
    val menuHoldMs: Long = 800L,
    val menuOpeningDropoutGraceMs: Long = 220L,
    val handLossGraceMs: Long = 350L,
) {
    init {
        require(pinchCloseRatio > 0f)
        require(pinchReleaseRatio > pinchCloseRatio)
        require(pinchZWeight >= 0f)
        require(pinchReleaseConfirmMs >= 0L)
        require(primaryClickMinMs >= 0L)
        require(primaryDragHoldMs > primaryClickMinMs)
        require(contextConfirmMs >= 0L)
        require(victoryHoldMs > 0L)
        require(victoryDropoutGraceMs >= 0L)
        require(victoryReleaseMs >= 0L)
        require(menuHoldMs >= 0L)
        require(menuOpeningDropoutGraceMs >= 0L)
        require(handLossGraceMs >= 0L)
    }
}

enum class GestureInteractionPhase {
    NEUTRAL,
    PRIMARY_CANDIDATE,
    DRAGGING,
    CONTEXT_CANDIDATE,
    CONTEXT_LATCHED,
    AMBIGUOUS,
    AWAITING_RELEASE,
    MENU_OPENING,
    MENU_ACTIVE,
}

data class GestureEngineSnapshot(
    val paused: Boolean,
    val interaction: GestureInteractionPhase,
    val waitingForVictoryRelease: Boolean,
    val handTracked: Boolean,
)

enum class DragEndReason {
    RELEASED,
    HAND_LOST,
    PAUSED,
    VICTORY_GESTURE,
    EXTERNAL_CANCEL,
}

enum class MenuCloseReason {
    POSE_RELEASED,
    HAND_LOST,
    PAUSED,
    VICTORY_GESTURE,
    EXTERNAL_CANCEL,
}

sealed interface GestureEffect {
    data class CursorMoved(val indexTip: Point3) : GestureEffect
    data class Tap(val position: Point3) : GestureEffect
    data class DragStarted(val position: Point3) : GestureEffect
    data class DragMoved(val position: Point3) : GestureEffect
    data class DragEnded(val position: Point3, val reason: DragEndReason) : GestureEffect
    data class ContextClick(val position: Point3) : GestureEffect
    data class MenuOpened(val anchor: Point3) : GestureEffect
    data class MenuPointerMoved(val thumbTip: Point3) : GestureEffect
    data class MenuClosed(val reason: MenuCloseReason) : GestureEffect
    data object PauseActivated : GestureEffect
    data object PauseDeactivated : GestureEffect
    data object HandTrackingLost : GestureEffect
    data object HandTrackingResumed : GestureEffect
}

/**
 * Deterministic, Android-free gesture state machine.
 *
 * It consumes MediaPipe observations, but never dispatches Android actions itself. Callers must
 * serialize [GestureEffect] execution (especially accessibility gestures) in emission order.
 */
class GestureEngine(
    private val thresholds: GestureThresholds = GestureThresholds(),
    private val classifier: HandPoseClassifier = HandPoseClassifier(),
) {
    private sealed interface Interaction {
        data object Neutral : Interaction
        data class PrimaryCandidate(
            val startedAtMs: Long,
            val anchor: Point3,
            val releaseStartedAtMs: Long? = null,
        ) : Interaction
        data class Dragging(
            val lastPosition: Point3,
            val releaseStartedAtMs: Long? = null,
        ) : Interaction
        data class ContextCandidate(
            val startedAtMs: Long,
            val anchor: Point3,
            val releaseStartedAtMs: Long? = null,
        ) : Interaction
        data class ContextLatched(val releaseStartedAtMs: Long? = null) : Interaction
        data class Ambiguous(val releaseStartedAtMs: Long? = null) : Interaction
        data class AwaitingRelease(val releaseStartedAtMs: Long? = null) : Interaction
        data class MenuOpening(
            val startedAtMs: Long,
            val dropoutStartedAtMs: Long? = null,
        ) : Interaction
        data class MenuActive(val anchor: Point3) : Interaction
    }

    private enum class PauseTransition {
        NONE,
        ACTIVATED,
        DEACTIVATED,
    }

    private data class VictoryGateUpdate(
        val transition: PauseTransition = PauseTransition.NONE,
        val reservesControls: Boolean = false,
    )

    private var interaction: Interaction = Interaction.Neutral
    private var paused = false
    private var handTracked = false
    private var lastTimestampMs = Long.MIN_VALUE
    private var lastIndexTip: Point3? = null
    private var handLostSinceMs: Long? = null

    private var victoryHoldStartedAtMs: Long? = null
    private var victoryDropoutStartedAtMs: Long? = null
    private var victoryReleaseStartedAtMs: Long? = null
    private var victoryMustBeReleased = false

    val snapshot: GestureEngineSnapshot
        get() = GestureEngineSnapshot(
            paused = paused,
            interaction = interaction.toPublicPhase(),
            waitingForVictoryRelease = victoryMustBeReleased,
            handTracked = handTracked,
        )

    fun onHandPose(pose: HandPose): List<GestureEffect> {
        if (!acceptTimestamp(pose.timestampMs)) return emptyList()

        val effects = mutableListOf<GestureEffect>()
        if (!handTracked) effects += GestureEffect.HandTrackingResumed
        handTracked = true
        handLostSinceMs = null

        val indexTip = pose[HandLandmark.INDEX_TIP]
        lastIndexTip = indexTip
        val victory = classifier.isVictory(pose)

        val victoryUpdate = updateVictoryGate(victory, pose.timestampMs)
        when (victoryUpdate.transition) {
            PauseTransition.ACTIVATED -> {
                cancelInteractionForPause(effects)
                effects += GestureEffect.PauseActivated
                return effects
            }
            PauseTransition.DEACTIVATED -> {
                interaction = Interaction.Neutral
                effects += GestureEffect.PauseDeactivated
                return effects
            }
            PauseTransition.NONE -> Unit
        }

        if (paused) return effects

        // A V pose and its short classification dropouts are reserved from the first frame. They
        // cannot become clicks while the pause timer is still armed.
        if (victoryUpdate.reservesControls) {
            cancelInteractionForVictory(effects)
            return effects
        }

        if (interaction !is Interaction.MenuActive) {
            effects += GestureEffect.CursorMoved(indexTip)
        }

        processActivePose(pose, effects)
        return effects
    }

    /** Reports a frame for which no usable hand was detected. */
    fun onHandLost(timestampMs: Long): List<GestureEffect> {
        require(timestampMs >= 0L) { "timestampMs must be non-negative" }
        if (!acceptTimestamp(timestampMs)) return emptyList()

        val effects = mutableListOf<GestureEffect>()
        updateVictoryGate(isVictory = false, timestampMs = timestampMs)

        val lostAt = handLostSinceMs ?: timestampMs.also { handLostSinceMs = it }

        // A partial pinch must never survive an uncertain frame and become a phantom action.
        // Modal interactions, however, tolerate a short detector dropout so the cursor and menu
        // do not flicker when one or two frames are missed.
        when (interaction) {
            is Interaction.PrimaryCandidate,
            is Interaction.ContextCandidate,
            is Interaction.MenuOpening,
            is Interaction.ContextLatched,
            is Interaction.Ambiguous,
            is Interaction.AwaitingRelease,
            -> interaction = Interaction.Neutral
            else -> Unit
        }

        if (timestampMs - lostAt < thresholds.handLossGraceMs) return effects

        if (handTracked) {
            handTracked = false
            if (!paused) effects += GestureEffect.HandTrackingLost
        }

        if (paused) {
            interaction = Interaction.Neutral
            return effects
        }

        when (val current = interaction) {
            is Interaction.Dragging -> {
                effects += GestureEffect.DragEnded(
                    position = current.lastPosition,
                    reason = DragEndReason.HAND_LOST,
                )
                interaction = Interaction.Neutral
            }
            is Interaction.MenuActive -> {
                effects += GestureEffect.MenuClosed(MenuCloseReason.HAND_LOST)
                interaction = Interaction.Neutral
            }
            else -> Unit
        }
        return effects
    }

    /** Releases menu ownership after the radial controller closes it with BACK. */
    fun finishMenuInteraction(timestampMs: Long) {
        require(timestampMs >= 0L) { "timestampMs must be non-negative" }
        if (!acceptTimestamp(timestampMs)) return
        if (interaction is Interaction.MenuActive || interaction is Interaction.MenuOpening) {
            interaction = Interaction.AwaitingRelease()
        }
    }

    /** Cancels modal work before an external coordinate-space change, such as display rotation. */
    fun cancelActiveInteraction(timestampMs: Long): List<GestureEffect> {
        require(timestampMs >= 0L) { "timestampMs must be non-negative" }
        if (!acceptTimestamp(timestampMs)) return emptyList()
        val effects = mutableListOf<GestureEffect>()
        when (val current = interaction) {
            is Interaction.Dragging -> effects += GestureEffect.DragEnded(
                current.lastPosition,
                DragEndReason.EXTERNAL_CANCEL,
            )
            is Interaction.MenuActive -> effects += GestureEffect.MenuClosed(
                MenuCloseReason.EXTERNAL_CANCEL,
            )
            else -> Unit
        }
        interaction = Interaction.Neutral
        return effects
    }

    /** Resets tracking after the camera pipeline is interrupted or rebound. */
    fun resetTracking(timestampMs: Long): List<GestureEffect> {
        require(timestampMs >= 0L) { "timestampMs must be non-negative" }
        if (!acceptTimestamp(timestampMs)) return emptyList()
        val effects = mutableListOf<GestureEffect>()
        when (val current = interaction) {
            is Interaction.Dragging -> effects += GestureEffect.DragEnded(
                current.lastPosition,
                DragEndReason.EXTERNAL_CANCEL,
            )
            is Interaction.MenuActive -> effects += GestureEffect.MenuClosed(
                MenuCloseReason.EXTERNAL_CANCEL,
            )
            else -> Unit
        }
        if (handTracked) effects += GestureEffect.HandTrackingLost
        interaction = Interaction.Neutral
        handTracked = false
        handLostSinceMs = null
        lastIndexTip = null
        victoryHoldStartedAtMs = null
        victoryDropoutStartedAtMs = null
        victoryReleaseStartedAtMs = null
        return effects
    }

    private fun processActivePose(pose: HandPose, effects: MutableList<GestureEffect>) {
        val timestampMs = pose.timestampMs
        val indexTip = pose[HandLandmark.INDEX_TIP]
        val thumbTip = pose[HandLandmark.THUMB_TIP]
        val primaryDistance = pose.normalizedDistance(
            HandLandmark.THUMB_TIP,
            HandLandmark.INDEX_TIP,
            thresholds.pinchZWeight,
        )
        val contextDistance = pose.normalizedDistance(
            HandLandmark.THUMB_TIP,
            HandLandmark.MIDDLE_TIP,
            thresholds.pinchZWeight,
        )
        val primaryClosed = primaryDistance <= thresholds.pinchCloseRatio
        val contextClosed = contextDistance <= thresholds.pinchCloseRatio
        val primaryReleased = primaryDistance >= thresholds.pinchReleaseRatio
        val contextReleased = contextDistance >= thresholds.pinchReleaseRatio
        val bothReleased = primaryReleased && contextReleased
        val thumbMenu = classifier.isThumbMenu(pose)

        when (val current = interaction) {
            Interaction.Neutral -> when {
                primaryClosed && contextClosed -> interaction = Interaction.Ambiguous()
                thumbMenu && (primaryClosed || contextClosed) -> interaction = Interaction.Ambiguous()
                thumbMenu -> interaction = Interaction.MenuOpening(timestampMs)
                primaryClosed -> interaction = Interaction.PrimaryCandidate(timestampMs, indexTip)
                contextClosed -> interaction = Interaction.ContextCandidate(timestampMs, indexTip)
            }

            is Interaction.PrimaryCandidate -> when {
                contextClosed -> interaction = Interaction.Ambiguous()
                primaryReleased -> {
                    val releaseStartedAt = current.releaseStartedAtMs ?: timestampMs
                    if (timestampMs - releaseStartedAt >= thresholds.pinchReleaseConfirmMs) {
                        val heldFor = releaseStartedAt - current.startedAtMs
                        if (heldFor >= thresholds.primaryClickMinMs &&
                            heldFor < thresholds.primaryDragHoldMs
                        ) {
                            effects += GestureEffect.Tap(current.anchor)
                        }
                        interaction = Interaction.Neutral
                    } else {
                        interaction = current.copy(releaseStartedAtMs = releaseStartedAt)
                    }
                }
                timestampMs - current.startedAtMs >= thresholds.primaryDragHoldMs -> {
                    effects += GestureEffect.DragStarted(current.anchor)
                    if (indexTip != current.anchor) effects += GestureEffect.DragMoved(indexTip)
                    interaction = Interaction.Dragging(indexTip)
                }
                else -> interaction = current.copy(releaseStartedAtMs = null)
            }

            is Interaction.Dragging -> {
                if (primaryReleased) {
                    val releaseStartedAt = current.releaseStartedAtMs ?: timestampMs
                    if (timestampMs - releaseStartedAt >= thresholds.pinchReleaseConfirmMs) {
                        effects += GestureEffect.DragEnded(
                            current.lastPosition,
                            DragEndReason.RELEASED,
                        )
                        interaction = Interaction.Neutral
                    } else {
                        interaction = current.copy(releaseStartedAtMs = releaseStartedAt)
                    }
                } else {
                    effects += GestureEffect.DragMoved(indexTip)
                    interaction = current.copy(
                        lastPosition = indexTip,
                        releaseStartedAtMs = null,
                    )
                }
            }

            is Interaction.ContextCandidate -> when {
                primaryClosed -> interaction = Interaction.Ambiguous()
                contextReleased -> {
                    val releaseStartedAt = current.releaseStartedAtMs ?: timestampMs
                    interaction = if (
                        timestampMs - releaseStartedAt >= thresholds.pinchReleaseConfirmMs
                    ) {
                        Interaction.Neutral
                    } else {
                        current.copy(releaseStartedAtMs = releaseStartedAt)
                    }
                }
                timestampMs - current.startedAtMs >= thresholds.contextConfirmMs -> {
                    effects += GestureEffect.ContextClick(current.anchor)
                    interaction = Interaction.ContextLatched()
                }
                else -> interaction = current.copy(releaseStartedAtMs = null)
            }

            is Interaction.ContextLatched -> {
                interaction = stableBothReleasedState(
                    bothReleased = bothReleased,
                    timestampMs = timestampMs,
                    releaseStartedAtMs = current.releaseStartedAtMs,
                    onWaiting = { current.copy(releaseStartedAtMs = it) },
                )
            }

            is Interaction.Ambiguous -> {
                interaction = stableBothReleasedState(
                    bothReleased = bothReleased,
                    timestampMs = timestampMs,
                    releaseStartedAtMs = current.releaseStartedAtMs,
                    onWaiting = { current.copy(releaseStartedAtMs = it) },
                )
            }

            is Interaction.AwaitingRelease -> {
                interaction = stableBothReleasedState(
                    bothReleased = bothReleased && !thumbMenu,
                    timestampMs = timestampMs,
                    releaseStartedAtMs = current.releaseStartedAtMs,
                    onWaiting = { current.copy(releaseStartedAtMs = it) },
                )
            }

            is Interaction.MenuOpening -> when {
                primaryClosed || contextClosed -> interaction = Interaction.Ambiguous()
                !thumbMenu -> {
                    val dropoutStartedAt = current.dropoutStartedAtMs ?: timestampMs
                    interaction = if (
                        timestampMs - dropoutStartedAt >= thresholds.menuOpeningDropoutGraceMs
                    ) {
                        Interaction.Neutral
                    } else {
                        current.copy(dropoutStartedAtMs = dropoutStartedAt)
                    }
                }
                current.dropoutStartedAtMs != null &&
                    timestampMs - current.dropoutStartedAtMs >= thresholds.menuOpeningDropoutGraceMs -> {
                    interaction = Interaction.MenuOpening(timestampMs)
                }
                timestampMs - current.startedAtMs >= thresholds.menuHoldMs -> {
                    effects += GestureEffect.MenuOpened(thumbTip)
                    interaction = Interaction.MenuActive(thumbTip)
                }
                else -> interaction = current.copy(dropoutStartedAtMs = null)
            }

            is Interaction.MenuActive -> {
                // The distinctive thumb pose is an opening gesture, not a constraint on every
                // navigation frame. Keeping the menu modal here prevents normal thumb movement
                // from closing it before a sector can complete its dwell.
                effects += GestureEffect.MenuPointerMoved(thumbTip)
            }
        }
    }

    private fun stableBothReleasedState(
        bothReleased: Boolean,
        timestampMs: Long,
        releaseStartedAtMs: Long?,
        onWaiting: (Long?) -> Interaction,
    ): Interaction {
        if (!bothReleased) return onWaiting(null)
        val releaseStartedAt = releaseStartedAtMs ?: timestampMs
        return if (timestampMs - releaseStartedAt >= thresholds.pinchReleaseConfirmMs) {
            Interaction.Neutral
        } else {
            onWaiting(releaseStartedAt)
        }
    }

    private fun updateVictoryGate(isVictory: Boolean, timestampMs: Long): VictoryGateUpdate {
        if (isVictory) {
            val resumedAfterExpiredDropout = victoryDropoutStartedAtMs?.let { dropoutStartedAt ->
                timestampMs - dropoutStartedAt >= thresholds.victoryDropoutGraceMs
            } == true
            victoryDropoutStartedAtMs = null
            victoryReleaseStartedAtMs = null
            if (victoryMustBeReleased) return VictoryGateUpdate(reservesControls = true)

            if (resumedAfterExpiredDropout) victoryHoldStartedAtMs = timestampMs

            val startedAt = victoryHoldStartedAtMs
            if (startedAt == null) {
                victoryHoldStartedAtMs = timestampMs
                return VictoryGateUpdate(reservesControls = true)
            }
            if (timestampMs - startedAt < thresholds.victoryHoldMs) {
                return VictoryGateUpdate(reservesControls = true)
            }

            paused = !paused
            victoryHoldStartedAtMs = null
            victoryDropoutStartedAtMs = null
            victoryMustBeReleased = true
            return VictoryGateUpdate(
                transition = if (paused) PauseTransition.ACTIVATED else PauseTransition.DEACTIVATED,
                reservesControls = true,
            )
        }

        if (victoryMustBeReleased) {
            victoryHoldStartedAtMs = null
            victoryDropoutStartedAtMs = null
            val releaseStartedAt = victoryReleaseStartedAtMs
            if (releaseStartedAt == null) {
                victoryReleaseStartedAtMs = timestampMs
            } else if (timestampMs - releaseStartedAt >= thresholds.victoryReleaseMs) {
                victoryMustBeReleased = false
                victoryReleaseStartedAtMs = null
            }
            return VictoryGateUpdate()
        }

        victoryReleaseStartedAtMs = null
        if (victoryHoldStartedAtMs != null) {
            val dropoutStartedAt = victoryDropoutStartedAtMs ?: timestampMs.also {
                victoryDropoutStartedAtMs = it
            }
            if (timestampMs - dropoutStartedAt < thresholds.victoryDropoutGraceMs) {
                return VictoryGateUpdate(reservesControls = true)
            }
            victoryHoldStartedAtMs = null
            victoryDropoutStartedAtMs = null
        } else {
            victoryDropoutStartedAtMs = null
        }
        return VictoryGateUpdate()
    }

    private fun cancelInteractionForVictory(effects: MutableList<GestureEffect>) {
        when (val current = interaction) {
            is Interaction.Dragging -> effects += GestureEffect.DragEnded(
                current.lastPosition,
                DragEndReason.VICTORY_GESTURE,
            )
            is Interaction.MenuActive -> effects += GestureEffect.MenuClosed(
                MenuCloseReason.VICTORY_GESTURE,
            )
            else -> Unit
        }
        interaction = Interaction.Neutral
    }

    private fun cancelInteractionForPause(effects: MutableList<GestureEffect>) {
        when (val current = interaction) {
            is Interaction.Dragging -> effects += GestureEffect.DragEnded(
                current.lastPosition,
                DragEndReason.PAUSED,
            )
            is Interaction.MenuActive -> effects += GestureEffect.MenuClosed(
                MenuCloseReason.PAUSED,
            )
            else -> Unit
        }
        interaction = Interaction.Neutral
    }

    private fun acceptTimestamp(timestampMs: Long): Boolean {
        if (timestampMs < lastTimestampMs) return false
        lastTimestampMs = timestampMs
        return true
    }

    private fun Interaction.toPublicPhase(): GestureInteractionPhase = when (this) {
        Interaction.Neutral -> GestureInteractionPhase.NEUTRAL
        is Interaction.PrimaryCandidate -> GestureInteractionPhase.PRIMARY_CANDIDATE
        is Interaction.Dragging -> GestureInteractionPhase.DRAGGING
        is Interaction.ContextCandidate -> GestureInteractionPhase.CONTEXT_CANDIDATE
        is Interaction.ContextLatched -> GestureInteractionPhase.CONTEXT_LATCHED
        is Interaction.Ambiguous -> GestureInteractionPhase.AMBIGUOUS
        is Interaction.AwaitingRelease -> GestureInteractionPhase.AWAITING_RELEASE
        is Interaction.MenuOpening -> GestureInteractionPhase.MENU_OPENING
        is Interaction.MenuActive -> GestureInteractionPhase.MENU_ACTIVE
    }
}
