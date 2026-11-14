package com.fiangg20.wavelength.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the downloader can be configured with. Each field maps to a yt-dlp option. */
data class DlPrefs(
    // Output
    val format: Int = 0, // index into DlOptions.formats
    val bitrate: Int = 2, // index into DlOptions.bitrates
    val source: Int = 0, // index into DlOptions.sources
    val customFormat: String = "",
    val nameStyle: Int = 0, // index into DlOptions.nameStyles
    val customTemplate: String = "",
    val restrictNames: Boolean = false,
    val saveFolder: String = "Wavelength",
    // Metadata
    val embedThumb: Boolean = true,
    val squareCover: Boolean = false,
    val embedMeta: Boolean = true,
    val parseArtist: Boolean = true,
    val cleanTitle: Boolean = true,
    val embedChapters: Boolean = false,
    // Playlists
    val playlistMode: Int = 1, // 0 yt-dlp default, 1 single video only, 2 whole playlist
    val playlistItems: String = "",
    val playlistReverse: Boolean = false,
    val playlistRandom: Boolean = false,
    val maxDownloads: Int = 0,
    val useArchive: Boolean = false,
    val skipExisting: Boolean = true,
    val searchCount: Int = 1,
    // SponsorBlock
    val sponsorBlock: Boolean = false,
    val sbMask: Int = 1 or 8 or 32,
    // Network
    val proxy: String = "",
    val limitRate: String = "",
    val retries: Int = 10,
    val fragRetries: Int = 10,
    val threads: Int = 4,
    val timeout: Int = 0,
    val sleepInterval: Int = 0,
    val forceIp: Int = 0, // 0 auto, 1 IPv4, 2 IPv6
    val geoBypass: Boolean = true,
    val noCertCheck: Boolean = false,
    val userAgent: String = "",
    val useCookies: Boolean = false,
    val ytClient: Int = 0, // index into DlOptions.clients
    // Engine and queue
    val updateChannel: Int = 0, // 0 stable, 1 nightly, 2 master
    val autoUpdate: Boolean = true,
    val parallel: Int = 1,
    val wifiOnly: Boolean = false,
    val autoRefresh: Boolean = true,
    // Raw yt-dlp arguments appended last
    val extraArgs: String = "",
)

/** Lists shown in the UI, and the yt-dlp values behind them. */
object DlOptions {
    val formats = listOf("mp3", "m4a", "opus", "flac", "wav", "aac", "vorbis", "best")
    val formatLabels = listOf(
        "MP3", "M4A (AAC)", "Opus", "FLAC (lossless)", "WAV (lossless)", "AAC", "Ogg Vorbis",
        "Original, no conversion",
    )
    val bitrates = listOf("320K", "256K", "192K", "160K", "128K", "96K", "64K", "0")
    val bitrateLabels = listOf(
        "320 kbps", "256 kbps", "192 kbps", "160 kbps", "128 kbps", "96 kbps", "64 kbps",
        "Best variable quality",
    )
    val sources = listOf(
        "bestaudio/best",
        "bestaudio[ext=m4a]/bestaudio/best",
        "bestaudio[acodec=opus]/bestaudio/best",
        "worstaudio/worst",
    )
    val sourceLabels = listOf(
        "Best audio available",
        "Prefer M4A source",
        "Prefer Opus source",
        "Smallest audio (data saver)",
        "Custom selector",
    )
    val nameStyles = listOf(
        "%(title)s",
        "%(artist,uploader)s - %(title)s",
        "%(uploader)s - %(title)s",
        "%(playlist_index&{} - |)s%(title)s",
    )
    val nameStyleLabels = listOf(
        "Title",
        "Artist - Title",
        "Uploader - Title",
        "Number - Title (playlists)",
    )
    val clients = listOf("", "android", "ios", "web", "mweb", "tv")
    val clientLabels = listOf(
        "Automatic (yt-dlp default)", "Android", "iOS", "Web", "Mobile web", "TV",
    )
    val sbKeys = listOf(
        "sponsor", "intro", "outro", "selfpromo", "interaction", "music_offtopic", "preview", "filler",
    )
    val sbLabels = listOf(
        "Sponsor", "Intro", "Outro", "Self promotion", "Interaction reminder",
        "Non-music section", "Preview", "Filler",
    )
    val channels = listOf("Stable", "Nightly", "Master")
}

