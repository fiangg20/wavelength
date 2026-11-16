package com.fiangg20.wavelength.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.max

/** The play queue. Long-press a song to move it, remove it, or send it to the front or back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(vm: PlayerViewModel, ui: PlaybackUi, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var saving by remember { mutableStateOf(false) }
    val last = ui.queue.lastIndex

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerLow,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Up next",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { saving = true }, enabled = ui.queue.isNotEmpty()) { Text("Save as playlist") }
            TextButton(onClick = { vm.clearUpcoming() }, enabled = ui.index < last) { Text("Clear") }
        }
        Text(
            text = "Long-press a song to move it or take it out of the queue.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = max(0, ui.index - 2))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        ) {
            itemsIndexed(ui.queue) { i, t ->
                val toNext = if (i > ui.index) ui.index + 1 else ui.index
                val actions = TrackActions(
                    liked = t.id in favs,
                    onPlayNext = { vm.moveQueueItem(i, toNext) },
                    onAddToQueue = { vm.moveQueueItem(i, last) },
                    onToggleLike = { vm.toggleFavorite(t.id) },
                    onRemove = if (i != ui.index) ({ vm.removeQueueItem(i) }) else null,
                    onMoveUp = if (i > 0) ({ vm.moveQueueItem(i, i - 1) }) else null,
                    onMoveDown = if (i < last) ({ vm.moveQueueItem(i, i + 1) }) else null,
                    removeLabel = "Remove from queue",
                    queueMode = true,
                )
                TrackRow(
                    track = t,
                    active = i == ui.index,
                    playing = ui.isPlaying,
                    onClick = { vm.skipTo(i) },
                    actions = actions,
                )
            }
        }
    }

    if (saving) {
        NamePlaylistDialog(
            title = "Save queue as playlist",
            initial = "Queue",
            confirmLabel = "Save",
            onConfirm = { name ->
                vm.saveQueueAsPlaylist(name)
                saving = false
            },
            onDismiss = { saving = false },
        )
    }
}
