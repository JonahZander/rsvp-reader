package com.jonahzander.rsvpreader.core

import com.jonahzander.rsvpreader.core.model.Book
import com.jonahzander.rsvpreader.core.model.Chapter
import com.jonahzander.rsvpreader.core.sync.DataLayerContract
import com.jonahzander.rsvpreader.core.sync.ReaderSettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataLayerContractTest {

    private val book = Book(
        id = "b1",
        title = "Fables",
        author = "Aesop",
        chapters = listOf(
            Chapter("The Fox", "The quick brown fox jumps."),
            Chapter("The Crow", "A crow sat on a branch."),
        ),
    )

    @Test
    fun `book path round-trips through id extraction`() {
        assertEquals("/books/b1", DataLayerContract.bookPath("b1"))
        assertEquals("b1", DataLayerContract.bookIdFromPath("/books/b1"))
    }

    @Test
    fun `id extraction rejects foreign and empty paths`() {
        assertNull(DataLayerContract.bookIdFromPath("/settings"))
        assertNull(DataLayerContract.bookIdFromPath("/books/"))
    }

    @Test
    fun `book encodes and decodes losslessly`() {
        assertEquals(book, DataLayerContract.decodeBook(DataLayerContract.encodeBook(book)))
    }

    @Test
    fun `settings encode and decode losslessly`() {
        val settings = ReaderSettings(wpm = 450, font = "opendyslexic")
        assertEquals(settings, DataLayerContract.decodeSettings(DataLayerContract.encodeSettings(settings)))
    }

    @Test
    fun `settings decoding tolerates unknown fields for forward compatibility`() {
        val decoded = DataLayerContract.decodeSettings("""{"wpm":350,"font":"atkinson","future":true}""".encodeToByteArray())
        assertEquals(ReaderSettings(wpm = 350, font = "atkinson"), decoded)
    }

    @Test
    fun `total words counts across chapters`() {
        assertEquals(11, book.totalWords)
    }
}
