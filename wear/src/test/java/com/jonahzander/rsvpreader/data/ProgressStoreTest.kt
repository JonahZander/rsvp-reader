package com.jonahzander.rsvpreader.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.jonahzander.rsvpreader.core.sync.ReaderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgressStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

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
    fun `defaults are 300 wpm and atkinson font`() = runStoreTest { store ->
        assertEquals(ReaderSettings(wpm = 300, font = "atkinson"), store.settings.first())
    }

    @Test
    fun `wpm and font changes persist`() = runStoreTest { store ->
        store.setWpm(450)
        store.setFont("opendyslexic")
        assertEquals(ReaderSettings(wpm = 450, font = "opendyslexic"), store.settings.first())
    }

    @Test
    fun `applySettings overwrites both fields`() = runStoreTest { store ->
        store.setWpm(450)
        store.applySettings(ReaderSettings(wpm = 350, font = "system"))
        assertEquals(ReaderSettings(wpm = 350, font = "system"), store.settings.first())
    }

    @Test
    fun `unknown book starts at chapter zero word zero`() = runStoreTest { store ->
        assertEquals(ReadingPosition(0, 0), store.position("new-book").first())
    }

    @Test
    fun `saved position is restored per book`() = runStoreTest { store ->
        store.savePosition("book-a", ReadingPosition(chapterIndex = 2, wordIndex = 41))
        store.savePosition("book-b", ReadingPosition(chapterIndex = 1, wordIndex = 5))
        assertEquals(ReadingPosition(2, 41), store.position("book-a").first())
        assertEquals(ReadingPosition(1, 5), store.position("book-b").first())
    }

    @Test
    fun `no continue snapshot by default`() = runStoreTest { store ->
        assertNull(store.continueSnapshot.first())
    }

    @Test
    fun `savePosition with snapshot stores it for the tile`() = runStoreTest { store ->
        val snapshot = ContinueSnapshot(
            bookId = "book-a",
            bookTitle = "A Book",
            word = "spiral",
            chapterNumber = 2,
            chapterCount = 5,
        )
        store.savePosition("book-a", ReadingPosition(1, 7), snapshot)
        assertEquals(snapshot, store.continueSnapshot.first())
    }

    @Test
    fun `savePosition without snapshot leaves the previous one untouched`() = runStoreTest { store ->
        val snapshot = ContinueSnapshot("book-a", "A Book", "spiral", 2, 5)
        store.savePosition("book-a", ReadingPosition(1, 7), snapshot)
        store.savePosition("book-a", ReadingPosition(1, 8))
        assertEquals(snapshot, store.continueSnapshot.first())
    }

    @Test
    fun `no last book by default`() = runStoreTest { store ->
        assertNull(store.lastBookId.first())
    }

    @Test
    fun `savePosition records the last book read`() = runStoreTest { store ->
        store.savePosition("book-a", ReadingPosition(1, 2))
        store.savePosition("book-b", ReadingPosition(0, 0))
        assertEquals("book-b", store.lastBookId.first())
    }

    @Test
    fun `cleared position resets to start`() = runStoreTest { store ->
        store.savePosition("book-a", ReadingPosition(3, 7))
        store.clearPosition("book-a")
        assertEquals(ReadingPosition(0, 0), store.position("book-a").first())
    }
}
