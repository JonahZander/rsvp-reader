package com.jonahzander.rsvpreader.companion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
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
                        // Only a declared EPUB goes through the EPUB parser: a
                        // text/plain share can also arrive as a stream (e.g. a
                        // .txt file from a file manager) and must be read as text.
                        stream != null && intent.type == "application/epub+zip" ->
                            repository.importEpub { contentResolver.openInputStream(stream) }
                                .getOrThrow().title
                        !text.isNullOrBlank() -> {
                            val bookTitle = title?.takeIf { it.isNotBlank() } ?: shortTitleFor(text)
                            repository.addText(bookTitle, text)
                            bookTitle
                        }
                        stream != null -> {
                            val streamText = contentResolver.openInputStream(stream)!!
                                .use { it.readBytes().decodeToString() }
                            val bookTitle = title?.takeIf { it.isNotBlank() } ?: shortTitleFor(streamText)
                            repository.addText(bookTitle, streamText)
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
