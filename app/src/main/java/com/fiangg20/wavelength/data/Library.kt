package com.fiangg20.wavelength.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
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
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
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
            while (c.moveToNext()) {
                val id = c.getLong(idC)
                out.add(
                    Track(
                        id = id,
                        title = clean(c.getString(titleC), "Untitled"),
                        artist = clean(c.getString(artistC), "Unknown artist"),
                        album = clean(c.getString(albumC), "Unknown album"),
                        albumId = c.getLong(albumIdC),
                        durationMs = c.getLong(durC),
                        dateAdded = c.getLong(addedC),
                        trackNo = c.getInt(trackC),
                        uri = ContentUris.withAppendedId(collection, id),
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
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val misses = HashSet<String>()

    private fun key(track: Track, px: Int): String =
        if (track.albumId > 0L) "a${track.albumId}:$px" else "t${track.id}:$px"

    fun peek(track: Track, px: Int): Bitmap? = cache.get(key(track, px))

    suspend fun load(context: Context, track: Track, px: Int): Bitmap? {
        val k = key(track, px)
        val hit = cache.get(k)
        if (hit != null) return hit
        val known = synchronized(misses) { misses.contains(k) }
        if (known) return null
        val bmp = withContext(Dispatchers.IO) { decode(context, track, px) }
        if (bmp != null) {
            cache.put(k, bmp)
        } else {
            synchronized(misses) { misses.add(k) }
        }
        return bmp
    }

    private fun decode(context: Context, track: Track, px: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(track.uri, Size(px, px), null)
            } else {
                val artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    track.albumId,
                )
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(artUri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) {
                    sample *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(artUri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
