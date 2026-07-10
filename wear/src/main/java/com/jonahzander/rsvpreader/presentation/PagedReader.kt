package com.jonahzander.rsvpreader.presentation

import android.os.SystemClock
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.jonahzander.rsvpreader.core.RotaryAccumulator
import com.jonahzander.rsvpreader.core.RsvpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * E-reader-style paged mode: the chapter is laid out into full pages that fit
 * a round-screen-safe content box. Tap the right/left 40% of the screen (or
 * turn the crown) to page forward/back; the center strip is a no-op. There is
 * no play/pause — the session is used purely as the position holder, saved on
 * every page turn as the first word of the current page.
 */
@Composable
fun PagedReader(
    session: ReaderSession,
    font: String,
    wordSize: Int,
    chromeOpen: () -> Boolean,
) {
    val state by session.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rotary = remember(density) { RotaryAccumulator(thresholdPx = with(density) { 48.dp.toPx() }) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Paged mode has no playback: if the mode was switched mid-play, stop the
    // RSVP clock. Only pause when actually playing, so a fresh session's
    // position restore is never clobbered by an early save.
    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) session.pause()
    }

    val text = remember(session, state.chapterIndex) { session.chapterText(state.chapterIndex) }
    val tokens = remember(text) { RsvpEngine.tokenizeWithOffsets(text) }
    val style = MaterialTheme.typography.body1.copy(
        fontSize = wordSize.sp,
        fontFamily = fontFamilyFor(font),
        lineHeight = (wordSize * 1.35f).sp,
    )
    val measurer = rememberTextMeasurer()

    var pages by remember(text, style) { mutableStateOf<List<Page>?>(null) }
    var pageIndex by remember(text, style) { mutableIntStateOf(0) }

    /** Turn to [target], crossing chapters past either end; true if anything moved. */
    fun goTo(target: Int): Boolean {
        val p = pages ?: return false
        return when {
            target > p.lastIndex -> {
                val hasNext = state.chapterIndex + 1 < session.chapterCount
                if (hasNext) session.jumpToChapter(state.chapterIndex + 1)
                hasNext
            }
            target < 0 -> {
                val hasPrevious = state.chapterIndex > 0
                if (hasPrevious) session.jumpToChapter(state.chapterIndex - 1)
                hasPrevious
            }
            else -> {
                pageIndex = target
                session.jumpToWord(p[target].startWord)
                session.persistPosition()
                true
            }
        }
    }

    // External jumps (seek sheet, restore) move wordIndex; follow to its page.
    LaunchedEffect(pages) {
        val p = pages ?: return@LaunchedEffect
        snapshotFlow { state.wordIndex }.collect { idx ->
            val current = p[pageIndex]
            if (idx < current.startWord || idx >= current.endWord.coerceAtLeast(current.startWord + 1)) {
                pageIndex = p.pageFor(idx)
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                if (chromeOpen()) return@onRotaryScrollEvent true
                val steps = rotary.onScroll(event.verticalScrollPixels, SystemClock.uptimeMillis())
                if (steps != 0 && goTo(pageIndex + steps.coerceIn(-1, 1))) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            // Keyed like the pages/pageIndex remembers, so a chapter or style
            // change re-captures goTo — otherwise the tap handler would keep
            // writing to the previous chapter's dead state objects.
            .pointerInput(text, style) {
                detectTapGestures { offset ->
                    if (chromeOpen()) return@detectTapGestures
                    val turned = when {
                        offset.x > size.width * 0.6f -> goTo(pageIndex + 1)
                        offset.x < size.width * 0.4f -> goTo(pageIndex - 1)
                        else -> false // Center tap: no-op.
                    }
                    if (turned) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Round-screen safe content box.
        val boxW = maxWidth - 56.dp
        val boxH = maxHeight - 72.dp
        val boxWPx = with(density) { boxW.roundToPx() }
        val boxHPx = with(density) { boxH.roundToPx() }

        LaunchedEffect(text, style, boxWPx, boxHPx) {
            val result = withContext(Dispatchers.Default) {
                ChapterPaginator(text, tokens, measurer, style, boxWPx, boxHPx).paginate()
            }
            pages = result
            pageIndex = result.pageFor(session.state.value.wordIndex)
        }

        val p = pages
        val current = p?.getOrNull(pageIndex)
        if (p == null || current == null) {
            Text("Loading…")
        } else {
            Text(
                text = current.text,
                style = style,
                color = MaterialTheme.colors.onBackground,
                modifier = Modifier.width(boxW).height(boxH),
            )
            Text(
                text = "${pageIndex + 1} / ${p.size}",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            )
        }
    }
}
