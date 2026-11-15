package com.fiangg20.wavelength.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fiangg20.wavelength.data.ArtistSplitter
import com.fiangg20.wavelength.data.Prefs
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// Categories and search index
// ---------------------------------------------------------------------------------------------

private fun categoryTitle(key: String): String = when (key) {
    "appearance" -> "Appearance"
    "player" -> "Player"
    "lyrics" -> "Lyrics"
    "playback" -> "Playback"
    "library" -> "Library"
    "feel" -> "Feel"
    "tool:eq" -> "Equalizer"
    "tool:downloads" -> "Downloader"
    "tool:dlsettings" -> "Downloader settings"
    else -> "Settings"
}

private data class Entry(val title: String, val target: String, val words: String = "")

/** Every setting, so the search box can find it. Keep in step with the category pages below. */
private val entries = listOf(
    // Appearance
    Entry("Theme", "appearance", "light dark system mode night day"),
    Entry("Pure black", "appearance", "amoled oled dark"),
    Entry("Wallpaper colours", "appearance", "dynamic material you monet system palette"),
    Entry("Accent colour", "appearance", "hue custom colour color swatch"),
    Entry("Vibrancy", "appearance", "saturation chroma"),
    Entry("Adaptive app icon", "appearance", "launcher home screen"),
    Entry("Colours from artwork", "appearance", "album cover palette player"),
    Entry("Corner roundness", "appearance", "sharp soft round pill radius shape"),
    Entry("Typeface", "appearance", "font google sans flex serif mono custom"),
    Entry("Font width", "appearance", "typeface axis"),
    Entry("Font roundness", "appearance", "typeface axis"),
    // Player
    Entry("Artwork shape", "player", "cookie circle squircle soft cover"),
    Entry("Cookie lobes", "player", "petals scallops artwork"),
    Entry("Artwork size", "player", "cover scale"),
    Entry("Spin artwork", "player", "rotate rotation vinyl"),
    Entry("Blurred backdrop", "player", "glow background blur"),
    Entry("Progress bar", "player", "wavy flat seek slider"),
    Entry("Wave height", "player", "wavy progress intensity"),
    Entry("Show time remaining", "player", "countdown duration"),
    Entry("Keep screen on", "player", "awake display"),
    Entry("Mini player skip button", "player", "next"),
    Entry("Mini player progress line", "player", "bar"),
    // Lyrics
    Entry("Find lyrics online", "lyrics", "lrclib internet download"),
    Entry("Timing shift", "lyrics", "offset delay sync early late"),
    Entry("Word highlight", "lyrics", "karaoke word by word sync estimated"),
    Entry("Lyrics alignment", "lyrics", "left centre center right"),
    Entry("Lyrics highlight colour", "lyrics", "accent neutral tertiary color"),
    Entry("Lyrics text size", "lyrics", "font"),
    Entry("Lyrics line spacing", "lyrics", "gap"),
    Entry("Other lines brightness", "lyrics", "dim fade opacity"),
    Entry("Bold playing line", "lyrics", "weight"),
    Entry("Zoom playing line", "lyrics", "scale bigger"),
    // Playback
    Entry("Skip silence", "playback", "gaps quiet"),
    Entry("Pause when headphones unplug", "playback", "bluetooth wired"),
    Entry("Restore last queue", "playback", "session resume reopen"),
    // Library
    Entry("Artwork in lists", "library", "thumbnails covers"),
    Entry("Compact rows", "library", "dense tight list"),
    Entry("Album grid", "library", "compact cozy large size"),
    Entry("Sort songs by", "library", "title artist album recently added length order"),
    Entry("Ascending order", "library", "sort reverse descending"),
    Entry("Split artist names", "library", "separator comma feat multiple artists"),
    Entry("Never split these names", "library", "artist exception keep ac/dc"),
    Entry("Fix untagged files", "library", "smart tags filename underscores"),
    Entry("Hide short clips", "library", "minimum length duration"),
    Entry("Hide folders", "library", "exclude ignore whatsapp ringtones"),
    // Feel
    Entry("Haptic feedback", "feel", "vibration tick buzz"),
    // Tools
    Entry("Equalizer", "tool:eq", "bands presets bass boost loudness virtualizer"),
    Entry("Downloader", "tool:downloads", "yt-dlp youtube save audio mp3 link"),
    Entry("Downloader settings", "tool:dlsettings", "format tags playlists network proxy raw arguments cookies"),
)

