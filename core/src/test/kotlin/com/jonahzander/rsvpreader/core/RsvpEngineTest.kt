package com.jonahzander.rsvpreader.core

import org.junit.Test
import kotlin.test.assertEquals

class RsvpEngineTest {

    // --- tokenize ---

    @Test
    fun `tokenize splits on whitespace including newlines and drops blanks`() {
        assertEquals(
            listOf("The", "quick", "fox."),
            RsvpEngine.tokenize("The quick\n\n  fox."),
        )
    }

    @Test
    fun `tokenize of blank text is empty`() {
        assertEquals(emptyList(), RsvpEngine.tokenize("  \n\t "))
    }

    @Test
    fun `tokenize keeps punctuation attached to words`() {
        assertEquals(
            listOf("\"Hello,\"", "she", "said."),
            RsvpEngine.tokenize("\"Hello,\" she said."),
        )
    }

    @Test
    fun `tokenize splits long hyphenated compounds keeping the hyphen on the first part`() {
        assertEquals(
            listOf("miniature-", "painter"),
            RsvpEngine.tokenize("miniature-painter"),
        )
    }

    @Test
    fun `tokenize keeps short hyphenated words whole`() {
        assertEquals(listOf("re-do", "well-fed."), RsvpEngine.tokenize("re-do well-fed."))
    }

    @Test
    fun `tokenize splits multi-hyphen compounds at the largest fitting prefix`() {
        assertEquals(
            listOf("state-of-", "the-art-", "technology"),
            RsvpEngine.tokenize("state-of-the-art-technology"),
        )
    }

    @Test
    fun `tokenize keeps punctuation around split hyphen compounds`() {
        assertEquals(
            listOf("\"miniature-", "painter,\""),
            RsvpEngine.tokenize("\"miniature-painter,\""),
        )
    }

    @Test
    fun `tokenize does not split on dashes without letters on both sides`() {
        assertEquals(
            listOf("wait--what", "loooooooooooong--"),
            RsvpEngine.tokenize("wait--what loooooooooooong--"),
        )
    }

    // --- orpIndex (classic Spritz letter-position rules by core word length) ---

    @Test
    fun `orp of single letter word is first letter`() {
        assertEquals(0, RsvpEngine.orpIndex("a"))
    }

    @Test
    fun `orp of short word is second letter`() {
        assertEquals(1, RsvpEngine.orpIndex("cat"))
        assertEquals(1, RsvpEngine.orpIndex("hello"))
    }

    @Test
    fun `orp of medium word is third letter`() {
        assertEquals(2, RsvpEngine.orpIndex("reading"))
        assertEquals(2, RsvpEngine.orpIndex("wonderful"))
    }

    @Test
    fun `orp of long word is fourth letter`() {
        assertEquals(3, RsvpEngine.orpIndex("presentation"))
    }

    @Test
    fun `orp of very long word is fifth letter`() {
        assertEquals(3 + 1, RsvpEngine.orpIndex("extraordinarily"))
    }

    @Test
    fun `orp skips leading punctuation`() {
        // core word "hello" has ORP at its 2nd letter; leading quote shifts index by 1
        assertEquals(2, RsvpEngine.orpIndex("\"hello"))
    }

    @Test
    fun `orp ignores trailing punctuation`() {
        assertEquals(1, RsvpEngine.orpIndex("cat,"))
        assertEquals(1, RsvpEngine.orpIndex("cat.\""))
    }

    // --- durationMs ---

    @Test
    fun `base duration is 60000 over wpm`() {
        assertEquals(200L, RsvpEngine.durationMs("cat", 300))
        assertEquals(120L, RsvpEngine.durationMs("cat", 500))
    }

    @Test
    fun `long words get fifty percent extra time`() {
        // "wonderful" = 9 letters
        assertEquals(300L, RsvpEngine.durationMs("wonderful", 300))
    }

    @Test
    fun `sentence-ending punctuation doubles the duration`() {
        assertEquals(400L, RsvpEngine.durationMs("end.", 300))
        assertEquals(400L, RsvpEngine.durationMs("what?", 300))
        assertEquals(400L, RsvpEngine.durationMs("stop!\"", 300))
    }

    @Test
    fun `clause punctuation adds fifty percent`() {
        assertEquals(300L, RsvpEngine.durationMs("yes,", 300))
        assertEquals(300L, RsvpEngine.durationMs("first;", 300))
        assertEquals(300L, RsvpEngine.durationMs("this:", 300))
    }

    @Test
    fun `long word and sentence punctuation bonuses stack`() {
        // base 200 * (1 + 0.5 long + 1.0 sentence) = 500
        assertEquals(500L, RsvpEngine.durationMs("wonderful.", 300))
    }

    // --- tokens end-to-end ---

    @Test
    fun `tokens combines tokenization orp and timing`() {
        val tokens = RsvpEngine.tokens("I read.", 300)
        assertEquals(
            listOf(
                RsvpToken("I", 0, 200L),
                RsvpToken("read.", 1, 400L),
            ),
            tokens,
        )
    }

    // --- tokenizeWithOffsets ---

    @Test
    fun `tokenizeWithOffsets matches tokenize and records original offsets`() {
        val text = "The quick\n\n  fox."
        val tokens = RsvpEngine.tokenizeWithOffsets(text)
        assertEquals(RsvpEngine.tokenize(text), tokens.map { it.word })
        assertEquals(listOf(0, 4, 13), tokens.map { it.startOffset })
        // Offsets point at the words in the original text.
        tokens.forEach { assertEquals(it.word, text.substring(it.startOffset, it.startOffset + it.word.length)) }
    }

    @Test
    fun `tokenizeWithOffsets keeps hyphen-split parts contiguous in the original text`() {
        val text = "a miniature-painter here"
        val tokens = RsvpEngine.tokenizeWithOffsets(text)
        assertEquals(listOf("a", "miniature-", "painter", "here"), tokens.map { it.word })
        assertEquals(listOf(0, 2, 12, 20), tokens.map { it.startOffset })
    }

    @Test
    fun `wordIndexAt finds the word covering or preceding a char offset`() {
        val tokens = RsvpEngine.tokenizeWithOffsets("one two three")
        assertEquals(0, RsvpEngine.wordIndexAt(tokens, 0))
        assertEquals(0, RsvpEngine.wordIndexAt(tokens, 3)) // the space after "one"
        assertEquals(1, RsvpEngine.wordIndexAt(tokens, 4))
        assertEquals(2, RsvpEngine.wordIndexAt(tokens, 99))
        assertEquals(0, RsvpEngine.wordIndexAt(tokens, -5))
    }
}
