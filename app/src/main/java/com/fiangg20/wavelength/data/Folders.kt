package com.fiangg20.wavelength.data

/** A folder on the phone and the songs in it. */
data class FolderUi(
    val path: String,
    val name: String,
    val tracks: List<Track>,
)

object Folders {
    private val fileName = Regex(""".+\.[A-Za-z0-9]{2,5}""")

    /** The folder a song lives in, without a trailing slash and without the file name on older phones. */
    fun pathOf(t: Track): String {
        val parts = t.folder.trim().trimEnd('/', '\\').split('/', '\\').toMutableList()
        if (parts.isNotEmpty() && fileName.matches(parts.last())) parts.removeAt(parts.lastIndex)
        return parts.joinToString("/").ifEmpty { "Other" }
    }

    fun group(tracks: List<Track>): List<FolderUi> =
        tracks.groupBy { pathOf(it) }
            .map { (path, list) ->
                FolderUi(path, path.substringAfterLast('/').ifEmpty { path }, list)
            }
            .sortedBy { it.name.lowercase() }
}
