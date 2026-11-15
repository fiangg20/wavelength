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

/** One word (or syllable) of a line. [endMs] is the moment the next word starts, or -1 when unknown. */
data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

/** [words] is empty for plain line-synced lyrics; it holds real timing when the file has word stamps. */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
)

/** [synced] is empty when only plain text exists. */
data class Lyrics(
    val synced: List<LyricLine>,
    val plain: String,
    val source: String,
) {
    /** True when the file carries its own word timing (enhanced LRC). */
    val hasRealWords: Boolean get() = synced.any { it.words.isNotEmpty() }

    /** The lyrics as plain text, for copying and sharing. */
    fun asText(): String =
        if (synced.isNotEmpty()) synced.joinToString("\n") { it.text } else plain
}

data class LyricsResult(val lyrics: Lyrics?, val message: String)

/**
 * Reads the common .lrc format: [mm:ss.xx] text, several stamps per line, an optional [offset:ms] tag,
 * and the "enhanced" form that also times every word: [mm:ss.xx] <mm:ss.xx> word <mm:ss.xx> word
 */
object LrcParser {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""\[offset:\s*(-?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val wordStamp = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")

    private fun toMs(min: String, sec: String, frac: String): Long {
        val minutes = min.toLongOrNull() ?: 0L
        val seconds = sec.toLongOrNull() ?: 0L
        val millis = when (frac.length) {
            0 -> 0L
            1 -> (frac.toLongOrNull() ?: 0L) * 100L
            2 -> (frac.toLongOrNull() ?: 0L) * 10L
            else -> frac.take(3).toLongOrNull() ?: 0L
        }
        return minutes * 60_000L + seconds * 1000L + millis
    }

    /** Splits "<t> Hel<t>lo <t> world <t>" into timed words. Returns empty when there are no word stamps. */
    private fun parseWords(content: String, lineStart: Long): List<LyricWord> {
        val marks = wordStamp.findAll(content).toList()
        if (marks.isEmpty()) return emptyList()

        val starts = ArrayList<Long>()
        val texts = ArrayList<String>()

        // Anything written before the first stamp belongs to the very first moment of the line.
        val lead = content.substring(0, marks[0].range.first)
        if (lead.isNotBlank()) {
            starts.add(lineStart)
            texts.add(lead)
        }
        var trailingEnd = -1L
        for ((i, m) in marks.withIndex()) {
            val t = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            val from = m.range.last + 1
            val to = if (i + 1 < marks.size) marks[i + 1].range.first else content.length
            val piece = content.substring(from, to)
            if (piece.isBlank()) {
                // A stamp with nothing after it. At the very end it marks when the last word stops.
                if (i == marks.lastIndex) trailingEnd = t
                else if (texts.isNotEmpty() && piece.isNotEmpty()) texts[texts.lastIndex] = texts.last() + " "
                continue
            }
            starts.add(t)
            texts.add(piece)
        }
        if (starts.isEmpty()) return emptyList()

        // Tidy the spacing: a space at the start of a piece belongs to the end of the word before it.
        for (i in texts.indices) {
            val raw = texts[i]
            if (i > 0 && raw.first().isWhitespace() && !texts[i - 1].last().isWhitespace()) {
                texts[i - 1] = texts[i - 1] + " "
            }
            texts[i] = raw.trimStart()
        }
        texts[texts.lastIndex] = texts.last().trimEnd()

        val out = ArrayList<LyricWord>()
        for (i in starts.indices) {
            val end = if (i + 1 < starts.size) starts[i + 1] else trailingEnd
            out.add(LyricWord(starts[i], end, texts[i]))
        }
        return out.filter { it.text.isNotEmpty() }
    }

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
            val content = line.substring(stamps.last().range.last + 1)
            val firstTime = toMs(stamps[0].groupValues[1], stamps[0].groupValues[2], stamps[0].groupValues[3])
            val baseWords = parseWords(content, firstTime)
            val shown = if (baseWords.isNotEmpty()) {
                baseWords.joinToString("") { it.text }.trim()
            } else {
                content.trim()
            }
            for (m in stamps) {
                val t = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3])
                // Repeated stamps (a chorus) reuse the same word timing, shifted to each repeat.
                val shift = t - firstTime - offset
                val words = baseWords.map {
                    LyricWord(
                        max(0L, it.startMs + shift),
                        if (it.endMs >= 0L) max(0L, it.endMs + shift) else -1L,
                        it.text,
                    )
                }
                out.add(LyricLine(max(0L, t - offset), shown, words))
            }
        }
        return out.sortedBy { it.timeMs }
    }
}

/** Works out when each word of a line is sung, from real timing when it exists and by estimate otherwise. */
object WordTiming {
    /** mode 0: no word timing, 1: real when the file has it, otherwise estimated, 2: real only. */
    fun wordsFor(line: LyricLine, nextStartMs: Long, mode: Int): List<LyricWord> {
        if (mode == 0 || line.text.isBlank()) return emptyList()
        if (line.words.isNotEmpty()) return resolve(line, nextStartMs)
        return if (mode == 1) estimate(line, nextStartMs) else emptyList()
    }

    private fun resolve(line: LyricLine, nextStartMs: Long): List<LyricWord> {
        val w = line.words
        return w.mapIndexed { i, word ->
            val end = when {
                word.endMs > word.startMs -> word.endMs
                i + 1 < w.size -> w[i + 1].startMs
                else -> minOf(
                    if (nextStartMs > word.startMs) nextStartMs else Long.MAX_VALUE,
                    word.startMs + 1200L,
                )
            }
            LyricWord(word.startMs, max(end, word.startMs + 1L), word.text)
        }
    }

    private val token = Regex("""\S+\s*""")

    private fun isCjk(c: Char): Boolean = c.code in 0x2E80..0xA4CF || c.code in 0xAC00..0xD7AF || c.code in 0xFF00..0xFFEF

    private fun tokens(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.none { it.isWhitespace() } && trimmed.length > 1 && trimmed.any { isCjk(it) }) {
            return trimmed.map { it.toString() }
        }
        return token.findAll(trimmed).map { it.value }.toList()
    }

    private fun estimate(line: LyricLine, nextStartMs: Long): List<LyricWord> {
        val parts = tokens(line.text)
        if (parts.isEmpty()) return emptyList()
        val chars = parts.sumOf { it.trim().length }.coerceAtLeast(1)
        val gap = if (nextStartMs > line.timeMs) nextStartMs - line.timeMs else 5000L
        // People sing a line in roughly 80 ms per letter; never run into the next line, never stretch over a long break.
        val natural = chars * 80L + 500L
        val sing = minOf((gap * 0.95).toLong(), (natural * 1.6).toLong()).coerceAtLeast(400L)

        val total = parts.sumOf { it.trim().length.coerceAtLeast(1) }.toFloat()
        var cursor = line.timeMs.toFloat()
        val out = ArrayList<LyricWord>()
        for (p in parts) {
            val share = p.trim().length.coerceAtLeast(1) / total
            val start = cursor.toLong()
            cursor += share * sing
            out.add(LyricWord(start, cursor.toLong().coerceAtLeast(start + 1L), p))
        }
        return out
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
