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
import com.fiangg20.wavelength.data.Artist
import com.fiangg20.wavelength.data.MusicRepository
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

    private val processed: StateFlow<List<Track>> = combine(allTracks, processPrefs) { all, pp ->
        val skip = pp.second.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        var list = all
        if (skip.isNotEmpty()) {
            list = list.filter { t -> skip.none { t.folder.contains(it, ignoreCase = true) } }
        }
        if (pp.first) list = list.map { smartClean(it) }
        list
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    val liked: StateFlow<List<Track>> = combine(tracks, favorites) { list, favs ->
        list.filter { it.id in favs }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<Album>> = tracks
        .map { list ->
            list.groupBy { it.albumId }
                .map { (id, ts) ->
                    val ordered = ts.sortedWith(compareBy<Track> { it.trackNo }.thenBy { it.title.lowercase() })
                    val artists = ordered.map { it.artist }.distinct()
                    Album(
                        id = id,
                        title = ordered.first().album,
                        artist = if (artists.size > 1) "Various artists" else artists.first(),
                        tracks = ordered,
                    )
                }
                .sortedBy { it.title.lowercase() }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<Artist>> = tracks
        .map { list ->
            list.groupBy { it.artist }
                .map { (name, ts) -> Artist(name, ts) }
                .sortedBy { it.name.lowercase() }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun loadLibrary() {
        viewModelScope.launch {
            loading.value = true
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
    }

    // ---- playback -------------------------------------------------------------------------

    private val _ui = MutableStateFlow(PlaybackUi())
    val ui: StateFlow<PlaybackUi> = _ui.asStateFlow()

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
                loadLibrary()
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

    fun skipTo(index: Int) = withController { c ->
        c.seekTo(index, 0L)
        c.play()
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
