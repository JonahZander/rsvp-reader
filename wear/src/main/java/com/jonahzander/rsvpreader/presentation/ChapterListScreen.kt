package com.jonahzander.rsvpreader.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.jonahzander.rsvpreader.core.model.Book

@Composable
fun ChapterListScreen(
    book: Book,
    onContinueReading: () -> Unit,
    onOpenChapter: (Int) -> Unit,
) {
    val listState = rememberScalingLazyListState()
    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text(book.title, maxLines = 2) } }
            item {
                Chip(
                    onClick = onContinueReading,
                    label = { Text("Continue reading") },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            book.chapters.forEachIndexed { index, chapter ->
                item {
                    Chip(
                        onClick = { onOpenChapter(index) },
                        label = { Text(chapter.title, maxLines = 2) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
