package com.fiangg20.wavelength.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Prefs(
    // Appearance
    val themeMode: Int = 0, // 0 system, 1 light, 2 dark
    val pureBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    val hue: Float = 285f,
    val adaptiveIcon: Boolean = true, // launcher icon colour follows the accent
    val vibrancy: Float = 1f,
    val artColors: Boolean = true,
    val corners: Int = 1, // 0 sharp, 1 soft, 2 round, 3 pill
    val font: Int = 0, // 0 Google Sans Flex, 1 system, 2 serif, 3 mono, 4 custom file
    val fontWidth: Float = 100f, // Google Sans Flex width axis
    val fontRound: Float = 0f, // Google Sans Flex roundness axis
    // Player
    val artShape: Int = 0, // 0 cookie, 1 circle, 2 squircle, 3 soft square
    val lobes: Int = 9,
    val spinArt: Boolean = true,
    val artScale: Float = 1f,
    val blurArt: Boolean = true,
    val progressStyle: Int = 0, // 0 wavy, 1 flat
    val waveIntensity: Float = 1f,
    val keepScreenOn: Boolean = true,
    val showRemaining: Boolean = false,
    val miniSkip: Boolean = true,
    val miniProgress: Boolean = true,
    // Playback
    val skipSilence: Boolean = false,
    val pauseOnUnplug: Boolean = true,
    val restoreSession: Boolean = true,
    val vinylSync: Boolean = true, // speed and pitch move together
    val replayGain: Boolean = false, // even out loudness using ReplayGain tags in the files
    val rgPreamp: Float = 0f, // dB added on top of the ReplayGain value
    val fadeSec: Float = 0f, // seconds of fade at the end and start of songs, 0 is off
    val lyricsOnline: Boolean = true, // look up lyrics on lrclib.net
    val lyricsSize: Float = 1f,
    val lyricsWordMode: Int = 1, // 0 line only, 1 words (estimated when the file has no word timing), 2 real word timing only
    val lyricsAlign: Int = 0, // 0 left, 1 centre, 2 right
    val lyricsColor: Int = 0, // 0 accent, 1 white, 2 tertiary
    val lyricsDim: Float = 0.42f, // how bright the lines that are not playing stay
    val lyricsSpacing: Float = 1f, // gap between lines
    val lyricsBold: Boolean = true,
    val lyricsZoom: Boolean = true, // the playing line is a little bigger
    val lyricsShiftMs: Int = 120, // global timing nudge for every song
    val lyricsOnDownload: Boolean = true, // fetch lyrics for songs the downloader just saved
    // Library
    val showListArt: Boolean = true,
    val compactList: Boolean = false,
    val sortBy: Int = 0, // 0 title, 1 artist, 2 album, 3 recently added, 4 duration
    val sortAsc: Boolean = true,
    val minSeconds: Int = 0,
    val smartTags: Boolean = true,
    val excluded: String = "",
    val albumGrid: Int = 1, // 0 compact, 1 cozy, 2 large
    val artistSplit: Boolean = true, // "RYO, Limbx" becomes two artists
    val artistSeps: Int = 31, // bit mask of the separators in use, see ArtistSplitter.SEPARATORS
    val artistKeep: String = "AC/DC\nTyler, The Creator", // names that must never be split, one per line
    val onlineImages: Boolean = true, // look up artist and album pictures on the internet when the files have none
    // Feedback
    val haptics: Boolean = true,
    // Channels
    val balance: Float = 0f, // -1 left .. 1 right
    val leftLevel: Float = 1f,
    val rightLevel: Float = 1f,
    val monoAudio: Boolean = false,
) {
    val cornerScale: Float
        get() = when (corners) {
            0 -> 0.35f
            1 -> 0.8f
            2 -> 1.3f
            else -> 1.9f
        }
}

