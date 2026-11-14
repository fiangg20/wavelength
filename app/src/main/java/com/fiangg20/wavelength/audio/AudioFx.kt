package com.fiangg20.wavelength.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ln
import kotlin.math.roundToInt

/** A preset is a curve in dB at five reference frequencies; it is mapped onto however many bands the device has. */
data class EqPreset(val name: String, val gains: List<Float>)

data class EqSettings(
    val enabled: Boolean = false,
    val preset: Int = 0, // index into EqPresets.all, or -1 for a hand-made curve
    val bands: List<Int> = emptyList(), // millibels per band
    val bass: Int = 0, // 0..1000
    val virtualizer: Int = 0, // 0..1000
    val loudness: Int = 0, // millibels of extra gain, 0..1500
)

data class EqInfo(
    val available: Boolean = false,
    val bandCount: Int = 0,
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val freqsHz: List<Int> = emptyList(),
    val bassOk: Boolean = false,
    val virtualizerOk: Boolean = false,
    val loudnessOk: Boolean = false,
)

object EqPresets {
    private val refHz = listOf(60f, 230f, 910f, 3600f, 14000f)

    val all = listOf(
        EqPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
        EqPreset("Bass boost", listOf(6f, 4f, 1f, 0f, 0f)),
        EqPreset("Bass and treble", listOf(5f, 2f, -1f, 2f, 5f)),
        EqPreset("Treble boost", listOf(0f, 0f, 0f, 3f, 6f)),
        EqPreset("Vocal", listOf(-3f, -1f, 3f, 4f, 1f)),
        EqPreset("Rock", listOf(5f, 3f, -2f, 3f, 5f)),
        EqPreset("Pop", listOf(-1f, 2f, 4f, 2f, -1f)),
        EqPreset("Hip-hop", listOf(6f, 4f, 0f, 1f, 3f)),
        EqPreset("Electronic", listOf(5f, 3f, 0f, 2f, 5f)),
        EqPreset("Jazz", listOf(3f, 2f, -1f, 2f, 4f)),
        EqPreset("Classical", listOf(4f, 3f, -1f, 3f, 4f)),
        EqPreset("Acoustic", listOf(3f, 3f, 1f, 2f, 3f)),
        EqPreset("Lounge", listOf(-2f, 0f, 2f, 1f, -1f)),
    )

    /** Level in millibels for a band centred at [hz], interpolating the preset curve on a log-frequency axis. */
    fun levelFor(preset: EqPreset, hz: Float, minMb: Int, maxMb: Int): Int {
        val g = preset.gains
        val idx = refHz.indexOfFirst { it >= hz }
        val db = when {
            idx == 0 -> g[0]
            idx == -1 -> g[g.size - 1]
            else -> {
                val x0 = ln(refHz[idx - 1])
                val x1 = ln(refHz[idx])
                val t = (ln(hz) - x0) / (x1 - x0)
                g[idx - 1] + (g[idx] - g[idx - 1]) * t
            }
        }
        return (db * 100f).roundToInt().coerceIn(minMb, maxMb)
    }
}

/**
 * Audio effects for the player. The playback service and the UI live in one process, so this
 * singleton is shared: the service attaches it to the player's audio session, the UI changes it.
 * Everything runs on the main thread.
 */
object AudioFx {
    private var prefs: SharedPreferences? = null
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null

    private val _settings = MutableStateFlow(EqSettings())
    val settings: StateFlow<EqSettings> = _settings.asStateFlow()

