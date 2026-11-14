package com.fiangg20.wavelength.data

import android.net.Uri

const val UNKNOWN_ARTIST = "Unknown artist"

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dateAdded: Long,
    val trackNo: Int,
    val uri: Uri,
    val folder: String = "",
)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val tracks: List<Track>,
)

data class Artist(
    val name: String,
    val tracks: List<Track>,
) {
    val albumCount: Int get() = tracks.map { it.albumId }.distinct().size
}

/** Fixes untagged files: "Artist - Title" in the title becomes artist and title, underscores become spaces. */
fun smartClean(t: Track): Track {
    var title = t.title.replace('_', ' ').trim()
    var artist = t.artist
    if (artist == UNKNOWN_ARTIST) {
        val idx = title.indexOf(" - ")
        if (idx in 1..60 && idx + 3 < title.length) {
            artist = title.substring(0, idx).trim()
            title = title.substring(idx + 3).trim()
        }
    }
    if (title.isBlank()) title = t.title
    if (artist.isBlank()) artist = t.artist
    return if (title == t.title && artist == t.artist) t else t.copy(title = title, artist = artist)
}
