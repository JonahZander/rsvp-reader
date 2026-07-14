package com.jonahzander.rsvpreader.tile

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.jonahzander.rsvpreader.core.model.Book
import com.jonahzander.rsvpreader.core.model.Chapter
import com.jonahzander.rsvpreader.data.ContinueSnapshot
import com.jonahzander.rsvpreader.data.ProgressStore
import com.jonahzander.rsvpreader.data.ReadingPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ContinueReadingTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val book = Book(
        id = "b",
        title = "Test Book",
        chapters = listOf(
            Chapter("First", "one two three."),
            Chapter("Second", "four five"),
        ),
    )

    private fun runStoreTest(block: suspend (ProgressStore) -> Unit) = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            tmp.newFolder().resolve("test.preferences_pb")
        }
        try {
            block(ProgressStore(dataStore))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `null when nothing has been read yet`() = runStoreTest { store ->
        assertNull(loadContinueReadingInfo(store) { book })
    }

    @Test
    fun `resolves word title and progress`() = runStoreTest { store ->
        store.savePosition("b", ReadingPosition(chapterIndex = 1, wordIndex = 1))
        val info = loadContinueReadingInfo(store) { if (it == "b") book else null }
        assertEquals(
            ContinueReadingInfo(
                bookId = "b",
                bookTitle = "Test Book",
                lastWord = "five",
                chapterNumber = 2,
                chapterCount = 2,
            ),
            info,
        )
    }

    @Test
    fun `matching snapshot is used without loading the book`() = runStoreTest { store ->
        store.savePosition(
            "b",
            ReadingPosition(1, 1),
            ContinueSnapshot("b", "Test Book", "five", 2, 2),
        )
        val info = loadContinueReadingInfo(store) { error("must not load the book") }
        assertEquals(
            ContinueReadingInfo(
                bookId = "b",
                bookTitle = "Test Book",
                lastWord = "five",
                chapterNumber = 2,
                chapterCount = 2,
            ),
            info,
        )
    }

    @Test
    fun `snapshot for a different book falls back to loading`() = runStoreTest { store ->
        store.savePosition("b", ReadingPosition(0, 0), ContinueSnapshot("other", "Other", "x", 1, 1))
        val info = loadContinueReadingInfo(store) { book }
        assertEquals("one", info?.lastWord)
    }

    @Test
    fun `null when the book no longer exists`() = runStoreTest { store ->
        store.savePosition("gone", ReadingPosition(0, 0))
        assertNull(loadContinueReadingInfo(store) { null })
    }

    @Test
    fun `out-of-range position clamps into the book`() = runStoreTest { store ->
        store.savePosition("b", ReadingPosition(chapterIndex = 9, wordIndex = 99))
        val info = loadContinueReadingInfo(store) { book }
        assertEquals("five", info?.lastWord)
        assertEquals(2, info?.chapterNumber)
    }
}