/** Puts one page of settings back to what a fresh install has. */
private fun resetCategory(key: String, p: Prefs): Prefs {
    val d = Prefs()
    return when (key) {
        "appearance" -> p.copy(
            themeMode = d.themeMode, pureBlack = d.pureBlack, dynamicColor = d.dynamicColor, hue = d.hue,
            adaptiveIcon = d.adaptiveIcon, vibrancy = d.vibrancy, artColors = d.artColors, corners = d.corners,
            font = d.font, fontWidth = d.fontWidth, fontRound = d.fontRound,
        )
        "player" -> p.copy(
            artShape = d.artShape, lobes = d.lobes, spinArt = d.spinArt, artScale = d.artScale, blurArt = d.blurArt,
            progressStyle = d.progressStyle, waveIntensity = d.waveIntensity, keepScreenOn = d.keepScreenOn,
            showRemaining = d.showRemaining, miniSkip = d.miniSkip, miniProgress = d.miniProgress,
        )
        "lyrics" -> p.copy(
            lyricsOnline = d.lyricsOnline, lyricsSize = d.lyricsSize, lyricsWordMode = d.lyricsWordMode,
            lyricsAlign = d.lyricsAlign, lyricsColor = d.lyricsColor, lyricsDim = d.lyricsDim,
            lyricsSpacing = d.lyricsSpacing, lyricsBold = d.lyricsBold, lyricsZoom = d.lyricsZoom,
            lyricsShiftMs = d.lyricsShiftMs,
        )
        "playback" -> p.copy(
            skipSilence = d.skipSilence, pauseOnUnplug = d.pauseOnUnplug, restoreSession = d.restoreSession,
            vinylSync = d.vinylSync,
        )
        "library" -> p.copy(
            showListArt = d.showListArt, compactList = d.compactList, albumGrid = d.albumGrid, sortBy = d.sortBy,
            sortAsc = d.sortAsc, artistSplit = d.artistSplit, artistSeps = d.artistSeps, artistKeep = d.artistKeep,
            smartTags = d.smartTags, minSeconds = d.minSeconds, excluded = d.excluded,
        )
        "feel" -> p.copy(haptics = d.haptics)
        else -> p
    }
}

