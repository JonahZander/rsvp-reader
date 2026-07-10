package com.jonahzander.rsvpreader.core

/**
 * Accelerating hold-and-drag scrubbing: horizontal displacement from the
 * gesture origin sets a velocity (quadratic beyond a dead zone); each frame
 * integrates velocity over elapsed time and emits whole-word deltas.
 */
class ScrubMapper(
    private val deadZonePx: Float,
    private val gain: Float,
) {
    private var carried = 0.0

    fun onFrame(displacementPx: Float, dtMs: Long): Int {
        val magnitude = kotlin.math.abs(displacementPx)
        if (magnitude <= deadZonePx) return 0
        val speedWordsPerSec = gain * (magnitude - deadZonePx) * (magnitude - deadZonePx)
        val signed = if (displacementPx < 0) -speedWordsPerSec else speedWordsPerSec
        carried += signed * (dtMs / 1000.0)
        val whole = carried.toInt()
        carried -= whole
        return whole
    }

    fun reset() {
        carried = 0.0
    }
}
