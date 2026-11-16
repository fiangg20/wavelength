package com.fiangg20.wavelength.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

/**
 * Finds pictures of artists and albums on the internet (Deezer's public search, no account needed).
 * Pictures are saved on the phone, so each one is downloaded only once.
 */
object OnlineArt {
    private val mem = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // Nothing found online, remembered for a while so the list does not ask again on every scroll.
    private val misses = HashMap<String, Long>()
    private const val MISS_TTL_MS = 10 * 60_000L
    private const val NONE_FILE_TTL_MS = 3L * 24 * 60 * 60_000L

    // Never more than three requests at once.
    private val gate = Semaphore(3)

    suspend fun artist(context: Context, name: String): Bitmap? {
        val clean = name.trim()
        if (clean.isEmpty() || clean == UNKNOWN_ARTIST) return null
        return load(context, "artist|" + norm(clean)) {
            val q = URLEncoder.encode(clean, "UTF-8")
            val json = getText("https://api.deezer.com/search/artist?limit=5&q=$q") ?: return@load Result.Failed
            val data = JSONObject(json).optJSONArray("data") ?: return@load Result.NotFound
            for (i in 0 until data.length()) {
                val o = data.optJSONObject(i) ?: continue
                if (norm(o.optString("name")) == norm(clean)) {
                    val url = o.optString("picture_big").ifBlank { o.optString("picture_xl") }
                    // Deezer shows a grey placeholder for artists without a picture, its address has no picture id.
                    if (url.isNotBlank() && !url.contains("/images/artist//")) return@load Result.Url(url)
                }
            }
            Result.NotFound
        }
    }

    suspend fun album(context: Context, artist: String, album: String): Bitmap? {
        val a = album.trim()
        if (a.isEmpty() || a == "Unknown album") return null
        val who = artist.trim().let { if (it == UNKNOWN_ARTIST) "" else it }
        return load(context, "album|" + norm(who) + "|" + norm(a)) {
            val query = if (who.isEmpty()) a else "$who $a"
            val q = URLEncoder.encode(query, "UTF-8")
            val json = getText("https://api.deezer.com/search/album?limit=8&q=$q") ?: return@load Result.Failed
            val data = JSONObject(json).optJSONArray("data") ?: return@load Result.NotFound
            for (i in 0 until data.length()) {
                val o = data.optJSONObject(i) ?: continue
                val titleOk = norm(o.optString("title")) == norm(a)
                val artistName = o.optJSONObject("artist")?.optString("name") ?: ""
                val artistOk = who.isEmpty() || norm(artistName) == norm(who) ||
                    norm(who).contains(norm(artistName)) || norm(artistName).contains(norm(who))
                if (titleOk && artistOk) {
                    val url = o.optString("cover_big").ifBlank { o.optString("cover_xl") }
                    if (url.isNotBlank()) return@load Result.Url(url)
                }
            }
            Result.NotFound
        }
    }

    private sealed class Result {
        class Url(val url: String) : Result()
        object NotFound : Result()
        object Failed : Result() // no network or server error: do not remember it as "nothing found"
    }

    private suspend fun load(context: Context, key: String, find: () -> Result): Bitmap? {
        mem.get(key)?.let { return it }
        val missedAt = synchronized(misses) { misses[key] }
        if (missedAt != null && SystemClock.elapsedRealtime() - missedAt < MISS_TTL_MS) return null

        return withContext(Dispatchers.IO) {
            val dir = File(context.applicationContext.filesDir, "onlineart").apply { mkdirs() }
            val base = hash(key)
            val file = File(dir, "$base.jpg")
            val none = File(dir, "$base.none")

            if (file.exists()) {
                decode(file.readBytes(), 600)?.let {
                    mem.put(key, it)
                    return@withContext it
                }
                file.delete()
            }
            if (none.exists() && System.currentTimeMillis() - none.lastModified() < NONE_FILE_TTL_MS) {
                synchronized(misses) { misses[key] = SystemClock.elapsedRealtime() }
                return@withContext null
            }

            val outcome = try {
                gate.withPermit { find() }
            } catch (e: Exception) {
                Result.Failed
            }
            when (outcome) {
                is Result.Url -> {
                    val bytes = getBytes(outcome.url)
                    val bmp = if (bytes != null) decode(bytes, 600) else null
                    if (bmp != null) {
                        try {
                            file.writeBytes(bytes!!)
                        } catch (e: Exception) {
                            // Cache is optional.
                        }
                        mem.put(key, bmp)
                        bmp
                    } else {
                        synchronized(misses) { misses[key] = SystemClock.elapsedRealtime() }
                        null
                    }
                }
                is Result.NotFound -> {
                    try {
                        none.writeText("")
                        none.setLastModified(System.currentTimeMillis())
                    } catch (e: Exception) {
                        // Cache is optional.
                    }
                    synchronized(misses) { misses[key] = SystemClock.elapsedRealtime() }
                    null
                }
                is Result.Failed -> {
                    synchronized(misses) { misses[key] = SystemClock.elapsedRealtime() }
                    null
                }
            }
        }
    }

    /** Delete every saved picture, so the next look-up asks the internet again. */
    fun clear(context: Context) {
        File(context.applicationContext.filesDir, "onlineart").listFiles()?.forEach { it.delete() }
        mem.evictAll()
        synchronized(misses) { misses.clear() }
    }

    private fun norm(s: String): String =
        s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun decode(data: ByteArray, px: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
        BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("User-Agent", "Wavelength/2.1")
        }

    private fun getText(url: String): String? = try {
        val c = open(url)
        try {
            if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private fun getBytes(url: String): ByteArray? = try {
        val c = open(url)
        try {
            if (c.responseCode in 200..299) c.inputStream.use { it.readBytes() } else null
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}
