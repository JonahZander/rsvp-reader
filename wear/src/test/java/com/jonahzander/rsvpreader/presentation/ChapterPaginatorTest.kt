package com.jonahzander.rsvpreader.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterPaginatorTest {

    // Word ranges [0,5), [5,9), [9,12) — page text is irrelevant to pageFor.
    private val pages = listOf(
        Page(0, 5, "p0"),
        Page(5, 9, "p1"),
        Page(9, 12, "p2"),
    )

    @Test
    fun `pageFor finds the page containing the word`() {
        assertEquals(0, pages.pageFor(0))
        assertEquals(0, pages.pageFor(4))
        assertEquals(1, pages.pageFor(5)) // exact page boundary
        assertEquals(1, pages.pageFor(8))
        assertEquals(2, pages.pageFor(9))
        assertEquals(2, pages.pageFor(11))
    }

    @Test
    fun `pageFor clamps out-of-range indices`() {
        assertEquals(0, pages.pageFor(-3)) // before the first page
        assertEquals(2, pages.pageFor(100)) // past the last page
    }

    @Test
    fun `pageFor tolerates an empty page list and the empty-chapter page`() {
        assertEquals(0, emptyList<Page>().pageFor(7))
        val empty = listOf(Page(0, 0, ""))
        assertEquals(0, empty.pageFor(0))
        assertEquals(0, empty.pageFor(3))
    }
}
