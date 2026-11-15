package com.fiangg20.wavelength.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    onOpenEqualizer: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    bottomPad: Dp,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        PageHeader("Settings", onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // ---------------- Tools ----------------
            SectionTitle("Tools")
            Group {
                LinkRow(
                    title = "Equalizer",
                    subtitle = "Bands, presets, bass boost and loudness",
                    onClick = onOpenEqualizer,
                )
                LinkRow(
                    title = "Downloader",
                    subtitle = "Save audio from links with yt-dlp",
                    onClick = onOpenDownloads,
                )
                LinkRow(
                    title = "Downloader settings",
                    subtitle = "Format, tags, playlists, network, raw arguments",
                    onClick = onOpenDownloadSettings,
                )
            }

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
                    title = "Adaptive app icon",
                    subtitle = "The home screen icon takes your accent colour. Your launcher may need a few seconds to refresh it.",
                    checked = prefs.adaptiveIcon,
                    onChecked = { v -> onChange { it.copy(adaptiveIcon = v) } },
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
            }

            Group {
                Label("Typeface")
                SinglePills(
                    options = listOf("Google Sans Flex", "System", "Serif", "Mono", "Custom"),
                    selected = prefs.font,
                    onSelect = { v -> onChange { it.copy(font = v) } },
                )
                if (prefs.font == 0) {
                    if (flexFontId(context) == 0) {
                        Hint("Google Sans Flex was not found in this build, so the system font is used. It is downloaded while GitHub builds the app. Check that the build log shows the font download step.")
                    } else {
                        SliderRow(
                            title = "Width",
                            valueText = "${prefs.fontWidth.toInt()}",
                            value = prefs.fontWidth,
                            range = 75f..125f,
                            onChange = { v -> onChange { it.copy(fontWidth = v) } },
                        )
                        SliderRow(
                            title = "Roundness",
                            valueText = "${prefs.fontRound.toInt()}",
                            value = prefs.fontRound,
                            range = 0f..100f,
                            onChange = { v -> onChange { it.copy(fontRound = v) } },
                        )
                    }
                }
                if (prefs.font == 4 && customFontId(context) == 0) {
                    Hint("No custom font found. Add a .ttf named app_font.ttf to app/src/main/res/font/ and rebuild.")
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
                SliderRow(
                    title = "Artwork size",
                    valueText = "${(prefs.artScale * 100).toInt()}%",
                    value = prefs.artScale,
                    range = 0.7f..1.1f,
                    onChange = { v -> onChange { it.copy(artScale = v) } },
                )
                SwitchRow(
                    title = "Spin artwork",
                    subtitle = "Slow rotation while playing (cookie and circle)",
                    checked = prefs.spinArt,
                    onChecked = { v -> onChange { it.copy(spinArt = v) } },
                )
                if (Build.VERSION.SDK_INT >= 31) {
                    SwitchRow(
                        title = "Blurred backdrop",
                        subtitle = "A soft glow of the cover behind the player",
                        checked = prefs.blurArt,
                        onChecked = { v -> onChange { it.copy(blurArt = v) } },
                    )
                }
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

            Group {
                Label("Mini player")
                SwitchRow(
                    title = "Skip button",
                    subtitle = null,
                    checked = prefs.miniSkip,
                    onChecked = { v -> onChange { it.copy(miniSkip = v) } },
                )
                SwitchRow(
                    title = "Progress line",
                    subtitle = null,
                    checked = prefs.miniProgress,
                    onChecked = { v -> onChange { it.copy(miniProgress = v) } },
                )
            }

            // ---------------- Playback ----------------
            SectionTitle("Playback")
            Group {
                SwitchRow(
                    title = "Skip silence",
                    subtitle = "Jumps over quiet gaps in tracks",
                    checked = prefs.skipSilence,
                    onChecked = { v -> onChange { it.copy(skipSilence = v) } },
                )
                SwitchRow(
                    title = "Pause when headphones unplug",
                    subtitle = null,
                    checked = prefs.pauseOnUnplug,
                    onChecked = { v -> onChange { it.copy(pauseOnUnplug = v) } },
                )
                SwitchRow(
                    title = "Restore last queue",
                    subtitle = "Pick up where you left off after reopening the app",
                    checked = prefs.restoreSession,
                    onChecked = { v -> onChange { it.copy(restoreSession = v) } },
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
                Gap()
                Label("Album grid")
                ConnectedChoice(
                    options = listOf("Compact", "Cozy", "Large"),
                    selected = prefs.albumGrid,
                    onSelect = { v -> onChange { it.copy(albumGrid = v) } },
                )
                DropdownRow(
                    title = "Sort songs by",
                    options = listOf("Title", "Artist", "Album", "Recently added", "Length"),
                    selected = prefs.sortBy,
                    onSelect = { v -> onChange { it.copy(sortBy = v) } },
                )
                SwitchRow(
                    title = "Ascending order",
                    subtitle = null,
                    checked = prefs.sortAsc,
                    onChecked = { v -> onChange { it.copy(sortAsc = v) } },
                )
            }

            Group {
                SwitchRow(
                    title = "Fix untagged files",
                    subtitle = "Reads \"Artist - Title\" from file names and tidies underscores",
                    checked = prefs.smartTags,
                    onChecked = { v -> onChange { it.copy(smartTags = v) } },
                )
                SliderRow(
                    title = "Hide short clips",
                    valueText = if (prefs.minSeconds == 0) "Off" else "< ${prefs.minSeconds}s",
                    value = prefs.minSeconds.toFloat(),
                    range = 0f..90f,
                    steps = 8,
                    onChange = { v -> onChange { it.copy(minSeconds = v.toInt()) } },
                )
                TextRow(
                    label = "Hide folders",
                    value = prefs.excluded,
                    onValue = { v -> onChange { it.copy(excluded = v) } },
                    placeholder = "WhatsApp, Notifications, Ringtones",
                )
                Hint("Songs whose folder path contains any of these words (comma separated) are left out of the library.")
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

            SectionTitle("About")
            Group {
                Text("Wavelength 2.0", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Material 3 Expressive music player. Downloads are powered by yt-dlp and ffmpeg running on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(bottomPad))
        }
    }
}
