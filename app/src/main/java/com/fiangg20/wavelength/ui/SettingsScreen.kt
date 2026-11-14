package com.fiangg20.wavelength.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fiangg20.wavelength.data.Prefs
import java.util.Locale

@Composable
fun SettingsScreen(
    prefs: Prefs,
    onChange: ((Prefs) -> Prefs) -> Unit,
    onBack: () -> Unit,
    bottomPad: Dp,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressiveButton(
                onClick = onBack,
                modifier = Modifier.size(52.dp),
                containerColor = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
                restCorner = 26.dp,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
            }
            Spacer(Modifier.width(16.dp))
            Text("Settings", style = MaterialTheme.typography.displaySmall)
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // ---------------- Appearance ----------------
            SectionTitle("Appearance")
            Group {
                Label("Theme")
                ConnectedChoice(
                    options = listOf("System", "Light", "Dark"),
                    selected = prefs.themeMode,
                    onSelect = { v -> onChange { it.copy(themeMode = v) } },
                )
                Gap()
                SwitchRow(
                    title = "Pure black",
                    subtitle = "True black surfaces in dark theme",
                    checked = prefs.pureBlack,
                    onChecked = { v -> onChange { it.copy(pureBlack = v) } },
                )
                if (Build.VERSION.SDK_INT >= 31) {
                    SwitchRow(
                        title = "Wallpaper colours",
                        subtitle = "Use your system's dynamic palette",
                        checked = prefs.dynamicColor,
                        onChecked = { v -> onChange { it.copy(dynamicColor = v) } },
                    )
                }
            }

            Group {
                Label("Accent")
                HueSwatches(
                    hue = prefs.hue,
                    enabled = !(prefs.dynamicColor && Build.VERSION.SDK_INT >= 31),
                    onPick = { h -> onChange { it.copy(hue = h) } },
                )
                SliderRow(
                    title = "Custom hue",
                    valueText = "${prefs.hue.toInt()}°",
                    value = prefs.hue,
                    range = 0f..359f,
                    onChange = { v -> onChange { it.copy(hue = v) } },
                )
                SliderRow(
                    title = "Vibrancy",
                    valueText = "${(prefs.vibrancy * 100).toInt()}%",
                    value = prefs.vibrancy,
                    range = 0.3f..1.3f,
                    onChange = { v -> onChange { it.copy(vibrancy = v) } },
                )
                SwitchRow(
                    title = "Colours from artwork",
                    subtitle = "The player takes its palette from the album cover",
                    checked = prefs.artColors,
                    onChecked = { v -> onChange { it.copy(artColors = v) } },
                )
            }

            Group {
                Label("Corner roundness")
                ConnectedChoice(
                    options = listOf("Sharp", "Soft", "Round", "Pill"),
                    selected = prefs.corners,
                    onSelect = { v -> onChange { it.copy(corners = v) } },
                )
                Gap()
                Label("Typeface")
                ConnectedChoice(
                    options = listOf("Sans", "Serif", "Mono", "Custom"),
                    selected = prefs.font,
                    onSelect = { v -> onChange { it.copy(font = v) } },
                )
                if (prefs.font == 3 && customFontId(context) == 0) {
                    Text(
                        text = "No custom font found. Add a variable .ttf named app_font.ttf to app/src/main/res/font/ and rebuild.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            // ---------------- Player ----------------
            SectionTitle("Player")
            Group {
                Label("Artwork shape")
                ConnectedChoice(
                    options = listOf("Cookie", "Circle", "Squircle", "Soft"),
                    selected = prefs.artShape,
                    onSelect = { v -> onChange { it.copy(artShape = v) } },
                )
                if (prefs.artShape == 0) {
                    SliderRow(
                        title = "Cookie lobes",
                        valueText = "${prefs.lobes}",
                        value = prefs.lobes.toFloat(),
                        range = 5f..16f,
                        steps = 10,
                        onChange = { v -> onChange { it.copy(lobes = v.toInt()) } },
                    )
                }
                SwitchRow(
                    title = "Spin artwork",
                    subtitle = "Slow rotation while playing (cookie and circle)",
                    checked = prefs.spinArt,
                    onChecked = { v -> onChange { it.copy(spinArt = v) } },
                )
            }

            Group {
                Label("Progress bar")
                ConnectedChoice(
                    options = listOf("Wavy", "Flat"),
                    selected = prefs.progressStyle,
                    onSelect = { v -> onChange { it.copy(progressStyle = v) } },
                )
                if (prefs.progressStyle == 0) {
                    SliderRow(
                        title = "Wave height",
                        valueText = String.format(Locale.US, "%.1f×", prefs.waveIntensity),
                        value = prefs.waveIntensity,
                        range = 0.3f..1.8f,
                        onChange = { v -> onChange { it.copy(waveIntensity = v) } },
                    )
                }
                SwitchRow(
                    title = "Show time remaining",
                    subtitle = "Count down instead of showing the total",
                    checked = prefs.showRemaining,
                    onChecked = { v -> onChange { it.copy(showRemaining = v) } },
                )
                SwitchRow(
                    title = "Keep screen on",
                    subtitle = "While the player is open",
                    checked = prefs.keepScreenOn,
                    onChecked = { v -> onChange { it.copy(keepScreenOn = v) } },
                )
            }

            // ---------------- Library ----------------
            SectionTitle("Library")
            Group {
                SwitchRow(
                    title = "Artwork in lists",
                    subtitle = null,
                    checked = prefs.showListArt,
                    onChecked = { v -> onChange { it.copy(showListArt = v) } },
                )
                SwitchRow(
                    title = "Compact rows",
                    subtitle = "Tighter song lists",
                    checked = prefs.compactList,
                    onChecked = { v -> onChange { it.copy(compactList = v) } },
                )
                SliderRow(
                    title = "Hide short clips",
                    valueText = if (prefs.minSeconds == 0) "Off" else "< ${prefs.minSeconds}s",
                    value = prefs.minSeconds.toFloat(),
                    range = 0f..90f,
                    steps = 8,
                    onChange = { v -> onChange { it.copy(minSeconds = v.toInt()) } },
                )
            }

            // ---------------- Feel ----------------
            SectionTitle("Feel")
            Group {
                SwitchRow(
                    title = "Haptic feedback",
                    subtitle = "A small tick on button presses",
                    checked = prefs.haptics,
                    onChecked = { v -> onChange { it.copy(haptics = v) } },
                )
            }

            Spacer(Modifier.height(bottomPad))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings building blocks
// ---------------------------------------------------------------------------------------------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 22.dp, bottom = 10.dp),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun Gap() {
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onChecked(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    steps: Int = 0,
) {
    Column(Modifier.padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
        )
    }
}

private val presetHues = listOf(25f, 55f, 100f, 150f, 195f, 240f, 285f, 330f)

@Composable
private fun HueSwatches(hue: Float, enabled: Boolean, onPick: (Float) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presetHues.forEach { h ->
            val selected = enabled && kotlin.math.abs(h - hue) < 8f
            val color = Oklch.color(62.0, 0.16, h.toDouble())
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(if (enabled) color else color.copy(alpha = 0.35f))
                    .clickable(enabled = enabled) { onPick(h) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
