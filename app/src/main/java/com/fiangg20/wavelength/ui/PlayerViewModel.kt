package com.fiangg20.wavelength.ui

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fiangg20.wavelength.PlaybackService
import com.fiangg20.wavelength.data.Album
import com.fiangg20.wavelength.data.ArtLoader
import com.fiangg20.wavelength.data.Artist
import com.fiangg20.wavelength.data.ArtistConfig
import com.fiangg20.wavelength.data.FolderUi
import com.fiangg20.wavelength.data.Folders
import com.fiangg20.wavelength.data.Library
import com.fiangg20.wavelength.data.HistoryStore
import com.fiangg20.wavelength.data.MusicRepository
import com.fiangg20.wavelength.data.PlaylistStore
import com.fiangg20.wavelength.data.SmartList
import com.fiangg20.wavelength.data.ArtistSplitter
import com.fiangg20.wavelength.data.TagEdit
import com.fiangg20.wavelength.data.TagEditStore
import com.fiangg20.wavelength.data.withEdit
import com.fiangg20.wavelength.lyrics.LyricsRepo
import com.fiangg20.wavelength.data.PlaylistUi
import com.fiangg20.wavelength.data.Prefs
import com.fiangg20.wavelength.data.SettingsStore
import com.fiangg20.wavelength.data.Track
import com.fiangg20.wavelength.data.smartClean
import com.fiangg20.wavelength.download.DownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

data class PlaybackUi(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val queue: List<Track> = emptyList(),
    val index: Int = 0,
    val hasMedia: Boolean = false,
)

data class ListeningStats(
    val plays: Int = 0,
    val listenedMs: Long = 0L,
    val songsPlayed: Int = 0,
    val librarySongs: Int = 0,
    val topTracks: List<Pair<Track, Int>> = emptyList(),
    val topArtists: List<Pair<String, Int>> = emptyList(),
    val topAlbums: List<Pair<String, Int>> = emptyList(),
)

class PlayerViewModel(private val app: Application) : AndroidViewModel(app) {

    // ---- settings -------------------------------------------------------------------------

    private val store = SettingsStore.get(app)
    val prefs: StateFlow<Prefs> = store.state
    fun updatePrefs(block: (Prefs) -> Prefs) = store.update(block)

    private val statePrefs = app.getSharedPreferences("wavelength_state", Context.MODE_PRIVATE)

    // ---- favourites -----------------------------------------------------------------------

    private val _favorites = MutableStateFlow(readFavorites())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private fun readFavorites(): Set<Long> =
        (statePrefs.getString("favs", "") ?: "")
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()

    fun toggleFavorite(id: Long) {
        val next = _favorites.value.toMutableSet()
        if (!next.add(id)) next.remove(id)
        _favorites.value = next
        statePrefs.edit().putString("favs", next.joinToString(",")).apply()
    }

    // ---- playlists ------------------------------------------------------------------------

    private val playlistStore = PlaylistStore.get(app)
    private val history = HistoryStore.get(app)

    // ---- library --------------------------------------------------------------------------

    private val allTracks = MutableStateFlow<List<Track>>(emptyList())

    val loading = MutableStateFlow(false)
    val search = MutableStateFlow("")
    fun setQuery(q: String) {
        search.value = q
    }

    // Tag clean-up and excluded folders.
    private val processPrefs = prefs
        .map { Pair(it.smartTags, it.excluded) }
        .distinctUntilChanged()

    private val tagEdits = TagEditStore.get(app)

