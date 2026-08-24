package com.luics415.biogesture.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadialMenuControllerTest {
    private val viewport = ScalarViewport(1080.0, 2400.0)
    private val center = ScalarPoint(540.0, 1200.0)

    @Test
    fun `external activation opens principal immediately with a typed event`() {
        val controller = RadialMenuController()

        val opened = controller.openPrincipal(
            timestampMillis = 400L,
            anchor = center,
            viewport = viewport,
        )

        assertEquals(RadialMenuPhase.OPEN, opened.phase)
        assertEquals(MenuLevelId.PRINCIPAL, opened.level)
        assertEquals(RadialMenuEvent.Opened(MenuLevelId.PRINCIPAL), opened.event)
        assertEquals(center, opened.layout!!.center)
        assertTrue(opened.selectionArmed)
        assertEquals(1.0, opened.armingProgress, 0.0)
    }

    @Test
    fun `external opening can select play after the normal sector dwell`() {
        val controller = RadialMenuController()
        val principal = controller.openPrincipal(0L, center, viewport)
        val definition = principal.definition!!
        val playIndex = definition.items.indexOfFirst {
            it.id == MenuItemId.OpenLevel(MenuLevelId.PLAY)
        }
        val playDirection = principal.layout!!.pointForSector(playIndex, definition.items.size)

        sample(controller, 1L, active = true, pointer = playDirection)
        val selected = sample(controller, 751L, active = true, pointer = playDirection)

        assertEquals(
            RadialMenuEvent.LevelChanged(MenuLevelId.PRINCIPAL, MenuLevelId.PLAY),
            selected.event,
        )
        assertEquals(MenuLevelId.PLAY, selected.level)
        assertFalse(selected.selectionArmed)
    }

    @Test
    fun `external selection requires recenter before a second selection`() {
        val controller = RadialMenuController()
        val principal = controller.openPrincipal(0L, center, viewport)
        val principalDefinition = principal.definition!!
        val playIndex = principalDefinition.items.indexOfFirst {
            it.id == MenuItemId.OpenLevel(MenuLevelId.PLAY)
        }
        val playDirection = principal.layout!!.pointForSector(
            playIndex,
            principalDefinition.items.size,
        )
        sample(controller, 1L, active = true, pointer = playDirection)
        val playMenu = sample(controller, 751L, active = true, pointer = playDirection)

        val playDefinition = playMenu.definition!!
        val actionIndex = playDefinition.items.indexOfFirst {
            it.id == MenuItemId.RunAction(MenuActionId.MEDIA_PLAY)
        }
        val actionDirection = playMenu.layout!!.pointForSector(actionIndex, playDefinition.items.size)
        sample(controller, 752L, active = true, pointer = actionDirection)
        val blocked = sample(controller, 1_502L, active = true, pointer = actionDirection)
        assertNull(blocked.event)
        assertNull(blocked.highlightedIndex)
        assertFalse(blocked.selectionArmed)

        val recentered = sample(controller, 1_503L, active = true, pointer = playMenu.layout.center)
        assertTrue(recentered.selectionArmed)
        sample(controller, 1_504L, active = true, pointer = actionDirection)
        val action = sample(controller, 2_254L, active = true, pointer = actionDirection)

        assertEquals(
            RadialMenuEvent.ActionSelected(MenuLevelId.PLAY, MenuActionId.MEDIA_PLAY),
            action.event,
        )
        assertFalse(action.selectionArmed)
    }

    @Test
    fun `opening requires 800 milliseconds of uninterrupted activation`() {
        val controller = RadialMenuController()

        assertEquals(RadialMenuPhase.ARMING, sample(controller, 0L, active = true).phase)
        assertEquals(RadialMenuPhase.ARMING, sample(controller, 799L, active = true).phase)
        assertEquals(RadialMenuPhase.CLOSED, sample(controller, 799L, active = false).phase)

        assertEquals(RadialMenuPhase.ARMING, sample(controller, 1_000L, active = true).phase)
        val opened = sample(controller, 1_800L, active = true)
        assertEquals(RadialMenuPhase.OPEN, opened.phase)
        assertEquals(RadialMenuEvent.Opened(MenuLevelId.PRINCIPAL), opened.event)
    }

    @Test
    fun `play opens its submenu and latch requires recenter before another selection`() {
        val controller = RadialMenuController()
        val principal = open(controller)
        val playIndex = principal.definition!!.items.indexOfFirst {
            it.id == MenuItemId.OpenLevel(MenuLevelId.PLAY)
        }
        val playDirection = principal.layout!!.pointForSector(playIndex, principal.definition.items.size)

        sample(controller, 801L, active = true, pointer = playDirection)
        val changed = sample(controller, 1_551L, active = true, pointer = playDirection)
        assertEquals(RadialMenuEvent.LevelChanged(MenuLevelId.PRINCIPAL, MenuLevelId.PLAY), changed.event)
        assertEquals(MenuLevelId.PLAY, changed.level)
        assertFalse(changed.selectionArmed)

        val heldWithoutRecentering = sample(controller, 2_500L, active = true, pointer = playDirection)
        assertNull(heldWithoutRecentering.event)
        assertNull(heldWithoutRecentering.highlightedIndex)

        val recentered = sample(controller, 2_501L, active = true, pointer = changed.layout!!.center)
        assertTrue(recentered.selectionArmed)

        val playActionIndex = recentered.definition!!.items.indexOfFirst {
            it.id == MenuItemId.RunAction(MenuActionId.MEDIA_PLAY)
        }
        val actionDirection = recentered.layout!!.pointForSector(
            playActionIndex,
            recentered.definition.items.size,
        )
        sample(controller, 2_502L, active = true, pointer = actionDirection)
        val action = sample(controller, 3_252L, active = true, pointer = actionDirection)
        assertEquals(
            RadialMenuEvent.ActionSelected(MenuLevelId.PLAY, MenuActionId.MEDIA_PLAY),
            action.event,
        )
        assertFalse(action.selectionArmed)
    }

    @Test
    fun `principal back closes and cannot reopen until pose is released`() {
        val controller = RadialMenuController()
        val principal = open(controller)
        val backIndex = principal.definition!!.items.indexOfFirst { it.id == MenuItemId.Close }
        val backDirection = principal.layout!!.pointForSector(backIndex, principal.definition.items.size)

        sample(controller, 801L, active = true, pointer = backDirection)
        val closed = sample(controller, 1_551L, active = true, pointer = backDirection)

        assertEquals(RadialMenuPhase.CLOSED, closed.phase)
        assertEquals(RadialMenuEvent.Closed(RadialMenuCloseReason.PRINCIPAL_BACK), closed.event)
        assertTrue(closed.awaitingActivationRelease)
        assertEquals(RadialMenuPhase.CLOSED, sample(controller, 3_000L, active = true).phase)

        val released = sample(controller, 3_001L, active = false)
        assertFalse(released.awaitingActivationRelease)
        assertEquals(RadialMenuPhase.ARMING, sample(controller, 3_002L, active = true).phase)
        assertEquals(RadialMenuPhase.OPEN, sample(controller, 3_802L, active = true).phase)
    }

    @Test
    fun `closing clears a partial dwell`() {
        val controller = RadialMenuController()
        val principal = open(controller)
        val direction = principal.layout!!.pointForSector(0, principal.definition!!.items.size)

        sample(controller, 801L, active = true, pointer = direction)
        val partial = sample(controller, 1_400L, active = true, pointer = direction)
        assertTrue(partial.dwellProgress in 0.0..0.9999)

        val closed = sample(controller, 1_401L, active = false)
        assertEquals(RadialMenuPhase.CLOSED, closed.phase)
        assertNull(closed.highlightedIndex)
        assertEquals(0.0, closed.dwellProgress, 0.0)

        sample(controller, 2_000L, active = true)
        val reopened = sample(controller, 2_800L, active = true)
        assertEquals(RadialMenuPhase.OPEN, reopened.phase)
        assertNull(reopened.highlightedIndex)
        assertEquals(0.0, reopened.dwellProgress, 0.0)
    }

    @Test
    fun `orientation change recomputes safe geometry and clears dwell`() {
        val controller = RadialMenuController()
        val principal = open(controller)
        val direction = principal.layout!!.pointForSector(0, principal.definition!!.items.size)
        sample(controller, 801L, active = true, pointer = direction)

        val landscape = ScalarViewport(
            2400.0,
            1080.0,
            ScalarInsets(left = 80.0, right = 100.0, bottom = 40.0),
        )
        val rotated = controller.update(
            RadialMenuInput(
                timestampMillis = 900L,
                activationPoseDetected = true,
                pointer = ScalarPoint(1200.0, 540.0),
                viewport = landscape,
            ),
        )

        assertEquals(RadialMenuPhase.OPEN, rotated.phase)
        assertEquals(landscape, rotated.layout!!.viewport)
        assertNull(rotated.highlightedIndex)
        assertEquals(0.0, rotated.dwellProgress, 0.0)
        assertTrue(rotated.selectionArmed)
    }

    private fun open(controller: RadialMenuController): RadialMenuSnapshot {
        sample(controller, 0L, active = true)
        return sample(controller, 800L, active = true)
    }

    private fun sample(
        controller: RadialMenuController,
        timestamp: Long,
        active: Boolean,
        pointer: ScalarPoint = center,
    ): RadialMenuSnapshot = controller.update(
        RadialMenuInput(
            timestampMillis = timestamp,
            activationPoseDetected = active,
            pointer = pointer,
            viewport = viewport,
        ),
    )
}
