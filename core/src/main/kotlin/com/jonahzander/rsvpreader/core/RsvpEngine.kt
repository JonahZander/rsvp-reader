package com.jonahzander.rsvpreader.core

/**
 * A single word ready for RSVP display.
 *
 * @param word the token as displayed, punctuation included
 * @param orpIndex index of the optimal-recognition-point letter to highlight
 * @param durationMs how long the word stays on screen at the requested WPM
 */
data class RsvpToken(
    val word: String,
    val orpIndex: Int,
    val durationMs: Long,
)

object RsvpEngine {

    private const val LONG_WORD_LETTERS = 9
    private const val LONG_WORD_BONUS = 0.5
    private const val SENTENCE_END_BONUS = 1.0
    private const val CLAUSE_END_BONUS = 0.5

    private val SENTENCE_END = charArrayOf('.', '!', '?', '…')
    private val CLAUSE_END = charArrayOf(',', ';', ':')

    /** Hyphenated compounds longer than this are split so each part fits the screen. */
    private const val MAX_TOKEN_CHARS = 12

    fun tokenize(text: String): List<String> =
        text.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .flatMap { splitLongCompound(it) }

    /**
     * Splits an overlong hyphenated compound ("miniature-painter") into parts
     * that fit on screen, keeping the hyphen on the leading part
     * ("miniature-", "painter"). Splits at the largest fitting prefix and
     * recurses, so multi-hyphen compounds break into as few parts as possible.
     */
    private fun splitLongCompound(token: String): List<String> {
        if (token.length <= MAX_TOKEN_CHARS) return listOf(token)
        // Split points: just after a '-' that has a letter or digit on both sides.
        val splitPoints = token.indices.filter { i ->
            token[i] == '-' && i > 0 && i < token.lastIndex &&
                token[i - 1].isLetterOrDigit() && token[i + 1].isLetterOrDigit()
        }.map { it + 1 }
        if (splitPoints.isEmpty()) return listOf(token)
        val cut = splitPoints.lastOrNull { it <= MAX_TOKEN_CHARS } ?: splitPoints.first()
        return listOf(token.substring(0, cut)) + splitLongCompound(token.substring(cut))
    }

    fun orpIndex(word: String): Int {
        val leading = word.indexOfFirst { it.isLetterOrDigit() }.coerceAtLeast(0)
        val core = coreOf(word)
        val orpInCore = when {
            core.length <= 1 -> 0
            core.length <= 5 -> 1
            core.length <= 9 -> 2
            core.length <= 13 -> 3
            else -> 4
        }
        return leading + orpInCore
    }

    fun durationMs(word: String, wpm: Int): Long {
        val base = 60_000.0 / wpm
        var multiplier = 1.0
        if (coreOf(word).length >= LONG_WORD_LETTERS) multiplier += LONG_WORD_BONUS
        val trailing = word.takeLastWhile { !it.isLetterOrDigit() }
        when {
            trailing.any { it in SENTENCE_END } -> multiplier += SENTENCE_END_BONUS
            trailing.any { it in CLAUSE_END } -> multiplier += CLAUSE_END_BONUS
        }
        return (base * multiplier).toLong()
    }

    fun tokens(text: String, wpm: Int): List<RsvpToken> =
        tokenize(text).map { RsvpToken(it, orpIndex(it), durationMs(it, wpm)) }

    /** The word with surrounding punctuation stripped. */
    private fun coreOf(word: String): String =
        word.dropWhile { !it.isLetterOrDigit() }.dropLastWhile { !it.isLetterOrDigit() }
}