    private val _info = MutableStateFlow(EqInfo())
    val info: StateFlow<EqInfo> = _info.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences("wavelength_eq", Context.MODE_PRIVATE)
        prefs = sp
        _settings.value = EqSettings(
            enabled = sp.getBoolean("enabled", false),
            preset = sp.getInt("preset", 0),
            bands = (sp.getString("bands", "") ?: "").split(",").mapNotNull { it.trim().toIntOrNull() },
            bass = sp.getInt("bass", 0),
            virtualizer = sp.getInt("virtualizer", 0),
            loudness = sp.getInt("loudness", 0),
        )
    }

    private fun save() {
        val sp = prefs ?: return
        val s = _settings.value
        sp.edit()
            .putBoolean("enabled", s.enabled)
            .putInt("preset", s.preset)
            .putString("bands", s.bands.joinToString(","))
            .putInt("bass", s.bass)
            .putInt("virtualizer", s.virtualizer)
            .putInt("loudness", s.loudness)
            .apply()
    }

    /** Called by the playback service when the player has an audio session. */
    fun attach(sessionId: Int) {
        release()
        if (sessionId == 0) return

        try {
            eq = Equalizer(0, sessionId)
        } catch (e: Exception) {
            eq = null
        }
        try {
            bass = BassBoost(0, sessionId)
        } catch (e: Exception) {
            bass = null
        }
        try {
            virt = Virtualizer(0, sessionId)
        } catch (e: Exception) {
            virt = null
        }
        try {
            loud = LoudnessEnhancer(sessionId)
        } catch (e: Exception) {
            loud = null
        }

        val e = eq
        if (e == null) {
            _info.value = EqInfo(
                available = false,
                bassOk = bass != null,
                virtualizerOk = virt != null,
                loudnessOk = loud != null,
            )
        } else {
            val count = e.numberOfBands.toInt()
            val range = e.bandLevelRange
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()
            val freqs = (0 until count).map { e.getCenterFreq(it.toShort()) / 1000 }
            _info.value = EqInfo(
                available = true,
                bandCount = count,
                minLevel = minLevel,
                maxLevel = maxLevel,
                freqsHz = freqs,
                bassOk = bass != null,
                virtualizerOk = virt != null,
                loudnessOk = loud != null,
            )
            if (_settings.value.bands.size != count) {
                val s = _settings.value
                val preset = EqPresets.all.getOrNull(s.preset)
                val levels = freqs.map { hz ->
                    if (preset != null) {
                        EqPresets.levelFor(preset, hz.toFloat(), minLevel, maxLevel)
                    } else {
                        0
                    }
                }
                _settings.value = s.copy(bands = levels)
                save()
            }
        }
        apply()
    }

    fun release() {
        try { eq?.release() } catch (e: Exception) { }
        try { bass?.release() } catch (e: Exception) { }
        try { virt?.release() } catch (e: Exception) { }
        try { loud?.release() } catch (e: Exception) { }
        eq = null
        bass = null
        virt = null
        loud = null
    }

    private fun apply() {
        val s = _settings.value
        val e = eq
        if (e != null) {
            try {
                e.setEnabled(s.enabled)
                if (s.enabled) {
                    val count = e.numberOfBands.toInt()
                    for (i in 0 until minOf(count, s.bands.size)) {
                        e.setBandLevel(i.toShort(), s.bands[i].toShort())
                    }
                }
            } catch (ex: Exception) {
                // Some devices reject changes while no audio is routed; the next attach reapplies.
            }
        }
        val b = bass
        if (b != null) {
            try {
                b.setEnabled(s.enabled && s.bass > 0)
                if (b.strengthSupported) b.setStrength(s.bass.toShort())
            } catch (ex: Exception) { }
        }
        val v = virt
        if (v != null) {
            try {
                v.setEnabled(s.enabled && s.virtualizer > 0)
                if (v.strengthSupported) v.setStrength(s.virtualizer.toShort())
            } catch (ex: Exception) { }
        }
        val l = loud
        if (l != null) {
            try {
                l.setEnabled(s.enabled && s.loudness > 0)
                l.setTargetGain(s.loudness)
            } catch (ex: Exception) { }
        }
    }

    private fun change(block: (EqSettings) -> EqSettings) {
        _settings.value = block(_settings.value)
        save()
        apply()
    }

    fun setEnabled(on: Boolean) = change { it.copy(enabled = on) }

    fun setBand(index: Int, millibels: Int) = change { s ->
        val info = _info.value
        val list = s.bands.toMutableList()
        if (index in list.indices) list[index] = millibels.coerceIn(info.minLevel, info.maxLevel)
        s.copy(bands = list, preset = -1)
    }

    fun setPreset(index: Int) = change { s ->
        val preset = EqPresets.all.getOrNull(index) ?: return@change s
        val info = _info.value
        val levels = if (info.freqsHz.isEmpty()) {
            s.bands
        } else {
            info.freqsHz.map { hz ->
                EqPresets.levelFor(preset, hz.toFloat(), info.minLevel, info.maxLevel)
            }
        }
        s.copy(preset = index, bands = levels)
    }

    fun setBass(value: Int) = change { it.copy(bass = value.coerceIn(0, 1000)) }
    fun setVirtualizer(value: Int) = change { it.copy(virtualizer = value.coerceIn(0, 1000)) }
    fun setLoudness(value: Int) = change { it.copy(loudness = value.coerceIn(0, 1500)) }

    fun resetAll() = change { s ->
        s.copy(preset = 0, bands = s.bands.map { 0 }, bass = 0, virtualizer = 0, loudness = 0)
    }
}
