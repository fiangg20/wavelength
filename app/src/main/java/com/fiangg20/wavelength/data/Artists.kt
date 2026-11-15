package com.fiangg20.wavelength.data

/** What the user chose for splitting artist names. */
data class ArtistConfig(
    val enabled: Boolean,
    val mask: Int,
    val keep: List<String>,
) {
    companion object {
        fun from(p: Prefs): ArtistConfig = ArtistConfig(
            enabled = p.artistSplit,
            mask = p.artistSeps,
            keep = p.artistKeep.split('\n', ';').map { it.trim() }.filter { it.isNotEmpty() },
        )
    }
}

/** Turns "RYO, Limbx" or "A feat. B" into separate artist names. */
object ArtistSplitter {
    /** Labels for the settings screen. The position in this list is the bit in [ArtistConfig.mask]. */
    val SEPARATORS = listOf(",", ";", "/", "&", "feat. / ft.", "x", "+")

    private val patterns = listOf(
        """\s*,\s*""",
        """\s*;\s*""",
        """\s*/\s*""",
        """\s*&\s*""",
        """\s*[(\[]?\s*\b(?:feat|ft|featuring)\b\.?\s+""",
        """\s+[x×]\s+""",
        """\s*\+\s*""",
    )

    private val cache = HashMap<Int, Regex?>()

    private fun regexFor(mask: Int): Regex? = synchronized(cache) {
        cache.getOrPut(mask) {
            val parts = patterns.filterIndexed { i, _ -> mask and (1 shl i) != 0 }
            if (parts.isEmpty()) null else Regex(parts.joinToString("|"), RegexOption.IGNORE_CASE)
        }
    }

    private val holder = Regex("""\u0001(\d+)\u0001""")

    fun split(raw: String, cfg: ArtistConfig): List<String> {
        val name = raw.trim()
        if (!cfg.enabled || name.isEmpty() || name == UNKNOWN_ARTIST) return listOf(name)
        val regex = regexFor(cfg.mask) ?: return listOf(name)

        // Hide the names that must stay whole, so their commas and slashes are not touched.
        val saved = ArrayList<String>()
        var work = name
        for (k in cfg.keep) {
            work = Regex(Regex.escape(k), RegexOption.IGNORE_CASE).replace(work) { m ->
                saved.add(m.value)
                "\u0001${saved.lastIndex}\u0001"
            }
        }

        val out = ArrayList<String>()
        for (piece in work.split(regex)) {
            val restored = holder.replace(piece) { m -> saved.getOrElse(m.groupValues[1].toInt()) { "" } }
            val tidy = restored.trim().trim('(', ')', '[', ']').trim()
            if (tidy.isNotEmpty() && out.none { it.equals(tidy, ignoreCase = true) }) out.add(tidy)
        }
        return if (out.isEmpty()) listOf(name) else out
    }
}

/** Builds the album and artist lists, fixing albums that Android has split in two. */
object Library {

    private fun key(s: String) = s.trim().lowercase().replace(Regex("""\s+"""), " ")

    /** The folder name of a track, without a file name on the end (older phones store the whole path). */
    private fun folderName(t: Track): String {
        val parts = t.folder.trimEnd('/', '\\').split('/', '\\')
        val last = parts.lastOrNull().orEmpty()
        val looksLikeFile = Regex(""".+\.[A-Za-z0-9]{2,5}""").matches(last)
        return if (looksLikeFile) parts.getOrNull(parts.size - 2).orEmpty() else last
    }

    /**
     * Android names an album after its folder when the file has no album tag. Those are not real albums,
     * so every one with the same name is gathered into a single card.
     */
    private fun isFolderAlbum(tracks: List<Track>): Boolean {
        val title = tracks.first().album
        if (title == "Unknown album") return true
        return tracks.all { key(folderName(it)) == key(it.album) }
    }

    fun albums(tracks: List<Track>, cfg: ArtistConfig): List<Album> {
        // 1. What Android calls an album.
        val byId = tracks.groupBy { it.albumId }.values.toList()

        // 2. Clusters with the same title may still be one album.
        val result = ArrayList<List<Track>>()
        val byTitle = byId.groupBy { key(it.first().album) }
        for ((_, clusters) in byTitle) {
            if (clusters.size == 1) {
                result.add(clusters[0])
                continue
            }
            if (clusters.all { isFolderAlbum(it) }) {
                result.add(clusters.flatten())
                continue
            }
            // Merge clusters that share at least one artist, e.g. "RYO, Limbx" and "RYO, Noya Clarissa".
            val groups = ArrayList<MutableList<List<Track>>>()
            val groupArtists = ArrayList<MutableSet<String>>()
            for (cl in clusters) {
                val names = cl.flatMap { ArtistSplitter.split(it.artist, cfg) }.map { key(it) }.toSet()
                var home = -1
                for (i in groups.indices) {
                    if (groupArtists[i].any { it in names }) {
                        home = i
                        break
                    }
                }
                if (home >= 0) {
                    groups[home].add(cl)
                    groupArtists[home].addAll(names)
                } else {
                    groups.add(mutableListOf(cl))
                    groupArtists.add(names.toMutableSet())
                }
            }
            // A merge can make two earlier groups overlap, so fold again until nothing changes.
            var changed = true
            while (changed) {
                changed = false
                loop@ for (a in groups.indices) {
                    for (b in a + 1 until groups.size) {
                        if (groupArtists[a].any { it in groupArtists[b] }) {
                            groups[a].addAll(groups[b])
                            groupArtists[a].addAll(groupArtists[b])
                            groups.removeAt(b)
                            groupArtists.removeAt(b)
                            changed = true
                            break@loop
                        }
                    }
                }
            }
            for (g in groups) result.add(g.flatten())
        }

        return result.map { all ->
            val ordered = all.sortedWith(compareBy<Track> { it.trackNo }.thenBy { it.title.lowercase() })
            // The first track that knows a real album id is a good cover and a stable id for the route.
            val id = all.minOf { it.albumId }
            Album(
                id = id,
                title = ordered.first().album,
                artist = albumArtist(ordered, cfg),
                tracks = ordered,
            )
        }.sortedBy { it.title.lowercase() }
    }

    /** The artist most songs start with, or "Various artists" when no one really owns the album. */
    private fun albumArtist(tracks: List<Track>, cfg: ArtistConfig): String {
        val firsts = tracks.map { ArtistSplitter.split(it.artist, cfg).first() }
        val top = firsts.groupingBy { key(it) }.eachCount().maxByOrNull { it.value } ?: return UNKNOWN_ARTIST
        return if (top.value * 10 >= tracks.size * 6) {
            firsts.first { key(it) == top.key }
        } else {
            "Various artists"
        }
    }

    fun artists(tracks: List<Track>, cfg: ArtistConfig): List<Artist> {
        val names = LinkedHashMap<String, String>() // lower-case key -> name as first seen
        val songs = LinkedHashMap<String, MutableList<Track>>()
        for (t in tracks) {
            for (n in ArtistSplitter.split(t.artist, cfg)) {
                val k = key(n)
                names.putIfAbsent(k, n)
                songs.getOrPut(k) { ArrayList() }.add(t)
            }
        }
        return songs.map { (k, ts) ->
            // Albums are counted by title, so a split album is not counted twice.
            val albumCount = ts.map { key(it.album) }.distinct().size
            Artist(names.getValue(k), ts, albumCount)
        }.sortedBy { it.name.lowercase() }
    }
}
