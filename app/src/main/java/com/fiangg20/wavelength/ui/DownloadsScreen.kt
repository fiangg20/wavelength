package com.fiangg20.wavelength.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fiangg20.wavelength.data.DlOptions
import com.fiangg20.wavelength.data.DlStore
import com.fiangg20.wavelength.download.DlStatus
import com.fiangg20.wavelength.download.DownloadItem
import com.fiangg20.wavelength.download.DownloadManager
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun DownloadsScreen(
    initialInput: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomPad: Dp,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { DlStore.get(context) }
    val cfg by store.state.collectAsState()
    val items by DownloadManager.items.collectAsState()
    val engine by DownloadManager.engine.collectAsState()

    var input by remember(initialInput) { mutableStateOf(initialInput) }
    var trim by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (!DownloadManager.engine.value.ready) DownloadManager.prepare(context)
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Downloader", onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Group {
                TextRow(
                    label = "Link, playlist or search words",
                    value = input,
                    onValue = { input = it },
                    placeholder = "Paste one or more links, one per line",
                    singleLine = false,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction(
                        text = "Paste",
                        onClick = {
                            val text = readClipboard(context)
                            if (text.isNotBlank()) {
                                input = if (input.isBlank()) text else input.trimEnd() + "\n" + text
                            }
                        },
                        container = scheme.secondaryContainer,
                        content = scheme.onSecondaryContainer,
                    )
                    SmallAction(
                        text = "Clear",
                        onClick = { input = "" },
                        container = scheme.surfaceContainerHighest,
                        content = scheme.onSurfaceVariant,
                    )
                }
                Gap()
                Label("Format")
                ConnectedChoice(
                    options = listOf("MP3", "M4A", "Opus", "FLAC"),
                    selected = if (cfg.format in 0..3) cfg.format else -1,
                    onSelect = { v -> store.update { it.copy(format = v) } },
                )
                DropdownRow(
                    title = "Quality",
                    options = DlOptions.bitrateLabels,
                    selected = cfg.bitrate,
                    onSelect = { v -> store.update { it.copy(bitrate = v) } },
                )
                TextRow(
                    label = "Trim (optional)",
                    value = trim,
                    onValue = { trim = it },
                    placeholder = "1:30-3:45",
                )
                Spacer(Modifier.height(8.dp))
                ExpressiveButton(
                    onClick = {
                        val lines = input.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        if (lines.isNotEmpty()) {
                            DownloadManager.enqueue(context, lines, trim)
                            input = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    containerColor = if (input.isBlank()) scheme.surfaceContainerHighest else scheme.primary,
                    contentColor = if (input.isBlank()) scheme.onSurfaceVariant else scheme.onPrimary,
                    restCorner = 30.dp,
                ) {
                    Text(
                        "Download as ${DlOptions.formats[cfg.format.coerceIn(0, DlOptions.formats.lastIndex)].uppercase(Locale.US)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Hint("Files are saved to Music/${cfg.saveFolder.ifBlank { "Wavelength" }} and appear in your library.")
            }

            Group {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("yt-dlp engine", style = MaterialTheme.typography.titleMedium)
                        val status = when {
                            engine.message.isNotEmpty() -> engine.message
                            engine.busy -> "Working"
                            engine.ready -> "Version ${engine.version.ifEmpty { "unknown" }}"
                            else -> "Starts with your first download"
                        }
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    SmallAction(
                        text = "Update",
                        onClick = { scope.launch { DownloadManager.updateEngine(context) } },
                        container = scheme.secondaryContainer,
                        content = scheme.onSecondaryContainer,
                        enabled = !engine.busy,
                    )
                }
                LinkRow(
                    title = "Downloader settings",
                    subtitle = "Formats, metadata, playlists, network and raw arguments",
                    onClick = onOpenSettings,
                )
            }

            SectionTitle(if (items.isEmpty()) "Queue" else "Queue (${items.size})")
            if (items.isEmpty()) {
                Group {
                    Text(
                        "Nothing yet. Paste a link above, or share a link to Wavelength from YouTube, SoundCloud or any other site yt-dlp supports.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction(
                        text = "Clear finished",
                        onClick = { DownloadManager.clearFinished() },
                        container = scheme.surfaceContainerHigh,
                        content = scheme.onSurface,
                    )
                    if (items.any { it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED }) {
                        SmallAction(
                            text = "Cancel all",
                            onClick = { DownloadManager.cancelAll() },
                            container = scheme.errorContainer,
                            content = scheme.onErrorContainer,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                for (item in items.reversed()) {
                    DownloadCard(item)
                }
            }

            Spacer(Modifier.height(bottomPad))
        }
    }
}

@Composable
private fun DownloadCard(item: DownloadItem) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showLog by remember(item.id) { mutableStateOf(false) }

    val failed = item.status == DlStatus.FAILED
    val statusText = when (item.status) {
        DlStatus.QUEUED -> "Queued"
        DlStatus.RUNNING -> {
            val pct = item.progress.toInt().coerceIn(0, 100)
            if (item.eta >= 0) "$pct%  ·  ${formatEta(item.eta)} left" else "$pct%"
        }
        DlStatus.DONE -> item.message.ifEmpty { "Saved" }
        DlStatus.FAILED -> "Failed"
        DlStatus.CANCELED -> "Canceled"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        shape = MaterialTheme.shapes.large,
        color = if (failed) scheme.errorContainer else scheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = item.title.ifEmpty { item.input },
                style = MaterialTheme.typography.titleMedium,
                color = if (failed) scheme.onErrorContainer else scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = if (failed) scheme.onErrorContainer else scheme.primary,
            )
            if (item.status == DlStatus.RUNNING) {
                Spacer(Modifier.height(10.dp))
                if (item.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (item.line.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = item.line,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (failed && item.message.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onErrorContainer,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showLog && item.log.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = scheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = item.log.takeLast(25).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (item.status) {
                    DlStatus.QUEUED, DlStatus.RUNNING -> SmallAction(
                        text = "Cancel",
                        onClick = { DownloadManager.cancel(item.id) },
                        container = scheme.surfaceContainerHighest,
                        content = scheme.onSurface,
                    )
                    DlStatus.FAILED, DlStatus.CANCELED -> {
                        SmallAction(
                            text = "Retry",
                            onClick = { DownloadManager.retry(context, item.id) },
                            container = scheme.primary,
                            content = scheme.onPrimary,
                        )
                        SmallAction(
                            text = "Remove",
                            onClick = { DownloadManager.remove(item.id) },
                            container = scheme.surfaceContainerHighest,
                            content = scheme.onSurface,
                        )
                    }
                    DlStatus.DONE -> SmallAction(
                        text = "Remove",
                        onClick = { DownloadManager.remove(item.id) },
                        container = scheme.surfaceContainerHighest,
                        content = scheme.onSurface,
                    )
                }
                if (item.log.isNotEmpty()) {
                    SmallAction(
                        text = if (showLog) "Hide log" else "Log",
                        onClick = { showLog = !showLog },
                        container = Color.Transparent,
                        content = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun SmallAction(
    text: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    enabled: Boolean = true,
) {
    ExpressiveButton(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.height(40.dp),
        containerColor = if (enabled) container else container.copy(alpha = 0.5f),
        contentColor = if (enabled) content else content.copy(alpha = 0.5f),
        restCorner = 20.dp,
        pressedCorner = 8.dp,
    ) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun readClipboard(context: Context): String {
    return try {
        val cm = context.getSystemService(ClipboardManager::class.java)
        val clip = cm?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(context)?.toString() ?: ""
        } else {
            ""
        }
    } catch (e: Exception) {
        ""
    }
}

private fun formatEta(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}
