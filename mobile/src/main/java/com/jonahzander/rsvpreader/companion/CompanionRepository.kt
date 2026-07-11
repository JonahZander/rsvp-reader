package com.jonahzander.rsvpreader.companion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.android.gms.wearable.Wearable
import com.jonahzander.rsvpreader.companion.epub.EpubParser
import com.jonahzander.rsvpreader.companion.sync.BookSender
import com.jonahzander.rsvpreader.companion.util.contentId
import com.jonahzander.rsvpreader.core.BookStorage
import com.jonahzander.rsvpreader.core.model.Book
import com.jonahzander.rsvpreader.core.model.Chapter
import com.jonahzander.rsvpreader.core.sync.DataLayerContract
import com.jonahzander.rsvpreader.core.sync.ReaderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * The phone's library of sent material plus default reader settings.
 * Every mutation is mirrored into the Wearable Data Layer.
 */
class CompanionRepository(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    private val storage = BookStorage(File(context.filesDir, "books"))
    private val sender = BookSender(context)

    private val wpmKey = intPreferencesKey("wpm")
    private val fontKey = stringPreferencesKey("font")
    private val ackedKey = stringSetPreferencesKey("ackedBooks")

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books

    val settings: Flow<ReaderSettings> = dataStore.data.map { prefs ->
        ReaderSettings(
            wpm = prefs[wpmKey] ?: ReaderSettings().wpm,
            font = prefs[fontKey] ?: ReaderSettings().font,
        )
    }

    val ackedIds: Flow<Set<String>> = dataStore.data.map { it[ackedKey] ?: emptySet() }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _books.value = storage.list()
    }

    suspend fun markAcked(ids: Collection<String>) {
        if (ids.isEmpty()) return
        dataStore.edit { it[ackedKey] = (it[ackedKey] ?: emptySet()) + ids }
    }

    private suspend fun clearAcked(id: String) {
        dataStore.edit { it[ackedKey] = (it[ackedKey] ?: emptySet()) - id }
    }

    /** Sweep existing ack DataItems — covers acks that arrived while the app was dead. */
    suspend fun sweepAcks() {
        runCatching {
            val items = Wearable.getDataClient(context).dataItems.await()
            val ids = try {
                items.mapNotNull { item -> item.uri.path?.let { DataLayerContract.ackIdFromPath(it) } }
            } finally {
                items.release()
            }
            markAcked(ids)
        }
    }

    /**
     * Re-send every book the watch has not acked — a send can fail (Play
     * Services down) or be cancelled mid-flight, and nothing else retries it.
     * Re-sending an already-delivered book is harmless: the watch re-imports
     * and re-acks.
     */
    suspend fun resendUnacked() {
        val acked = ackedIds.first()
        for (book in _books.value) {
            if (book.id !in acked) runCatching { sender.sendBook(book) }
        }
    }

    /** Parse an EPUB stream and add it to the library; parse errors come back as failures. */
    suspend fun importEpub(open: () -> InputStream?): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            val book = (open() ?: error("Could not open file")).use { EpubParser.parse(it) }
            addBook(book)
            book
        }
    }

    // add/remove run NonCancellable: once the local save starts, the mirrored
    // Data Layer write must run too, or a cancelled caller (share sheet
    // dismissed mid-import) leaves a book saved locally but never sent.
    suspend fun addBook(book: Book) = withContext(Dispatchers.IO + NonCancellable) {
        storage.save(book)
        refresh()
        // Sync is best-effort: the local library must work even when
        // Play Services or the watch is unavailable.
        runCatching { sender.sendBook(book) }
    }

    suspend fun addText(title: String, text: String) {
        addBook(
            Book(
                id = contentId(text.encodeToByteArray()),
                title = title,
                chapters = listOf(Chapter(title, text)),
            )
        )
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO + NonCancellable) {
        storage.delete(id)
        clearAcked(id)
        refresh()
        runCatching { sender.deleteBook(id) }
    }

    suspend fun setWpm(wpm: Int) {
        dataStore.edit { it[wpmKey] = wpm }
        pushSettings()
    }

    suspend fun setFont(font: String) {
        dataStore.edit { it[fontKey] = font }
        pushSettings()
    }

    suspend fun isWatchConnected(): Boolean = sender.isWatchConnected()

    private suspend fun pushSettings() {
        runCatching { sender.sendSettings(settings.first()) }
    }
}
