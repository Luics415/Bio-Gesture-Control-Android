package com.luics415.biogesture

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaVolumeLevelTest {
    @Test
    fun `step changes one real stream level and respects its limits`() {
        assertEquals(7, MediaVolumeLevel.stepTarget(6, 0, 15, 1))
        assertEquals(5, MediaVolumeLevel.stepTarget(6, 0, 15, -1))
        assertEquals(15, MediaVolumeLevel.stepTarget(15, 0, 15, 1))
        assertEquals(0, MediaVolumeLevel.stepTarget(0, 0, 15, -1))
    }

    @Test
    fun `restore uses saved level or a bounded useful default`() {
        assertEquals(9, MediaVolumeLevel.restoreTarget(9, 0, 15))
        assertEquals(5, MediaVolumeLevel.restoreTarget(0, 0, 15))
        assertEquals(3, MediaVolumeLevel.restoreTarget(0, 2, 6))
    }

    @Test
    fun `percentage reports the effective level`() {
        assertEquals(0, MediaVolumeLevel.percentage(0, 0, 15))
        assertEquals(40, MediaVolumeLevel.percentage(6, 0, 15))
        assertEquals(100, MediaVolumeLevel.percentage(15, 0, 15))
    }
}
