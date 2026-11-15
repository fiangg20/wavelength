@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fiangg20.wavelength.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fiangg20.wavelength.data.Track
import com.fiangg20.wavelength.lyrics.LyricLine
import com.fiangg20.wavelength.lyrics.Lyrics
import com.fiangg20.wavelength.lyrics.LyricsRepo
import java.util.Locale

private sealed interface LyricsUi {
    data object Loading : LyricsUi
    data class Found(val lyrics: Lyrics) : LyricsUi
    data class Missing(val message: String) : LyricsUi
}

/** Index of the last line that has started at [timeMs], or -1 before the first line. */
private fun currentIndex(lines: List<LyricLine>, timeMs: Long): Int {
    if (lines.isEmpty() || timeMs < lines[0].timeMs) return -1
    var lo = 0
    var hi = lines.lastIndex
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (lines[mid].timeMs <= timeMs) lo = mid else hi = mid - 1
    }
    return lo
}

private fun offsetLabel(ms: Long): String =
    String.format(Locale.US, "%+.1fs", ms / 1000f)

@Composable
fun LyricsSheet(vm: PlayerViewModel, track: Track, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val prefs = LocalPrefs.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val position by vm.position.collectAsStateWithLifecycle()

    var reload by remember { mutableIntStateOf(0) }
    var state by remember(track.id) { mutableStateOf<LyricsUi>(LyricsUi.Loading) }
    var offset by remember(track.id) { mutableLongStateOf(LyricsRepo.getOffset(context, track.id)) }

    LaunchedEffect(track.id, reload) {
        state = LyricsUi.Loading
        val result = LyricsRepo.load(context, track, prefs.lyricsOnline, force = reload > 0)
        val found = result.lyrics
        state = if (found != null) LyricsUi.Found(found) else LyricsUi.Missing(result.message)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                val text = if (bytes == null) "" else String(bytes, Charsets.UTF_8)
                val saved = LyricsRepo.saveImported(context, track.id, text)
                state = if (saved != null) LyricsUi.Found(saved) else LyricsUi.Missing("That file has no lyrics in it.")
            } catch (e: Exception) {
                state = LyricsUi.Missing("Could not read that file.")
            }
        }
    }

    val current = state
    val lines = if (current is LyricsUi.Found) current.lyrics.synced else emptyList()
    val index by remember(lines, offset) {
        derivedStateOf { currentIndex(lines, position + offset + 120L) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            // Header
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                val subtitle = buildString {
                    append(track.artist)
                    if (current is LyricsUi.Found) {
                        append("  ·  ")
                        append(if (current.lyrics.synced.isNotEmpty()) "Synced" else "Not synced")
                        append(" · ")
                        append(current.lyrics.source)
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction(
                        text = "Import .lrc",
                        onClick = { picker.launch(arrayOf("*/*")) },
                        container = scheme.secondaryContainer,
                        content = scheme.onSecondaryContainer,
                    )
                    SmallAction(
                        text = "Search again",
                        onClick = { reload++ },
                        container = scheme.surfaceContainerHighest,
                        content = scheme.onSurface,
                        enabled = state !is LyricsUi.Loading,
                    )
                }
                if (lines.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Timing",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        SmallAction(
                            text = "-0.5s",
                            onClick = {
                                offset -= 500L
                                LyricsRepo.setOffset(context, track.id, offset)
                            },
                            container = scheme.surfaceContainerHighest,
                            content = scheme.onSurface,
                        )
                        Text(
                            offsetLabel(offset),
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(64.dp),
                        )
                        SmallAction(
                            text = "+0.5s",
                            onClick = {
                                offset += 500L
                                LyricsRepo.setOffset(context, track.id, offset)
                            },
                            container = scheme.surfaceContainerHighest,
                            content = scheme.onSurface,
                        )
                    }
                    Text(
                        "Lyrics too late? Use +. Too early? Use -.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Body
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (current) {
                    is LyricsUi.Loading -> Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    is LyricsUi.Missing -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            current.message.ifEmpty { "No lyrics found for this song." },
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You can import a .lrc file from your phone with the button above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    is LyricsUi.Found -> {
                        if (lines.isNotEmpty()) {
                            SyncedLines(
                                lines = lines,
                                index = index,
                                offset = offset,
                                size = prefs.lyricsSize,
                                onSeek = { vm.seekToMs(it) },
                            )
                        } else {
                            val st = MaterialTheme.typography.titleLarge
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                            ) {
                                Text(
                                    text = current.lyrics.plain,
                                    style = st.copy(
                                        fontSize = st.fontSize * prefs.lyricsSize,
                                        lineHeight = if (st.lineHeight.isSpecified) st.lineHeight * prefs.lyricsSize else st.lineHeight,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                                Spacer(Modifier.height(48.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedLines(
    lines: List<LyricLine>,
    index: Int,
    offset: Long,
    size: Float,
    onSeek: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val base = MaterialTheme.typography.headlineSmall

    // Keep the current line a third of the way down the screen.
    LaunchedEffect(index, lines.size) {
        if (index >= 0) {
            val h = listState.layoutInfo.viewportSize.height
            listState.animateScrollToItem(index, -(h / 3))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 120.dp, bottom = 320.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(lines) { i, line ->
            val active = i == index
            val alpha by animateFloatAsState(if (active) 1f else 0.42f, label = "lyricAlpha")
            Text(
                text = line.text.ifBlank { "♪" },
                style = base.copy(
                    fontSize = base.fontSize * size,
                    lineHeight = if (base.lineHeight.isSpecified) base.lineHeight * size else base.lineHeight,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                ),
                color = (if (active) scheme.primary else scheme.onSurface).copy(alpha = alpha),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onSeek(line.timeMs - offset) }
                    .padding(vertical = 4.dp),
            )
        }
    }
}
