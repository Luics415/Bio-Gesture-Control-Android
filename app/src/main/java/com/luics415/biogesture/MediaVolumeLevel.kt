package com.luics415.biogesture

import kotlin.math.roundToInt

/** Pure helpers for applying and reporting a real media-stream level. */
object MediaVolumeLevel {
    fun stepTarget(current: Int, minimum: Int, maximum: Int, delta: Int): Int {
        require(minimum <= maximum)
        require(delta == -1 || delta == 1)
        return (current + delta).coerceIn(minimum, maximum)
    }

    fun restoreTarget(saved: Int, minimum: Int, maximum: Int): Int {
        require(minimum <= maximum)
        val usefulDefault = minimum + ((maximum - minimum) / 3).coerceAtLeast(1)
        return (if (saved > minimum) saved else usefulDefault).coerceIn(minimum, maximum)
    }

    fun percentage(current: Int, minimum: Int, maximum: Int): Int {
        require(minimum <= maximum)
        if (maximum == minimum) return 100
        return (((current.coerceIn(minimum, maximum) - minimum).toFloat() /
            (maximum - minimum)) * 100f).roundToInt()
    }
}
