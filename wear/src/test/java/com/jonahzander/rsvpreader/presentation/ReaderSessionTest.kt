package com.jonahzander.rsvpreader.presentation

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.jonahzander.rsvpreader.core.model.Book
import com.jonahzander.rsvpreader.core.model.Chapter
import com.jonahzander.rsvpreader.data.ProgressStore
import com.jonahzander.rsvpreader.data.ReadingPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderSessionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // "one two three." + second chapter "four five" — all words at 300 wpm base = 200ms,
    // except "three." (sentence end) = 400ms
    private val book = Book(
        id = "b",
        title = "Test Book",
        chapters = listOf(
            Chapter("First", "one two three."),
            Chapter("Second", "four five"),
        ),
    )

    private fun TestScope.newHarness(onPositionSaved: () -> Unit = {}): Pair<ReaderSession, ProgressStore> {
        val dsScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val store = ProgressStore(
            PreferenceDataStoreFactory.create(scope = dsScope) {
                tmp.newFolder().resolve("p.preferences_pb")
            }
        )
        val session = ReaderSession(
            book,
            store,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
            onPositionSaved = onPositionSaved,
        )
        runCurrent()
        return session to store
    }

    @Test
    fun `starts paused at first word of first chapter`() = runTest {
        val (session, _) = newHarness()
        val s = session.state.value
        assertEquals("one", s.word)
        assertEquals("First", s.chapterTitle)
        assertEquals(3, s.chapterWordCount)
        assertFalse(s.isPlaying)
    }

    @Test
    fun `restores saved position and wpm`() = runTest {
        val dsScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val store = ProgressStore(
            PreferenceDataStoreFactory.create(scope = dsScope) {
                tmp.newFolder().resolve("p.preferences_pb")
            }
        )
        store.setWpm(600)
        store.savePosition("b", ReadingPosition(chapterIndex = 1, wordIndex = 1))
        val session = ReaderSession(book, store, CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()))
        runCurrent()

        val s = session.state.value
        assertEquals("five", s.word)
        assertEquals(600, s.wpm)
        assertEquals(1, s.chapterIndex)
    }

    @Test
    fun `play advances words on the rsvp clock`() = runTest {
        val (session, _) = newHarness()
        session.togglePlay()
        runCurrent()
        assertTrue(session.state.value.isPlaying)
        assertEquals("one", session.state.value.word)

        advanceTimeBy(201)
        assertEquals("two", session.state.value.word)

        advanceTimeBy(200)
        assertEquals("three.", session.state.value.word)
    }

    @Test
    fun `pause stops advancement and saves position`() = runTest {
        val (session, store) = newHarness()
        session.togglePlay()
        advanceTimeBy(201) // now on "two"
        session.togglePlay()
        runCurrent()

        assertFalse(session.state.value.isPlaying)
        assertEquals("two", session.state.value.word)
        assertEquals(ReadingPosition(0, 1), store.position("b").first())

        advanceTimeBy(2_000)
        assertEquals("two", session.state.value.word)
    }

    @Test
    fun `chapter auto-advances when last word has been shown`() = runTest {
        val (session, _) = newHarness()
        session.togglePlay()
        // one(200) + two(200) + three.(400)
        advanceTimeBy(801)
        val s = session.state.value
        assertEquals("four", s.word)
        assertEquals(1, s.chapterIndex)
        assertEquals("Second", s.chapterTitle)
    }

    @Test
    fun `book end pauses and marks finished`() = runTest {
        val (session, _) = newHarness()
        session.togglePlay()
        // chapter 1: 800ms; chapter 2: four(200) + five(200)
        advanceTimeBy(1_300)
        val s = session.state.value
        assertTrue(s.finished)
        assertFalse(s.isPlaying)
        assertEquals("five", s.word)
    }

    @Test
    fun `wpm adjustment speeds up playback and persists`() = runTest {
        val (session, store) = newHarness()
        session.togglePlay()
        session.adjustWpm(300) // 600 wpm -> 100ms per plain word
        runCurrent()
        advanceTimeBy(101)
        assertEquals("two", session.state.value.word)
        assertEquals(600, store.settings.first().wpm)
    }

    @Test
    fun `wpm is clamped to bounds`() = runTest {
        val (session, _) = newHarness()
        session.adjustWpm(-10_000)
        runCurrent()
        assertEquals(100, session.state.value.wpm)
        session.adjustWpm(10_000)
        runCurrent()
        assertEquals(900, session.state.value.wpm)
    }

    @Test
    fun `scrub while paused steps words and clamps at chapter edges`() = runTest {
        val (session, _) = newHarness()
        session.scrub(2)
        runCurrent()
        assertEquals("three.", session.state.value.word)
        session.scrub(5)
        runCurrent()
        assertEquals("three.", session.state.value.word) // clamped at chapter end
        session.scrub(-10)
        runCurrent()
        assertEquals("one", session.state.value.word) // clamped at start
    }

    @Test
    fun `jump to chapter starts at its first word and saves`() = runTest {
        val (session, store) = newHarness()
        session.jumpToChapter(1)
        runCurrent()
        assertEquals("four", session.state.value.word)
        assertEquals(ReadingPosition(1, 0), store.position("b").first())
    }

    @Test
    fun `seekToFraction jumps within the current chapter and saves`() = runTest {
        val (session, store) = newHarness()
        session.seekToFraction(1.0)
        runCurrent()
        assertEquals("three.", session.state.value.word) // last word of chapter 0
        assertEquals(ReadingPosition(0, 2), store.position("b").first())
    }

    @Test
    fun `jumpToWord moves within the chapter while paused and clamps`() = runTest {
        val (session, _) = newHarness()
        session.jumpToWord(2)
        runCurrent()
        assertEquals("three.", session.state.value.word)
        session.jumpToWord(99)
        runCurrent()
        assertEquals("three.", session.state.value.word)
        session.jumpToWord(-5)
        runCurrent()
        assertEquals("one", session.state.value.word)
    }

    @Test
    fun `jumpToWord is ignored while playing`() = runTest {
        val (session, _) = newHarness()
        session.togglePlay()
        runCurrent()
        session.jumpToWord(2)
        runCurrent()
        assertEquals("one", session.state.value.word)
    }

    @Test
    fun `persistPosition saves the current word`() = runTest {
        val (session, store) = newHarness()
        session.jumpToWord(1)
        session.persistPosition()
        runCurrent()
        assertEquals(ReadingPosition(0, 1), store.position("b").first())
    }

    @Test
    fun `wordsOf returns the words of a chapter and empty out of range`() = runTest {
        val (session, _) = newHarness()
        assertEquals(listOf("one", "two", "three."), session.wordsOf(0))
        assertEquals(listOf("four", "five"), session.wordsOf(1))
        assertTrue(session.wordsOf(7).isEmpty())
    }

    @Test
    fun `saving position also writes the tile snapshot`() = runTest {
        val (session, store) = newHarness()
        session.jumpToWord(1) // "two"
        session.persistPosition()
        runCurrent()
        assertEquals(
            com.jonahzander.rsvpreader.data.ContinueSnapshot(
                bookId = "b",
                bookTitle = "Test Book",
                word = "two",
                chapterNumber = 1,
                chapterCount = 2,
            ),
            store.continueSnapshot.first(),
        )
    }

    @Test
    fun `pause after playback scope cancellation still saves via the save scope`() = runTest {
        val dsScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val store = ProgressStore(
            PreferenceDataStoreFactory.create(scope = dsScope) {
                tmp.newFolder().resolve("p.preferences_pb")
            }
        )
        val playScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val saveScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val session = ReaderSession(book, store, playScope, saveScope)
        runCurrent()
        session.jumpToWord(1)
        playScope.cancel() // what ViewModel clearing does to viewModelScope
        session.pause()
        runCurrent()
        assertEquals(ReadingPosition(0, 1), store.position("b").first())
    }

    @Test
    fun `saving position notifies the listener`() = runTest {
        var notified = 0
        val (session, _) = newHarness(onPositionSaved = { notified++ })
        session.pause() // pause always persists
        runCurrent()
        assertTrue(notified > 0)
    }

    @Test
    fun `seekToFraction clamps and pauses playback`() = runTest {
        val (session, _) = newHarness()
        session.togglePlay()
        runCurrent()
        session.seekToFraction(-0.5)
        runCurrent()
        assertEquals("one", session.state.value.word)
        assertFalse(session.state.value.isPlaying)
    }

    @Test
    fun `chapterText returns the raw chapter text and empty out of range`() = runTest {
        val (session, _) = newHarness()
        assertEquals("one two three.", session.chapterText(0))
        assertEquals("four five", session.chapterText(1))
        assertEquals("", session.chapterText(9))
    }
}
