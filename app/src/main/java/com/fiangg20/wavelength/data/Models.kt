package com.fiangg20.wavelength.data

import android.net.Uri

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
