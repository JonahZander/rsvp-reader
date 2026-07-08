package com.jonahzander.rsvpreader.core

import com.jonahzander.rsvpreader.core.model.Book
import com.jonahzander.rsvpreader.core.model.Chapter
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun storage() = BookStorage(tmp.root.resolve("books"))

    private fun book(id: String, title: String = "Title $id") =
        Book(id = id, title = title, chapters = listOf(Chapter("One", "Some text here.")))

    @Test
    fun `saved book can be read back by id`() {
        val storage = storage()
        storage.save(book("a"))
        assertEquals(book("a"), storage.get("a"))
    }

    @Test
    fun `get of unknown id is null`() {
        assertNull(storage().get("nope"))
    }

    @Test
    fun `list returns all saved books sorted by title`() {
        val storage = storage()
        storage.save(book("1", "Zebra"))
        storage.save(book("2", "Aardvark"))
        assertEquals(listOf("Aardvark", "Zebra"), storage.list().map { it.title })
    }

    @Test
    fun `saving the same id twice overwrites`() {
        val storage = storage()
        storage.save(book("a", "Old"))
        storage.save(book("a", "New"))
        assertEquals("New", storage.get("a")?.title)
        assertEquals(1, storage.list().size)
    }

    @Test
    fun `deleted book is gone`() {
        val storage = storage()
        storage.save(book("a"))
        storage.delete("a")
        assertNull(storage.get("a"))
        assertEquals(emptyList(), storage.list())
    }

    @Test
    fun `corrupt file is skipped by list`() {
        val storage = storage()
        storage.save(book("a"))
        tmp.root.resolve("books/broken.json").writeText("not json")
        assertEquals(1, storage.list().size)
    }
}
