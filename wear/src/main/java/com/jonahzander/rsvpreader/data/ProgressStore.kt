package com.jonahzander.rsvpreader.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jonahzander.rsvpreader.core.sync.ReaderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ReadingPosition(
    val chapterIndex: Int = 0,
    val wordIndex: Int = 0,
)

/**
 * Reader settings (WPM, font) and per-book reading positions,
 * backed by Preferences DataStore.
 */
class ProgressStore(private val dataStore: DataStore<Preferences>) {

    private val wpmKey = intPreferencesKey("wpm")
    private val fontKey = stringPreferencesKey("font")
    private fun chapterKey(bookId: String) = intPreferencesKey("pos_${bookId}_chapter")
    private fun wordKey(bookId: String) = intPreferencesKey("pos_${bookId}_word")
    private val lastBookKey = stringPreferencesKey("last_book")

    private val defaults = ReaderSettings()

    val settings: Flow<ReaderSettings> = dataStore.data.map { prefs ->
        ReaderSettings(
            wpm = prefs[wpmKey] ?: defaults.wpm,
            font = prefs[fontKey] ?: defaults.font,
        )
    }

    /** Id of the most recently read book (last savePosition call), or null. */
    val lastBookId: Flow<String?> = dataStore.data.map { it[lastBookKey] }

    suspend fun setWpm(wpm: Int) {
        dataStore.edit { it[wpmKey] = wpm }
    }

    suspend fun setFont(font: String) {
        dataStore.edit { it[fontKey] = font }
    }

    suspend fun applySettings(settings: ReaderSettings) {
        dataStore.edit {
            it[wpmKey] = settings.wpm
            it[fontKey] = settings.font
        }
    }

    fun position(bookId: String): Flow<ReadingPosition> = dataStore.data.map { prefs ->
        ReadingPosition(
            chapterIndex = prefs[chapterKey(bookId)] ?: 0,
            wordIndex = prefs[wordKey(bookId)] ?: 0,
        )
    }

    suspend fun savePosition(bookId: String, position: ReadingPosition) {
        dataStore.edit {
            it[chapterKey(bookId)] = position.chapterIndex
            it[wordKey(bookId)] = position.wordIndex
            it[lastBookKey] = bookId
        }
    }

    suspend fun clearPosition(bookId: String) {
        dataStore.edit {
            it.remove(chapterKey(bookId))
            it.remove(wordKey(bookId))
        }
    }
}
