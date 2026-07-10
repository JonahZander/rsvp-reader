package com.jonahzander.rsvpreader.companion

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jonahzander.rsvpreader.companion.epub.EpubParser
import com.jonahzander.rsvpreader.companion.ui.MainScreen
import com.jonahzander.rsvpreader.companion.ui.SettingsScreen
import com.jonahzander.rsvpreader.core.sync.ReaderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as CompanionApplication).repository
        setContent {
            MaterialTheme {
                CompanionApp(repository)
            }
        }
    }
}

@Composable
private fun CompanionApp(repository: CompanionRepository) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val books by repository.books.collectAsStateWithLifecycle()
    val settings by repository.settings.collectAsStateWithLifecycle(ReaderSettings())
    val ackedIds by repository.ackedIds.collectAsStateWithLifecycle(emptySet())
    var watchConnected by remember { mutableStateOf<Boolean?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.refresh()
        watchConnected = repository.isWatchConnected()
        repository.sweepAcks()
    }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    val epubPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { EpubParser.parse(it) }
                }
            }
            result
                .onSuccess { book ->
                    repository.addBook(book)
                    toast("Sent \"${book.title}\" to watch (${book.chapters.size} chapters)")
                }
                .onFailure { toast("Could not read that EPUB: ${it.message}") }
        }
    }

    if (showSettings) {
        SettingsScreen(
            settings = settings,
            onWpmChange = { scope.launch { repository.setWpm(it) } },
            onFontChange = { scope.launch { repository.setFont(it) } },
            onBack = { showSettings = false },
        )
    } else {
        MainScreen(
            books = books,
            watchConnected = watchConnected,
            ackedIds = ackedIds,
            onImportEpub = {
                epubPicker.launch(arrayOf("application/epub+zip", "application/octet-stream"))
            },
            onPasteText = { title, text ->
                scope.launch {
                    repository.addText(title, text)
                    toast("Sent \"$title\" to watch")
                }
            },
            onRemove = { book ->
                scope.launch {
                    repository.remove(book.id)
                    toast("Removed \"${book.title}\"")
                }
            },
            onOpenSettings = { showSettings = true },
        )
    }
}