// ---------------------------------------------------------------------------------------------
// Home: search and category tiles
// ---------------------------------------------------------------------------------------------

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    bottomPad: Dp,
) {
    val scheme = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }

    fun goTo(target: String) {
        when (target) {
            "tool:eq" -> onOpenEqualizer()
            "tool:downloads" -> onOpenDownloads()
            "tool:dlsettings" -> onOpenDownloadSettings()
            else -> onOpenCategory(target)
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Settings", onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search settings") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (query.isBlank()) {
                SectionTitle("Customize")
                TileRow(
                    CategoryTile("Appearance", "Theme, accent, corners, typeface", Icons.Rounded.Palette,
                        scheme.primaryContainer, scheme.onPrimaryContainer) { goTo("appearance") },
                    CategoryTile("Player", "Artwork, progress bar, mini player", Icons.Rounded.PlayCircle,
                        scheme.secondaryContainer, scheme.onSecondaryContainer) { goTo("player") },
                )
                TileRow(
                    CategoryTile("Lyrics", "Word sync, look, timing", Icons.Rounded.Lyrics,
                        scheme.tertiaryContainer, scheme.onTertiaryContainer) { goTo("lyrics") },
                    CategoryTile("Playback", "Silence, headphones, queue", Icons.Rounded.GraphicEq,
                        scheme.surfaceContainerHigh, scheme.onSurface) { goTo("playback") },
                )
                TileRow(
                    CategoryTile("Library", "Lists, sorting, artists, folders", Icons.Rounded.LibraryMusic,
                        scheme.secondaryContainer, scheme.onSecondaryContainer) { goTo("library") },
                    CategoryTile("Feel", "Haptics", Icons.Rounded.Vibration,
                        scheme.primaryContainer, scheme.onPrimaryContainer) { goTo("feel") },
                )

                SectionTitle("Tools")
                TileRow(
                    CategoryTile("Equalizer", "Bands, presets, bass, loudness", Icons.Rounded.Equalizer,
                        scheme.tertiaryContainer, scheme.onTertiaryContainer) { goTo("tool:eq") },
                    CategoryTile("Downloader", "Save audio from links", Icons.Rounded.Download,
                        scheme.surfaceContainerHigh, scheme.onSurface) { goTo("tool:downloads") },
                )
                TileRow(
                    CategoryTile("Downloader settings", "Format, tags, playlists, network, raw arguments",
                        Icons.Rounded.Settings, scheme.secondaryContainer, scheme.onSecondaryContainer) {
                        goTo("tool:dlsettings")
                    },
                    null,
                )

                SectionTitle("About")
                Group {
                    Text("Wavelength 2.0", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Material 3 Expressive music player. Downloads are powered by yt-dlp and ffmpeg running on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            } else {
                val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
                val hits = entries.filter { e ->
                    val hay = (e.title + " " + e.words + " " + categoryTitle(e.target)).lowercase()
                    words.all { it in hay }
                }
                Spacer(Modifier.height(12.dp))
                if (hits.isEmpty()) {
                    Text(
                        "No settings match \"${query.trim()}\".",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                    )
                } else {
                    Group {
                        hits.forEach { e ->
                            LinkRow(
                                title = e.title,
                                subtitle = if (e.target.startsWith("tool:")) "Tools" else categoryTitle(e.target),
                                onClick = { goTo(e.target) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(bottomPad))
        }
    }
}

private class CategoryTile(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color,
    val onClick: () -> Unit,
)

/** Two tiles side by side. A null second tile leaves the right half empty. */
@Composable
private fun TileRow(first: CategoryTile, second: CategoryTile?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tile(first, Modifier.weight(1f))
        if (second != null) Tile(second, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Tile(t: CategoryTile, modifier: Modifier) {
    ExpressiveButton(
        onClick = t.onClick,
        modifier = modifier.height(148.dp),
        containerColor = t.container,
        contentColor = t.content,
        restCorner = 28.dp,
        pressedCorner = 12.dp,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CookieShape(8, 0.12f))
                    .background(t.content.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(t.icon, contentDescription = null, tint = t.content)
            }
            Column {
                Text(
                    t.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    t.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = t.content.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// One category page
// ---------------------------------------------------------------------------------------------

@Composable
fun SettingsCategoryScreen(
    category: String,
    prefs: Prefs,
    onChange: ((Prefs) -> Prefs) -> Unit,
    onBack: () -> Unit,
    bottomPad: Dp,
) {
    val scheme = MaterialTheme.colorScheme
    var confirm by remember { mutableStateOf(false) }
    val title = categoryTitle(category)

    Column(Modifier.fillMaxSize()) {
        PageHeader(title, onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            when (category) {
                "appearance" -> AppearanceSettings(prefs, onChange)
                "player" -> PlayerSettings(prefs, onChange)
                "lyrics" -> LyricsSettings(prefs, onChange)
                "playback" -> PlaybackSettings(prefs, onChange)
                "library" -> LibrarySettings(prefs, onChange)
                "feel" -> FeelSettings(prefs, onChange)
            }

            Spacer(Modifier.height(12.dp))
            ExpressiveButton(
                onClick = { confirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                containerColor = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
                restCorner = 28.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset $title to defaults", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(bottomPad))
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Reset $title?") },
            text = { Text("Everything on this page goes back to how a fresh install starts. Other pages are not touched.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        onChange { resetCategory(category, it) }
                    },
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AppearanceSettings(prefs: Prefs, onChange: ((Prefs) -> Prefs) -> Unit) {
    val context = LocalContext.current
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
}

@Composable
private fun PlayerSettings(prefs: Prefs, onChange: ((Prefs) -> Prefs) -> Unit) {
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
}

@Composable
private fun LyricsSettings(prefs: Prefs, onChange: ((Prefs) -> Prefs) -> Unit) {
    Group {
        SwitchRow(
            title = "Find lyrics online",
            subtitle = "Looks up synced lyrics on lrclib.net the first time you open them. They are saved on the phone afterwards.",
            checked = prefs.lyricsOnline,
            onChecked = { v -> onChange { it.copy(lyricsOnline = v) } },
        )
        SliderRow(
            title = "Timing shift",
            valueText = "${if (prefs.lyricsShiftMs > 0) "+" else ""}${prefs.lyricsShiftMs} ms",
            value = prefs.lyricsShiftMs.toFloat(),
            range = -1000f..1000f,
            steps = 39,
            onChange = { v -> onChange { it.copy(lyricsShiftMs = (v / 50f).toInt() * 50) } },
        )
        Hint("Plus shows every song's lyrics a little earlier, minus a little later. Each song also has its own fine-tune inside the lyrics.")
    }

    Group {
        LyricsStyleControls(prefs = prefs, onChange = onChange)
    }
}

@Composable
private fun PlaybackSettings(prefs: Prefs, onChange: ((Prefs) -> Prefs) -> Unit) {
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
}

@Composable
private fun LibrarySettings(prefs: Prefs, onChange: ((Prefs) -> Prefs) -> Unit) {
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
            title = "Split artist names",
            subtitle = "\"RYO, Limbx\" becomes two artists, each with their own page. Albums keep the first artist as their owner.",
            checked = prefs.artistSplit,
            onChecked = { v -> onChange { it.copy(artistSplit = v) } },
        )
        if (prefs.artistSplit) {
            Gap()
            Label("Split at")
            MultiPills(
                options = ArtistSplitter.SEPARATORS,
                isOn = { i -> prefs.artistSeps and (1 shl i) != 0 },
                onToggle = { i -> onChange { it.copy(artistSeps = it.artistSeps xor (1 shl i)) } },
            )
            Hint("\"x\" and \"+\" are off by default because many band names contain them.")
            Gap()
            TextRow(
                label = "Never split these names",
                value = prefs.artistKeep,
                onValue = { v -> onChange { it.copy(artistKeep = v) } },
                placeholder = "AC/DC",
                singleLine = false,
            )
            Hint("One name per line. Use it for artists whose name contains a comma, slash or &.")
        }
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
}

@Composable
private fun FeelSettings(prefs: Prefs, onChange: ((Prefs) -> Prefs) -> Unit) {
    Group {
        SwitchRow(
            title = "Haptic feedback",
            subtitle = "A small tick on button presses",
            checked = prefs.haptics,
            onChecked = { v -> onChange { it.copy(haptics = v) } },
        )
    }
}
