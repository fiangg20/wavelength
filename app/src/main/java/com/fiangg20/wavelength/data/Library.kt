package com.fiangg20.wavelength.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MusicRepository {

    fun load(context: Context): List<Track> {
        val collection: Uri = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val pathColumn = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            MediaStore.Audio.Media.DATA
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            pathColumn,
        )
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"

        val out = ArrayList<Track>()
        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val addedC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val trackC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val pathC = c.getColumnIndexOrThrow(pathColumn)
            while (c.moveToNext()) {
                val id = c.getLong(idC)
                out.add(
                    Track(
                        id = id,
                        title = clean(c.getString(titleC), "Untitled"),
                        artist = clean(c.getString(artistC), UNKNOWN_ARTIST),
                        album = clean(c.getString(albumC), "Unknown album"),
                        albumId = c.getLong(albumIdC),
                        durationMs = c.getLong(durC),
                        dateAdded = c.getLong(addedC),
                        trackNo = c.getInt(trackC),
                        uri = ContentUris.withAppendedId(collection, id),
                        folder = c.getString(pathC) ?: "",
                    )
                )
            }
        }
        return out
    }

    private fun clean(value: String?, fallback: String): String =
        if (value.isNullOrBlank() || value == "<unknown>") fallback else value
}

object ArtLoader {
    private fun cacheBytes(): Int =
        (Runtime.getRuntime().maxMemory() / 8L).toInt().coerceIn(24 * 1024 * 1024, 64 * 1024 * 1024)

    private val cache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // Songs that had no artwork, with the time we looked. They get another try after a while.
    private val misses = HashMap<String, Long>()
    private const val MISS_TTL_MS = 90_000L

    // Every song has its own key. Songs from the same album id do not always share a cover
    // (untagged files in one folder all share one id), so sharing a key showed the wrong art.
    private fun key(track: Track, px: Int): String = "t${track.id}:$px"

    fun peek(track: Track, px: Int): Bitmap? = cache.get(key(track, px))

    suspend fun load(context: Context, track: Track, px: Int): Bitmap? {
        val k = key(track, px)
        val hit = cache.get(k)
        if (hit != null) return hit
        val missedAt = synchronized(misses) { misses[k] }
        if (missedAt != null && SystemClock.elapsedRealtime() - missedAt < MISS_TTL_MS) return null
        val bmp = withContext(Dispatchers.IO) { decode(context, track, px) }
        if (bmp != null) {
            cache.put(k, bmp)
            synchronized(misses) { misses.remove(k) }
        } else {
            synchronized(misses) { misses[k] = SystemClock.elapsedRealtime() }
        }
        return bmp
    }

    /** Forget "no artwork" results, so freshly downloaded or retagged files get another try. */
    fun forgetMisses() {
        synchronized(misses) { misses.clear() }
    }

    private fun decode(context: Context, track: Track, px: Int): Bitmap? {
        var bmp: Bitmap? = null
        // 1. The system thumbnail (works on Android 10 and newer).
        if (Build.VERSION.SDK_INT >= 29) {
            bmp = try {
                context.contentResolver.loadThumbnail(track.uri, Size(px, px), null)
            } catch (e: Exception) {
                null
            }
        }
        // 2. The picture stored inside the file itself.
        if (bmp == null) bmp = embedded(context, track, px)
        // 3. The album art the media scanner saved, for older phones.
        if (bmp == null && track.albumId > 0L) bmp = albumArt(context, track, px)
        return bmp
    }

    private fun embedded(context: Context, track: Track, px: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, track.uri)
            val data = retriever.embeddedPicture
            if (data == null) null else decodeScaled(data, px)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Nothing to clean up.
            }
        }
    }

    private fun albumArt(context: Context, track: Track, px: Int): Bitmap? {
        return try {
            val artUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                track.albumId,
            )
            val data = context.contentResolver.openInputStream(artUri)?.use { it.readBytes() }
            if (data == null) null else decodeScaled(data, px)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Decodes a picture at about the size asked for, so big covers do not eat memory. */
    private fun decodeScaled(data: ByteArray, px: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(data, 0, data.size, opts)
    }
}
