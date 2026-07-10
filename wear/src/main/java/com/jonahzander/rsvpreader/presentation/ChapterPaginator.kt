package com.jonahzander.rsvpreader.presentation

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import com.jonahzander.rsvpreader.core.OffsetToken

/** One page of a paginated chapter. [endWord] is exclusive. */
data class Page(val startWord: Int, val endWord: Int, val text: String)

/**
 * Splits a chapter into pages that fit a boxWidth×boxHeight content area at
 * the given style. Greedy: each page takes the largest word range that fits
 * (binary search on the measured height). Page text is a substring of the
 * original chapter, so hyphenated compounds appear unsplit.
 */
class ChapterPaginator(
    private val text: String,
    private val tokens: List<OffsetToken>,
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val boxWidthPx: Int,
    private val boxHeightPx: Int,
) {
    fun paginate(): List<Page> {
        if (tokens.isEmpty()) return listOf(Page(0, 0, ""))
        val pages = mutableListOf<Page>()
        var start = 0
        while (start < tokens.size) {
            val end = fitWords(start)
            pages += Page(start, end, slice(start, end))
            start = end
        }
        return pages
    }

    /** Largest end (exclusive) such that words [start, end) fit; always ≥ start+1. */
    private fun fitWords(start: Int): Int {
        var lo = start + 1
        var hi = tokens.size
        while (lo < hi) {
            val mid = lo + (hi - lo + 1) / 2
            if (fits(start, mid)) lo = mid else hi = mid - 1
        }
        return lo
    }

    private fun fits(start: Int, end: Int): Boolean {
        val layout = measurer.measure(
            AnnotatedString(slice(start, end)),
            style = style,
            constraints = Constraints(maxWidth = boxWidthPx),
        )
        return layout.size.height <= boxHeightPx
    }

    private fun slice(start: Int, end: Int): String {
        val last = tokens[end - 1]
        return text.substring(tokens[start].startOffset, last.startOffset + last.word.length)
    }
}

/** Index of the page containing [wordIndex]; 0 for an empty list or out-of-range index. */
fun List<Page>.pageFor(wordIndex: Int): Int =
    indexOfLast { it.startWord <= wordIndex }.coerceAtLeast(0)
