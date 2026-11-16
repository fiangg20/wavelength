package com.fiangg20.wavelength.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A playlist as it is stored: just song ids, so renaming or retagging a file never breaks it. */
data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<Long>,
    val createdAt: Long,
)

/** A playlist with its songs looked up in the current library. */
data class PlaylistUi(
    val id: String,
    val name: String,
    val tracks: List<Track>,
)

/** A list the app fills by itself, such as "Never played". Read only. */
data class SmartList(
    val id: String, // always starts with "smart:"
    val title: String,
    val subtitle: String,
    val tracks: List<Track>,
)

/** Id of the built-in "Liked songs" list used in navigation routes. */
const val LIKED_ID = "liked"

class PlaylistStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("wavelength_playlists", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<List<Playlist>> = _state.asStateFlow()

    private var counter = 0

    private fun read(): List<Playlist> {
        val raw = prefs.getString("data", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val ids = o.optJSONArray("tracks")
                val list = if (ids == null) emptyList() else (0 until ids.length()).map { ids.optLong(it) }
                Playlist(
                    id = o.optString("id"),
                    name = o.optString("name", "Playlist"),
                    trackIds = list,
                    createdAt = o.optLong("created", 0L),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(list: List<Playlist>) {
        val arr = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("name", p.name)
            o.put("created", p.createdAt)
            val ids = JSONArray()
            for (t in p.trackIds) ids.put(t)
            o.put("tracks", ids)
            arr.put(o)
        }
        prefs.edit().putString("data", arr.toString()).apply()
    }

    @Synchronized
    private fun change(block: (List<Playlist>) -> List<Playlist>) {
        val next = block(_state.value)
        _state.value = next
        write(next)
    }

    fun create(name: String, firstIds: List<Long> = emptyList()): String {
        val id = "pl" + System.currentTimeMillis() + "_" + (counter++)
        val clean = name.trim().ifEmpty { "New playlist" }
        change { it + Playlist(id, clean, firstIds.distinct(), System.currentTimeMillis()) }
        return id
    }

    fun rename(id: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        change { list -> list.map { if (it.id == id) it.copy(name = clean) else it } }
    }

    fun delete(id: String) {
        change { list -> list.filter { it.id != id } }
    }

    /** Adds songs that are not in the playlist yet. Returns how many were added. */
    fun addTracks(id: String, ids: List<Long>): Int {
        val current = _state.value.firstOrNull { it.id == id } ?: return 0
        val fresh = ids.distinct().filter { it !in current.trackIds }
        if (fresh.isEmpty()) return 0
        change { list -> list.map { if (it.id == id) it.copy(trackIds = it.trackIds + fresh) else it } }
        return fresh.size
    }

    fun removeTrack(id: String, trackId: Long) {
        change { list ->
            list.map { if (it.id == id) it.copy(trackIds = it.trackIds.filter { t -> t != trackId }) else it }
        }
    }

    /** Moves a song up (delta -1) or down (delta +1) inside a playlist. */
    fun move(id: String, trackId: Long, delta: Int) {
        change { list ->
            list.map { p ->
                if (p.id != id) {
                    p
                } else {
                    val ids = p.trackIds.toMutableList()
                    val from = ids.indexOf(trackId)
                    val to = from + delta
                    if (from >= 0 && to >= 0 && to < ids.size) {
                        ids.removeAt(from)
                        ids.add(to, trackId)
                    }
                    p.copy(trackIds = ids)
                }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: PlaylistStore? = null

        fun get(context: Context): PlaylistStore =
            instance ?: synchronized(this) {
                instance ?: PlaylistStore(context.applicationContext).also { instance = it }
            }
    }
}
