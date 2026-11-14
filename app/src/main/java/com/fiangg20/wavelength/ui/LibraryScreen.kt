@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.fiangg20.wavelength.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fiangg20.wavelength.data.Album
import com.fiangg20.wavelength.data.Artist
import com.fiangg20.wavelength.data.Track

// ---------------------------------------------------------------------------------------------
// Library (tabs)
// ---------------------------------------------------------------------------------------------

@Composable
fun LibraryScreen(
    vm: PlayerViewModel,
    tab: Int,
    onTab: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenDetail: (String) -> Unit,
    bottomPad: Dp,
) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val query by vm.search.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }

    LaunchedEffect(searching) {
        if (searching) {
            try {
                focus.requestFocus()
            } catch (e: Exception) {
                // The field is not attached yet; the user can tap it.
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.weight(1f),
            )
            ExpressiveButton(
                onClick = {
                    if (searching) {
                        searching = false
                        vm.setQuery("")
                    } else {
                        searching = true
                    }
                },
                modifier = Modifier.size(52.dp),
                containerColor = if (searching) scheme.primary else scheme.surfaceContainerHigh,
                contentColor = if (searching) scheme.onPrimary else scheme.onSurface,
                restCorner = if (searching) 16.dp else 26.dp,
            ) {
                Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, null)
            }
            Spacer(Modifier.width(8.dp))
            ExpressiveButton(
                onClick = onOpenDownloads,
                modifier = Modifier.size(52.dp),
                containerColor = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
                restCorner = 26.dp,
            ) {
                Icon(Icons.Rounded.Download, contentDescription = "Downloader")
            }
            Spacer(Modifier.width(8.dp))
            ExpressiveButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(52.dp),
                containerColor = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
                restCorner = 26.dp,
            ) {
                Icon(Icons.Rounded.Settings, null)
            }
        }

        ConnectedChoice(
            options = listOf("Songs", "Albums", "Artists", "Liked"),
            selected = tab,
            onSelect = onTab,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        AnimatedVisibility(
            visible = searching,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            TextField(
                value = query,
                onValueChange = { vm.setQuery(it) },
                placeholder = { Text("Songs, artists, albums") },
                singleLine = true,
                shape = CircleShape,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .focusRequester(focus),
            )
        }

        Spacer(Modifier.height(8.dp))

        if (loading && tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (tracks.isEmpty()) {
            EmptyState(
                message = if (query.isNotBlank()) "Nothing matches that search" else "No music found on this phone",
            )
        } else {
            when (tab) {
                0 -> SongList(
                    tracks = tracks,
                    ui = ui,
                    bottomPad = bottomPad,
                    onPlay = { vm.playQueue(tracks, it) },
                    onPlayAll = { vm.playQueue(tracks, 0, shuffle = false) },
                    onShuffle = { vm.playShuffled(tracks) },
                    vm = vm,
                )
                1 -> AlbumGrid(albums, bottomPad) { onOpenDetail("album:${it.id}") }
                2 -> ArtistList(artists, bottomPad) { onOpenDetail("artist:${it.name}") }
                else -> if (liked.isEmpty()) {
                    EmptyState(message = "No liked songs yet. Long-press a song to like it.")
                } else {
                    SongList(
                        tracks = liked,
                        ui = ui,
                        bottomPad = bottomPad,
                        onPlay = { vm.playQueue(liked, it) },
                        onPlayAll = { vm.playQueue(liked, 0, shuffle = false) },
                        onShuffle = { vm.playShuffled(liked) },
                        vm = vm,
                        showSort = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Songs
// ---------------------------------------------------------------------------------------------

@Composable
private fun SongList(
    tracks: List<Track>,
    ui: PlaybackUi,
    bottomPad: Dp,
    onPlay: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    vm: PlayerViewModel,
    showSort: Boolean = true,
) {
    val favs by vm.favorites.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPad),
    ) {
        item(key = "actions") {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(
                    text = "${"%,d".format(tracks.size)} songs",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                PlayShuffleRow(onPlayAll = onPlayAll, onShuffle = onShuffle) {
                    if (showSort) SortMenu(vm)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, t ->
            val isLiked = t.id in favs
            TrackRow(
                track = t,
                active = ui.current?.id == t.id,
                playing = ui.isPlaying,
                onClick = { onPlay(index) },
                actions = TrackActions(
                    liked = isLiked,
                    onPlayNext = { vm.playNext(t) },
                    onAddToQueue = { vm.addToQueue(t) },
                    onToggleLike = { vm.toggleFavorite(t.id) },
                ),
            )
        }
    }
}

@Composable
fun PlayShuffleRow(
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpressiveButton(
            onClick = onPlayAll,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
            restCorner = 28.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Play", style = MaterialTheme.typography.labelLarge)
            }
        }
        ExpressiveButton(
            onClick = onShuffle,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            restCorner = 28.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shuffle, null)
                Spacer(Modifier.width(6.dp))
                Text("Shuffle", style = MaterialTheme.typography.labelLarge)
            }
        }
        trailing()
    }
}

@Composable
private fun SortMenu(vm: PlayerViewModel) {
    val prefs = LocalPrefs.current
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box {
        ExpressiveButton(
            onClick = { open = true },
            modifier = Modifier.size(56.dp),
            containerColor = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
            restCorner = 18.dp,
        ) {
            Icon(Icons.Rounded.FilterList, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf("Title", "Artist", "Album", "Recently added", "Length").forEachIndexed { i, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        vm.updatePrefs { it.copy(sortBy = i) }
                        open = false
                    },
                    trailingIcon = {
                        if (prefs.sortBy == i) Icon(Icons.Rounded.Check, null)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (prefs.sortAsc) "Ascending" else "Descending") },
                onClick = {
                    vm.updatePrefs { it.copy(sortAsc = !it.sortAsc) }
                    open = false
                },
            )
        }
    }
}

/** What the long-press menu on a song can do. */
class TrackActions(
    val liked: Boolean,
    val onPlayNext: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onToggleLike: () -> Unit,
)

@Composable
fun TrackRow(
    track: Track,
    active: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    actions: TrackActions? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val prefs = LocalPrefs.current
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        targetValue = if (active) scheme.secondaryContainer else Color.Transparent,
        label = "rowBg",
    )
    val vPad = if (prefs.compactList) 6.dp else 10.dp
    val artSize = if (prefs.compactList) 44.dp else 54.dp
    val titleColor = if (active) scheme.onSecondaryContainer else scheme.onSurface
    val subColor = if (active) scheme.onSecondaryContainer.copy(alpha = 0.75f) else scheme.onSurfaceVariant

    Box {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(bg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (actions != null) ({ menu = true }) else null,
            )
            .padding(horizontal = 12.dp, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefs.showListArt) {
            Artwork(
                track = track,
                modifier = Modifier.size(artSize),
                shape = MaterialTheme.shapes.small,
                px = 160,
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = subColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (actions != null && actions.liked) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        if (active) {
            EqBars(color = scheme.onSecondaryContainer, animate = playing)
        } else {
            Text(
                text = formatTime(track.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
    if (actions != null) {
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Play next") },
                onClick = {
                    actions.onPlayNext()
                    menu = false
                },
            )
            DropdownMenuItem(
                text = { Text("Add to queue") },
                onClick = {
                    actions.onAddToQueue()
                    menu = false
                },
            )
            DropdownMenuItem(
                text = { Text(if (actions.liked) "Remove from liked" else "Like") },
                leadingIcon = {
                    Icon(if (actions.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null)
                },
                onClick = {
                    actions.onToggleLike()
                    menu = false
                },
            )
        }
    }
    }
}

// ---------------------------------------------------------------------------------------------
// Albums and artists
// ---------------------------------------------------------------------------------------------

@Composable
private fun AlbumGrid(albums: List<Album>, bottomPad: Dp, onClick: (Album) -> Unit) {
    val cell = when (LocalPrefs.current.albumGrid) {
        0 -> 110.dp
        2 -> 190.dp
        else -> 150.dp
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cell),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                Modifier
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onClick(album) }
            ) {
                Artwork(
                    track = album.tracks.firstOrNull(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = MaterialTheme.shapes.large,
                    px = 400,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    album.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Text(
                    album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ArtistList(artists: List<Artist>, bottomPad: Dp, onClick: (Artist) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPad),
    ) {
        items(artists, key = { it.name }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onClick(artist) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val initial = artist.name.firstOrNull { it.isLetterOrDigit() }
                    ?.uppercaseChar()?.toString() ?: "?"
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CookieShape(8, 0.12f))
                        .background(scheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initial,
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onTertiaryContainer,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        artist.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val songs = artist.tracks.size
                    val albums = artist.albumCount
                    Text(
                        "$songs ${if (songs == 1) "song" else "songs"} · $albums ${if (albums == 1) "album" else "albums"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Album / artist detail
// ---------------------------------------------------------------------------------------------

@Composable
fun DetailScreen(
    title: String,
    subtitle: String,
    isArtist: Boolean,
    tracks: List<Track>,
    ui: PlaybackUi,
    bottomPad: Dp,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
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
                    Artwork(
                        track = tracks.firstOrNull(),
                        modifier = Modifier.size(132.dp),
                        shape = if (isArtist) CookieShape(9, 0.1f) else MaterialTheme.shapes.large,
                        px = 400,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "buttons") {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                    PlayShuffleRow(onPlayAll = onPlayAll, onShuffle = onShuffle)
                }
            }
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, t ->
                TrackRow(
                    track = t,
                    active = ui.current?.id == t.id,
                    playing = ui.isPlaying,
                    onClick = { onPlay(index) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Mini player
// ---------------------------------------------------------------------------------------------

@Composable
fun MiniPlayer(
    ui: PlaybackUi,
    position: Long,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = LocalPrefs.current
    val scheme = MaterialTheme.colorScheme
    val progress = if (ui.durationMs > 0L) (position.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(scheme.primaryContainer)
            .clickable(onClick = onOpen)
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 10.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                track = ui.current,
                modifier = Modifier.size(52.dp),
                shape = artShapeFor(prefs, 0.09f),
                px = 160,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = ui.current?.title ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = ui.current?.artist ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            ExpressiveButton(
                onClick = onToggle,
                modifier = Modifier.size(52.dp),
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
                restCorner = if (ui.isPlaying) 17.dp else 26.dp,
            ) {
                Icon(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
            }
            if (prefs.miniSkip) {
                Spacer(Modifier.width(4.dp))
                ExpressiveButton(
                    onClick = onNext,
                    modifier = Modifier.size(48.dp),
                    containerColor = Color.Transparent,
                    contentColor = scheme.onPrimaryContainer,
                    restCorner = 24.dp,
                ) {
                    Icon(Icons.Rounded.SkipNext, null)
                }
            }
        }
        if (prefs.miniProgress) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(scheme.primary)
            )
        }
    }
}
