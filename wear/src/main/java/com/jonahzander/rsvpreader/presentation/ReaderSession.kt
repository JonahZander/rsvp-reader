package com.jonahzander.rsvpreader.presentation

import com.jonahzander.rsvpreader.core.RsvpEngine
import com.jonahzander.rsvpreader.core.model.Book
import com.jonahzander.rsvpreader.data.ProgressStore
import com.jonahzander.rsvpreader.data.ReadingPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ReaderState(
    val word: String = "",
    val orpIndex: Int = 0,
    val isPlaying: Boolean = false,
    val wpm: Int = 300,
    val chapterIndex: Int = 0,
    val wordIndex: Int = 0,
    val chapterWordCount: Int = 0,
    val chapterTitle: String = "",
    val finished: Boolean = false,
)

/**
 * Drives RSVP playback for one book: word advancement at the current WPM,
 * chapter auto-advance, scrubbing while paused, and position persistence.
 *
 * Plain class (not a ViewModel) so it can be tested with virtual time.
 */
class ReaderSession(
    private val book: Book,
    private val progressStore: ProgressStore,
    private val scope: CoroutineScope,
    private val onPositionSaved: () -> Unit = {},
) {
    private val chapterWords: List<List<String>> =
        book.chapters.map { RsvpEngine.tokenize(it.text) }

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state

    private var playJob: Job? = null

    init {
        scope.launch {
            val settings = progressStore.settings.first()
            val position = progressStore.position(book.id).first()
            val chapter = position.chapterIndex.coerceIn(0, book.chapters.lastIndex)
            val word = position.wordIndex.coerceIn(0, chapterWords[chapter].lastIndex.coerceAtLeast(0))
            _state.value = stateAt(chapter, word).copy(wpm = settings.wpm)
        }
    }

    fun togglePlay() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun pause() {
        playJob?.cancel()
        playJob = null
        if (_state.value.isPlaying) {
            _state.value = _state.value.copy(isPlaying = false)
        }
        savePosition()
    }

    private fun play() {
        if (playJob != null) return
        _state.value = _state.value.copy(isPlaying = true, finished = false)
        startLoop()
    }

    private fun startLoop() {
        playJob = scope.launch {
            while (true) {
                val s = _state.value
                delay(RsvpEngine.durationMs(s.word, s.wpm))
                if (!advance()) break
            }
        }
    }

    /** Move to the next word; returns false (and stops playback) at the end of the book. */
    private fun advance(): Boolean {
        val s = _state.value
        val words = chapterWords[s.chapterIndex]
        return when {
            s.wordIndex + 1 < words.size -> {
                _state.value = stateAt(s.chapterIndex, s.wordIndex + 1)
                true
            }
            s.chapterIndex + 1 < book.chapters.size -> {
                _state.value = stateAt(s.chapterIndex + 1, 0)
                savePosition()
                true
            }
            else -> {
                playJob = null
                _state.value = s.copy(isPlaying = false, finished = true)
                savePosition()
                false
            }
        }
    }

    /** Adjust reading speed; clamped to sane bounds. Takes effect immediately. */
    fun adjustWpm(delta: Int) {
        val wpm = (_state.value.wpm + delta).coerceIn(MIN_WPM, MAX_WPM)
        _state.value = _state.value.copy(wpm = wpm)
        if (_state.value.isPlaying) {
            playJob?.cancel()
            startLoop()
        }
        scope.launch { progressStore.setWpm(wpm) }
    }

    /** Step through words while paused. */
    fun scrub(delta: Int) {
        if (_state.value.isPlaying) return
        val s = _state.value
        val words = chapterWords[s.chapterIndex]
        val target = (s.wordIndex + delta).coerceIn(0, words.lastIndex.coerceAtLeast(0))
        _state.value = stateAt(s.chapterIndex, target)
    }

    /** Jump to the start of a chapter. */
    fun jumpToChapter(index: Int) {
        pause()
        val chapter = index.coerceIn(0, book.chapters.lastIndex)
        _state.value = stateAt(chapter, 0)
        savePosition()
    }

    /** Jump to a fraction (0..1) of the current chapter. Pauses playback. */
    fun seekToFraction(fraction: Double) {
        pause()
        val s = _state.value
        val words = chapterWords[s.chapterIndex]
        val target = (fraction.coerceIn(0.0, 1.0) * words.lastIndex.coerceAtLeast(0))
            .toInt()
        _state.value = stateAt(s.chapterIndex, target)
        savePosition()
    }

    /** All words of a chapter, or an empty list for an out-of-range index. */
    fun wordsOf(chapterIndex: Int): List<String> =
        chapterWords.getOrElse(chapterIndex) { emptyList() }

    /** Jump directly to a word in the current chapter. Only while paused; does not persist. */
    fun jumpToWord(index: Int) {
        if (_state.value.isPlaying) return
        val s = _state.value
        val target = index.coerceIn(0, chapterWords[s.chapterIndex].lastIndex.coerceAtLeast(0))
        if (target != s.wordIndex) _state.value = stateAt(s.chapterIndex, target)
    }

    /** Persist the current position (called when a scrub gesture settles). */
    fun persistPosition() = savePosition()

    private fun stateAt(chapterIndex: Int, wordIndex: Int): ReaderState {
        val words = chapterWords[chapterIndex]
        val word = words.getOrElse(wordIndex) { "" }
        return _state.value.copy(
            word = word,
            orpIndex = RsvpEngine.orpIndex(word),
            chapterIndex = chapterIndex,
            wordIndex = wordIndex,
            chapterWordCount = words.size,
            chapterTitle = book.chapters[chapterIndex].title,
        )
    }

    private fun savePosition() {
        val s = _state.value
        scope.launch {
            progressStore.savePosition(book.id, ReadingPosition(s.chapterIndex, s.wordIndex))
            onPositionSaved()
        }
    }

    private companion object {
        const val MIN_WPM = 100
        const val MAX_WPM = 900
    }
}
