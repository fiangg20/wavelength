package com.fiangg20.wavelength.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fiangg20.wavelength.audio.AudioFx
import com.fiangg20.wavelength.audio.EqPresets
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(onBack: () -> Unit, bottomPad: Dp) {
    val scheme = MaterialTheme.colorScheme
    val s by AudioFx.settings.collectAsState()
    val info by AudioFx.info.collectAsState()
    val live = s.enabled

    Column(Modifier.fillMaxSize()) {
        PageHeader("Equalizer", onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Group {
                SwitchRow(
                    title = "Equalizer",
                    subtitle = if (info.available) "Shape the sound of everything you play" else null,
                    checked = s.enabled,
                    onChecked = { AudioFx.setEnabled(it) },
                )
                if (!info.available) {
                    Hint("The equalizer attaches when the player starts. Play a song and come back. If it stays unavailable, this device does not expose a system equalizer.")
                }
            }

            if (info.available) {
                SectionTitle("Presets")
                Group {
                    SinglePills(
                        options = EqPresets.all.map { it.name },
                        selected = s.preset,
                        onSelect = { AudioFx.setPreset(it) },
                    )
                    if (s.preset == -1) Hint("Custom curve")
                }

                SectionTitle("Bands")
                Group {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (live) 1f else 0.5f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        for (i in 0 until info.bandCount) {
                            val level = s.bands.getOrElse(i) { 0 }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = formatDb(level),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.primary,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(8.dp))
                                BandSlider(
                                    level = level,
                                    min = info.minLevel,
                                    max = info.maxLevel,
                                    onLevel = { AudioFx.setBand(i, it) },
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = formatHz(info.freqsHz.getOrElse(i) { 0 }),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    if (!live) Hint("Turn the equalizer on to hear changes")
                }
            }

            if (info.bassOk || info.virtualizerOk || info.loudnessOk) {
                SectionTitle("Effects")
                Group {
                    if (info.bassOk) {
                        SliderRow(
                            title = "Bass boost",
                            valueText = "${s.bass / 10}%",
                            value = s.bass.toFloat(),
                            range = 0f..1000f,
                            onChange = { AudioFx.setBass(it.toInt()) },
                        )
                    }
                    if (info.virtualizerOk) {
                        SliderRow(
                            title = "Surround widening",
                            valueText = "${s.virtualizer / 10}%",
                            value = s.virtualizer.toFloat(),
                            range = 0f..1000f,
                            onChange = { AudioFx.setVirtualizer(it.toInt()) },
                        )
                    }
                    if (info.loudnessOk) {
                        SliderRow(
                            title = "Loudness boost",
                            valueText = String.format(Locale.US, "+%.1f dB", s.loudness / 100f),
                            value = s.loudness.toFloat(),
                            range = 0f..1500f,
                            onChange = { AudioFx.setLoudness(it.toInt()) },
                        )
                    }
                    Hint("Effects follow the equalizer switch above.")
                }
            }

            Gap()
            ExpressiveButton(
                onClick = { AudioFx.resetAll() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                containerColor = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
                restCorner = 28.dp,
            ) {
                Text("Reset everything", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(bottomPad))
        }
    }
}

private fun formatDb(millibels: Int): String =
    String.format(Locale.US, "%+.1f", millibels / 100f)

private fun formatHz(hz: Int): String =
    if (hz >= 1000) {
        val k = hz / 1000f
        if (k >= 10f) String.format(Locale.US, "%.0fk", k) else String.format(Locale.US, "%.1fk", k)
    } else {
        "$hz"
    }

/** A vertical slider: the fill grows from the centre line, the handle is a small pill. */
@Composable
private fun BandSlider(
    level: Int,
    min: Int,
    max: Int,
    onLevel: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val track = scheme.surfaceContainerHighest
    val fill = scheme.primary
    val tick = scheme.outline
    val handle = scheme.primary
    val currentOnLevel by rememberUpdatedState(onLevel)
    val lo by rememberUpdatedState(min)
    val hi by rememberUpdatedState(max)

    Canvas(
        modifier = Modifier
            .width(44.dp)
            .height(220.dp)
            .pointerInput(Unit) {
                fun levelAt(y: Float): Int = levelFromY(y, size.height.toFloat(), 14.dp.toPx(), lo, hi)
                detectTapGestures { offset -> currentOnLevel(levelAt(offset.y)) }
            }
            .pointerInput(Unit) {
                fun levelAt(y: Float): Int = levelFromY(y, size.height.toFloat(), 14.dp.toPx(), lo, hi)
                detectVerticalDragGestures(
                    onDragStart = { offset -> currentOnLevel(levelAt(offset.y)) },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        currentOnLevel(levelAt(change.position.y))
                    },
                )
            }
    ) {
        val pad = 14.dp.toPx()
        val trackW = 14.dp.toPx()
        val cx = size.width / 2f
        val span = size.height - 2f * pad
        val frac = if (hi > lo) (level - lo).toFloat() / (hi - lo).toFloat() else 0.5f
        val y = pad + (1f - frac.coerceIn(0f, 1f)) * span
        val zeroFrac = if (hi > lo) (0 - lo).toFloat() / (hi - lo).toFloat() else 0.5f
        val zeroY = pad + (1f - zeroFrac.coerceIn(0f, 1f)) * span

        // Track
        drawRoundRect(
            color = track,
            topLeft = Offset(cx - trackW / 2f, pad - 6.dp.toPx()),
            size = Size(trackW, span + 12.dp.toPx()),
            cornerRadius = CornerRadius(trackW / 2f),
        )
        // Fill between the zero line and the handle
        val top = minOf(y, zeroY)
        val bottom = maxOf(y, zeroY)
        drawRoundRect(
            color = fill,
            topLeft = Offset(cx - trackW / 2f, top),
            size = Size(trackW, (bottom - top).coerceAtLeast(1f)),
            cornerRadius = CornerRadius(trackW / 2f),
        )
        // Zero tick
        drawRoundRect(
            color = tick,
            topLeft = Offset(cx - 11.dp.toPx(), zeroY - 1.dp.toPx()),
            size = Size(22.dp.toPx(), 2.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
        // Handle
        val hw = 40.dp.toPx()
        val hh = 12.dp.toPx()
        drawRoundRect(
            color = handle,
            topLeft = Offset(cx - hw / 2f, y - hh / 2f),
            size = Size(hw, hh),
            cornerRadius = CornerRadius(hh / 2f),
        )
    }
}

/** Maps a touch position to a band level in millibels, snapped to 0.5 dB. */
private fun levelFromY(y: Float, height: Float, pad: Float, lo: Int, hi: Int): Int {
    val span = (height - 2f * pad).coerceAtLeast(1f)
    val frac = 1f - ((y - pad) / span).coerceIn(0f, 1f)
    val raw = lo + frac * (hi - lo)
    return ((raw / 50f).roundToInt() * 50).coerceIn(lo, hi)
}
