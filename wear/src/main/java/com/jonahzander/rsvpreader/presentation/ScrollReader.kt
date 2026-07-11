package com.jonahzander.rsvpreader.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.jonahzander.rsvpreader.core.RsvpEngine
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/**
 * Scroll mode: the chapter as one vertical column of text that auto-scrolls
 * upward at a speed derived from the wpm and the measured average words per
 * line. Tap toggles auto-scroll; dragging or the crown stops it (tap resumes).
 * The reading position is the first word of the topmost fully visible line,
 * saved whenever scrolling settles and on pause. The menu opens via
 * long-press — vertical gestures belong to scrolling — and there is no seek
 * sheet (scrolling *is* seeking). The session is used purely as the position
 * holder; the RSVP clock never runs here.
 */
@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun ScrollReader(
    session: ReaderSession,
    font: String,
    wordSize: Int,
    chromeOpen: () -> Boolean,
    openMenu: () -> Unit,
) {
    val state by session.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    // onRotaryScrollEvent only fires on the focused node. Pushed destinations
    // (Settings, Chapters) keep this reader composed beneath while their own
    // lists take focus, so a one-shot focus request left the crown dead after
    // returning; the active-focus requester re-requests whenever this
    // destination becomes the foreground entry again. Also reclaim focus when
    // the chrome (menu) closes, in case its controls took it.
    val focusRequester = rememberActiveFocusRequester()
    LaunchedEffect(Unit) {
        snapshotFlow { chromeOpen() }
            .drop(1)
            .filter { !it }
            .collect { focusRequester.requestFocus() }
    }

    // Scroll mode has no RSVP playback: if the mode was switched mid-play,
    // stop the clock. Only pause when actually playing, so a fresh session's
    // async position restore is never clobbered by an early save.
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

    // Everything positional is keyed on the chapter text, so a chapter change
    // resets the column to the top, stops auto-scroll, and drops stale layout.
    val scrollState = remember(text) { ScrollState(0) }
    var layout by remember(text, style) { mutableStateOf<TextLayoutResult?>(null) }
    var autoScrolling by remember(text) { mutableStateOf(false) }

    // The text sits below the column's own top padding, so layout-space y and
    // scroll-space y differ by this amount.
    val topPadPx = with(density) { VerticalPadding.roundToPx() }

    /** Word index of the first word on the topmost fully visible line. */
    fun topWordIndex(l: TextLayoutResult): Int {
        if (tokens.isEmpty() || l.lineCount == 0) return 0
        val y = (scrollState.value - topPadPx).coerceAtLeast(0).toFloat()
        var line = l.getLineForVerticalPosition(y)
        // The line at the viewport edge may be partially cut off; the spec
        // wants the first FULLY visible line.
        if (l.getLineTop(line) < y && line + 1 < l.lineCount) line++
        return RsvpEngine.wordIndexAt(tokens, l.getLineStart(line))
    }

    fun saveTopWord() {
        val l = layout ?: return
        session.jumpToWord(topWordIndex(l))
        session.persistPosition()
    }

    // Backgrounding mid-auto-scroll (crown/home press): stop the crawl and
    // save the top word. The cancelled auto-scroll effect never reaches its
    // own post-loop save, and a frame-clocked animateScrollBy would resume
    // the moment the activity returns, so the motion must be stopped here.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        autoScrolling = false
        saveTopWord()
    }

    // Keep the screen on only while auto-scrolling — and never leak the flag
    // when the composable goes away mid-scroll.
    DisposableEffect(autoScrolling) {
        view.keepScreenOn = autoScrolling
        onDispose { view.keepScreenOn = false }
    }

    // External jumps (the session's async cold-open restore, chapter jumps)
    // move wordIndex; scroll its line to the top of the viewport. After our
    // own saves wordIndex IS the top word, so this never fights user scrolls.
    LaunchedEffect(layout) {
        val l = layout ?: return@LaunchedEffect
        snapshotFlow { state.wordIndex }.collect { idx ->
            if (!autoScrolling && !scrollState.isScrollInProgress && idx != topWordIndex(l)) {
                val offset = tokens.getOrNull(idx)?.startOffset ?: 0
                val line = l.getLineForOffset(offset.coerceAtMost((text.length - 1).coerceAtLeast(0)))
                // Truncate, never round up: landing a fraction of a pixel past
                // the line top would make topWordIndex skip to the next line,
                // creeping the position forward on every reopen.
                scrollState.scrollTo((l.getLineTop(line) + topPadPx).toInt())
            }
        }
    }

    // Auto-scroll: one line per (avg words per line / wpm) minutes, linearly.
    // The wpm is read once when auto-scroll starts; a mid-scroll change
    // applies on the next toggle.
    LaunchedEffect(autoScrolling, layout) {
        val l = layout ?: return@LaunchedEffect
        if (!autoScrolling) return@LaunchedEffect
        if (tokens.isNotEmpty() && l.lineCount > 0) {
            val wordsPerLine = (tokens.size.toFloat() / l.lineCount).coerceAtLeast(1f)
            val msPerLine = (wordsPerLine * 60_000f / session.state.value.wpm)
                .roundToInt().coerceAtLeast(1)
            val lineH = (l.size.height.toFloat() / l.lineCount).coerceAtLeast(1f)
            while (scrollState.value < scrollState.maxValue) {
                scrollState.animateScrollBy(lineH, tween(msPerLine, easing = LinearEasing))
                // Keep the session index current (no persist) so leaving
                // mid-scroll — menu, swipe-back, dispose — saves the words
                // actually read, not the pre-scroll position. The restore
                // follower ignores these: it is gated on !autoScrolling.
                session.jumpToWord(topWordIndex(l))
            }
        }
        autoScrolling = false // reached the end of the chapter (or nothing to scroll)
        saveTopWord()
    }

    // Any user drag stops auto-scroll. The programmatic animateScrollBy acts
    // on the ScrollState directly and bypasses nested-scroll dispatch
    // entirely, so it can never cancel itself here.
    val stopOnUserScroll = remember(text) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) autoScrolling = false
                return Offset.Zero
            }
        }
    }

    // Persist once the column has been at rest for a moment. Watching the
    // value as well as isScrollInProgress covers crown motion, whose raw
    // deltas never set isScrollInProgress; collectLatest restarts the timer
    // on every movement, debouncing per-event saves into one DataStore write.
    // drop(1) skips the initial at-rest emission, so composing never writes
    // word 0 over a position the session is still restoring.
    LaunchedEffect(layout) {
        if (layout == null) return@LaunchedEffect
        snapshotFlow { scrollState.value to scrollState.isScrollInProgress }
            .drop(1)
            .collectLatest { (_, inProgress) ->
                // Keep the session index current on every movement (no
                // persist), mirroring the auto-scroll loop: swipe-back
                // mid-fling or mid-debounce is an in-app nav pop (no ON_STOP),
                // so only the teardown save runs — it must see the words
                // actually read, not the pre-gesture position.
                if (!autoScrolling) layout?.let { session.jumpToWord(topWordIndex(it)) }
                if (!inProgress) {
                    delay(250)
                    if (!autoScrolling) saveTopWord()
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                if (chromeOpen()) return@onRotaryScrollEvent true
                autoScrolling = false
                // Synchronous raw delta (no coroutine per event); it bypasses
                // isScrollInProgress, which is why the settle-save watches the
                // scroll value too.
                scrollState.dispatchRawDelta(event.verticalScrollPixels)
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .nestedScroll(stopOnUserScroll)
            .verticalScroll(scrollState, enabled = !chromeOpen())
            // Keyed like the positional remembers, so a chapter change
            // re-captures the handlers — otherwise they would keep writing to
            // the previous chapter's dead state objects.
            .pointerInput(text) {
                detectTapGestures(
                    onTap = {
                        if (!chromeOpen()) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            autoScrolling = !autoScrolling
                            if (!autoScrolling) saveTopWord()
                        }
                    },
                    onLongPress = {
                        if (!chromeOpen()) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            autoScrolling = false
                            // Save here: the cancelled auto-scroll effect never
                            // reaches its own post-loop save.
                            saveTopWord()
                            openMenu()
                        }
                    },
                )
            }
            .padding(horizontal = HorizontalPadding, vertical = VerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = style,
            color = MaterialTheme.colors.onBackground,
            onTextLayout = { layout = it },
        )
        if (state.chapterIndex < session.chapterCount - 1) {
            CompactChip(
                onClick = { session.jumpToChapter(state.chapterIndex + 1) },
                label = { Text("Next chapter") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/** Round-screen-safe insets around the scrolling column. */
private val HorizontalPadding = 28.dp
private val VerticalPadding = 36.dp
