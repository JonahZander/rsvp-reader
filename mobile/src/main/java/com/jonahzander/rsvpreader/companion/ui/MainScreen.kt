package com.jonahzander.rsvpreader.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jonahzander.rsvpreader.core.model.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    books: List<Book>,
    watchConnected: Boolean?,
    ackedIds: Set<String>,
    onImportEpub: () -> Unit,
    onPasteText: (title: String, text: String) -> Unit,
    onRemove: (Book) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showPasteDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RSVP Reader") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text(
                text = when (watchConnected) {
                    true -> "⌚ Watch connected"
                    false -> "⌚ Watch not reachable — books sync when it reconnects"
                    null -> "⌚ Checking watch connection…"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(Modifier.padding(vertical = 12.dp)) {
                Button(onClick = onImportEpub) { Text("Import EPUB") }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { showPasteDialog = true }) { Text("Paste text") }
            }

            if (books.isEmpty()) {
                Text(
                    "Nothing sent yet. Import an EPUB, paste text, or share text from any app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                Text("On your watch", style = MaterialTheme.typography.titleMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(books, key = { it.id }) { book ->
                        ListItem(
                            headlineContent = { Text(book.title) },
                            supportingContent = {
                                val chapters = "${book.chapters.size} chapter${if (book.chapters.size == 1) "" else "s"}"
                                val status = if (book.id in ackedIds) "On watch ✓" else "Sending…"
                                Text(listOfNotNull(book.author, chapters, status).joinToString(" · "))
                            },
                            trailingContent = {
                                IconButton(onClick = { onRemove(book) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showPasteDialog) {
        PasteTextDialog(
            onDismiss = { showPasteDialog = false },
            onConfirm = { title, text ->
                showPasteDialog = false
                onPasteText(title, text)
            },
        )
    }
}

@Composable
private fun PasteTextDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send text to watch") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(title.ifBlank { "Pasted text" }, text) },
            ) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