    private val processed: StateFlow<List<Track>> = combine(allTracks, processPrefs, tagEdits.state) { all, pp, edits ->
        val skip = pp.second.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        var list = all
        if (skip.isNotEmpty()) {
            list = list.filter { t -> skip.none { t.folder.contains(it, ignoreCase = true) } }
        }
        if (pp.first) list = list.map { smartClean(it) }
        if (edits.isNotEmpty()) list = list.map { it.withEdit(edits[it.id]) }
        list
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Every song on the phone, after tag clean-up and hidden folders, ignoring search and sorting. */
    val library: StateFlow<List<Track>> get() = processed

    private var lookupSource: List<Track>? = null
    private var lookupMap: Map<Long, Track> = emptyMap()
    private val trackLookup: Map<Long, Track>
        get() {
            val src = processed.value
            if (src !== lookupSource) {
                lookupSource = src
                lookupMap = src.associateBy { it.id }
            }
            return lookupMap
        }

    // Only the library-related settings should trigger a re-sort (not e.g. the accent hue slider).
    private val libraryPrefs = prefs
        .map { Triple(it.sortBy, it.sortAsc, it.minSeconds) }
        .distinctUntilChanged()

    val tracks: StateFlow<List<Track>> = combine(processed, libraryPrefs, search) { all, lp, q ->
        val sortBy = lp.first
        val sortAsc = lp.second
        val minSeconds = lp.third
        var list = all
        if (minSeconds > 0) {
            val minMs = minSeconds * 1000L
            list = list.filter { it.durationMs >= minMs }
        }
        if (q.isNotBlank()) {
            list = list.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.album.contains(q, ignoreCase = true)
            }
        }
        val cmp: Comparator<Track> = when (sortBy) {
            1 -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }
            2 -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.album }
            3 -> compareBy<Track> { it.dateAdded }
            4 -> compareBy<Track> { it.durationMs }
            else -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
        }
        if (sortAsc) list.sortedWith(cmp) else list.sortedWith(cmp.reversed())
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** User playlists with their songs resolved against the library (missing files drop out). */
    val playlists: StateFlow<List<PlaylistUi>> = combine(playlistStore.state, processed) { stored, all ->
        val byId = all.associateBy { it.id }
        stored.map { p -> PlaylistUi(p.id, p.name, p.trackIds.mapNotNull { byId[it] }) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun createPlaylist(name: String, first: Track? = null): String {
        val id = playlistStore.create(name, if (first == null) emptyList() else listOf(first.id))
        if (first != null) toast("Added to ${name.trim().ifEmpty { "New playlist" }}")
        return id
    }

    fun renamePlaylist(id: String, name: String) = playlistStore.rename(id, name)

    fun deletePlaylist(id: String) = playlistStore.delete(id)

    fun addToPlaylist(id: String, name: String, songs: List<Track>) {
        val added = playlistStore.addTracks(id, songs.map { it.id })
        toast(if (added > 0) "Added to $name" else "Already in $name")
    }

    fun removeFromPlaylist(id: String, trackId: Long) = playlistStore.removeTrack(id, trackId)

    fun movePlaylistTrack(id: String, trackId: Long, delta: Int) = playlistStore.move(id, trackId, delta)

    // ---- home -----------------------------------------------------------------------------

    /** Songs you played lately, newest first. */
    val recentlyPlayed: StateFlow<List<Track>> = combine(history.recent, processed) { ids, all ->
        val byId = all.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Songs you play the most, once they have been played a few times. */
    val mostPlayed: StateFlow<List<Track>> = combine(history.counts, processed) { counts, all ->
        val byId = all.associateBy { it.id }
        counts.entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .mapNotNull { byId[it.key] }
            .take(10)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The newest files on the phone, whatever the library is sorted by. */
    val recentlyAdded: StateFlow<List<Track>> = processed
        .map { all -> all.sortedByDescending { it.dateAdded }.take(20) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun clearHistory() = history.clear()

    // ---- recent searches ------------------------------------------------------------------

    private val _searches = MutableStateFlow(
        (statePrefs.getString("searches", "") ?: "").split('\u001F').filter { it.isNotBlank() }
    )
    val recentSearches: StateFlow<List<String>> = _searches.asStateFlow()

    fun rememberSearch(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        val next = (listOf(q) + _searches.value.filter { !it.equals(q, ignoreCase = true) }).take(8)
        _searches.value = next
        statePrefs.edit().putString("searches", next.joinToString("\u001F")).apply()
    }

    fun clearSearches() {
        _searches.value = emptyList()
        statePrefs.edit().remove("searches").apply()
    }

    /** Lists the app builds by itself from what is on the phone and what you play. */
    val smartLists: StateFlow<List<SmartList>> = combine(processed, history.counts) { all, counts ->
        val out = ArrayList<SmartList>()
        val newest = all.sortedByDescending { it.dateAdded }.take(50)
        if (newest.isNotEmpty()) out.add(SmartList("smart:recent", "Recently added", "The 50 newest files", newest))
        val top = counts.entries.sortedByDescending { it.value }.take(50)
        val byId = all.associateBy { it.id }
        val topTracks = top.mapNotNull { byId[it.key] }
        if (topTracks.isNotEmpty()) out.add(SmartList("smart:top", "Most played", "Your top songs", topTracks))
        val never = all.filter { it.id !in counts }.sortedByDescending { it.dateAdded }.take(300)
        if (never.isNotEmpty()) out.add(SmartList("smart:never", "Never played", "Songs you have not played yet", never))
        val long = all.filter { it.durationMs >= 6 * 60_000L }.sortedByDescending { it.durationMs }.take(200)
        if (long.isNotEmpty()) out.add(SmartList("smart:long", "Long songs", "Six minutes or more", long))
        out
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- tag editor -----------------------------------------------------------------------

    /** The song whose info is being edited, or null when no editor is open. */
    val editingTrack = MutableStateFlow<Track?>(null)
    fun editInfo(track: Track) {
        editingTrack.value = track
    }

    fun saveTagEdit(track: Track, title: String, artist: String, album: String) {
        // Only fields that differ from the file's own tags are stored, so "reset" is always possible.
        val original = allTracks.value.firstOrNull { it.id == track.id }
        val base = original?.let { if (store.state.value.smartTags) smartClean(it) else it } ?: track
        tagEdits.set(
            track.id,
            TagEdit(
                title = title.trim().takeIf { it.isNotEmpty() && it != base.title },
                artist = artist.trim().takeIf { it.isNotEmpty() && it != base.artist },
                album = album.trim().takeIf { it.isNotEmpty() && it != base.album },
            ),
        )
        editingTrack.value = null
        toast("Saved")
    }

    fun resetTagEdit(track: Track) {
        tagEdits.clear(track.id)
        editingTrack.value = null
        toast("Back to the tags in the file")
    }

    fun hasTagEdit(id: Long): Boolean = tagEdits.state.value.containsKey(id)

    // ---- stats ----------------------------------------------------------------------------

    val stats: StateFlow<ListeningStats> = combine(history.counts, processed, artistConfig()) { counts, all, cfg ->
        val byId = all.associateBy { it.id }
        var plays = 0
        var ms = 0L
        val perArtist = HashMap<String, Int>()
        val perAlbum = HashMap<String, Int>()
        val tracks = ArrayList<Pair<Track, Int>>()
        for ((id, n) in counts) {
            val t = byId[id] ?: continue
            plays += n
            ms += t.durationMs * n
            tracks.add(t to n)
            for (a in ArtistSplitter.split(t.artist, cfg)) perArtist[a] = (perArtist[a] ?: 0) + n
            if (t.album.isNotBlank()) perAlbum[t.album] = (perAlbum[t.album] ?: 0) + n
        }
        ListeningStats(
            plays = plays,
            listenedMs = ms,
            songsPlayed = tracks.size,
            librarySongs = all.size,
            topTracks = tracks.sortedByDescending { it.second }.take(10),
            topArtists = perArtist.entries.sortedByDescending { it.value }.take(10).map { it.key to it.value },
            topAlbums = perAlbum.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ListeningStats())

    val liked: StateFlow<List<Track>> = combine(tracks, favorites) { list, favs ->
        list.filter { it.id in favs }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // How artist names are split decides how albums and artists are grouped.
    private fun artistConfig() = prefs
        .map { ArtistConfig.from(it) }
        .distinctUntilChanged()

    val albums: StateFlow<List<Album>> = combine(tracks, artistConfig()) { list, cfg ->
        Library.albums(list, cfg)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<Artist>> = combine(tracks, artistConfig()) { list, cfg ->
        Library.artists(list, cfg)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folders: StateFlow<List<FolderUi>> = tracks
        .map { Folders.group(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun loadLibrary() {
        viewModelScope.launch { reloadLibrary() }
    }

    private suspend fun reloadLibrary() {
        loading.value = true
        ArtLoader.forgetMisses()
        val list = withContext(Dispatchers.IO) {
            try {
                MusicRepository.load(app)
            } catch (e: Exception) {
                emptyList()
            }
        }
        allTracks.value = list
        loading.value = false
    }

    /** Looks up lyrics for songs that were not in the library before, so a fresh download is ready to sing along. */
    private suspend fun fetchLyricsForNew(before: Set<Long>) {
        val p = store.state.value
        if (!p.lyricsOnDownload || !p.lyricsOnline) return
        val fresh = processed.value.filter { it.id !in before }.take(30)
        for (t in fresh) {
            try {
                LyricsRepo.load(app, t, online = true, force = false)
            } catch (e: Exception) {
                // One failed lookup should not stop the rest.
            }
        }
    }

    // ---- playback -------------------------------------------------------------------------

    private val _ui = MutableStateFlow(PlaybackUi())
    val ui: StateFlow<PlaybackUi> = _ui.asStateFlow()

    // A song counts as played once it has been playing for a few seconds.
    init {
        viewModelScope.launch {
            var lastRecorded = -1L
            _ui.map { Pair(it.current?.id, it.isPlaying) }
                .distinctUntilChanged()
                .collectLatest { state ->
                    val id = state.first
                    if (id != null && state.second && id != lastRecorded) {
                        delay(7000L)
                        lastRecorded = id
                        history.record(id)
                    }
                }
        }
    }

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _sleepMinutes = MutableStateFlow(0)
    val sleepMinutes: StateFlow<Int> = _sleepMinutes.asStateFlow()

    private var controller: MediaController? = null
    private var pending: ((MediaController) -> Unit)? = null
    private val controllerFuture = MediaController.Builder(
        app,
        SessionToken(app, ComponentName(app, PlaybackService::class.java)),
    ).buildAsync()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh(events.contains(Player.EVENT_TIMELINE_CHANGED))
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                events.contains(Player.EVENT_TIMELINE_CHANGED)
            ) {
                saveSession()
            }
        }
    }

    private var sleepJob: Job? = null
    private var sleepEndsAt = 0L
    private var restored = false

    init {
        controllerFuture.addListener(
            {
                try {
                    val c = controllerFuture.get()
                    controller = c
                    c.addListener(listener)
                    refresh(true)
                    val action = pending
                    pending = null
                    action?.invoke(c)
                } catch (e: Exception) {
                    // The service could not be reached; the UI simply stays idle.
                }
            },
            ContextCompat.getMainExecutor(app),
        )

        viewModelScope.launch {
            var ticks = 0
            while (isActive) {
                val c = controller
                if (c != null) {
                    _position.value = c.currentPosition.coerceAtLeast(0L)
                }
                if (sleepEndsAt > 0L) {
                    val left = (sleepEndsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                    _sleepMinutes.value = ((left + 59_999L) / 60_000L).toInt()
                }
                if (_ui.value.isPlaying) {
                    ticks++
                    if (ticks % 40 == 0) saveSession()
                }
                delay(if (_ui.value.isPlaying) 120L else 500L)
            }
        }

        // Put the last queue back once the library is known.
        viewModelScope.launch {
            processed.first { it.isNotEmpty() }
            restoreSession()
        }

        // New downloads should appear without a manual refresh.
        viewModelScope.launch {
            DownloadManager.finished.collect {
                delay(800L)
                val before = allTracks.value.map { it.id }.toSet()
                reloadLibrary()
                fetchLyricsForNew(before)
            }
        }
    }

    private fun saveSession() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        val count = min(c.mediaItemCount, 500)
        val ids = (0 until count).joinToString(",") { c.getMediaItemAt(it).mediaId }
        statePrefs.edit()
            .putString("queue", ids)
            .putString("current", c.currentMediaItem?.mediaId ?: "")
            .putLong("pos", c.currentPosition.coerceAtLeast(0L))
            .apply()
    }

    private fun restoreSession() {
        if (restored) return
        restored = true
        if (!store.state.value.restoreSession) return
        val ids = (statePrefs.getString("queue", "") ?: "")
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() }
        if (ids.isEmpty()) return
        val lookup = trackLookup
        val list = ids.mapNotNull { lookup[it] }
        if (list.isEmpty()) return
        val current = (statePrefs.getString("current", "") ?: "").toLongOrNull()
        val index = list.indexOfFirst { it.id == current }.coerceAtLeast(0)
        val position = statePrefs.getLong("pos", 0L)
        val items = list.map { it.toMediaItem() }
        withController { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItems(items, index, position)
                c.prepare()
            }
        }
    }

    private fun buildQueue(c: MediaController): List<Track> {
        val lookup = trackLookup
        return (0 until c.mediaItemCount).map { i ->
            val item = c.getMediaItemAt(i)
            val id = item.mediaId.toLongOrNull() ?: -1L
            lookup[id] ?: Track(
                id = id,
                title = item.mediaMetadata.title?.toString() ?: "Unknown",
                artist = item.mediaMetadata.artist?.toString() ?: "",
                album = item.mediaMetadata.albumTitle?.toString() ?: "",
                albumId = 0L,
                durationMs = 0L,
                dateAdded = 0L,
                trackNo = 0,
                uri = Uri.EMPTY,
            )
        }
    }

    private fun refresh(rebuildQueue: Boolean) {
        val c = controller ?: return
        val prev = _ui.value
        val queue = if (rebuildQueue || prev.queue.size != c.mediaItemCount) buildQueue(c) else prev.queue
        val index = c.currentMediaItemIndex
        val current = queue.getOrNull(index)
        val rawDuration = c.duration
        val duration = if (rawDuration == C.TIME_UNSET || rawDuration < 0L) {
            current?.durationMs ?: 0L
        } else {
            rawDuration
        }
        _ui.value = PlaybackUi(
            current = current,
            isPlaying = c.isPlaying,
            durationMs = duration,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
            speed = c.playbackParameters.speed,
            pitch = c.playbackParameters.pitch,
            queue = queue,
            index = index,
            hasMedia = c.mediaItemCount > 0,
        )
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending = block
    }

    private fun toast(text: String) {
        Toast.makeText(app, text, Toast.LENGTH_SHORT).show()
    }

    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(
                    ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId,
                    )
                )
                .build()
        )
        .build()

    /**
     * Starts playing [list] at [startIndex]. Very large libraries are windowed around the
     * tapped song, because the queue travels to the playback service in one binder transaction.
     */
    fun playQueue(list: List<Track>, startIndex: Int, shuffle: Boolean? = null) {
        if (list.isEmpty()) return
        val from = startIndex.coerceIn(0, list.lastIndex)
        val lo = max(0, from - 100)
        val hi = min(list.size, from + 300)
        val items = list.subList(lo, hi).map { it.toMediaItem() }
        withController { c ->
            if (shuffle != null) c.shuffleModeEnabled = shuffle
            c.setMediaItems(items, from - lo, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playShuffled(list: List<Track>) {
        if (list.isEmpty()) return
        playQueue(list.shuffled().take(400), 0, true)
    }

    fun playNext(track: Track) = withController { c ->
        if (c.mediaItemCount == 0) {
            c.setMediaItems(listOf(track.toMediaItem()))
            c.prepare()
            c.play()
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
            toast("Playing next")
        }
    }

    fun addToQueue(track: Track) = withController { c ->
        if (c.mediaItemCount == 0) {
            c.setMediaItems(listOf(track.toMediaItem()))
            c.prepare()
            c.play()
        } else {
            c.addMediaItem(track.toMediaItem())
            toast("Added to queue")
        }
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }

    fun seekToFraction(f: Float) = withController { c ->
        val d = _ui.value.durationMs
        if (d > 0L) {
            val target = (f.coerceIn(0f, 1f) * d).toLong()
            c.seekTo(target)
            _position.value = target
        }
    }

    fun seekToMs(ms: Long) = withController { c ->
        val target = ms.coerceAtLeast(0L)
        c.seekTo(target)
        _position.value = target
    }

    fun skipTo(index: Int) = withController { c ->
        c.seekTo(index, 0L)
        c.play()
    }

    // ---- queue editing --------------------------------------------------------------------

    fun moveQueueItem(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount && from != to) {
            c.moveMediaItem(from, to)
        }
    }

    fun removeQueueItem(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    /** Drops everything after the song that is playing. */
    fun clearUpcoming() = withController { c ->
        val from = c.currentMediaItemIndex + 1
        if (from < c.mediaItemCount) c.removeMediaItems(from, c.mediaItemCount)
    }

    fun saveQueueAsPlaylist(name: String) {
        val ids = _ui.value.queue.map { it.id }.filter { it > 0L }
        if (ids.isEmpty()) return
        playlistStore.create(name, ids)
        toast("Saved as $name")
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeedPitch(speed: Float, pitch: Float) = withController {
        it.playbackParameters = PlaybackParameters(speed, pitch)
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            sleepEndsAt = 0L
            _sleepMinutes.value = 0
            return
        }
        val ms = minutes * 60_000L
        sleepEndsAt = SystemClock.elapsedRealtime() + ms
        _sleepMinutes.value = minutes
        sleepJob = viewModelScope.launch {
            delay(ms)
            controller?.pause()
            sleepEndsAt = 0L
            _sleepMinutes.value = 0
        }
    }

    override fun onCleared() {
        saveSession()
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
        super.onCleared()
    }
}
