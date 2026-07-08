package com.jonahzander.rsvpreader.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

private val OrpColor = Color(0xFFE5484D)

fun fontFamilyFor(name: String): FontFamily = when (name) {
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    else -> FontFamily.Default
}

@Composable
fun ReaderScreen(viewModel: ReaderViewModel, font: String) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val current = session
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading…")
        }
        return
    }
    ReaderContent(current, font)
}

@Composable
private fun ReaderContent(session: ReaderSession, font: String) {
    val state by session.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val focusRequester = remember { FocusRequester() }

    var overlayUntil by remember { mutableLongStateOf(0L) }
    var overlayText by remember { mutableStateOf("") }
    var overlayVisible by remember { mutableStateOf(false) }

    fun showOverlay(text: String) {
        overlayText = text
        overlayUntil = System.currentTimeMillis() + 1200
        overlayVisible = true
    }

    LaunchedEffect(overlayUntil) {
        if (overlayUntil > 0) {
            kotlinx.coroutines.delay(1300)
            if (System.currentTimeMillis() >= overlayUntil) overlayVisible = false
        }
    }

    // Keep the screen on only while words are streaming
    LaunchedEffect(state.isPlaying) {
        view.keepScreenOn = state.isPlaying
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                if (state.isPlaying) {
                    val delta = if (event.verticalScrollPixels > 0) 25 else -25
                    session.adjustWpm(delta)
                    showOverlay("${session.state.value.wpm} wpm")
                } else {
                    session.scrub(if (event.verticalScrollPixels > 0) 1 else -1)
                    showOverlay("${session.state.value.wordIndex + 1} / ${state.chapterWordCount}")
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                session.togglePlay()
            },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = if (state.chapterWordCount == 0) 0f
            else (state.wordIndex + 1).toFloat() / state.chapterWordCount,
            modifier = Modifier.fillMaxSize().padding(2.dp),
            strokeWidth = 3.dp,
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OrpWord(word = state.word, orpIndex = state.orpIndex, font = font)
            if (!state.isPlaying) {
                Text(
                    text = if (state.finished) "Finished — tap to restart chapter" else "Paused — tap to play",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        Text(
            text = state.chapterTitle,
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
        )

        if (overlayVisible) {
            Text(
                text = overlayText,
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            )
        }
    }
}

/**
 * Displays one word with its optimal-recognition-point letter highlighted and
 * pinned to the horizontal center of the screen, so the eye never moves.
 */
@Composable
private fun OrpWord(word: String, orpIndex: Int, font: String) {
    val family = fontFamilyFor(font)
    val style = MaterialTheme.typography.title1.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        fontFamily = family,
    )
    val safeOrp = orpIndex.coerceIn(0, (word.length - 1).coerceAtLeast(0))
    val pre = word.take(safeOrp)
    val orp = word.getOrNull(safeOrp)?.toString() ?: ""
    val post = word.drop(safeOrp + 1)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(pre, style = style, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1f))
        Text(orp, style = style, color = OrpColor, maxLines = 1)
        Text(post, style = style, textAlign = TextAlign.Start, maxLines = 1, modifier = Modifier.weight(1f))
    }
}
