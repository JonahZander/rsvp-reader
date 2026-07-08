package com.jonahzander.rsvpreader.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.jonahzander.rsvpreader.core.model.Book

@Composable
fun LibraryScreen(
    books: List<Book>,
    onOpenBook: (Book) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("RSVP Reader") } }
            items(books, key = { it.id }) { book ->
                Chip(
                    onClick = { onOpenBook(book) },
                    label = { Text(book.title, maxLines = 2) },
                    secondaryLabel = book.author?.let { { Text(it, maxLines = 1) } },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                CompactChip(
                    onClick = onOpenSettings,
                    label = { Text("Settings") },
                    colors = ChipDefaults.primaryChipColors(),
                )
            }
        }
    }
}
