package com.jonahzander.rsvpreader.companion.epub

import com.jonahzander.rsvpreader.core.model.Book
import com.jonahzander.rsvpreader.core.model.Chapter
import io.documentnode.epub4j.epub.EpubReader
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Parses an EPUB stream into the app's [Book] model: OPF metadata for
 * title/author, spine order for chapters, Jsoup-cleaned plain text per chapter.
 */
object EpubParser {

    fun parse(input: InputStream): Book {
        val bytes = input.readBytes()
        val epub = EpubReader().readEpub(ByteArrayInputStream(bytes))

        val title = epub.title?.takeIf { it.isNotBlank() } ?: "Untitled"
        val author = epub.metadata.authors.firstOrNull()
            ?.let { listOf(it.firstname, it.lastname) }
            ?.filter { !it.isNullOrBlank() }
            ?.joinToString(" ")
            ?.takeIf { it.isNotBlank() }

        val chapters = epub.spine.spineReferences.mapNotNull { ref ->
            val resource = ref.resource ?: return@mapNotNull null
            val encoding = resource.inputEncoding ?: Charsets.UTF_8.name()
            parseChapter(String(resource.data, charset(encoding)))
        }
        val numbered = chapters.mapIndexed { i, chapter ->
            if (chapter.title.isBlank()) chapter.copy(title = "Chapter ${i + 1}") else chapter
        }

        return Book(id = idFor(bytes), title = title, author = author, chapters = numbered)
    }

    /** Returns null for spine items with no readable text (covers, image pages). */
    private fun parseChapter(xhtml: String): Chapter? {
        val doc = Jsoup.parse(xhtml)
        val heading = doc.selectFirst("h1, h2, h3")?.text()?.takeIf { it.isNotBlank() }
        doc.select("h1, h2, h3, h4, h5, h6").remove()

        val paragraphs = doc.select("p").map { clean(it.text()) }.filter { it.isNotBlank() }
        val text = if (paragraphs.isNotEmpty()) {
            paragraphs.joinToString("\n\n")
        } else {
            clean(doc.body()?.text().orEmpty())
        }
        if (text.isBlank()) return null
        return Chapter(title = heading ?: "", text = text)
    }

    private fun clean(text: String): String = text.replace(' ', ' ').trim()

    /** Stable, filename-safe id derived from the file's content. */
    private fun idFor(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes)
            .joinToString("") { "%02x".format(it) }
            .take(16)
}
