package com.luics415.biogesture.menu

data class RadialMenuTiming(
    val openingHoldMillis: Long = 800L,
    val sectorDwellMillis: Long = 750L,
) {
    init {
        require(openingHoldMillis > 0L)
        require(sectorDwellMillis > 0L)
    }
}

data class RadialMenuInput(
    val timestampMillis: Long,
    val activationPoseDetected: Boolean,
    val pointer: ScalarPoint,
    val viewport: ScalarViewport,
) {
    init {
        require(timestampMillis >= 0L) { "Timestamp must be non-negative." }
    }
}

enum class RadialMenuPhase {
    CLOSED,
    ARMING,
    OPEN,
}

enum class RadialMenuCloseReason {
    ACTIVATION_POSE_RELEASED,
    PRINCIPAL_BACK,
    EXPLICIT_RESET,
}

sealed interface RadialMenuEvent {
    data class Opened(val level: MenuLevelId) : RadialMenuEvent

    data class LevelChanged(
        val from: MenuLevelId,
        val to: MenuLevelId,
    ) : RadialMenuEvent

    data class ActionSelected(
        val level: MenuLevelId,
        val action: MenuActionId,
    ) : RadialMenuEvent

    data class Closed(val reason: RadialMenuCloseReason) : RadialMenuEvent
}

data class RadialMenuSnapshot(
    val phase: RadialMenuPhase,
    val armingProgress: Double = 0.0,
    val level: MenuLevelId? = null,
    val definition: RadialMenuDefinition? = null,
    val layout: RadialMenuLayout? = null,
    val highlightedIndex: Int? = null,
    val dwellProgress: Double = 0.0,
    val selectionArmed: Boolean = false,
    val awaitingActivationRelease: Boolean = false,
    val event: RadialMenuEvent? = null,
)

/**
 * Deterministic radial-menu state machine. The caller supplies monotonic time,
 * pose state, pointer coordinates and viewport dimensions for every sample.
 */
