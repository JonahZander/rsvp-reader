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
    fun `cleared position resets to start`() = runStoreTest { store ->
        store.savePosition("book-a", ReadingPosition(3, 7))
        store.clearPosition("book-a")
        assertEquals(ReadingPosition(0, 0), store.position("book-a").first())
    }
}
