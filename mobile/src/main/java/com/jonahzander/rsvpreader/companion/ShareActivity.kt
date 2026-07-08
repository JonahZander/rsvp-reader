package com.jonahzander.rsvpreader.companion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.jonahzander.rsvpreader.companion.epub.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Share-sheet target: receives plain text or an EPUB from any app,
 * adds it to the library, and syncs it to the watch.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as CompanionApplication).repository

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            ?: intent.getStringExtra(Intent.EXTRA_TITLE)
        @Suppress("DEPRECATION")
        val stream: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when {
                        stream != null -> {
                            val book = contentResolver.openInputStream(stream)!!.use { EpubParser.parse(it) }
                            repository.addBook(book)
                            book.title
                        }
                        !text.isNullOrBlank() -> {
                            val bookTitle = title?.takeIf { it.isNotBlank() } ?: shortTitleFor(text)
                            repository.addText(bookTitle, text)
                            bookTitle
                        }
                        else -> error("Nothing shareable found")
                    }
                }
            }
            result
                .onSuccess { Toast.makeText(this@ShareActivity, "Sent \"$it\" to watch", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(this@ShareActivity, "Could not send: ${it.message}", Toast.LENGTH_SHORT).show() }
            finish()
        }
    }

    private fun shortTitleFor(text: String): String {
        val words = text.trim().split(Regex("\\s+"))
        return words.take(6).joinToString(" ") + if (words.size > 6) "…" else ""
    }
}
