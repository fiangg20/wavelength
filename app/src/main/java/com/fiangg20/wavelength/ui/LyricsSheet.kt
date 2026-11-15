@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.fiangg20.wavelength.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fiangg20.wavelength.data.Track
import com.fiangg20.wavelength.lyrics.LyricLine
import com.fiangg20.wavelength.lyrics.Lyrics
import com.fiangg20.wavelength.lyrics.LyricsRepo
import com.fiangg20.wavelength.lyrics.WordTiming
import java.util.Locale
import kotlin.math.abs

private sealed interface LyricsUi {
    data object Loading : LyricsUi
    data class Found(val lyrics: Lyrics) : LyricsUi
    data class Missing(val message: String) : LyricsUi
}

/** How the lines look, gathered from the settings. */
private data class LineStyle(
    val size: Float,
    val align: TextAlign,
    val highlight: Color,
    val dim: Float,
    val bold: Boolean,
    val zoom: Boolean,
    val wordMode: Int,
)

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

private fun readClipText(context: Context): String {
    return try {
        val cm = context.getSystemService(ClipboardManager::class.java)
        val clip = cm?.primaryClip
        if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() ?: "" else ""
    } catch (e: Exception) {
        ""
    }
}

private fun copyText(context: Context, label: String, text: String) {
    try {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Could not copy", Toast.LENGTH_SHORT).show()
    }
}