class DlStore private constructor(context: Context) {
    private val sp = context.getSharedPreferences("wavelength_dl", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<DlPrefs> = _state.asStateFlow()

    fun update(block: (DlPrefs) -> DlPrefs) {
        val next = block(_state.value)
        _state.value = next
        write(next)
    }

    private fun str(key: String, def: String): String = sp.getString(key, def) ?: def

    private fun read(): DlPrefs {
        val d = DlPrefs()
        return DlPrefs(
            format = sp.getInt("format", d.format),
            bitrate = sp.getInt("bitrate", d.bitrate),
            source = sp.getInt("source", d.source),
            customFormat = str("customFormat", d.customFormat),
            nameStyle = sp.getInt("nameStyle", d.nameStyle),
            customTemplate = str("customTemplate", d.customTemplate),
            restrictNames = sp.getBoolean("restrictNames", d.restrictNames),
            saveFolder = str("saveFolder", d.saveFolder),
            embedThumb = sp.getBoolean("embedThumb", d.embedThumb),
            squareCover = sp.getBoolean("squareCover", d.squareCover),
            embedMeta = sp.getBoolean("embedMeta", d.embedMeta),
            parseArtist = sp.getBoolean("parseArtist", d.parseArtist),
            cleanTitle = sp.getBoolean("cleanTitle", d.cleanTitle),
            embedChapters = sp.getBoolean("embedChapters", d.embedChapters),
            playlistMode = sp.getInt("playlistMode", d.playlistMode),
            playlistItems = str("playlistItems", d.playlistItems),
            playlistReverse = sp.getBoolean("playlistReverse", d.playlistReverse),
            playlistRandom = sp.getBoolean("playlistRandom", d.playlistRandom),
            maxDownloads = sp.getInt("maxDownloads", d.maxDownloads),
            useArchive = sp.getBoolean("useArchive", d.useArchive),
            skipExisting = sp.getBoolean("skipExisting", d.skipExisting),
            searchCount = sp.getInt("searchCount", d.searchCount),
            sponsorBlock = sp.getBoolean("sponsorBlock", d.sponsorBlock),
            sbMask = sp.getInt("sbMask", d.sbMask),
            proxy = str("proxy", d.proxy),
            limitRate = str("limitRate", d.limitRate),
            retries = sp.getInt("retries", d.retries),
            fragRetries = sp.getInt("fragRetries", d.fragRetries),
            threads = sp.getInt("threads", d.threads),
            timeout = sp.getInt("timeout", d.timeout),
            sleepInterval = sp.getInt("sleepInterval", d.sleepInterval),
            forceIp = sp.getInt("forceIp", d.forceIp),
            geoBypass = sp.getBoolean("geoBypass", d.geoBypass),
            noCertCheck = sp.getBoolean("noCertCheck", d.noCertCheck),
            userAgent = str("userAgent", d.userAgent),
            useCookies = sp.getBoolean("useCookies", d.useCookies),
            ytClient = sp.getInt("ytClient", d.ytClient),
            updateChannel = sp.getInt("updateChannel", d.updateChannel),
            autoUpdate = sp.getBoolean("autoUpdate", d.autoUpdate),
            parallel = sp.getInt("parallel", d.parallel),
            wifiOnly = sp.getBoolean("wifiOnly", d.wifiOnly),
            autoRefresh = sp.getBoolean("autoRefresh", d.autoRefresh),
            extraArgs = str("extraArgs", d.extraArgs),
        )
    }

    private fun write(p: DlPrefs) {
        sp.edit()
            .putInt("format", p.format)
            .putInt("bitrate", p.bitrate)
            .putInt("source", p.source)
            .putString("customFormat", p.customFormat)
            .putInt("nameStyle", p.nameStyle)
            .putString("customTemplate", p.customTemplate)
            .putBoolean("restrictNames", p.restrictNames)
            .putString("saveFolder", p.saveFolder)
            .putBoolean("embedThumb", p.embedThumb)
            .putBoolean("squareCover", p.squareCover)
            .putBoolean("embedMeta", p.embedMeta)
            .putBoolean("parseArtist", p.parseArtist)
            .putBoolean("cleanTitle", p.cleanTitle)
            .putBoolean("embedChapters", p.embedChapters)
            .putInt("playlistMode", p.playlistMode)
            .putString("playlistItems", p.playlistItems)
            .putBoolean("playlistReverse", p.playlistReverse)
            .putBoolean("playlistRandom", p.playlistRandom)
            .putInt("maxDownloads", p.maxDownloads)
            .putBoolean("useArchive", p.useArchive)
            .putBoolean("skipExisting", p.skipExisting)
            .putInt("searchCount", p.searchCount)
            .putBoolean("sponsorBlock", p.sponsorBlock)
            .putInt("sbMask", p.sbMask)
            .putString("proxy", p.proxy)
            .putString("limitRate", p.limitRate)
            .putInt("retries", p.retries)
            .putInt("fragRetries", p.fragRetries)
            .putInt("threads", p.threads)
            .putInt("timeout", p.timeout)
            .putInt("sleepInterval", p.sleepInterval)
            .putInt("forceIp", p.forceIp)
            .putBoolean("geoBypass", p.geoBypass)
            .putBoolean("noCertCheck", p.noCertCheck)
            .putString("userAgent", p.userAgent)
            .putBoolean("useCookies", p.useCookies)
            .putInt("ytClient", p.ytClient)
            .putInt("updateChannel", p.updateChannel)
            .putBoolean("autoUpdate", p.autoUpdate)
            .putInt("parallel", p.parallel)
            .putBoolean("wifiOnly", p.wifiOnly)
            .putBoolean("autoRefresh", p.autoRefresh)
            .putString("extraArgs", p.extraArgs)
            .apply()
    }

    companion object {
        @Volatile
        private var instance: DlStore? = null

        fun get(context: Context): DlStore =
            instance ?: synchronized(this) {
                instance ?: DlStore(context.applicationContext).also { instance = it }
            }
    }
}
