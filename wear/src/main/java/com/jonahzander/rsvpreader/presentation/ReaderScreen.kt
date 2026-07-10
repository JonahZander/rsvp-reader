package com.jonahzander.rsvpreader.presentation

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.jonahzander.rsvpreader.core.RotaryAccumulator
import com.jonahzander.rsvpreader.core.RsvpEngine
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private val OrpColor = Color(0xFFE5484D)

/** The ORP letter is pinned slightly left of screen center, where the eye naturally fixates. */
private val OrpLeftShift = 16.dp

/** Horizontal padding around each word in the line (half the inter-word gap). */
private val WordSpacing = 3.dp

fun fontFamilyFor(name: String): FontFamily = when (name) {
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    else -> FontFamily.Default
}

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    font: String,
    wordSize: Int,
    mode: ReadingMode,
    onScrubbingChanged: (Boolean) -> Unit,
    onOpenChapters: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val current = session
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading…")
        }
        return
    }
    when (mode) {
        // Scroll (Task 7) falls back to the word line until implemented.
        ReadingMode.Rsvp, ReadingMode.Line, ReadingMode.Scroll -> {
            val overlay = rememberOverlayTextState()
            ReaderChrome(
                session = current,
                menuTrigger = MenuTrigger.SwipeDown,
                seekSheetEnabled = true,
                onOpenChapters = onOpenChapters,
                onOpenLibrary = onOpenLibrary,
                onOpenSettings = onOpenSettings,
                onExit = onExit,
                showOverlayText = overlay.text,
            ) { chromeOpen, _ ->
                WordLineReader(
                    session = current,
                    font = font,
                    wordSize = wordSize,
                    glide = mode == ReadingMode.Line,
                    onScrubbingChanged = onScrubbingChanged,
                    chromeOpen = chromeOpen,
                    showOverlay = overlay::show,
                )
            }
        }
        ReadingMode.Paged -> {
            val overlay = rememberOverlayTextState()
            // Paged has no swipeable line, so swipe-back stays enabled throughout.
            LaunchedEffect(Unit) { onScrubbingChanged(false) }
            ReaderChrome(
                session = current,
                menuTrigger = MenuTrigger.SwipeDown,
                seekSheetEnabled = true,
                onOpenChapters = onOpenChapters,
                onOpenLibrary = onOpenLibrary,
                onOpenSettings = onOpenSettings,
                onExit = onExit,
                showOverlayText = overlay.text,
            ) { chromeOpen, _ ->
                PagedReader(
                    session = current,
                    font = font,
                    wordSize = wordSize,
                    chromeOpen = chromeOpen,
                )
            }
        }
    }
}

