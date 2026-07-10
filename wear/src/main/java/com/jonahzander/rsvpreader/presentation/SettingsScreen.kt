package com.jonahzander.rsvpreader.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.InlineSliderDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.jonahzander.rsvpreader.core.sync.ReaderSettings

private val FONT_OPTIONS = listOf("default", "serif", "mono")

@Composable
fun SettingsScreen(
    settings: ReaderSettings,
    wordSize: Int,
    readingMode: ReadingMode,
    onWpmChange: (Int) -> Unit,
    onFontChange: (String) -> Unit,
    onWordSizeChange: (Int) -> Unit,
    onReadingModeChange: (ReadingMode) -> Unit,
) {
    val listState = rememberScalingLazyListState()
    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Settings") } }
            item { Text("Speed: ${settings.wpm} wpm") }
            item {
                InlineSlider(
                    value = settings.wpm.toFloat(),
                    onValueChange = { onWpmChange(it.toInt()) },
                    valueRange = 100f..900f,
                    steps = 31,
                    decreaseIcon = { Text("−") },
                    increaseIcon = { Text("+") },
                    colors = InlineSliderDefaults.colors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { ListHeader { Text("Word size") } }
            item { Text("Aa  $wordSize", fontSize = wordSize.sp, fontFamily = fontFamilyFor(settings.font)) }
            item {
                InlineSlider(
                    value = wordSize.toFloat(),
                    onValueChange = { onWordSizeChange(it.roundToInt()) },
                    valueRange = 20f..36f,
                    steps = 7, // 2sp increments
                    decreaseIcon = { Text("−") },
                    increaseIcon = { Text("+") },
                    colors = InlineSliderDefaults.colors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { ListHeader { Text("Reading mode") } }
            ReadingMode.entries.forEach { option ->
                item {
                    Chip(
                        onClick = { onReadingModeChange(option) },
                        label = { Text(option.label) },
                        colors = if (option == readingMode) ChipDefaults.primaryChipColors()
                        else ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item { ListHeader { Text("Font") } }
            FONT_OPTIONS.forEach { option ->
                item {
                    Chip(
                        onClick = { onFontChange(option) },
                        label = { Text(option.replaceFirstChar { it.uppercase() }) },
                        colors = if (option == settings.font) ChipDefaults.primaryChipColors()
                        else ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
