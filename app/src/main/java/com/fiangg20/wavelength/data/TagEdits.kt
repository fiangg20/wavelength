package com.fiangg20.wavelength.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** A correction the user made to a song's title, artist or album. A null field keeps the tag from the file. */
data class TagEdit(val title: String?, val artist: String?, val album: String?)

/**
 * Edits are kept inside the app, next to the library, instead of being written into the audio file.
 * That works on every Android version without any storage permission and never risks damaging a file.
 */
class TagEditStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("wavelength_tagedits", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Map<Long, TagEdit>> = _state.asStateFlow()

    private fun read(): Map<Long, TagEdit> {
        val raw = prefs.getString("data", "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return try {
            val o = JSONObject(raw)
            val out = HashMap<Long, TagEdit>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val id = k.toLongOrNull() ?: continue
                val e = o.optJSONObject(k) ?: continue
                out[id] = TagEdit(
                    title = if (e.has("t")) e.optString("t") else null,
                    artist = if (e.has("a")) e.optString("a") else null,
                    album = if (e.has("l")) e.optString("l") else null,
                )
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun write(map: Map<Long, TagEdit>) {
        val o = JSONObject()
        for ((id, e) in map) {
            val x = JSONObject()
            e.title?.let { x.put("t", it) }
            e.artist?.let { x.put("a", it) }
            e.album?.let { x.put("l", it) }
            o.put(id.toString(), x)
        }
        prefs.edit().putString("data", o.toString()).apply()
    }

    @Synchronized
    fun set(id: Long, edit: TagEdit) {
        val next = if (edit.title == null && edit.artist == null && edit.album == null) {
            _state.value - id
        } else {
            _state.value + (id to edit)
        }
        _state.value = next
        write(next)
    }

    @Synchronized
    fun clear(id: Long) {
        val next = _state.value - id
        _state.value = next
        write(next)
    }

    companion object {
        @Volatile
        private var instance: TagEditStore? = null

        fun get(context: Context): TagEditStore =
            instance ?: synchronized(this) {
                instance ?: TagEditStore(context.applicationContext).also { instance = it }
            }
    }
}

fun Track.withEdit(e: TagEdit?): Track {
    if (e == null) return this
    return copy(
        title = e.title?.takeIf { it.isNotBlank() } ?: title,
        artist = e.artist?.takeIf { it.isNotBlank() } ?: artist,
        album = e.album?.takeIf { it.isNotBlank() } ?: album,
    )
}