class SettingsStore private constructor(context: Context) {
    private val sp = context.getSharedPreferences("wavelength_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Prefs> = _state.asStateFlow()

    fun update(block: (Prefs) -> Prefs) {
        val next = block(_state.value)
        _state.value = next
        write(next)
    }

    private fun read(): Prefs {
        val d = Prefs()
        return Prefs(
            themeMode = sp.getInt("themeMode", d.themeMode),
            pureBlack = sp.getBoolean("pureBlack", d.pureBlack),
            dynamicColor = sp.getBoolean("dynamicColor2", d.dynamicColor),
            hue = sp.getFloat("hue", d.hue),
            adaptiveIcon = sp.getBoolean("adaptiveIcon", d.adaptiveIcon),
            vibrancy = sp.getFloat("vibrancy", d.vibrancy),
            artColors = sp.getBoolean("artColors", d.artColors),
            corners = sp.getInt("corners2", d.corners),
            font = sp.getInt("font2", d.font),
            fontWidth = sp.getFloat("fontWidth", d.fontWidth),
            fontRound = sp.getFloat("fontRound", d.fontRound),
            artShape = sp.getInt("artShape", d.artShape),
            lobes = sp.getInt("lobes", d.lobes),
            spinArt = sp.getBoolean("spinArt", d.spinArt),
            artScale = sp.getFloat("artScale", d.artScale),
            blurArt = sp.getBoolean("blurArt", d.blurArt),
            progressStyle = sp.getInt("progressStyle", d.progressStyle),
            waveIntensity = sp.getFloat("waveIntensity", d.waveIntensity),
            keepScreenOn = sp.getBoolean("keepScreenOn", d.keepScreenOn),
            showRemaining = sp.getBoolean("showRemaining", d.showRemaining),
            miniSkip = sp.getBoolean("miniSkip", d.miniSkip),
            miniProgress = sp.getBoolean("miniProgress", d.miniProgress),
            skipSilence = sp.getBoolean("skipSilence", d.skipSilence),
            pauseOnUnplug = sp.getBoolean("pauseOnUnplug", d.pauseOnUnplug),
            restoreSession = sp.getBoolean("restoreSession", d.restoreSession),
            vinylSync = sp.getBoolean("vinylSync", d.vinylSync),
            replayGain = sp.getBoolean("replayGain", d.replayGain),
            rgPreamp = sp.getFloat("rgPreamp", d.rgPreamp),
            fadeSec = sp.getFloat("fadeSec", d.fadeSec),
            lyricsOnDownload = sp.getBoolean("lyricsOnDownload", d.lyricsOnDownload),
            lyricsOnline = sp.getBoolean("lyricsOnline", d.lyricsOnline),
            lyricsSize = sp.getFloat("lyricsSize", d.lyricsSize),
            lyricsWordMode = sp.getInt("lyricsWordMode", d.lyricsWordMode),
            lyricsAlign = sp.getInt("lyricsAlign", d.lyricsAlign),
            lyricsColor = sp.getInt("lyricsColor", d.lyricsColor),
            lyricsDim = sp.getFloat("lyricsDim", d.lyricsDim),
            lyricsSpacing = sp.getFloat("lyricsSpacing", d.lyricsSpacing),
            lyricsBold = sp.getBoolean("lyricsBold", d.lyricsBold),
            lyricsZoom = sp.getBoolean("lyricsZoom", d.lyricsZoom),
            lyricsShiftMs = sp.getInt("lyricsShiftMs", d.lyricsShiftMs),
            showListArt = sp.getBoolean("showListArt", d.showListArt),
            compactList = sp.getBoolean("compactList", d.compactList),
            sortBy = sp.getInt("sortBy", d.sortBy),
            sortAsc = sp.getBoolean("sortAsc", d.sortAsc),
            minSeconds = sp.getInt("minSeconds", d.minSeconds),
            smartTags = sp.getBoolean("smartTags", d.smartTags),
            excluded = sp.getString("excluded", d.excluded) ?: d.excluded,
            albumGrid = sp.getInt("albumGrid", d.albumGrid),
            artistSplit = sp.getBoolean("artistSplit", d.artistSplit),
            artistSeps = sp.getInt("artistSeps", d.artistSeps),
            artistKeep = sp.getString("artistKeep", d.artistKeep) ?: d.artistKeep,
            onlineImages = sp.getBoolean("onlineImages", d.onlineImages),
            haptics = sp.getBoolean("haptics", d.haptics),
            balance = sp.getFloat("balance", d.balance),
            leftLevel = sp.getFloat("leftLevel", d.leftLevel),
            rightLevel = sp.getFloat("rightLevel", d.rightLevel),
            monoAudio = sp.getBoolean("monoAudio", d.monoAudio),
        )
    }

    private fun write(p: Prefs) {
        sp.edit()
            .putInt("themeMode", p.themeMode)
            .putBoolean("pureBlack", p.pureBlack)
            .putBoolean("dynamicColor2", p.dynamicColor)
            .putFloat("hue", p.hue)
            .putBoolean("adaptiveIcon", p.adaptiveIcon)
            .putFloat("vibrancy", p.vibrancy)
            .putBoolean("artColors", p.artColors)
            .putInt("corners2", p.corners)
            .putInt("font2", p.font)
            .putFloat("fontWidth", p.fontWidth)
            .putFloat("fontRound", p.fontRound)
            .putInt("artShape", p.artShape)
            .putInt("lobes", p.lobes)
            .putBoolean("spinArt", p.spinArt)
            .putFloat("artScale", p.artScale)
            .putBoolean("blurArt", p.blurArt)
            .putInt("progressStyle", p.progressStyle)
            .putFloat("waveIntensity", p.waveIntensity)
            .putBoolean("keepScreenOn", p.keepScreenOn)
            .putBoolean("showRemaining", p.showRemaining)
            .putBoolean("miniSkip", p.miniSkip)
            .putBoolean("miniProgress", p.miniProgress)
            .putBoolean("skipSilence", p.skipSilence)
            .putBoolean("pauseOnUnplug", p.pauseOnUnplug)
            .putBoolean("restoreSession", p.restoreSession)
            .putBoolean("vinylSync", p.vinylSync)
            .putBoolean("replayGain", p.replayGain)
            .putFloat("rgPreamp", p.rgPreamp)
            .putFloat("fadeSec", p.fadeSec)
            .putBoolean("lyricsOnDownload", p.lyricsOnDownload)
            .putBoolean("lyricsOnline", p.lyricsOnline)
            .putFloat("lyricsSize", p.lyricsSize)
            .putInt("lyricsWordMode", p.lyricsWordMode)
            .putInt("lyricsAlign", p.lyricsAlign)
            .putInt("lyricsColor", p.lyricsColor)
            .putFloat("lyricsDim", p.lyricsDim)
            .putFloat("lyricsSpacing", p.lyricsSpacing)
            .putBoolean("lyricsBold", p.lyricsBold)
            .putBoolean("lyricsZoom", p.lyricsZoom)
            .putInt("lyricsShiftMs", p.lyricsShiftMs)
            .putBoolean("showListArt", p.showListArt)
            .putBoolean("compactList", p.compactList)
            .putInt("sortBy", p.sortBy)
            .putBoolean("sortAsc", p.sortAsc)
            .putInt("minSeconds", p.minSeconds)
            .putBoolean("smartTags", p.smartTags)
            .putString("excluded", p.excluded)
            .putInt("albumGrid", p.albumGrid)
            .putBoolean("artistSplit", p.artistSplit)
            .putInt("artistSeps", p.artistSeps)
            .putString("artistKeep", p.artistKeep)
            .putBoolean("onlineImages", p.onlineImages)
            .putBoolean("haptics", p.haptics)
            .putFloat("balance", p.balance)
            .putFloat("leftLevel", p.leftLevel)
            .putFloat("rightLevel", p.rightLevel)
            .putBoolean("monoAudio", p.monoAudio)
            .apply()
    }

    companion object {
        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
