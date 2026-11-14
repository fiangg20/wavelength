package com.fiangg20.wavelength.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fiangg20.wavelength.data.DlOptions
import com.fiangg20.wavelength.data.DlPrefs
import com.fiangg20.wavelength.data.DlStore
import com.fiangg20.wavelength.download.CommandBuilder

@Composable
fun DownloadSettingsScreen(onBack: () -> Unit, bottomPad: Dp) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val store = remember { DlStore.get(context) }
    val c by store.state.collectAsState()
    val cookiesFile = remember { CommandBuilder.cookiesFile(context) }
    var hasCookies by remember { mutableStateOf(cookiesFile.exists()) }
    var resetCount by remember { mutableIntStateOf(0) }

    val pickCookies = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cookiesFile.outputStream().use { out -> input.copyTo(out) }
                }
                hasCookies = cookiesFile.exists()
                store.update { it.copy(useCookies = true) }
                Toast.makeText(context, "Cookies imported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Could not read that file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val lossy = DlOptions.formats[c.format.coerceIn(0, DlOptions.formats.lastIndex)].let {
        it != "flac" && it != "wav" && it != "best"
    }

    key(resetCount) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Downloader settings", onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // ---------------- Output ----------------
            SectionTitle("Output")
            Group {
                DropdownRow(
                    title = "Format",
                    options = DlOptions.formatLabels,
                    selected = c.format,
                    onSelect = { v -> store.update { it.copy(format = v) } },
                )
                if (lossy) {
                    DropdownRow(
                        title = "Quality",
                        options = DlOptions.bitrateLabels,
                        selected = c.bitrate,
                        onSelect = { v -> store.update { it.copy(bitrate = v) } },
                    )
                } else {
                    Hint("Lossless and original formats have no bitrate setting.")
                }
                DropdownRow(
                    title = "Audio source",
                    options = DlOptions.sourceLabels,
                    selected = c.source,
                    onSelect = { v -> store.update { it.copy(source = v) } },
                )
                if (c.source >= DlOptions.sources.size) {
                    TextRow(
                        label = "Format selector (-f)",
                        value = c.customFormat,
                        onValue = { v -> store.update { it.copy(customFormat = v) } },
                        placeholder = "bestaudio[abr<=160]/bestaudio",
                    )
                }
            }

            Group {
                DropdownRow(
                    title = "File name",
                    options = DlOptions.nameStyleLabels,
                    selected = c.nameStyle,
                    onSelect = { v -> store.update { it.copy(nameStyle = v) } },
                )
                TextRow(
                    label = "Custom name template (optional)",
                    value = c.customTemplate,
                    onValue = { v -> store.update { it.copy(customTemplate = v) } },
                    placeholder = "%(artist)s - %(title)s",
                )
                SwitchRow(
                    title = "Safe file names",
                    subtitle = "ASCII only, no spaces or special characters",
                    checked = c.restrictNames,
                    onChecked = { v -> store.update { it.copy(restrictNames = v) } },
                )
                TextRow(
                    label = "Save folder inside Music",
                    value = c.saveFolder,
                    onValue = { v -> store.update { it.copy(saveFolder = v) } },
                    placeholder = "Wavelength",
                )
            }

            // ---------------- Metadata ----------------
            SectionTitle("Tags and cover art")
            Group {
                SwitchRow(
                    title = "Embed tags",
                    subtitle = "Title, artist, album and date",
                    checked = c.embedMeta,
                    onChecked = { v -> store.update { it.copy(embedMeta = v) } },
                )
                SwitchRow(
                    title = "Embed cover art",
                    subtitle = "MP3, M4A and FLAC",
                    checked = c.embedThumb,
                    onChecked = { v -> store.update { it.copy(embedThumb = v) } },
                )
                if (c.embedThumb) {
                    SwitchRow(
                        title = "Crop cover to a square",
                        subtitle = "Turns 16:9 video thumbnails into album art",
                        checked = c.squareCover,
                        onChecked = { v -> store.update { it.copy(squareCover = v) } },
                    )
                }
                SwitchRow(
                    title = "Split artist from title",
                    subtitle = "Reads \"Artist - Title\" video names",
                    checked = c.parseArtist,
                    onChecked = { v -> store.update { it.copy(parseArtist = v) } },
                )
                SwitchRow(
                    title = "Clean titles",
                    subtitle = "Removes (Official Video), [Lyrics] and similar",
                    checked = c.cleanTitle,
                    onChecked = { v -> store.update { it.copy(cleanTitle = v) } },
                )
                SwitchRow(
                    title = "Embed chapters",
                    subtitle = null,
                    checked = c.embedChapters,
                    onChecked = { v -> store.update { it.copy(embedChapters = v) } },
                )
            }

            // ---------------- Playlists ----------------
            SectionTitle("Playlists and search")
            Group {
                Label("When a link is part of a playlist")
                ConnectedChoice(
                    options = listOf("Default", "Single", "Whole list"),
                    selected = c.playlistMode,
                    onSelect = { v -> store.update { it.copy(playlistMode = v) } },
                )
                TextRow(
                    label = "Only these items (optional)",
                    value = c.playlistItems,
                    onValue = { v -> store.update { it.copy(playlistItems = v) } },
                    placeholder = "1-5,8,10",
                )
                SwitchRow(
                    title = "Reverse order",
                    subtitle = null,
                    checked = c.playlistReverse,
                    onChecked = { v -> store.update { it.copy(playlistReverse = v) } },
                )
                SwitchRow(
                    title = "Random order",
                    subtitle = null,
                    checked = c.playlistRandom,
                    onChecked = { v -> store.update { it.copy(playlistRandom = v) } },
                )
                SwitchRow(
                    title = "Remember downloaded items",
                    subtitle = "Skips anything you already downloaded before",
                    checked = c.useArchive,
                    onChecked = { v -> store.update { it.copy(useArchive = v) } },
                )
                SliderRow(
                    title = "Stop after",
                    valueText = if (c.maxDownloads == 0) "No limit" else "${c.maxDownloads} files",
                    value = c.maxDownloads.toFloat(),
                    range = 0f..100f,
                    steps = 19,
                    onChange = { v -> store.update { it.copy(maxDownloads = v.toInt()) } },
                )
                SliderRow(
                    title = "Search results to download",
                    valueText = "${c.searchCount}",
                    value = c.searchCount.toFloat(),
                    range = 1f..10f,
                    steps = 8,
                    onChange = { v -> store.update { it.copy(searchCount = v.toInt()) } },
                )
                Hint("Typing words instead of a link searches YouTube and downloads that many top results.")
            }

            // ---------------- SponsorBlock ----------------
            SectionTitle("SponsorBlock")
            Group {
                SwitchRow(
                    title = "Cut segments",
                    subtitle = "Removes sections people have flagged in videos",
                    checked = c.sponsorBlock,
                    onChecked = { v -> store.update { it.copy(sponsorBlock = v) } },
                )
                if (c.sponsorBlock) {
                    Spacer(Modifier.height(8.dp))
                    MultiPills(
                        options = DlOptions.sbLabels,
                        isOn = { i -> (c.sbMask and (1 shl i)) != 0 },
                        onToggle = { i -> store.update { it.copy(sbMask = it.sbMask xor (1 shl i)) } },
                    )
                }
            }

            // ---------------- Network ----------------
            SectionTitle("Network")
            Group {
                TextRow(
                    label = "Proxy (optional)",
                    value = c.proxy,
                    onValue = { v -> store.update { it.copy(proxy = v) } },
                    placeholder = "socks5://127.0.0.1:1080",
                )
                TextRow(
                    label = "Speed limit (optional)",
                    value = c.limitRate,
                    onValue = { v -> store.update { it.copy(limitRate = v) } },
                    placeholder = "2M",
                )
                TextRow(
                    label = "User agent (optional)",
                    value = c.userAgent,
                    onValue = { v -> store.update { it.copy(userAgent = v) } },
                )
                Gap()
                Label("IP version")
                ConnectedChoice(
                    options = listOf("Auto", "IPv4", "IPv6"),
                    selected = c.forceIp,
                    onSelect = { v -> store.update { it.copy(forceIp = v) } },
                )
                SliderRow(
                    title = "Parallel fragments",
                    valueText = "${c.threads}",
                    value = c.threads.toFloat(),
                    range = 1f..16f,
                    steps = 14,
                    onChange = { v -> store.update { it.copy(threads = v.toInt()) } },
                )
                SliderRow(
                    title = "Retries",
                    valueText = "${c.retries}",
                    value = c.retries.toFloat(),
                    range = 0f..30f,
                    steps = 29,
                    onChange = { v -> store.update { it.copy(retries = v.toInt(), fragRetries = v.toInt()) } },
                )
                SliderRow(
                    title = "Socket timeout",
                    valueText = if (c.timeout == 0) "Default" else "${c.timeout}s",
                    value = c.timeout.toFloat(),
                    range = 0f..120f,
                    steps = 23,
                    onChange = { v -> store.update { it.copy(timeout = v.toInt()) } },
                )
                SliderRow(
                    title = "Pause between downloads",
                    valueText = if (c.sleepInterval == 0) "Off" else "${c.sleepInterval}s",
                    value = c.sleepInterval.toFloat(),
                    range = 0f..30f,
                    steps = 29,
                    onChange = { v -> store.update { it.copy(sleepInterval = v.toInt()) } },
                )
                SwitchRow(
                    title = "Bypass geo restrictions",
                    subtitle = null,
                    checked = c.geoBypass,
                    onChecked = { v -> store.update { it.copy(geoBypass = v) } },
                )
                SwitchRow(
                    title = "Skip certificate checks",
                    subtitle = "Only for a proxy that needs it",
                    checked = c.noCertCheck,
                    onChecked = { v -> store.update { it.copy(noCertCheck = v) } },
                )
            }

            Group {
                DropdownRow(
                    title = "YouTube client",
                    options = DlOptions.clientLabels,
                    selected = c.ytClient,
                    onSelect = { v -> store.update { it.copy(ytClient = v) } },
                )
                Hint("If YouTube downloads start failing, trying another client often fixes it.")
                SwitchRow(
                    title = "Use cookies",
                    subtitle = if (hasCookies) "A cookies.txt file is imported" else "Import a Netscape cookies.txt first",
                    checked = c.useCookies,
                    onChecked = { v -> store.update { it.copy(useCookies = v) } },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction(
                        text = "Import cookies.txt",
                        onClick = { pickCookies.launch(arrayOf("*/*")) },
                        container = scheme.secondaryContainer,
                        content = scheme.onSecondaryContainer,
                    )
                    if (hasCookies) {
                        SmallAction(
                            text = "Remove",
                            onClick = {
                                cookiesFile.delete()
                                hasCookies = false
                                store.update { it.copy(useCookies = false) }
                            },
                            container = scheme.surfaceContainerHighest,
                            content = scheme.onSurface,
                        )
                    }
                }
            }

            // ---------------- Engine and queue ----------------
            SectionTitle("Engine and queue")
            Group {
                SwitchRow(
                    title = "Update yt-dlp automatically",
                    subtitle = "Checks once each time the app starts downloading",
                    checked = c.autoUpdate,
                    onChecked = { v -> store.update { it.copy(autoUpdate = v) } },
                )
                Label("Update channel")
                ConnectedChoice(
                    options = DlOptions.channels,
                    selected = c.updateChannel,
                    onSelect = { v -> store.update { it.copy(updateChannel = v) } },
                )
                Gap()
                Label("Downloads at the same time")
                ConnectedChoice(
                    options = listOf("1", "2", "3"),
                    selected = (c.parallel - 1).coerceIn(0, 2),
                    onSelect = { v -> store.update { it.copy(parallel = v + 1) } },
                )
                SwitchRow(
                    title = "Wi-Fi only",
                    subtitle = "Fail instead of using mobile data",
                    checked = c.wifiOnly,
                    onChecked = { v -> store.update { it.copy(wifiOnly = v) } },
                )
                SwitchRow(
                    title = "Refresh library when done",
                    subtitle = "New songs show up right away",
                    checked = c.autoRefresh,
                    onChecked = { v -> store.update { it.copy(autoRefresh = v) } },
                )
            }

            // ---------------- Advanced ----------------
            SectionTitle("Advanced")
            Group {
                TextRow(
                    label = "Extra yt-dlp arguments",
                    value = c.extraArgs,
                    onValue = { v -> store.update { it.copy(extraArgs = v) } },
                    placeholder = "--audio-multistreams --no-mtime",
                    singleLine = false,
                )
                Hint("Added to the end of the command, so they can override anything above. Quote values that contain spaces.")
            }

            Gap()
            ExpressiveButton(
                onClick = {
                    store.update { DlPrefs() }
                    resetCount++
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                containerColor = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
                restCorner = 28.dp,
            ) {
                Text("Reset to defaults", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(bottomPad))
        }
    }
    }
}
