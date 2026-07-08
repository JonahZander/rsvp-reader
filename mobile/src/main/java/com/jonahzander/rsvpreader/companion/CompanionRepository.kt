package com.jonahzander.rsvpreader.companion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jonahzander.rsvpreader.companion.sync.BookSender
import com.jonahzander.rsvpreader.core.BookStorage
import com.jonahzander.rsvpreader.core.model.Book
import com.jonahzander.rsvpreader.core.model.Chapter
import com.jonahzander.rsvpreader.core.sync.ReaderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * The phone's library of sent material plus default reader settings.
 * Every mutation is mirrored into the Wearable Data Layer.
 */
class CompanionRepository(
    context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    private val storage = BookStorage(File(context.filesDir, "books"))
    private val sender = BookSender(context)

    private val wpmKey = intPreferencesKey("wpm")
    private val fontKey = stringPreferencesKey("font")

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books

    val settings: Flow<ReaderSettings> = dataStore.data.map { prefs ->
        ReaderSettings(
            wpm = prefs[wpmKey] ?: ReaderSettings().wpm,
            font = prefs[fontKey] ?: ReaderSettings().font,
        )
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _books.value = storage.list()
    }

    suspend fun addBook(book: Book) = withContext(Dispatchers.IO) {
        storage.save(book)
        refresh()
        sender.sendBook(book)
    }

    suspend fun addText(title: String, text: String) {
        addBook(
            Book(
                id = sha1(text).take(16),
                title = title,
                chapters = listOf(Chapter(title, text)),
            )
        )
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        storage.delete(id)
        refresh()
        sender.deleteBook(id)
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

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
}