/**
 * The reader is a single word line in both states. While playing, only the
 * current word is visible (neighbors at alpha 0) with its ORP letter pinned
 * to a fixed point; pausing fades the surrounding words in around it, so the
 * transition between reading and browsing never moves the current word.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WordLineReader(
    session: ReaderSession,
    font: String,
    wordSize: Int,
    glide: Boolean,
    onScrubbingChanged: (Boolean) -> Unit,
    chromeOpen: () -> Boolean,
    showOverlay: (String) -> Unit,
) {
    val state by session.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val focusRequester = remember { FocusRequester() }

    // Keep the screen on only while words are streaming
    LaunchedEffect(state.isPlaying) {
        view.keepScreenOn = state.isPlaying
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val rotary = remember(density) { RotaryAccumulator(thresholdPx = with(density) { 48.dp.toPx() }) }

    // Debounces the snap-to-pin after the crown stops turning.
    var rotarySettleJob by remember { mutableStateOf<Job?>(null) }

    // Paused = the line is swipeable, so the horizontal gestures must not leave the reader.
    LaunchedEffect(state.isPlaying) { onScrubbingChanged(!state.isPlaying) }
    // Never leave the nav host with swipe-back stuck off.
    DisposableEffect(Unit) { onDispose { onScrubbingChanged(false) } }
    // On Wear OS 4+ the edge swipe arrives as a system back gesture, which the
    // nav controller would pop regardless of userSwipeEnabled — swallow it too.
    BackHandler(enabled = !state.isPlaying) {}

    val listState = rememberLazyListState()
    val chapterWords = remember(session, state.chapterIndex) { session.wordsOf(state.chapterIndex) }

    val family = fontFamilyFor(font)
    val wordStyle = MaterialTheme.typography.title1.copy(
        fontWeight = FontWeight.Medium,
        fontSize = wordSize.sp,
        fontFamily = family,
    )
    val textMeasurer = rememberTextMeasurer()
    val orpShiftPx = with(density) { OrpLeftShift.toPx() }
    val metrics = remember(chapterWords, wordStyle, textMeasurer, density) {
        WordPinMetrics(chapterWords, textMeasurer, wordStyle, with(density) { WordSpacing.toPx() })
    }
    val pinnedIndex by remember(metrics) {
        derivedStateOf { pinnedWordIndex(listState, metrics, orpShiftPx) }
    }

    // Fling snaps so the settled word's ORP letter lands exactly on the pin.
    val snapPosition = remember(metrics, orpShiftPx) {
        object : SnapPosition {
            override fun position(
                layoutSize: Int,
                itemSize: Int,
                beforeContentPadding: Int,
                afterContentPadding: Int,
                itemIndex: Int,
                itemCount: Int,
            ): Int = SnapPosition.Center.position(
                layoutSize, itemSize, beforeContentPadding, afterContentPadding, itemIndex, itemCount,
            ) + (itemSize / 2f - metrics.orpCenter(itemIndex) - orpShiftPx).roundToInt()
        }
    }
    val flingBehavior = rememberSnapFlingBehavior(listState, snapPosition)

    // Playback drives the line: RSVP snaps each new word's ORP letter onto the
    // pin instantly; Line mode glides there at a linear pace lasting exactly
    // that word's RSVP duration, giving a continuous teleprompter crawl.
    LaunchedEffect(state.isPlaying, metrics, glide) {
        if (state.isPlaying) {
            // snapshotFlow conflates: a word missed while a glide is in flight is
            // skipped, and the next glide simply catches up from wherever it is.
            snapshotFlow { state.wordIndex }.collect { index ->
                if (glide) {
                    val duration = RsvpEngine
                        .durationMs(chapterWords.getOrElse(index) { "" }, session.state.value.wpm)
                        .toInt()
                    listState.glideToWord(index, metrics, orpShiftPx, duration)
                } else {
                    listState.pinWord(index, metrics, orpShiftPx, animate = false)
                }
            }
        }
    }

    // While paused, the pinned word IS the reading position…
    LaunchedEffect(state.isPlaying, metrics) {
        if (!state.isPlaying) {
            snapshotFlow { pinnedWordIndex(listState, metrics, orpShiftPx) }
                .collect { session.jumpToWord(it) }
        }
    }

    // …external jumps (position restore, seek sheet) move the line to match…
    LaunchedEffect(state.isPlaying, metrics) {
        if (!state.isPlaying) {
            listState.pinWord(session.state.value.wordIndex, metrics, orpShiftPx, animate = false)
            snapshotFlow { state.wordIndex }.collect { index ->
                if (!listState.isScrollInProgress &&
                    pinnedWordIndex(listState, metrics, orpShiftPx) != index
                ) {
                    listState.pinWord(index, metrics, orpShiftPx, animate = false)
                }
            }
        }
    }

    // …and the position is persisted whenever a drag/fling settles.
    LaunchedEffect(state.isPlaying) {
        if (!state.isPlaying) {
            snapshotFlow { listState.isScrollInProgress }
                .filter { !it }
                .collect { session.persistPosition() }
        }
    }

    // Neighbors fade in when pausing and out when reading resumes — except in
    // Line mode, where they stay visible throughout the glide.
    val neighborAlpha by animateFloatAsState(
        targetValue = if (state.isPlaying && !glide) 0f else 1f,
        animationSpec = tween(250),
        label = "neighborAlpha",
    )

    Box(
        Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                if (chromeOpen()) return@onRotaryScrollEvent true
                if (!state.isPlaying) {
                    // Crown scrolls the line 1:1 like a finger drag; once the
                    // crown rests, glide the nearest word onto the pin.
                    scope.launch { listState.scrollBy(event.verticalScrollPixels) }
                    rotarySettleJob?.cancel()
                    rotarySettleJob = scope.launch {
                        delay(180)
                        listState.pinWord(
                            pinnedWordIndex(listState, metrics, orpShiftPx),
                            metrics,
                            orpShiftPx,
                            animate = true,
                        )
                    }
                    showOverlay("${session.state.value.wordIndex + 1} / ${state.chapterWordCount}")
                    return@onRotaryScrollEvent true
                }
                val steps = rotary.onScroll(event.verticalScrollPixels, SystemClock.uptimeMillis())
                if (steps != 0) {
                    repeat(abs(steps)) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    session.adjustWpm(steps * 10)
                    showOverlay("${session.state.value.wpm} wpm")
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .clickable {
                if (!chromeOpen()) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // Playing → pause into the browsable line; paused → read on
                    // from the pinned word.
                    session.togglePlay()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        WordLine(
            words = chapterWords,
            listState = listState,
            pinnedIndex = pinnedIndex,
            style = wordStyle,
            neighborAlpha = neighborAlpha,
            flingBehavior = flingBehavior,
            scrollEnabled = !state.isPlaying,
        )

        if (!state.isPlaying) {
            Text(
                text = if (state.finished) "Finished — tap to restart chapter"
                else "Swipe to move — tap to read from here",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                // Anchored below the line without affecting its position.
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (wordSize + 26).dp),
            )
        }
    }
}

/**
 * The chapter as one continuous line of text. Words clip naturally at the
 * screen edges. The pinned word is always fully visible with its ORP letter
 * highlighted; the surrounding words render at [neighborAlpha] so they can
 * fade away entirely while playing.
 */
