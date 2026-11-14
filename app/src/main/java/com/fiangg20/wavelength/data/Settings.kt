package com.fiangg20.wavelength.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Prefs(
    // Appearance
    val themeMode: Int = 0, // 0 system, 1 light, 2 dark
    val pureBlack: Boolean = false,
    val dynamicColor: Boolean = false,
    val hue: Float = 285f,
    val vibrancy: Float = 1f,
    val artColors: Boolean = true,
    val corners: Int = 2, // 0 sharp, 1 soft, 2 round, 3 pill
    val font: Int = 0, // 0 sans, 1 serif, 2 mono, 3 custom
    // Player
    val artShape: Int = 0, // 0 cookie, 1 circle, 2 squircle, 3 soft square
    val lobes: Int = 9,
    val spinArt: Boolean = true,
    val progressStyle: Int = 0, // 0 wavy, 1 flat
    val waveIntensity: Float = 1f,
    val keepScreenOn: Boolean = true,
    val showRemaining: Boolean = false,
    // Library
    val showListArt: Boolean = true,
    val compactList: Boolean = false,
    val sortBy: Int = 0, // 0 title, 1 artist, 2 album, 3 recently added
    val sortAsc: Boolean = true,
    val minSeconds: Int = 0,
    // Feedback
    val haptics: Boolean = true,
) {
    val cornerScale: Float
        get() = when (corners) {
            0 -> 0.35f
            1 -> 0.8f
            2 -> 1.3f
            else -> 1.9f
        }
}

class SettingsStore(context: Context) {
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
            dynamicColor = sp.getBoolean("dynamicColor", d.dynamicColor),
            hue = sp.getFloat("hue", d.hue),
            vibrancy = sp.getFloat("vibrancy", d.vibrancy),
            artColors = sp.getBoolean("artColors", d.artColors),
            corners = sp.getInt("corners", d.corners),
            font = sp.getInt("font", d.font),
            artShape = sp.getInt("artShape", d.artShape),
            lobes = sp.getInt("lobes", d.lobes),
            spinArt = sp.getBoolean("spinArt", d.spinArt),
            progressStyle = sp.getInt("progressStyle", d.progressStyle),
            waveIntensity = sp.getFloat("waveIntensity", d.waveIntensity),
            keepScreenOn = sp.getBoolean("keepScreenOn", d.keepScreenOn),
            showRemaining = sp.getBoolean("showRemaining", d.showRemaining),
            showListArt = sp.getBoolean("showListArt", d.showListArt),
            compactList = sp.getBoolean("compactList", d.compactList),
            sortBy = sp.getInt("sortBy", d.sortBy),
            sortAsc = sp.getBoolean("sortAsc", d.sortAsc),
            minSeconds = sp.getInt("minSeconds", d.minSeconds),
            haptics = sp.getBoolean("haptics", d.haptics),
        )
    }

    private fun write(p: Prefs) {
        sp.edit()
            .putInt("themeMode", p.themeMode)
            .putBoolean("pureBlack", p.pureBlack)
            .putBoolean("dynamicColor", p.dynamicColor)
            .putFloat("hue", p.hue)
            .putFloat("vibrancy", p.vibrancy)
            .putBoolean("artColors", p.artColors)
            .putInt("corners", p.corners)
            .putInt("font", p.font)
            .putInt("artShape", p.artShape)
            .putInt("lobes", p.lobes)
            .putBoolean("spinArt", p.spinArt)
            .putInt("progressStyle", p.progressStyle)
            .putFloat("waveIntensity", p.waveIntensity)
            .putBoolean("keepScreenOn", p.keepScreenOn)
            .putBoolean("showRemaining", p.showRemaining)
            .putBoolean("showListArt", p.showListArt)
            .putBoolean("compactList", p.compactList)
            .putInt("sortBy", p.sortBy)
            .putBoolean("sortAsc", p.sortAsc)
            .putInt("minSeconds", p.minSeconds)
            .putBoolean("haptics", p.haptics)
            .apply()
    }
}
