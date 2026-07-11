package com.jonahzander.rsvpreader.presentation

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.InlineSliderDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How the in-reader menu is summoned in the current mode. */
enum class MenuTrigger { SwipeDown, LongPress }

/**
 * Transient overlay text shown near the bottom of the reader (e.g. "300 wpm",
 * "42 / 1200"). [text] is null while hidden; [show] displays a message for
 * ~1.2 s, extending the timer on every call.
 */
@Stable
class OverlayTextState {
    private val _text = mutableStateOf<String?>(null)
    val text: State<String?> get() = _text
    internal var until by mutableLongStateOf(0L)
        private set

    fun show(text: String) {
        _text.value = text
        until = SystemClock.uptimeMillis() + 1200
    }

    internal fun hide() {
        _text.value = null
    }
}

@Composable
fun rememberOverlayTextState(): OverlayTextState {
    val overlay = remember { OverlayTextState() }
    LaunchedEffect(overlay.until) {
        if (overlay.until > 0) {
            delay(1300)
            if (SystemClock.uptimeMillis() >= overlay.until) overlay.hide()
        }
    }
    return overlay
}

/**
 * Shared reader shell: circular chapter progress, chapter title, transient
 * overlay text, the swipe-down menu, and (optionally) the pull-up seek sheet.
 * Mode content renders inside; [content] receives `chromeOpen` — true while a
 * sheet or the menu is showing, so content should ignore its own taps — and
 * `openMenu` for modes that summon the menu themselves (e.g. long-press).
 * [seekSheetEnabled] has no effect when [menuTrigger] is [MenuTrigger.LongPress],
 * since the sheet is only reachable via the swipe-down drag handler.
 */
@Composable
fun ReaderChrome(
    session: ReaderSession,
    menuTrigger: MenuTrigger,
    seekSheetEnabled: Boolean,
    onOpenChapters: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    showOverlayText: State<String?>, // null = hidden
    content: @Composable (chromeOpen: () -> Boolean, openMenu: () -> Unit) -> Unit,
) {
    val state by session.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

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

    fun openMenu() {
        session.pause()
        menuVisible = true
        scope.launch { menuOffset.animateTo(0f, tween(200)) }
    }

    val chromeOpen = { sheetVisible || menuVisible }

    var modifier: Modifier = Modifier.fillMaxSize()
    if (menuTrigger == MenuTrigger.SwipeDown) {
        modifier = modifier.pointerInput(session) {
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
                        seekSheetEnabled && dragAmount < -6f -> {
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
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = if (state.chapterWordCount == 0) 0f
            else (state.wordIndex + 1).toFloat() / state.chapterWordCount,
            modifier = Modifier.fillMaxSize().padding(2.dp),
            strokeWidth = 3.dp,
        )

        content(chromeOpen, ::openMenu)

        Text(
            text = state.chapterTitle,
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
        )

        val overlayText = showOverlayText.value
        if (overlayText != null) {
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
            // Truncate like seekToFraction does, so the label names the exact
            // word "Go" lands on.
            "word ${((fraction * (wordCount - 1).coerceAtLeast(0)).toInt() + 1)} of ${wordCount.coerceAtLeast(1)}",
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
