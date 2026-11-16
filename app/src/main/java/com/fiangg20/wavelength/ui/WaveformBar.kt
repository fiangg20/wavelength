package com.fiangg20.wavelength.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * A seek bar drawn as the song's loudness. [bars] holds 0..1 heights; while they are still being worked out
 * (null) a calm row of short bars is shown, and the bar can already be used.
 */
@Composable
fun WaveformSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    bars: FloatArray?,
    activeColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue else progress.coerceIn(0f, 1f)
    val currentOnSeek by rememberUpdatedState(onSeek)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    currentOnSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragValue = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        currentOnSeek(dragValue)
                    },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragValue = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                )
            },
    ) {
        val n = bars?.size ?: 96
        val step = size.width / n
        val barW = step * 0.6f
        val minH = 4.dp.toPx()
        val radius = CornerRadius(barW / 2f, barW / 2f)
        for (i in 0 until n) {
            val amp = if (bars != null) bars[i].coerceIn(0f, 1f) else 0.16f
            val h = (size.height * amp).coerceAtLeast(minH)
            val x = i * step + (step - barW) / 2f
            val played = (i + 0.5f) / n <= shown
            drawRoundRect(
                color = if (played) activeColor else trackColor,
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = radius,
            )
        }
    }
}
