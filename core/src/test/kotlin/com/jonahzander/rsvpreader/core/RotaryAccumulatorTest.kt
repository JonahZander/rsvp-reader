package com.jonahzander.rsvpreader.core

import org.junit.Test
import kotlin.test.assertEquals

class RotaryAccumulatorTest {

    // threshold 100px for easy math; timestamps are injected (no wall clock)
    private fun acc() = RotaryAccumulator(thresholdPx = 100f, resetAfterMs = 300)

    @Test
    fun `small deltas below threshold emit no steps`() {
        val a = acc()
        assertEquals(0, a.onScroll(30f, timeMs = 0))
        assertEquals(0, a.onScroll(30f, timeMs = 50))
    }

    @Test
    fun `crossing the threshold emits one step and keeps the remainder`() {
        val a = acc()
        assertEquals(0, a.onScroll(80f, timeMs = 0))
        assertEquals(1, a.onScroll(40f, timeMs = 50)) // 120 -> 1 step, 20 remainder
        assertEquals(1, a.onScroll(80f, timeMs = 100)) // 100 -> 1 step
    }

    @Test
    fun `large single delta emits multiple steps`() {
        assertEquals(3, acc().onScroll(320f, timeMs = 0))
    }

    @Test
    fun `negative deltas emit negative steps`() {
        val a = acc()
        assertEquals(-1, a.onScroll(-150f, timeMs = 0))
    }

    @Test
    fun `direction reversal clears accumulated progress`() {
        val a = acc()
        assertEquals(0, a.onScroll(90f, timeMs = 0))
        assertEquals(0, a.onScroll(-90f, timeMs = 50))  // reset, then -90
        assertEquals(-1, a.onScroll(-20f, timeMs = 100)) // -110 -> one back step
    }

    @Test
    fun `inactivity resets the accumulator`() {
        val a = acc()
        assertEquals(0, a.onScroll(90f, timeMs = 0))
        assertEquals(0, a.onScroll(90f, timeMs = 400)) // >300ms gap: starts fresh at 90
        assertEquals(1, a.onScroll(20f, timeMs = 450))
    }
}
