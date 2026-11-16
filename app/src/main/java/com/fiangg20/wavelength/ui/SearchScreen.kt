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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fiangg20.wavelength.data.Track
import com.fiangg20.wavelength.lyrics.LyricsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun matches(text: String, words: List<String>): Boolean {
    val t = text.lowercase()
    return words.all { t.contains(it) }
}

/** One search box for songs, albums, artists, playlists and words that appear in saved lyrics. */
@Composable
fun SearchScreen(
    vm: PlayerViewModel,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    bottomPad: Dp,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var query by rememberSaveable { mutableStateOf("") }

    val library by vm.library.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val recent by vm.recentSearches.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val actionsFor = rememberTrackActions(vm, onAddToPlaylist)

    LaunchedEffect(Unit) { requester.requestFocus() }

    val words = remember(query) { query.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotEmpty() } }
    val active = words.isNotEmpty()

    val songHits = remember(words, library) {
        if (!active) emptyList() else library.filter { matches("${it.title} ${it.artist} ${it.album}", words) }.take(30)
    }
    val albumHits = remember(words, albums) {
        if (!active) emptyList() else albums.filter { matches("${it.title} ${it.artist}", words) }.take(8)
    }
    val artistHits = remember(words, artists) {
        if (!active) emptyList() else artists.filter { matches(it.name, words) }.take(8)
    }
    val playlistHits = remember(words, playlists) {
        if (!active) emptyList() else playlists.filter { matches(it.name, words) }.take(8)
    }
    // Words from saved lyrics. The lookup reads files, so it waits for a pause in typing.
    val lyricHits by produceState(emptyList<Pair<Long, String>>(), query) {
        val q = query.trim()
        if (q.length < 3) {
            value = emptyList()
        } else {
            delay(300L)
            value = withContext(Dispatchers.IO) { LyricsRepo.searchCached(context, q) }
        }
    }
    val byId = remember(library) { library.associateBy { it.id } }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Search", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Songs, albums, artists, lyrics") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") }
                }
            },
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                vm.rememberSearch(query)
                focus.clearFocus()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .focusRequester(requester),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPad),
        ) {
            if (!active) {
                if (recent.isNotEmpty()) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 4.dp, top = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Recent searches",
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { vm.clearSearches() }) { Text("Clear") }
                        }
                    }
                    items(recent) { q ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
                                .clickable { query = q }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.History, contentDescription = null, tint = scheme.onSurfaceVariant)
                            Spacer(Modifier.width(16.dp))
                            Text(q, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else {
                    item {
                        Text(
                            "Type a few letters. Lyrics are searched too, but only for songs whose lyrics were already opened or saved.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                return@LazyColumn
            }

            val nothing = songHits.isEmpty() && albumHits.isEmpty() && artistHits.isEmpty() &&
                playlistHits.isEmpty() && lyricHits.isEmpty()
            if (nothing) {
                item {
                    Text(
                        "Nothing found for \"${query.trim()}\".",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                return@LazyColumn
            }

            if (songHits.isNotEmpty()) {
                item { SectionTitle("Songs") }
                items(songHits.size) { i ->
                    val t = songHits[i]
                    TrackRow(
                        track = t,
                        active = ui.current?.id == t.id,
                        playing = ui.isPlaying,
                        onClick = {
                            vm.rememberSearch(query)
                            vm.playQueue(songHits, i)
                        },
                        actions = actionsFor(t),
                    )
                }
            }
            if (albumHits.isNotEmpty()) {
                item { SectionTitle("Albums") }
                items(albumHits) { a ->
                    ResultRow(
                        title = a.title,
                        subtitle = "${a.artist} · ${a.tracks.size} songs",
                        onClick = {
                            vm.rememberSearch(query)
                            onOpenDetail("album:${a.id}")
                        },
                        leading = {
                            AlbumArtwork(
                                tracks = a.tracks,
                                modifier = Modifier.size(54.dp),
                                shape = MaterialTheme.shapes.medium,
                                px = 160,
                                onlineArtist = a.artist,
                                onlineAlbum = a.title,
                            )
                        },
                    )
                }
            }
            if (artistHits.isNotEmpty()) {
                item { SectionTitle("Artists") }
                items(artistHits) { a ->
                    ResultRow(
                        title = a.name,
                        subtitle = "${a.tracks.size} songs",
                        onClick = {
                            vm.rememberSearch(query)
                            onOpenDetail("artist:${a.name}")
                        },
                        leading = {
                            ArtistArtwork(
                                name = a.name,
                                modifier = Modifier.size(54.dp),
                                shape = CookieShape(8, 0.12f),
                                px = 160,
                            ) {
                                Text(
                                    a.name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = scheme.onTertiaryContainer,
                                )
                            }
                        },
                    )
                }
            }
            if (playlistHits.isNotEmpty()) {
                item { SectionTitle("Playlists") }
                items(playlistHits) { l ->
                    ResultRow(
                        title = l.name,
                        subtitle = "${l.tracks.size} songs",
                        onClick = {
                            vm.rememberSearch(query)
                            onOpenDetail("playlist:${l.id}")
                        },
                        leading = {
                            Box(
                                Modifier
                                    .size(54.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(scheme.secondaryContainer),
                            )
                        },
                    )
                }
            }
            val lyricTracks = lyricHits.mapNotNull { (id, line) -> byId[id]?.let { it to line } }
            if (lyricTracks.isNotEmpty()) {
                item { SectionTitle("In lyrics") }
                items(lyricTracks) { (t, line) ->
                    ResultRow(
                        title = t.title,
                        subtitle = "\"$line\" · ${t.artist}",
                        onClick = {
                            vm.rememberSearch(query)
                            vm.playQueue(listOf(t), 0)
                        },
                        leading = {
                            Artwork(
                                track = t,
                                modifier = Modifier.size(54.dp),
                                shape = MaterialTheme.shapes.medium,
                                px = 160,
                            )
                        },
                    )
                }
            }
        }
    }
}


@Composable
private fun ResultRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
