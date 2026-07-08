package com.jonahzander.rsvpreader.data

import android.content.Context
import com.jonahzander.rsvpreader.core.BookStorage
import com.jonahzander.rsvpreader.core.model.Book
import com.jonahzander.rsvpreader.core.sync.DataLayerContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The watch's library: bundled sample books from assets plus books synced
 * from the phone (persisted via [BookStorage]).
 */
class BookRepository(private val context: Context) {

    private val storage = BookStorage(File(context.filesDir, "books"))

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _books.value = (bundledBooks() + storage.list()).sortedBy { it.title.lowercase() }
    }

    suspend fun get(id: String): Book? = withContext(Dispatchers.IO) {
        _books.value.firstOrNull { it.id == id }
            ?: bundledBooks().firstOrNull { it.id == id }
            ?: storage.get(id)
    }

    suspend fun saveSynced(book: Book) = withContext(Dispatchers.IO) {
        storage.save(book)
        refresh()
    }

    suspend fun deleteSynced(id: String) = withContext(Dispatchers.IO) {
        storage.delete(id)
        refresh()
    }

    private fun bundledBooks(): List<Book> =
        context.assets.list(SAMPLES_DIR).orEmpty().mapNotNull { name ->
            runCatching {
                context.assets.open("$SAMPLES_DIR/$name").use {
                    DataLayerContract.decodeBook(it.readBytes())
                }
            }.getOrNull()
        }

    private companion object {
        const val SAMPLES_DIR = "samples"
    }
}