class RadialMenuController(
    private val catalog: RadialMenuCatalog = RadialMenuCatalog.DEFAULT,
    private val geometry: RadialMenuGeometry = RadialMenuGeometry(),
    private val timing: RadialMenuTiming = RadialMenuTiming(),
) {
    private sealed interface State {
        data class Closed(val awaitingRelease: Boolean) : State

        data class Arming(
            val startedAtMillis: Long,
            val lastPointer: ScalarPoint,
            val viewport: ScalarViewport,
        ) : State

        data class Open(
            val level: MenuLevelId,
            val layout: RadialMenuLayout,
            val highlightedIndex: Int?,
            val dwellStartedAtMillis: Long?,
            val selectionArmed: Boolean,
        ) : State
    }

    private var state: State = State.Closed(awaitingRelease = false)
    private var lastTimestampMillis: Long? = null

    fun update(input: RadialMenuInput): RadialMenuSnapshot {
        recordTimestamp(input.timestampMillis)

        return when (val current = state) {
            is State.Closed -> updateClosed(current, input)
            is State.Arming -> updateArming(current, input)
            is State.Open -> updateOpen(current, input)
        }
    }

    /**
     * Opens the principal menu immediately after an external gesture engine has
     * already confirmed the activation pose. This avoids applying the 800 ms
     * opening hold twice while retaining the regular selection latch.
     */
    fun openPrincipal(
        timestampMillis: Long,
        anchor: ScalarPoint,
        viewport: ScalarViewport,
    ): RadialMenuSnapshot {
        recordTimestamp(timestampMillis)
        return openPrincipalAt(anchor, viewport)
    }

    fun snapshot(): RadialMenuSnapshot = snapshotOf(state)

    fun reset(): RadialMenuSnapshot {
        val wasActive = state !is State.Closed || (state as? State.Closed)?.awaitingRelease == true
        state = State.Closed(awaitingRelease = false)
        lastTimestampMillis = null
        return snapshotOf(
            state,
            event = if (wasActive) RadialMenuEvent.Closed(RadialMenuCloseReason.EXPLICIT_RESET) else null,
        )
    }

    private fun updateClosed(current: State.Closed, input: RadialMenuInput): RadialMenuSnapshot {
        if (current.awaitingRelease) {
            if (!input.activationPoseDetected) state = State.Closed(awaitingRelease = false)
            return snapshotOf(state)
        }
        if (!input.activationPoseDetected) return snapshotOf(current)

        state = State.Arming(
            startedAtMillis = input.timestampMillis,
            lastPointer = input.pointer,
            viewport = input.viewport,
        )
        return snapshotOf(state)
    }

    private fun updateArming(current: State.Arming, input: RadialMenuInput): RadialMenuSnapshot {
        if (!input.activationPoseDetected) {
            state = State.Closed(awaitingRelease = false)
            return snapshotOf(state)
        }

        val elapsed = input.timestampMillis - current.startedAtMillis
        if (elapsed < timing.openingHoldMillis) {
            state = current.copy(lastPointer = input.pointer, viewport = input.viewport)
            return snapshotOf(state)
        }

        return openPrincipalAt(input.pointer, input.viewport)
    }

    private fun updateOpen(current: State.Open, input: RadialMenuInput): RadialMenuSnapshot {
        if (!input.activationPoseDetected) {
            state = State.Closed(awaitingRelease = false)
            return snapshotOf(
                state,
                RadialMenuEvent.Closed(RadialMenuCloseReason.ACTIVATION_POSE_RELEASED),
            )
        }

        if (current.layout.viewport != input.viewport) {
            val newLayout = geometry.layout(input.pointer, input.viewport)
            state = current.copy(
                layout = newLayout,
                highlightedIndex = null,
                dwellStartedAtMillis = null,
                selectionArmed = newLayout.isInsideDeadZone(input.pointer),
            )
            return snapshotOf(state)
        }

        if (current.layout.isInsideDeadZone(input.pointer)) {
            state = current.copy(
                highlightedIndex = null,
                dwellStartedAtMillis = null,
                selectionArmed = true,
            )
            return snapshotOf(state)
        }

        if (!current.selectionArmed) {
            state = current.copy(highlightedIndex = null, dwellStartedAtMillis = null)
            return snapshotOf(state)
        }

        val definition = catalog.definition(current.level)
        val sector = checkNotNull(current.layout.sectorAt(input.pointer, definition.items.size))
        if (sector != current.highlightedIndex) {
            state = current.copy(
                highlightedIndex = sector,
                dwellStartedAtMillis = input.timestampMillis,
            )
            return snapshotOf(state)
        }

        val dwellStartedAt = current.dwellStartedAtMillis ?: input.timestampMillis
        if (input.timestampMillis - dwellStartedAt < timing.sectorDwellMillis) {
            state = current.copy(dwellStartedAtMillis = dwellStartedAt)
            return snapshotOf(state)
        }

        return select(definition.items[sector], current)
    }

    private fun select(item: RadialMenuItem, current: State.Open): RadialMenuSnapshot =
        when (val id = item.id) {
            is MenuItemId.OpenLevel -> {
                val from = current.level
                state = current.copy(
                    level = id.level,
                    highlightedIndex = null,
                    dwellStartedAtMillis = null,
                    selectionArmed = false,
                )
                snapshotOf(state, RadialMenuEvent.LevelChanged(from, id.level))
            }

            is MenuItemId.RunAction -> {
                state = current.copy(
                    highlightedIndex = null,
                    dwellStartedAtMillis = null,
                    selectionArmed = false,
                )
                snapshotOf(state, RadialMenuEvent.ActionSelected(current.level, id.action))
            }

            MenuItemId.Back -> {
                val parent = catalog.definition(current.level).parent
                if (parent == null) {
                    closeFromBack()
                } else {
                    val from = current.level
                    state = current.copy(
                        level = parent,
                        highlightedIndex = null,
                        dwellStartedAtMillis = null,
                        selectionArmed = false,
                    )
                    snapshotOf(state, RadialMenuEvent.LevelChanged(from, parent))
                }
            }

            MenuItemId.Close -> closeFromBack()
        }

    private fun closeFromBack(): RadialMenuSnapshot {
        state = State.Closed(awaitingRelease = true)
        return snapshotOf(state, RadialMenuEvent.Closed(RadialMenuCloseReason.PRINCIPAL_BACK))
    }

    private fun openPrincipalAt(
        anchor: ScalarPoint,
        viewport: ScalarViewport,
    ): RadialMenuSnapshot {
        val layout = geometry.layout(anchor, viewport)
        state = State.Open(
            level = MenuLevelId.PRINCIPAL,
            layout = layout,
            highlightedIndex = null,
            dwellStartedAtMillis = null,
            selectionArmed = layout.isInsideDeadZone(anchor),
        )
        return snapshotOf(state, RadialMenuEvent.Opened(MenuLevelId.PRINCIPAL))
    }

    private fun recordTimestamp(timestampMillis: Long) {
        require(timestampMillis >= 0L) { "Timestamp must be non-negative." }
        require(lastTimestampMillis == null || timestampMillis >= checkNotNull(lastTimestampMillis)) {
            "Radial-menu timestamps must be monotonic."
        }
        lastTimestampMillis = timestampMillis
    }

    private fun snapshotOf(
        source: State,
        event: RadialMenuEvent? = null,
    ): RadialMenuSnapshot = when (source) {
        is State.Closed -> RadialMenuSnapshot(
            phase = RadialMenuPhase.CLOSED,
            awaitingActivationRelease = source.awaitingRelease,
            event = event,
        )

        is State.Arming -> {
            val elapsed = (lastTimestampMillis ?: source.startedAtMillis) - source.startedAtMillis
            RadialMenuSnapshot(
                phase = RadialMenuPhase.ARMING,
                armingProgress = (elapsed.toDouble() / timing.openingHoldMillis).coerceIn(0.0, 1.0),
                event = event,
            )
        }

        is State.Open -> {
            val definition = catalog.definition(source.level)
            val elapsed = source.dwellStartedAtMillis?.let {
                (lastTimestampMillis ?: it) - it
            } ?: 0L
            RadialMenuSnapshot(
                phase = RadialMenuPhase.OPEN,
                armingProgress = 1.0,
                level = source.level,
                definition = definition,
                layout = source.layout,
                highlightedIndex = source.highlightedIndex,
                dwellProgress = if (source.highlightedIndex == null) {
                    0.0
                } else {
                    (elapsed.toDouble() / timing.sectorDwellMillis).coerceIn(0.0, 1.0)
                },
                selectionArmed = source.selectionArmed,
                event = event,
            )
        }
    }
}
