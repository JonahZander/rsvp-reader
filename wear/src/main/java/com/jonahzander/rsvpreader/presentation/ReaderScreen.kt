package com.jonahzander.rsvpreader.presentation

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.InlineSliderDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.jonahzander.rsvpreader.core.RotaryAccumulator
import com.jonahzander.rsvpreader.core.RsvpEngine
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private val OrpColor = Color(0xFFE5484D)

fun fontFamilyFor(name: String): FontFamily = when (name) {
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    else -> FontFamily.Default
}

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    font: String,
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
    ReaderContent(current, font, onScrubbingChanged, onOpenChapters, onOpenLibrary, onOpenSettings, onExit)
}

@Composable
private fun ReaderContent(
    session: ReaderSession,
    font: String,
    onScrubbingChanged: (Boolean) -> Unit,
    onOpenChapters: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
) {
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

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val rotary = remember(density) { RotaryAccumulator(thresholdPx = with(density) { 48.dp.toPx() }) }

    // Scrub mode is sticky: entered by long-press, left only by a tap (which resumes reading).
    var scrubbing by remember { mutableStateOf(false) }
    // True while the finger that entered scrub mode is still down, so its release isn't a "tap".
    var longPressGestureActive by remember { mutableStateOf(false) }

    LaunchedEffect(scrubbing) { onScrubbingChanged(scrubbing) }
    // Never leave the nav host with swipe-back stuck off.
    DisposableEffect(Unit) { onDispose { onScrubbingChanged(false) } }

    val scrubListState = rememberLazyListState()
    val centeredIndex by remember { derivedStateOf { centeredItemIndex(scrubListState) } }
    val chapterWords = remember(session, state.chapterIndex) { session.wordsOf(state.chapterIndex) }

    // Entering scrub mode (or crossing a chapter boundary while in it): start on the current word.
    LaunchedEffect(scrubbing, state.chapterIndex) {
        if (scrubbing) scrubListState.centerOnWord(session.state.value.wordIndex)
    }

    // While scrubbing, the word nearest the screen center IS the reading position.
    LaunchedEffect(scrubbing) {
        if (scrubbing) {
            snapshotFlow { centeredItemIndex(scrubListState) }
                .collect { session.jumpToWord(it) }
        }
    }

    // Persist once a drag/fling settles.
    LaunchedEffect(scrubbing) {
        if (scrubbing) {
            snapshotFlow { scrubListState.isScrollInProgress }
                .filter { !it }
                .collect { session.persistPosition() }
        }
    }

    // Seek sheet slides with the finger: offset fraction 1 = hidden below, 0 = fully open.
    var sheetVisible by remember { mutableStateOf(false) }
    val sheetOffset = remember { Animatable(1f) }

    // Menu slides in from the top: offset 1 = hidden above, 0 = fully open.
    var menuVisible by remember { mutableStateOf(false) }
    val menuOffset = remember { Animatable(1f) }

    fun dismissSheet() {
        scope.launch {
            sheetOffset.animateTo(1f, tween(200))
            sheetVisible = false
        }
    }

    /** After the finger lifts, snap the sheet fully open or dismiss it. */
    fun settleSheet() {
        scope.launch {
            if (sheetOffset.value < 0.5f) {
                sheetOffset.animateTo(0f, tween(200))
            } else {
                sheetOffset.animateTo(1f, tween(200))
                sheetVisible = false
            }
        }
    }

    fun dismissMenu() {
        scope.launch {
            menuOffset.animateTo(1f, tween(200))
            menuVisible = false
        }
    }

    /** After the finger lifts, snap the menu fully open or dismiss it. */
    fun settleMenu() {
        scope.launch {
            if (menuOffset.value < 0.5f) {
                menuOffset.animateTo(0f, tween(200))
            } else {
                menuOffset.animateTo(1f, tween(200))
                menuVisible = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                if (sheetVisible || menuVisible) return@onRotaryScrollEvent true
                val steps = rotary.onScroll(event.verticalScrollPixels, SystemClock.uptimeMillis())
                if (steps != 0) {
                    repeat(kotlin.math.abs(steps)) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    if (state.isPlaying) {
                        session.adjustWpm(steps * 10)
                        showOverlay("${session.state.value.wpm} wpm")
                    } else if (scrubbing) {
                        scope.launch {
                            val target = (centeredItemIndex(scrubListState) + steps)
                                .coerceIn(0, (chapterWords.size - 1).coerceAtLeast(0))
                            scrubListState.centerOnWord(target)
                        }
                        showOverlay("${session.state.value.wordIndex + 1} / ${state.chapterWordCount}")
                    } else {
                        session.scrub(steps)
                        showOverlay("${session.state.value.wordIndex + 1} / ${state.chapterWordCount}")
                    }
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(session) {
                // Long-press enters scrub mode; keeping the finger down drags the line directly.
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        if (!sheetVisible && !menuVisible) {
                            if (!scrubbing) {
                                session.pause()
                                scrubbing = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            longPressGestureActive = true
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (longPressGestureActive) {
                            change.consume()
                            scrubListState.dispatchRawDelta(-dragAmount.x)
                        }
                    },
                    onDragEnd = { longPressGestureActive = false },
                    onDragCancel = { longPressGestureActive = false },
                )
            }
            .pointerInput(session) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        when {
                            sheetVisible -> {
                                // Sheet follows the finger in both directions.
                                change.consume()
                                val target = (sheetOffset.value + dragAmount / height).coerceIn(0f, 1f)
                                scope.launch { sheetOffset.snapTo(target) }
                            }
                            menuVisible -> {
                                // Menu follows the finger: dragging down opens, up hides.
                                change.consume()
                                val target = (menuOffset.value - dragAmount / height).coerceIn(0f, 1f)
                                scope.launch { menuOffset.snapTo(target) }
                            }
                            dragAmount < -6f -> {
                                // Upward pull drags the seek sheet in from the bottom.
                                change.consume()
                                session.pause()
                                sheetVisible = true
                                scope.launch { sheetOffset.snapTo((1f + dragAmount / height).coerceIn(0f, 1f)) }
                            }
                            dragAmount > 6f -> {
                                // Downward pull drags the menu in from the top.
                                change.consume()
                                session.pause()
                                menuVisible = true
                                scope.launch { menuOffset.snapTo((1f - dragAmount / height).coerceIn(0f, 1f)) }
                            }
                        }
                    },
                    onDragEnd = {
                        if (sheetVisible) settleSheet()
                        if (menuVisible) settleMenu()
                    },
                    onDragCancel = {
                        if (sheetVisible) settleSheet()
                        if (menuVisible) settleMenu()
                    },
                )
            }
            .clickable {
                if (!sheetVisible && !menuVisible && !longPressGestureActive) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (scrubbing) scrubbing = false
                    session.togglePlay()
                }
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
            if (scrubbing) {
                ScrubLine(
                    words = chapterWords,
                    listState = scrubListState,
                    centeredIndex = centeredIndex,
                    font = font,
                )
            } else {
                OrpWord(word = state.word, orpIndex = state.orpIndex, font = font)
            }
            if (!state.isPlaying) {
                Text(
                    text = when {
                        scrubbing -> "Swipe to move — tap to read from here"
                        state.finished -> "Finished — tap to restart chapter"
                        else -> "Paused — tap to play"
                    },
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

        if (sheetVisible) {
            SeekSheet(
                wordIndex = state.wordIndex,
                wordCount = state.chapterWordCount,
                onSeek = { fraction -> session.seekToFraction(fraction) },
                onDismiss = { dismissSheet() },
                modifier = Modifier.graphicsLayer { translationY = sheetOffset.value * size.height },
            )
        }

        if (menuVisible) {
            ReaderMenu(
                onChapters = onOpenChapters,
                onLibrary = onOpenLibrary,
                onSettings = onOpenSettings,
                onExit = onExit,
                onDismiss = { dismissMenu() },
                modifier = Modifier.graphicsLayer { translationY = -menuOffset.value * size.height },
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

/**
 * The chapter as one continuous, fling-scrollable line of text. Words clip
 * naturally at the screen edges; the word nearest the center is emphasized
 * and drives the reading position.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScrubLine(
    words: List<String>,
    listState: LazyListState,
    centeredIndex: Int,
    font: String,
) {
    val family = fontFamilyFor(font)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val sidePadding = maxWidth / 2
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(horizontal = sidePadding),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(words) { index, word ->
                val centered = index == centeredIndex
                val text = if (centered && word.isNotEmpty()) {
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
                    fontFamily = family,
                    fontSize = 18.sp,
                    fontWeight = if (centered) FontWeight.Medium else FontWeight.Normal,
                    color = if (centered) MaterialTheme.colors.onBackground
                    else MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
            }
        }
    }
}

/** Index of the visible word whose center is closest to the viewport center. */
private fun centeredItemIndex(listState: LazyListState): Int {
    val info = listState.layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: 0
}

/** Scroll so the word at [index] sits exactly at the viewport center. */
private suspend fun LazyListState.centerOnWord(index: Int) {
    scrollToItem(index)
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    scrollBy(item.offset + item.size / 2f - center)
}

@Composable
private fun ReaderMenu(
    onChapters: () -> Unit,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Composable
    fun MenuChip(label: String, onClick: () -> Unit) {
        CompactChip(
            onClick = onClick,
            label = { Text(label) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth(0.7f),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() }, // tap outside the chips closes the menu
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        MenuChip("Chapters", onChapters)
        MenuChip("Library", onLibrary)
        MenuChip("Settings", onSettings)
        MenuChip("Exit reader", onExit)
    }
}

@Composable
private fun SeekSheet(
    wordIndex: Int,
    wordCount: Int,
    onSeek: (Double) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = if (wordCount <= 1) 0f else wordIndex.toFloat() / (wordCount - 1)
    var fraction by remember { mutableFloatStateOf(initial) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {}, // swallow taps under the sheet
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("${(fraction * 100).roundToInt()} %", style = MaterialTheme.typography.title2)
        Text(
            "word ${((fraction * (wordCount - 1).coerceAtLeast(0)).roundToInt() + 1)} of ${wordCount.coerceAtLeast(1)}",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
        )
        InlineSlider(
            value = fraction,
            onValueChange = { fraction = it },
            valueRange = 0f..1f,
            steps = 19, // 5% increments
            decreaseIcon = { Text("−") },
            increaseIcon = { Text("+") },
            colors = InlineSliderDefaults.colors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Row {
            Chip(
                onClick = onDismiss,
                label = { Text("Cancel") },
                colors = ChipDefaults.secondaryChipColors(),
            )
            Spacer(Modifier.width(8.dp))
            Chip(
                onClick = {
                    onSeek(fraction.toDouble())
                    onDismiss()
                },
                label = { Text("Go") },
                colors = ChipDefaults.primaryChipColors(),
            )
        }
    }
}
