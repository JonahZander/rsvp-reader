package com.jonahzander.rsvpreader.core

import com.jonahzander.rsvpreader.core.model.Book
import com.jonahzander.rsvpreader.core.sync.DataLayerContract
import java.io.File

/**
 * Persists books as one JSON file per book inside [dir].
 * Used on the watch for synced books and on the phone for the sent library.
 */
class BookStorage(private val dir: File) {

    fun save(book: Book) {
        dir.mkdirs()
        fileFor(book.id).writeBytes(DataLayerContract.encodeBook(book))
    }

    fun list(): List<Book> =
        dir.listFiles { f -> f.extension == "json" }
            .orEmpty()
            .mapNotNull { runCatching { DataLayerContract.decodeBook(it.readBytes()) }.getOrNull() }
            .sortedBy { it.title.lowercase() }

    fun get(id: String): Book? =
        fileFor(id).takeIf { it.exists() }
            ?.let { runCatching { DataLayerContract.decodeBook(it.readBytes()) }.getOrNull() }

    fun delete(id: String) {
        fileFor(id).delete()
    }

    private fun fileFor(id: String) = dir.resolve("$id.json")
}
