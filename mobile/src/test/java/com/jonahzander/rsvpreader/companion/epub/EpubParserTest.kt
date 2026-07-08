package com.jonahzander.rsvpreader.companion.epub

import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixtures are built in-memory: a minimal but spec-correct EPUB 2 zip with
 * container.xml, an OPF manifest/spine, and XHTML chapters.
 */
class EpubParserTest {

    private fun chapterXhtml(title: String?, body: String) = """
        <?xml version="1.0" encoding="utf-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml">
        <head><title>${title ?: ""}</title></head>
        <body>${if (title != null) "<h1>$title</h1>" else ""}$body</body>
        </html>
    """.trimIndent()

    private fun buildEpub(
        title: String = "The Test Book",
        author: String? = "Ada Writer",
        // spine order: filename -> xhtml content, in the order they should be read
        chapters: List<Pair<String, String>>,
    ): ByteArray {
        val manifestItems = chapters.mapIndexed { i, (name, _) ->
            """<item id="c$i" href="$name" media-type="application/xhtml+xml"/>"""
        }.joinToString("\n")
        val spineRefs = chapters.indices.joinToString("\n") { """<itemref idref="c$it"/>""" }
        val authorXml = author?.let { "<dc:creator>$it</dc:creator>" } ?: ""
        val opf = """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/" version="2.0" unique-identifier="id">
              <metadata>
                <dc:identifier id="id">test-book-1</dc:identifier>
                <dc:title>$title</dc:title>
                $authorXml
                <dc:language>en</dc:language>
              </metadata>
              <manifest>
                $manifestItems
              </manifest>
              <spine>
                $spineRefs
              </spine>
            </package>
        """.trimIndent().trim() // trim: multi-line interpolations defeat trimIndent, and <?xml must be at char 0
        val container = """
            <?xml version="1.0" encoding="utf-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            put("mimetype", "application/epub+zip")
            put("META-INF/container.xml", container)
            put("OEBPS/content.opf", opf)
            chapters.forEach { (name, xhtml) -> put("OEBPS/$name", xhtml) }
        }
        return out.toByteArray()
    }

    private fun parse(bytes: ByteArray) = EpubParser.parse(ByteArrayInputStream(bytes))

    @Test
    fun `title and author come from opf metadata`() {
        val book = parse(buildEpub(chapters = listOf("a.xhtml" to chapterXhtml("One", "<p>Text.</p>"))))
        assertEquals("The Test Book", book.title)
        assertEquals("Ada Writer", book.author)
    }

    @Test
    fun `missing author is null`() {
        val book = parse(buildEpub(author = null, chapters = listOf("a.xhtml" to chapterXhtml("One", "<p>Text.</p>"))))
        assertEquals(null, book.author)
    }

    @Test
    fun `chapters follow spine order not filename order`() {
        val book = parse(
            buildEpub(
                chapters = listOf(
                    "zzz.xhtml" to chapterXhtml("First Chapter", "<p>Alpha text.</p>"),
                    "aaa.xhtml" to chapterXhtml("Second Chapter", "<p>Beta text.</p>"),
                ),
            )
        )
        assertEquals(listOf("First Chapter", "Second Chapter"), book.chapters.map { it.title })
    }

    @Test
    fun `html is stripped to plain paragraphs`() {
        val body = """
            <p>She said <em>hello</em> to the <a href="x">world</a>.</p>
            <p>Second&nbsp;paragraph here.</p>
        """.trimIndent()
        val book = parse(buildEpub(chapters = listOf("a.xhtml" to chapterXhtml("One", body))))
        val text = book.chapters.single().text
        assertTrue("She said hello to the world." in text, "got: $text")
        assertTrue("Second paragraph here." in text, "got: $text")
        assertFalse("<" in text)
    }

    @Test
    fun `heading text is not duplicated into the body text`() {
        val book = parse(buildEpub(chapters = listOf("a.xhtml" to chapterXhtml("Unique Heading", "<p>Body only.</p>"))))
        assertFalse("Unique Heading" in book.chapters.single().text)
    }

    @Test
    fun `chapter without heading gets numbered title`() {
        val book = parse(
            buildEpub(
                chapters = listOf(
                    "a.xhtml" to chapterXhtml(null, "<p>First.</p>"),
                    "b.xhtml" to chapterXhtml(null, "<p>Second.</p>"),
                ),
            )
        )
        assertEquals(listOf("Chapter 1", "Chapter 2"), book.chapters.map { it.title })
    }

    @Test
    fun `spine items with no readable text are skipped`() {
        val book = parse(
            buildEpub(
                chapters = listOf(
                    "cover.xhtml" to chapterXhtml(null, """<img src="cover.jpg"/>"""),
                    "a.xhtml" to chapterXhtml("Real", "<p>Content.</p>"),
                ),
            )
        )
        assertEquals(listOf("Real"), book.chapters.map { it.title })
    }

    @Test
    fun `book id is stable and filename safe`() {
        val bytes = buildEpub(chapters = listOf("a.xhtml" to chapterXhtml("One", "<p>Text.</p>")))
        val id1 = parse(bytes).id
        val id2 = parse(bytes).id
        assertEquals(id1, id2)
        assertTrue(id1.isNotBlank())
        assertTrue(id1.matches(Regex("[A-Za-z0-9_-]+")), "got: $id1")
    }
}