@Composable
private fun WordLine(
    words: List<String>,
    listState: LazyListState,
    pinnedIndex: Int,
    style: TextStyle,
    neighborAlpha: Float,
    flingBehavior: FlingBehavior,
    scrollEnabled: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val sidePadding = maxWidth / 2
        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            userScrollEnabled = scrollEnabled,
            contentPadding = PaddingValues(horizontal = sidePadding),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(words) { index, word ->
                val pinned = index == pinnedIndex
                val text = if (pinned && word.isNotEmpty()) {
                    buildAnnotatedString {
                        append(word)
                        val orp = RsvpEngine.orpIndex(word).coerceIn(0, word.length - 1)
                        addStyle(SpanStyle(color = OrpColor), orp, orp + 1)
                    }
                } else {
                    AnnotatedString(word)
                }
                Text(
                    text = text,
                    maxLines = 1,
                    style = style,
                    color = if (pinned) MaterialTheme.colors.onBackground
                    else MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.6f * neighborAlpha),
                    modifier = Modifier.padding(horizontal = WordSpacing),
                )
            }
        }
    }
}

/**
 * Measures where each word's ORP letter sits within its list item, so the
 * line can be aligned with the letter — not the word's bounding box — on the
 * fixed pin point. Results are memoized per word index.
 */
private class WordPinMetrics(
    private val words: List<String>,
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val itemPaddingPx: Float,
) {
    val size: Int get() = words.size
    private val cache = HashMap<Int, Float>()

    /** X of the ORP letter's center, measured from the item's left edge. */
    fun orpCenter(index: Int): Float = cache.getOrPut(index) {
        val word = words.getOrNull(index).orEmpty()
        if (word.isEmpty()) return@getOrPut itemPaddingPx
        val orp = RsvpEngine.orpIndex(word).coerceIn(0, word.length - 1)
        val box = measurer.measure(AnnotatedString(word), style, maxLines = 1).getBoundingBox(orp)
        itemPaddingPx + (box.left + box.right) / 2f
    }
}

/** The fixed X the ORP letter is pinned to, in the list's layout coordinates. */
private fun wordPin(listState: LazyListState, orpShiftPx: Float): Float {
    val info = listState.layoutInfo
    return (info.viewportStartOffset + info.viewportEndOffset) / 2f - orpShiftPx
}

/** Index of the visible word whose ORP letter is closest to the pin. */
private fun pinnedWordIndex(listState: LazyListState, metrics: WordPinMetrics, orpShiftPx: Float): Int {
    val pin = wordPin(listState, orpShiftPx)
    return listState.layoutInfo.visibleItemsInfo
        .minByOrNull { abs(it.offset + metrics.orpCenter(it.index) - pin) }?.index ?: 0
}

/** Scroll delta that puts the visible word's ORP letter on the pin, or null if not laid out. */
private fun LazyListState.deltaToPin(index: Int, metrics: WordPinMetrics, orpShiftPx: Float): Float? {
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
    return item.offset + metrics.orpCenter(index) - wordPin(this, orpShiftPx)
}

/** Scroll so the word's ORP letter sits exactly on the pin. */
private suspend fun LazyListState.pinWord(
    index: Int,
    metrics: WordPinMetrics,
    orpShiftPx: Float,
    animate: Boolean,
) {
    if (index !in 0 until metrics.size) return
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
    val delta = deltaToPin(index, metrics, orpShiftPx) ?: return
    if (animate) animateScrollBy(delta) else scrollBy(delta)
}

/** Glides the word onto the pin over [durationMs] — the teleprompter crawl of Line mode. */
private suspend fun LazyListState.glideToWord(
    index: Int,
    metrics: WordPinMetrics,
    orpShiftPx: Float,
    durationMs: Int,
) {
    val delta = deltaToPin(index, metrics, orpShiftPx)
    if (delta == null) {
        pinWord(index, metrics, orpShiftPx, animate = false)
        return
    }
    animateScrollBy(delta, tween(durationMs.coerceAtLeast(1), easing = LinearEasing))
}
