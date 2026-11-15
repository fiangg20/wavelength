package com.fiangg20.wavelength.lyrics

import android.content.Context
import com.fiangg20.wavelength.data.Track
import com.fiangg20.wavelength.data.UNKNOWN_ARTIST
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.max

data class LyricLine(val timeMs: Long, val text: String)

/** [synced] is empty when only plain text exists. */
data class Lyrics(
    val synced: List<LyricLine>,
    val plain: String,
    val source: String,
)

data class LyricsResult(val lyrics: Lyrics?, val message: String)

/** Reads the common .lrc format: [mm:ss.xx] text, several stamps per line, and an optional [offset:ms] tag. */
object LrcParser {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""\[offset:\s*(-?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val wordStamp = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    fun parse(text: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        var offset = 0L
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val stamps = stamp.findAll(line).toList()
            if (stamps.isEmpty()) {
                val off = offsetTag.find(line)
                if (off != null) offset = off.groupValues[1].toLongOrNull() ?: 0L
                continue
            }
            val content = wordStamp.replace(line.substring(stamps.last().range.last + 1), "").trim()
            for (m in stamps) {
                val minutes = m.groupValues[1].toLongOrNull() ?: 0L
                val seconds = m.groupValues[2].toLongOrNull() ?: 0L
                val frac = m.groupValues[3]
                val millis = when (frac.length) {
                    0 -> 0L
                    1 -> (frac.toLongOrNull() ?: 0L) * 100L
                    2 -> (frac.toLongOrNull() ?: 0L) * 10L
                    else -> frac.take(3).toLongOrNull() ?: 0L
                }
                val time = max(0L, minutes * 60_000L + seconds * 1000L + millis - offset)
                out.add(LyricLine(time, content))
            }
        }
        return out.sortedBy { it.timeMs }
    }
}

/** Finds, caches and imports lyrics. Online lookups use the free LRCLIB service (lrclib.net). */
object LyricsRepo {
    private const val NEGATIVE_DAYS = 7L

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, "lyrics").also { it.mkdirs() }

    private fun fromSynced(lines: List<LyricLine>, source: String) =
        Lyrics(lines, lines.joinToString("\n") { it.text }, source)

