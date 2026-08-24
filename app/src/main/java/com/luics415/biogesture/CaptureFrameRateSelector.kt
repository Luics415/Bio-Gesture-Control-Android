package com.luics415.biogesture

data class CaptureFpsRange(
    val lower: Int,
    val upper: Int,
) {
    init {
        require(lower > 0)
        require(upper >= lower)
    }
}

/** Selects a supported range whose maximum does not exceed the processing budget when possible. */
object CaptureFrameRateSelector {
    fun select(targetFps: Int, supported: Collection<CaptureFpsRange>): CaptureFpsRange? {
        require(targetFps > 0)
        if (supported.isEmpty()) return null
        val atOrBelow = supported.filter { it.upper <= targetFps }
        return atOrBelow.maxWithOrNull(
            compareBy<CaptureFpsRange> { it.upper }.thenBy { it.lower },
        ) ?: supported.minWithOrNull(
            compareBy<CaptureFpsRange> { it.upper }.thenBy { it.lower },
        )
    }
}