private fun shareText(context: Context, track: Track, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${track.title} - ${track.artist}")
        putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.artist}\n\n$text")
    }
    val chooser = Intent.createChooser(send, "Share lyrics").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Nothing to share with", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun LyricsSheet(vm: PlayerViewModel, track: Track, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val prefs = LocalPrefs.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Held as State objects and read only where needed, so the whole sheet does not redraw every few milliseconds.
    val posState = vm.position.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val playing = ui.isPlaying
    val speed = ui.speed

    var reload by remember { mutableIntStateOf(0) }
    var state by remember(track.id) { mutableStateOf<LyricsUi>(LyricsUi.Loading) }
    var offset by remember(track.id) { mutableLongStateOf(LyricsRepo.getOffset(context, track.id)) }
    var showStyle by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

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

    // A clock that glides between the player's position updates, so word highlighting moves smoothly.
    val clock = remember { mutableLongStateOf(vm.position.value) }
    LaunchedEffect(playing, speed) {
        if (!playing) {
            snapshotFlow { posState.value }.collect { clock.longValue = it }
        } else {
            var anchorPos = posState.value
            var anchorNs = withFrameNanos { it }
            var seen = anchorPos
            while (true) {
                val now = withFrameNanos { it }
                val p = posState.value
                if (p != seen) {
                    seen = p
                    val estimate = anchorPos + ((now - anchorNs) / 1_000_000f * speed).toLong()
                    if (abs(estimate - p) > 250L) {
                        anchorPos = p
                        anchorNs = now
                    }
                }
                clock.longValue = anchorPos + ((now - anchorNs) / 1_000_000f * speed).toLong()
            }
        }
    }

    val current = state
    val lines = if (current is LyricsUi.Found) current.lyrics.synced else emptyList()
    val totalOffset = offset + prefs.lyricsShiftMs
    val index by remember(lines, totalOffset) {
        derivedStateOf { currentIndex(lines, clock.longValue + totalOffset) }
    }

    val lineStyle = LineStyle(
        size = prefs.lyricsSize,
        align = when (prefs.lyricsAlign) {
            1 -> TextAlign.Center
            2 -> TextAlign.End
            else -> TextAlign.Start
        },
        highlight = when (prefs.lyricsColor) {
            1 -> scheme.onSurface
            2 -> scheme.tertiary
            else -> scheme.primary
        },
        dim = prefs.lyricsDim,
        bold = prefs.lyricsBold,
        zoom = prefs.lyricsZoom,
        wordMode = prefs.lyricsWordMode,
    )

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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = buildString {
                        append(track.artist)
                        if (current is LyricsUi.Found) {
                            append("  ·  ")
                            append(
                                when {
                                    current.lyrics.hasRealWords -> "Word-synced"
                                    current.lyrics.synced.isNotEmpty() ->
                                        if (prefs.lyricsWordMode == 1) "Line-synced, words estimated" else "Line-synced"
                                    else -> "Not synced"
                                }
                            )
                            append(" · ")
                            append(current.lyrics.source)
                        }
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ExpressiveButton(
                    onClick = { showStyle = !showStyle },
                    modifier = Modifier.size(44.dp),
                    containerColor = if (showStyle) scheme.primaryContainer else scheme.surfaceContainerHighest,
                    contentColor = if (showStyle) scheme.onPrimaryContainer else scheme.onSurface,
                    restCorner = 22.dp,
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Lyrics style")
                }
                Spacer(Modifier.width(6.dp))
                Box {
                    ExpressiveButton(
                        onClick = { menu = true },
                        modifier = Modifier.size(44.dp),
                        containerColor = scheme.surfaceContainerHighest,
                        contentColor = scheme.onSurface,
                        restCorner = 22.dp,
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Import .lrc file") },
                            onClick = {
                                menu = false
                                picker.launch(arrayOf("*/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Paste from clipboard") },
                            onClick = {
                                menu = false
                                val text = readClipText(context)
                                if (text.isBlank()) {
                                    Toast.makeText(context, "The clipboard is empty", Toast.LENGTH_SHORT).show()
                                } else {
                                    val saved = LyricsRepo.saveImported(context, track.id, text)
                                    state = if (saved != null) LyricsUi.Found(saved)
                                    else LyricsUi.Missing("The clipboard has no lyrics in it.")
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Search again") },
                            enabled = state !is LyricsUi.Loading,
                            onClick = {
                                menu = false
                                reload++
                            },
                        )
                        if (current is LyricsUi.Found) {
                            DropdownMenuItem(
                                text = { Text("Copy lyrics") },
                                onClick = {
                                    menu = false
                                    copyText(context, track.title, current.lyrics.asText())
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share lyrics") },
                                onClick = {
                                    menu = false
                                    shareText(context, track, current.lyrics.asText())
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove saved lyrics") },
                                onClick = {
                                    menu = false
                                    LyricsRepo.forget(context, track.id)
                                    state = LyricsUi.Missing("Saved lyrics removed.")
                                },
                            )
                        }
                    }
                }
            }

            // Quick style panel
            AnimatedVisibility(
                visible = showStyle,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = scheme.surfaceContainer,
                ) {
                    Column(
                        Modifier
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        if (lines.isNotEmpty()) {
                            Label("This song's timing")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                NudgeButton("-0.5") { offset -= 500L; LyricsRepo.setOffset(context, track.id, offset) }
                                NudgeButton("-0.1") { offset -= 100L; LyricsRepo.setOffset(context, track.id, offset) }
                                Text(
                                    offsetLabel(offset),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = scheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                                NudgeButton("+0.1") { offset += 100L; LyricsRepo.setOffset(context, track.id, offset) }
                                NudgeButton("+0.5") { offset += 500L; LyricsRepo.setOffset(context, track.id, offset) }
                            }
                            Hint("Lyrics too late? Use +. Too early? Use -. Saved for this song only.")
                            if (offset != 0L) {
                                Spacer(Modifier.height(4.dp))
                                SmallAction(
                                    text = "Reset timing",
                                    onClick = {
                                        offset = 0L
                                        LyricsRepo.setOffset(context, track.id, 0L)
                                    },
                                    container = scheme.surfaceContainerHighest,
                                    content = scheme.onSurface,
                                )
                            }
                            Gap()
                        }
                        LyricsStyleControls(prefs = prefs, onChange = { block -> vm.updatePrefs(block) })
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Body
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (current) {
                    is LyricsUi.Loading -> Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
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
                            "Open the menu at the top to import a .lrc file, paste lyrics from the clipboard, or search again. Enhanced .lrc files with word timing get real word-by-word highlighting.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    is LyricsUi.Found -> {
                        if (lines.isNotEmpty()) {
                            key(track.id) {
                                SyncedLines(
                                    lines = lines,
                                    index = index,
                                    clock = clock,
                                    totalOffset = totalOffset,
                                    style = lineStyle,
                                    spacing = prefs.lyricsSpacing,
                                    onSeek = { vm.seekToMs(it) },
                                    onCopyLine = { copyText(context, track.title, it) },
                                )
                            }
                        } else {
                            val st = MaterialTheme.typography.titleLarge
                            SelectionContainer {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 24.dp, vertical = 16.dp)
                                ) {
                                    Text(
                                        text = current.lyrics.plain,
                                        textAlign = lineStyle.align,
                                        modifier = Modifier.fillMaxWidth(),
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
}

@Composable
private fun NudgeButton(text: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    ExpressiveButton(
        onClick = onClick,
        modifier = Modifier.height(38.dp),
        containerColor = scheme.surfaceContainerHighest,
        contentColor = scheme.onSurface,
        restCorner = 19.dp,
        pressedCorner = 8.dp,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun SyncedLines(
    lines: List<LyricLine>,
    index: Int,
    clock: androidx.compose.runtime.LongState,
    totalOffset: Long,
    style: LineStyle,
    spacing: Float,
    onSeek: (Long) -> Unit,
    onCopyLine: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }

    // Dragging the list yourself stops the auto-scroll until you ask for it back.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) follow = false
        }
    }

    // Keep the current line a third of the way down the screen.
    LaunchedEffect(index, follow) {
        if (follow && index >= 0) {
            val h = listState.layoutInfo.viewportSize.height
            listState.animateScrollToItem(index, -(h / 3))
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 120.dp, bottom = 320.dp),
            verticalArrangement = Arrangement.spacedBy((18f * spacing).dp),
        ) {
            itemsIndexed(lines) { i, line ->
                val next = if (i + 1 < lines.size) lines[i + 1].timeMs else 0L
                LyricRow(
                    line = line,
                    nextStart = next,
                    active = i == index,
                    clock = clock,
                    totalOffset = totalOffset,
                    style = style,
                    onClick = {
                        follow = true
                        onSeek(line.timeMs - totalOffset)
                    },
                    onLongClick = { onCopyLine(line.text) },
                )
            }
        }

        AnimatedVisibility(
            visible = !follow && index >= 0,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val scheme = MaterialTheme.colorScheme
            ExpressiveButton(
                onClick = { follow = true },
                modifier = Modifier.height(48.dp),
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
                restCorner = 24.dp,
            ) {
                Text(
                    "Back to current line",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    nextStart: Long,
    active: Boolean,
    clock: androidx.compose.runtime.LongState,
    totalOffset: Long,
    style: LineStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val base = MaterialTheme.typography.headlineSmall

    val words = remember(line, nextStart, style.wordMode) {
        WordTiming.wordsFor(line, nextStart, style.wordMode)
    }
    val scale by animateFloatAsState(
        targetValue = if (active || !style.zoom) 1f else 0.93f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "lyricScale",
    )
    val alpha by animateFloatAsState(if (active) 1f else style.dim, label = "lyricAlpha")
    val origin = remember(style.align) {
        TransformOrigin(
            when (style.align) {
                TextAlign.Center -> 0.5f
                TextAlign.End -> 1f
                else -> 0f
            },
            0.5f,
        )
    }

    val textStyle = base.copy(
        fontSize = base.fontSize * style.size,
        lineHeight = if (base.lineHeight.isSpecified) base.lineHeight * style.size else base.lineHeight,
        fontWeight = if (active && style.bold) FontWeight.ExtraBold else FontWeight.SemiBold,
        textAlign = style.align,
    )

    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = origin
            }
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 4.dp)
    ) {
        if (active && words.isNotEmpty()) {
            // Reading the clock here redraws only this one line, once per frame.
            val t = clock.longValue + totalOffset
            val sung = style.highlight
            val waiting = scheme.onSurface.copy(alpha = (style.dim + 0.18f).coerceIn(0.3f, 0.85f))
            val text = buildAnnotatedString {
                for (w in words) {
                    val span = (w.endMs - w.startMs).coerceAtLeast(1L)
                    val p = ((t - w.startMs).toFloat() / span).coerceIn(0f, 1f)
                    withStyle(SpanStyle(color = lerp(waiting, sung, p))) { append(w.text) }
                }
            }
            Text(text = text, style = textStyle, modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                text = line.text.ifBlank { "♪" },
                style = textStyle,
                color = (if (active) style.highlight else scheme.onSurface).copy(alpha = alpha),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
