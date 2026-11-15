package com.fiangg20.wavelength.ui

import androidx.compose.runtime.Composable
import com.fiangg20.wavelength.data.Prefs
import kotlin.math.roundToInt

/** Every lyric look-and-feel control in one place, used by Settings and by the quick panel in the lyrics sheet. */
@Composable
fun LyricsStyleControls(prefs: Prefs, onChange: ((Prefs) -> Prefs) -> Unit) {
    Label("Word highlight")
    ConnectedChoice(
        options = listOf("Off", "Words", "Real only"),
        selected = prefs.lyricsWordMode,
        onSelect = { v -> onChange { it.copy(lyricsWordMode = v) } },
    )
    Hint(
        when (prefs.lyricsWordMode) {
            0 -> "Only the whole line lights up."
            1 -> "Words fill in as they are sung. Songs without word timing use an estimate spread over each line."
            else -> "Only songs that carry real word timing (an enhanced .lrc) get word highlighting."
        }
    )
    Gap()
    Label("Alignment")
    ConnectedChoice(
        options = listOf("Left", "Centre", "Right"),
        selected = prefs.lyricsAlign,
        onSelect = { v -> onChange { it.copy(lyricsAlign = v) } },
    )
    Gap()
    Label("Highlight colour")
    ConnectedChoice(
        options = listOf("Accent", "Neutral", "Tertiary"),
        selected = prefs.lyricsColor,
        onSelect = { v -> onChange { it.copy(lyricsColor = v) } },
    )
    SliderRow(
        title = "Text size",
        valueText = "${(prefs.lyricsSize * 100).roundToInt()}%",
        value = prefs.lyricsSize,
        range = 0.7f..1.6f,
        onChange = { v -> onChange { it.copy(lyricsSize = v) } },
    )
    SliderRow(
        title = "Line spacing",
        valueText = "${(prefs.lyricsSpacing * 100).roundToInt()}%",
        value = prefs.lyricsSpacing,
        range = 0.5f..2f,
        onChange = { v -> onChange { it.copy(lyricsSpacing = v) } },
    )
    SliderRow(
        title = "Other lines brightness",
        valueText = "${(prefs.lyricsDim * 100).roundToInt()}%",
        value = prefs.lyricsDim,
        range = 0.15f..0.8f,
        onChange = { v -> onChange { it.copy(lyricsDim = v) } },
    )
    SwitchRow(
        title = "Bold playing line",
        subtitle = null,
        checked = prefs.lyricsBold,
        onChecked = { v -> onChange { it.copy(lyricsBold = v) } },
    )
    SwitchRow(
        title = "Zoom playing line",
        subtitle = "The line being sung is slightly larger",
        checked = prefs.lyricsZoom,
        onChecked = { v -> onChange { it.copy(lyricsZoom = v) } },
    )
}
