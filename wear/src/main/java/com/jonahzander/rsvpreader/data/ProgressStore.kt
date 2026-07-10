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

/** Ready-made tile content, written alongside every position save so the tile never loads a book. */
data class ContinueSnapshot(
    val bookId: String,
    val bookTitle: String,
    val word: String,
    val chapterNumber: Int,
    val chapterCount: Int,
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
    private val wordSizeKey = intPreferencesKey("word_size")
    private val readingModeKey = stringPreferencesKey("reading_mode")
    private val snapBookKey = stringPreferencesKey("snap_book")
    private val snapTitleKey = stringPreferencesKey("snap_title")
    private val snapWordKey = stringPreferencesKey("snap_word")
    private val snapChapterNumKey = intPreferencesKey("snap_chapter_num")
    private val snapChapterCountKey = intPreferencesKey("snap_chapter_count")

    private val defaults = ReaderSettings()

    val settings: Flow<ReaderSettings> = dataStore.data.map { prefs ->
        ReaderSettings(
            wpm = prefs[wpmKey] ?: defaults.wpm,
            font = prefs[fontKey] ?: defaults.font,
        )
    }

    /** Reading word size in sp; watch-local (not synced from the phone). */
    val wordSize: Flow<Int> = dataStore.data.map { it[wordSizeKey] ?: DEFAULT_WORD_SIZE }

    suspend fun setWordSize(sp: Int) {
        dataStore.edit { it[wordSizeKey] = sp }
    }

    /** Selected reading style: rsvp | line | paged | scroll. Watch-local. */
    val readingMode: Flow<String> = dataStore.data.map { it[readingModeKey] ?: "rsvp" }

    suspend fun setReadingMode(mode: String) {
        dataStore.edit { it[readingModeKey] = mode }
    }

    /** Id of the most recently read book (last savePosition call), or null. */
    val lastBookId: Flow<String?> = dataStore.data.map { it[lastBookKey] }

    /** Tile content captured at the last position save, or null if never written. */
    val continueSnapshot: Flow<ContinueSnapshot?> = dataStore.data.map { prefs ->
        val bookId = prefs[snapBookKey] ?: return@map null
        ContinueSnapshot(
            bookId = bookId,
            bookTitle = prefs[snapTitleKey] ?: return@map null,
            word = prefs[snapWordKey] ?: return@map null,
            chapterNumber = prefs[snapChapterNumKey] ?: return@map null,
            chapterCount = prefs[snapChapterCountKey] ?: return@map null,
        )
    }

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

    suspend fun savePosition(bookId: String, position: ReadingPosition, snapshot: ContinueSnapshot? = null) {
        dataStore.edit {
            it[chapterKey(bookId)] = position.chapterIndex
            it[wordKey(bookId)] = position.wordIndex
            it[lastBookKey] = bookId
            if (snapshot != null) {
                it[snapBookKey] = snapshot.bookId
                it[snapTitleKey] = snapshot.bookTitle
                it[snapWordKey] = snapshot.word
                it[snapChapterNumKey] = snapshot.chapterNumber
                it[snapChapterCountKey] = snapshot.chapterCount
            }
        }
    }

    suspend fun clearPosition(bookId: String) {
        dataStore.edit {
            it.remove(chapterKey(bookId))
            it.remove(wordKey(bookId))
        }
    }

    companion object {
        const val DEFAULT_WORD_SIZE = 26
    }
}
