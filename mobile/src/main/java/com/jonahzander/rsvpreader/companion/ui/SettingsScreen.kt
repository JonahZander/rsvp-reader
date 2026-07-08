package com.jonahzander.rsvpreader.companion.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jonahzander.rsvpreader.core.sync.ReaderSettings

private val FONT_OPTIONS = listOf("default", "serif", "mono")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: ReaderSettings,
    onWpmChange: (Int) -> Unit,
    onFontChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reader settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            var sliderWpm by remember(settings.wpm) { mutableFloatStateOf(settings.wpm.toFloat()) }
            Text("Reading speed: ${sliderWpm.toInt()} wpm", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = sliderWpm,
                onValueChange = { sliderWpm = it },
                onValueChangeFinished = { onWpmChange(sliderWpm.toInt()) },
                valueRange = 100f..900f,
                steps = 31,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "Font",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            Row {
                FONT_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = option == settings.font,
                        onClick = { onFontChange(option) },
                        label = { Text(option.replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Text(
                "Settings sync to your watch automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
