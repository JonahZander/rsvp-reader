package com.jonahzander.rsvpreader.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrubMapperTest {

    // deadZone 12px, gain tuned so displacement 100px ≈ 15.5 words/s
    private fun mapper() = ScrubMapper(deadZonePx = 12f, gain = 0.002f)

    @Test
    fun `displacement inside dead zone moves nothing`() {
        val m = mapper()
        assertEquals(0, m.onFrame(displacementPx = 8f, dtMs = 500))
    }

    @Test
    fun `small displacement accumulates into single word steps`() {
        val m = mapper()
        // v = 0.002 * (30-12)^2 = 0.648 words/s -> ~1 word after ~1.6s
        var total = 0
        repeat(4) { total += m.onFrame(30f, dtMs = 400) }
        assertEquals(1, total)
    }

    @Test
    fun `large displacement moves many words per second`() {
        val m = mapper()
        // v = 0.002 * (150-12)^2 ≈ 38 words/s -> ≈19 words in 500ms
        val words = m.onFrame(150f, dtMs = 500)
        assertTrue(words in 17..21, "got $words")
    }

    @Test
    fun `negative displacement moves backwards`() {
        val m = mapper()
        assertTrue(m.onFrame(-150f, dtMs = 500) < 0)
    }

    @Test
    fun `fractional progress carries across frames`() {
        val m = mapper()
        val a = m.onFrame(40f, dtMs = 300)
        val b = m.onFrame(40f, dtMs = 300)
        val c = m.onFrame(40f, dtMs = 300)
        // v = 0.002*28^2 = 1.568 w/s; 0.9s total ≈ 1.41 words -> exactly 1 emitted so far
        assertEquals(1, a + b + c)
    }

    @Test
    fun `reset clears carried progress`() {
        val m = mapper()
        m.onFrame(40f, dtMs = 300)
        m.reset()
        assertEquals(0, m.onFrame(8f, dtMs = 1000))
    }
}
