@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fiangg20.wavelength.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fiangg20.wavelength.data.LIKED_ID
import com.fiangg20.wavelength.data.Track

/** Builds the long-press menu for a song, wired to the view model. */
@Composable
fun rememberTrackActions(vm: PlayerViewModel, onAddToPlaylist: (Track) -> Unit): (Track) -> TrackActions {
    val favs by vm.favorites.collectAsStateWithLifecycle()
    return { t ->
        TrackActions(
            liked = t.id in favs,
            onPlayNext = { vm.playNext(t) },
            onAddToQueue = { vm.addToQueue(t) },
            onToggleLike = { vm.toggleFavorite(t.id) },
            onAddToPlaylist = { onAddToPlaylist(t) },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Small dialogs
// ---------------------------------------------------------------------------------------------

@Composable
fun NamePlaylistDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) onConfirm(name.trim())
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Playlists tab
// ---------------------------------------------------------------------------------------------

@Composable
private fun ListRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun songsLabel(n: Int): String = "$n ${if (n == 1) "song" else "songs"}"

@Composable
private fun LikedTile() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CookieShape(8, 0.12f))
            .background(scheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = scheme.onPrimaryContainer)
    }
}

@Composable
private fun EmptyTile() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(MaterialTheme.shapes.small)
            .background(scheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = scheme.onSurfaceVariant)
    }
}

@Composable
fun PlaylistsTab(vm: PlayerViewModel, bottomPad: Dp, onOpen: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val lists by vm.playlists.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPad),
    ) {
        item(key = "new") {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                ExpressiveButton(
                    onClick = { creating = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    containerColor = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                    restCorner = 28.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("New playlist", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        item(key = "liked") {
            ListRow(
                title = "Liked songs",
                subtitle = songsLabel(liked.size),
                onClick = { onOpen(LIKED_ID) },
                leading = { LikedTile() },
            )
        }
        items(lists, key = { it.id }) { p ->
            ListRow(
                title = p.name,
                subtitle = songsLabel(p.tracks.size),
                onClick = { onOpen(p.id) },
                leading = {
                    val first = p.tracks.firstOrNull()
                    if (first == null) {
                        EmptyTile()
                    } else {
                        Artwork(
                            track = first,
                            modifier = Modifier.size(54.dp),
                            shape = MaterialTheme.shapes.small,
                            px = 160,
                        )
                    }
                },
            )
        }
        if (lists.isEmpty()) {
            item(key = "hint") {
                Text(
                    "Make a playlist here, or long-press any song and choose Add to playlist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
    }

    if (creating) {
        NamePlaylistDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                val id = vm.createPlaylist(name)
                creating = false
                onOpen(id)
            },
            onDismiss = { creating = false },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// One playlist
// ---------------------------------------------------------------------------------------------

@Composable
fun PlaylistScreen(
    vm: PlayerViewModel,
    id: String,
    ui: PlaybackUi,
    bottomPad: Dp,
    onBack: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val lists by vm.playlists.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    val actionsFor = rememberTrackActions(vm, onAddToPlaylist)
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val isLiked = id == LIKED_ID
    val playlist = lists.firstOrNull { it.id == id }

    // A brand-new list can take a moment to reach this screen, so just draw nothing until it does.
    if (playlist == null && !isLiked) return

    val tracks = if (isLiked) liked else (playlist?.tracks ?: emptyList())
    val name = if (isLiked) "Liked songs" else (playlist?.name ?: "")

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
            Spacer(Modifier.weight(1f))
            if (!isLiked) {
                Box {
                    ExpressiveButton(
                        onClick = { menu = true },
                        modifier = Modifier.size(52.dp),
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                        restCorner = 26.dp,
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menu = false
                                renaming = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete playlist") },
                            onClick = {
                                menu = false
                                deleting = true
                            },
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPad),
        ) {
            item(key = "header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val first = tracks.firstOrNull()
                    if (isLiked) {
                        Box(
                            modifier = Modifier
                                .size(132.dp)
                                .clip(CookieShape(10, 0.1f))
                                .background(scheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier.size(56.dp),
                            )
                        }
                    } else if (first == null) {
                        Box(
                            modifier = Modifier
                                .size(132.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(scheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.LibraryMusic,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    } else {
                        Artwork(
                            track = first,
                            modifier = Modifier.size(132.dp),
                            shape = MaterialTheme.shapes.large,
                            px = 400,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            songsLabel(tracks.size),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (tracks.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (isLiked) {
                            "Nothing liked yet. Tap the heart in the player, or long-press a song."
                        } else {
                            "No songs yet. Long-press any song and choose Add to playlist."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp),
                    )
                }
            } else {
                item(key = "buttons") {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                        PlayShuffleRow(
                            onPlayAll = { vm.playQueue(tracks, 0, shuffle = false) },
                            onShuffle = { vm.playShuffled(tracks) },
                        )
                    }
                }
            }
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, t ->
                val base = actionsFor(t)
                val actions = if (isLiked) {
                    base
                } else {
                    base.copy(
                        onRemove = { vm.removeFromPlaylist(id, t.id) },
                        onMoveUp = if (index > 0) ({ vm.movePlaylistTrack(id, t.id, -1) }) else null,
                        onMoveDown = if (index < tracks.lastIndex) ({ vm.movePlaylistTrack(id, t.id, 1) }) else null,
                    )
                }
                TrackRow(
                    track = t,
                    active = ui.current?.id == t.id,
                    playing = ui.isPlaying,
                    onClick = { vm.playQueue(tracks, index) },
                    actions = actions,
                )
            }
        }
    }

    if (renaming) {
        NamePlaylistDialog(
            title = "Rename playlist",
            initial = name,
            confirmLabel = "Save",
            onConfirm = { newName ->
                vm.renamePlaylist(id, newName)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete \"$name\"?") },
            text = { Text("The songs stay on your phone. Only the playlist is removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = false
                        vm.deletePlaylist(id)
                        onBack()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text("Cancel") }
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// "Add to playlist" sheet
// ---------------------------------------------------------------------------------------------

@Composable
fun AddToPlaylistSheet(vm: PlayerViewModel, track: Track, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val lists by vm.playlists.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var creating by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text("Add to playlist", style = MaterialTheme.typography.headlineSmall)
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            ExpressiveButton(
                onClick = { creating = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
                restCorner = 28.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New playlist", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(lists, key = { it.id }) { p ->
                    val has = p.tracks.any { it.id == track.id }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .clickable {
                                vm.addToPlaylist(p.id, p.name, listOf(track))
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                songsLabel(p.tracks.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        if (has) {
                            Icon(Icons.Rounded.Check, contentDescription = "Already added", tint = scheme.primary)
                        }
                    }
                }
            }
            if (lists.isEmpty()) {
                Text(
                    "No playlists yet. Make your first one above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }

    if (creating) {
        NamePlaylistDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                vm.createPlaylist(name, track)
                creating = false
                onDismiss()
            },
            onDismiss = { creating = false },
        )
    }
}