    fun cached(context: Context, id: Long): Lyrics? {
        return try {
            val lrc = File(dir(context), "$id.lrc")
            if (lrc.exists()) {
                val lines = LrcParser.parse(lrc.readText())
                if (lines.isNotEmpty()) return fromSynced(lines, "Saved")
            }
            val txt = File(dir(context), "$id.txt")
            if (txt.exists()) {
                val text = txt.readText()
                if (text.isNotBlank()) return Lyrics(emptyList(), text.trim(), "Saved")
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun forget(context: Context, id: Long) {
        File(dir(context), "$id.lrc").delete()
        File(dir(context), "$id.txt").delete()
        File(dir(context), "$id.none").delete()
    }

    /** Saves text the user picked from a file. Returns null if it holds no lyrics at all. */
    fun saveImported(context: Context, id: Long, text: String): Lyrics? {
        val lines = LrcParser.parse(text)
        forget(context, id)
        return if (lines.isNotEmpty()) {
            File(dir(context), "$id.lrc").writeText(text)
            fromSynced(lines, "Imported")
        } else if (text.isNotBlank()) {
            File(dir(context), "$id.txt").writeText(text.trim())
            Lyrics(emptyList(), text.trim(), "Imported")
        } else {
            null
        }
    }

    // ---- per-song timing offset ----------------------------------------------------------

    private fun offsets(context: Context) =
        context.applicationContext.getSharedPreferences("wavelength_lyrics", Context.MODE_PRIVATE)

    fun getOffset(context: Context, id: Long): Long = offsets(context).getLong("off_$id", 0L)

    fun setOffset(context: Context, id: Long, millis: Long) {
        offsets(context).edit().putLong("off_$id", millis).apply()
    }

    // ---- online lookup --------------------------------------------------------------------

    suspend fun load(context: Context, track: Track, online: Boolean, force: Boolean): LyricsResult =
        withContext(Dispatchers.IO) {
            val id = track.id
            if (!force) {
                val have = cached(context, id)
                if (have != null) return@withContext LyricsResult(have, "")
            }
            if (!online) {
                return@withContext LyricsResult(null, "Online lyrics are turned off in Settings.")
            }
            val none = File(dir(context), "$id.none")
            if (!force && none.exists()) {
                val ageDays = (System.currentTimeMillis() - none.lastModified()) / 86_400_000L
                if (ageDays < NEGATIVE_DAYS) return@withContext LyricsResult(null, "No lyrics found for this song.")
            }
            try {
                val found = search(track)
                if (found == null) {
                    none.writeText("none")
                    LyricsResult(null, "No lyrics found for this song.")
                } else {
                    forget(context, id)
                    if (found.synced.isNotEmpty()) {
                        File(dir(context), "$id.lrc").writeText(found.rawSynced)
                    } else if (found.plain.isNotBlank()) {
                        File(dir(context), "$id.txt").writeText(found.plain)
                    }
                    val lyrics = if (found.synced.isNotEmpty()) {
                        Lyrics(found.synced, found.plain.ifBlank { found.synced.joinToString("\n") { it.text } }, "LRCLIB")
                    } else {
                        Lyrics(emptyList(), found.plain, "LRCLIB")
                    }
                    LyricsResult(lyrics, "")
                }
            } catch (e: Exception) {
                LyricsResult(null, "Could not reach the lyrics server. Check your connection and try again.")
            }
        }

    private class Hit(val synced: List<LyricLine>, val rawSynced: String, val plain: String)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private val brackets = Regex("""\s*[\(\[][^\)\]]*[\)\]]""")

    private fun queryUrls(track: Track): List<String> {
        val base = "https://lrclib.net/api/search?"
        val title = track.title.trim()
        val bare = title.replace(brackets, "").trim().ifEmpty { title }
        val hasArtist = track.artist.isNotBlank() && track.artist != UNKNOWN_ARTIST
        val urls = ArrayList<String>()
        if (hasArtist) {
            urls.add(base + "track_name=" + enc(title) + "&artist_name=" + enc(track.artist))
            if (bare != title) urls.add(base + "track_name=" + enc(bare) + "&artist_name=" + enc(track.artist))
            urls.add(base + "q=" + enc(track.artist + " " + bare))
        } else {
            urls.add(base + "q=" + enc(title))
            if (bare != title) urls.add(base + "q=" + enc(bare))
        }
        return urls
    }

    private fun get(url: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "Wavelength/2.0 (https://github.com/fiangg20/wavelength)")
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return Pair(code, body)
        } finally {
            conn.disconnect()
        }
    }

    private fun search(track: Track): Hit? {
        val wantSeconds = track.durationMs / 1000.0
        for (url in queryUrls(track)) {
            val (code, body) = get(url)
            if (code !in 200..299) continue
            val arr = JSONArray(body)

            var best: Hit? = null
            var bestScore = Double.MAX_VALUE
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optBoolean("instrumental", false)) continue
                val synced = if (o.isNull("syncedLyrics")) "" else o.optString("syncedLyrics", "")
                val plain = if (o.isNull("plainLyrics")) "" else o.optString("plainLyrics", "")
                if (synced.isBlank() && plain.isBlank()) continue
                val dur = o.optDouble("duration", 0.0)
                val gap = if (wantSeconds > 1.0 && dur > 1.0) abs(dur - wantSeconds) else 0.0
                if (gap > 15.0) continue
                // Prefer synced lyrics, then the closest running time.
                val score = gap + if (synced.isBlank()) 100.0 else 0.0
                if (score < bestScore) {
                    val lines = if (synced.isBlank()) emptyList() else LrcParser.parse(synced)
                    best = Hit(lines, synced, plain)
                    bestScore = score
                }
            }
            if (best != null) return best
        }
        return null
    }
}
