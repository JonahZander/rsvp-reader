package com.jonahzander.rsvpreader.core

/**
 * Turns a stream of high-resolution rotary pixel deltas into discrete steps.
 * One step per [thresholdPx] accumulated in the same direction; accumulation
 * resets on direction reversal and after [resetAfterMs] of inactivity.
 */
class RotaryAccumulator(
    private val thresholdPx: Float,
    private val resetAfterMs: Long = 300,
) {
    private var accumulated = 0f
    private var lastEventMs = Long.MIN_VALUE

    /** Returns the number of steps (signed) released by this scroll event. */
    fun onScroll(deltaPx: Float, timeMs: Long): Int {
        if (lastEventMs != Long.MIN_VALUE && timeMs - lastEventMs > resetAfterMs) {
            accumulated = 0f
        }
        lastEventMs = timeMs
        if (deltaPx != 0f && accumulated != 0f && deltaPx.sign() != accumulated.sign()) {
            accumulated = 0f
        }
        accumulated += deltaPx
        val steps = (accumulated / thresholdPx).toInt()
        accumulated -= steps * thresholdPx
        return steps
    }

    private fun Float.sign(): Int = if (this < 0f) -1 else 1
}
