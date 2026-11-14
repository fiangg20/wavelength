package com.fiangg20.wavelength.download

import android.content.Context
import com.fiangg20.wavelength.data.DlOptions
import com.fiangg20.wavelength.data.DlPrefs
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/** Turns the downloader settings into a yt-dlp command line. */
object CommandBuilder {

    fun cookiesFile(app: Context): File = File(app.filesDir, "cookies.txt")

    fun archiveFile(app: Context): File = File(app.filesDir, "ytdl-archive.txt")

    /** Plain text that is not a link becomes a YouTube search. */
    fun normalizeInput(raw: String, c: DlPrefs): String {
        val s = raw.trim()
        val looksLikeLink = s.contains("://") ||
            s.startsWith("ytsearch") ||
            (s.contains('.') && !s.contains(' '))
        return if (looksLikeLink) s else "ytsearch${c.searchCount.coerceIn(1, 20)}:$s"
    }

    fun build(app: Context, rawInput: String, trim: String, outDir: File, c: DlPrefs): YoutubeDLRequest {
        val request = YoutubeDLRequest(normalizeInput(rawInput, c))
        val format = DlOptions.formats[c.format.coerceIn(0, DlOptions.formats.lastIndex)]

        // What to download and how to convert it
        val selector = if (c.source >= DlOptions.sources.size) {
            c.customFormat.trim().ifEmpty { "bestaudio/best" }
        } else {
            DlOptions.sources[c.source.coerceAtLeast(0)]
        }
        request.addOption("-f", selector)
        request.addOption("-x")
        request.addOption("--audio-format", format)
        if (format != "flac" && format != "wav" && format != "best") {
            request.addOption(
                "--audio-quality",
                DlOptions.bitrates[c.bitrate.coerceIn(0, DlOptions.bitrates.lastIndex)],
            )
        }

        // File name
        val base = c.customTemplate.trim().ifEmpty {
            DlOptions.nameStyles[c.nameStyle.coerceIn(0, DlOptions.nameStyles.lastIndex)]
        }
        request.addOption("-o", File(outDir, "$base.%(ext)s").absolutePath)
        request.addOption("--trim-filenames", 120)
        request.addOption("--no-mtime")
        if (c.restrictNames) request.addOption("--restrict-filenames")

        // Tags and cover art
        if (c.embedMeta) request.addOption("--embed-metadata")
        if (c.embedChapters) request.addOption("--embed-chapters")
        val canEmbedCover = format == "mp3" || format == "m4a" || format == "flac"
        if (c.embedThumb && canEmbedCover) {
            request.addOption("--embed-thumbnail")
            request.addOption("--convert-thumbnails", "jpg")
            if (c.squareCover) {
                request.addOption(
                    "--ppa",
                    "ThumbnailsConvertor+FFmpeg_o:-vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"",
                )
            }
        }
        if (c.parseArtist) request.addOption("--parse-metadata", "title:%(artist)s - %(title)s")
        if (c.cleanTitle) {
            request.addCommands(listOf("--replace-in-metadata", "title", TITLE_NOISE, ""))
        }

        // Playlists
        when (c.playlistMode) {
            1 -> request.addOption("--no-playlist")
            2 -> request.addOption("--yes-playlist")
        }
        if (c.playlistItems.isNotBlank()) request.addOption("--playlist-items", c.playlistItems.trim())
        if (c.playlistReverse) request.addOption("--playlist-reverse")
        if (c.playlistRandom) request.addOption("--playlist-random")
        if (c.maxDownloads > 0) request.addOption("--max-downloads", c.maxDownloads)
        if (c.useArchive) request.addOption("--download-archive", archiveFile(app).absolutePath)
        request.addOption("--ignore-errors")

        // SponsorBlock and trimming
        if (c.sponsorBlock) {
            val cats = DlOptions.sbKeys.filterIndexed { i, _ -> (c.sbMask and (1 shl i)) != 0 }
            if (cats.isNotEmpty()) request.addOption("--sponsorblock-remove", cats.joinToString(","))
        }
        val section = trim.trim()
        if (section.isNotEmpty()) {
            request.addOption("--download-sections", if (section.startsWith("*")) section else "*$section")
            request.addOption("--force-keyframes-at-cuts")
        }

        // Network
        if (c.proxy.isNotBlank()) request.addOption("--proxy", c.proxy.trim())
        if (c.limitRate.isNotBlank()) request.addOption("--limit-rate", c.limitRate.trim())
        request.addOption("--retries", c.retries)
        request.addOption("--fragment-retries", c.fragRetries)
        if (c.threads > 1) request.addOption("--concurrent-fragments", c.threads)
        if (c.timeout > 0) request.addOption("--socket-timeout", c.timeout)
        if (c.sleepInterval > 0) request.addOption("--sleep-interval", c.sleepInterval)
        when (c.forceIp) {
            1 -> request.addOption("--force-ipv4")
            2 -> request.addOption("--force-ipv6")
        }
        if (c.geoBypass) request.addOption("--geo-bypass")
        if (c.noCertCheck) request.addOption("--no-check-certificates")
        if (c.userAgent.isNotBlank()) request.addOption("--user-agent", c.userAgent.trim())
        val cookies = cookiesFile(app)
        if (c.useCookies && cookies.exists()) request.addOption("--cookies", cookies.absolutePath)
        val client = DlOptions.clients.getOrElse(c.ytClient) { "" }
        if (client.isNotEmpty()) request.addOption("--extractor-args", "youtube:player_client=$client")

        // Anything else the user typed
        val extra = tokenize(c.extraArgs)
        if (extra.isNotEmpty()) request.addCommands(extra)

        return request
    }

    /** Matches "(Official Video)", "[Lyrics]" and similar noise at the end of a title. */
    private const val TITLE_NOISE =
        """(?i)\s*[\(\[][^\)\]]*(official|lyrics?|audio|video|visuali[sz]er|m/v)[^\)\]]*[\)\]]"""

    /** Splits a line into arguments, honouring single and double quotes. */
    fun tokenize(text: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quote = '\u0000'
        var has = false
        for (ch in text) {
            when {
                quote != '\u0000' -> {
                    if (ch == quote) quote = '\u0000' else sb.append(ch)
                }
                ch == '"' || ch == '\'' -> {
                    quote = ch
                    has = true
                }
                ch.isWhitespace() -> {
                    if (has || sb.isNotEmpty()) {
                        out.add(sb.toString())
                        sb.setLength(0)
                        has = false
                    }
                }
                else -> sb.append(ch)
            }
        }
        if (has || sb.isNotEmpty()) out.add(sb.toString())
        return out
    }
}
