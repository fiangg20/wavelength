package com.fiangg20.wavelength.data

import android.content.Context
import com.fiangg20.wavelength.lyrics.LyricsRepo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Packs everything the app remembers into one JSON text: settings, playlists, favourites, play history,
 * tag edits, equalizer, downloader settings and saved lyrics. Songs themselves are never included.
 */
object Backup {
    private val FILES = listOf(
        "wavelength_prefs",
        "wavelength_playlists",
        "wavelength_history",
        "wavelength_lyrics",
        "wavelength_tagedits",
        "wavelength_dl",
        "wavelength_eq",
        "wavelength_state",
    )

    data class Summary(val playlists: Int, val lyrics: Int, val settings: Int)

    fun export(context: Context): String {
        val root = JSONObject()
        root.put("app", "wavelength")
        root.put("version", 1)
        root.put("created", System.currentTimeMillis())

        val prefs = JSONObject()
        for (name in FILES) {
            val sp = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val o = JSONObject()
            for ((k, v) in sp.all) {
                val e = JSONObject()
                when (v) {
                    is String -> { e.put("t", "s"); e.put("v", v) }
                    is Int -> { e.put("t", "i"); e.put("v", v) }
                    is Long -> { e.put("t", "l"); e.put("v", v) }
                    is Float -> { e.put("t", "f"); e.put("v", v.toDouble()) }
                    is Boolean -> { e.put("t", "b"); e.put("v", v) }
                    is Set<*> -> {
                        e.put("t", "set")
                        val arr = JSONArray()
                        v.forEach { arr.put(it.toString()) }
                        e.put("v", arr)
                    }
                    else -> continue
                }
                o.put(k, e)
            }
            prefs.put(name, o)
        }
        root.put("prefs", prefs)

        val lyrics = JSONObject()
        for ((name, text) in LyricsRepo.exportAll(context)) lyrics.put(name, text)
        root.put("lyrics", lyrics)
        return root.toString()
    }

    /** Returns what was restored, or null when the text is not a Wavelength backup. */
    fun restore(context: Context, json: String): Summary? {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return null
        }
        if (root.optString("app") != "wavelength") return null
        val prefs = root.optJSONObject("prefs") ?: return null

        var settings = 0
        var playlists = 0
        for (name in FILES) {
            val o = prefs.optJSONObject(name) ?: continue
            val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val e = o.optJSONObject(k) ?: continue
                when (e.optString("t")) {
                    "s" -> edit.putString(k, e.optString("v"))
                    "i" -> edit.putInt(k, e.optInt("v"))
                    "l" -> edit.putLong(k, e.optLong("v"))
                    "f" -> edit.putFloat(k, e.optDouble("v").toFloat())
                    "b" -> edit.putBoolean(k, e.optBoolean("v"))
                    "set" -> {
                        val arr = e.optJSONArray("v")
                        val set = HashSet<String>()
                        if (arr != null) for (i in 0 until arr.length()) set.add(arr.optString(i))
                        edit.putStringSet(k, set)
                    }
                }
                if (name == "wavelength_prefs") settings++
            }
            edit.commit()
            if (name == "wavelength_playlists") {
                playlists = try {
                    JSONArray(o.optJSONObject("data")?.optString("v") ?: "[]").length()
                } catch (e: Exception) {
                    0
                }
            }
        }

        val lyrics = root.optJSONObject("lyrics")
        val files = HashMap<String, String>()
        if (lyrics != null) {
            val keys = lyrics.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                files[k] = lyrics.optString(k)
            }
            LyricsRepo.importAll(context, files)
        }
        return Summary(playlists, files.size, settings)
    }
}
