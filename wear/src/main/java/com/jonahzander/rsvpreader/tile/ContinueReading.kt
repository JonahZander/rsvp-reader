package com.jonahzander.rsvpreader.tile

import com.jonahzander.rsvpreader.core.RsvpEngine
import com.jonahzander.rsvpreader.core.model.Book
import com.jonahzander.rsvpreader.data.ProgressStore
import kotlinx.coroutines.flow.first

data class ContinueReadingInfo(
    val bookId: String,
    val bookTitle: String,
    val lastWord: String,
    val chapterNumber: Int,
    val chapterCount: Int,
)

/**
 * Resolves the tile's content: the word at the last saved reading position.
 * Returns null when nothing has been read yet or the book no longer exists.
 */
suspend fun loadContinueReadingInfo(
    progressStore: ProgressStore,
    getBook: suspend (String) -> Book?,
): ContinueReadingInfo? {
    val bookId = progressStore.lastBookId.first() ?: return null
    // Fast path: the snapshot is written atomically with every position save,
    // so when it matches the last book no book needs to be loaded or tokenized.
    progressStore.continueSnapshot.first()?.let { snap ->
        if (snap.bookId == bookId) {
            return ContinueReadingInfo(
                bookId = snap.bookId,
                bookTitle = snap.bookTitle,
                lastWord = snap.word,
                chapterNumber = snap.chapterNumber,
                chapterCount = snap.chapterCount,
            )
        }
    }
    val book = getBook(bookId) ?: return null
    if (book.chapters.isEmpty()) return null
    val position = progressStore.position(bookId).first()
    val chapter = position.chapterIndex.coerceIn(0, book.chapters.lastIndex)
    val words = RsvpEngine.tokenize(book.chapters[chapter].text)
    if (words.isEmpty()) return null
    return ContinueReadingInfo(
        bookId = bookId,
        bookTitle = book.title,
        lastWord = words[position.wordIndex.coerceIn(0, words.lastIndex)],
        chapterNumber = chapter + 1,
        chapterCount = book.chapters.size,
    )
}
