package com.fiangg20.wavelength.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fiangg20.wavelength.data.Album
import com.fiangg20.wavelength.data.LIKED_ID
import com.fiangg20.wavelength.data.Track

private class HomeList(val route: String, val title: String, val tracks: List<Track>, val heart: Boolean)

@Composable
fun HomeScreen(
    vm: PlayerViewModel,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    bottomPad: Dp,
) {
    val scheme = MaterialTheme.colorScheme
    val ui by vm.ui.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val recent by vm.recentlyPlayed.collectAsStateWithLifecycle()
    val added by vm.recentlyAdded.collectAsStateWithLifecycle()
    val most by vm.mostPlayed.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val actionsFor = rememberTrackActions(vm, onAddToPlaylist)

    // Picked once per visit so the row does not reshuffle while you scroll.
    val discover = remember(albums.size) { albums.shuffled().take(12) }
    val currentId = ui.current?.id

    val hero = ui.current ?: recent.firstOrNull() ?: library.firstOrNull()
    val heroLabel = when {
        ui.hasMedia && ui.current != null -> "Now playing"
        recent.isNotEmpty() -> "Pick up where you left off"
        else -> "Start listening"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomPad),
    ) {
        item(key = "header") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Home",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f),
                )
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
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }
        }

        if (library.isEmpty()) {
            item(key = "empty") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 80.dp, start = 32.dp, end = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            "No music found yet. Add songs to your phone, or save some with the downloader.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            return@LazyColumn
        }

        if (hero != null) {
            item(key = "hero") {
                HeroCard(
                    track = hero,
                    label = heroLabel,
                    songCount = library.size,
                    playing = ui.isPlaying && currentId == hero.id,
                    onPlay = {
                        if (currentId == hero.id) {
                            vm.togglePlay()
                        } else {
                            val queue = if (recent.any { it.id == hero.id }) recent else library
                            vm.playQueue(queue, queue.indexOfFirst { it.id == hero.id }.coerceAtLeast(0))
                        }
                    },
                )
            }
        }

        item(key = "bento") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(196.dp)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BentoTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    container = scheme.tertiaryContainer,
                    content = scheme.onTertiaryContainer,
                    shape = RoundedCornerShape(40.dp),
                    onClick = { onOpenDetail("playlist:$LIKED_ID") },
                ) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CookieShape(6, 0.22f))
                            .background(scheme.tertiary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = scheme.onTertiary)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Liked songs", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${liked.size} ${if (liked.size == 1) "song" else "songs"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onTertiaryContainer.copy(alpha = 0.75f),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BentoTile(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        container = scheme.primary,
                        content = scheme.onPrimary,
                        shape = RoundedCornerShape(32.dp),
                        onClick = { vm.playShuffled(library) },
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(Modifier.weight(1f))
                        Text("Shuffle all", style = MaterialTheme.typography.titleMedium)
                    }
                    BentoTile(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        container = scheme.secondaryContainer,
                        content = scheme.onSecondaryContainer,
                        shape = RoundedCornerShape(32.dp),
                        onClick = {
                            val pick = albums.randomOrNull()
                            if (pick != null) vm.playQueue(pick.tracks, 0) else vm.playShuffled(library)
                        },
                    ) {
                        Icon(Icons.Rounded.Album, contentDescription = null)
                        Spacer(Modifier.weight(1f))
                        Text("Surprise me", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "recentTitle") { HomeSection("Jump back in", "Clear") { vm.clearHistory() } }
            item(key = "recentRow") {
                val list = recent.take(15)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                        TrackCard(t, active = t.id == currentId, shape = CookieShape(12, 0.06f)) { vm.playQueue(list, i) }
                    }
                }
            }
        }

        if (added.isNotEmpty()) {
            item(key = "addedTitle") { HomeSection("Recently added") }
            item(key = "addedRow") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(added, key = { _, t -> t.id }) { i, t ->
                        TrackCard(t, active = t.id == currentId, shape = RoundedCornerShape(44.dp)) { vm.playQueue(added, i) }
                    }
                }
            }
        }

        item(key = "listsTitle") { HomeSection("Your playlists") }
        item(key = "listsRow") {
            val lists = buildList {
                add(HomeList("playlist:$LIKED_ID", "Liked songs", liked, true))
                playlists.forEach { add(HomeList("playlist:${it.id}", it.name, it.tracks, false)) }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(lists, key = { _, l -> l.route }) { _, l ->
                    PlaylistCard(l, RoundedCornerShape(36.dp)) { onOpenDetail(l.route) }
                }
            }
        }

        if (most.size >= 3) {
            item(key = "mostTitle") { HomeSection("Most played") }
            itemsIndexed(most.take(5), key = { _, t -> "most${t.id}" }) { i, t ->
                Box(Modifier.padding(horizontal = 12.dp)) {
                    TrackRow(
                        track = t,
                        active = t.id == currentId,
                        playing = ui.isPlaying && t.id == currentId,
                        onClick = { vm.playQueue(most, i) },
                        actions = actionsFor(t),
                    )
                }
            }
        }

        if (discover.isNotEmpty()) {
            item(key = "discoverTitle") { HomeSection("Albums to revisit") }
            item(key = "discoverRow") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(discover, key = { _, a -> a.id }) { _, a ->
                        AlbumCard(a, CookieShape(7, 0.1f)) { onOpenDetail("album:${a.id}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSection(title: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun HeroCard(track: Track, label: String, songCount: Int, playing: Boolean, onPlay: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(44.dp))
            .background(scheme.primaryContainer)
            .clickable(onClick = onPlay)
            .padding(start = 24.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Surface(shape = CircleShape, color = scheme.primary.copy(alpha = 0.16f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "$songCount songs on this phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Artwork(
                track = track,
                modifier = Modifier.size(156.dp),
                shape = CookieShape(9, 0.1f),
                px = 500,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = scheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    track.artist,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            ExpressiveButton(
                onClick = onPlay,
                modifier = Modifier.size(68.dp),
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
                restCorner = 34.dp,
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@Composable
private fun BentoTile(
    modifier: Modifier,
    container: Color,
    content: Color,
    shape: Shape,
    onClick: () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(shape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides content) { body() }
    }
}

@Composable
private fun TrackCard(track: Track, active: Boolean, shape: Shape, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(150.dp)
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Artwork(
            track = track,
            modifier = Modifier.size(150.dp),
            shape = shape,
            px = 400,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            track.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (active) scheme.primary else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun AlbumCard(album: Album, shape: Shape, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(150.dp)
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        AlbumArtwork(
            tracks = album.tracks,
            modifier = Modifier.size(150.dp),
            shape = shape,
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
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun PlaylistCard(list: HomeList, shape: Shape, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(150.dp)
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Box {
            if (list.heart && list.tracks.isEmpty()) {
                Box(
                    Modifier
                        .size(150.dp)
                        .clip(shape)
                        .background(scheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = scheme.onTertiaryContainer,
                        modifier = Modifier.size(52.dp),
                    )
                }
            } else {
                AlbumArtwork(
                    tracks = list.tracks,
                    modifier = Modifier.size(150.dp),
                    shape = shape,
                    px = 400,
                )
                if (list.heart) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(scheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = scheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            list.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        val n = list.tracks.size
        Text(
            "$n ${if (n == 1) "song" else "songs"}",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Floating navigation between Home and Library
// ---------------------------------------------------------------------------------------------

@Composable
fun FloatingNav(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val homeWeight by animateFloatAsState(if (selected == 0) 1.25f else 1f, label = "navHome")
    val libraryWeight by animateFloatAsState(if (selected == 1) 1.25f else 1f, label = "navLibrary")
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = scheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NavItem("Home", Icons.Rounded.Home, selected == 0, Modifier.weight(homeWeight)) { onSelect(0) }
            NavItem("Library", Icons.Rounded.LibraryMusic, selected == 1, Modifier.weight(libraryWeight)) { onSelect(1) }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.primaryContainer else Color.Transparent, label = "navBg")
    val fg by animateColorAsState(
        if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        label = "navFg",
    )
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = fg)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}
