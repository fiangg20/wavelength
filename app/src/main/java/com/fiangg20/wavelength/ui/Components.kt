package com.fiangg20.wavelength.ui

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.fiangg20.wavelength.data.ArtLoader
import com.fiangg20.wavelength.data.Prefs
import com.fiangg20.wavelength.data.Track
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Shapes
// ---------------------------------------------------------------------------------------------

/** A scalloped "cookie" outline. depth 0 is a circle; around 0.1 is the classic expressive cookie. */
data class CookieShape(val lobes: Int, val depth: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy)
        val path = Path()
        val steps = 240
        for (i in 0..steps) {
            val t = i * 2f * PI.toFloat() / steps
            val rr = r * (1f - depth / 2f + (depth / 2f) * cos(lobes * t))
            val x = cx + rr * cos(t)
            val y = cy + rr * sin(t)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** A superellipse: squarer than a circle, rounder than a rounded rectangle. */
object SquircleShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val e = 2f / 4.5f
        val path = Path()
        val steps = 200
        for (i in 0..steps) {
            val t = i * 2f * PI.toFloat() / steps
            val c = cos(t)
            val s = sin(t)
            val x = cx + cx * sign(c) * abs(c).pow(e)
            val y = cy + cy * sign(s) * abs(s).pow(e)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

fun artShapeFor(prefs: Prefs, depth: Float): Shape = when (prefs.artShape) {
    1 -> CircleShape
    2 -> SquircleShape
    3 -> RoundedCornerShape(percent = 14)
    else -> CookieShape(prefs.lobes, depth)
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

/**
 * A button whose corners spring tighter while pressed. restCorner can change at runtime
 * (the play button uses that to morph between a stadium and a rounded square).
 */
@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    restCorner: Dp = 28.dp,
    pressedCorner: Dp = 10.dp,
    shapeFor: (Dp) -> Shape = { RoundedCornerShape(it) },
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) pressedCorner else restCorner,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "corner",
    )
    val haptic = LocalHapticFeedback.current
    val haptics = LocalPrefs.current.haptics
    Box(
        modifier = modifier
            .clip(shapeFor(corner))
            .background(containerColor)
            .clickable(interactionSource = interaction, indication = LocalIndication.current) {
                if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

/** A connected row of choices. The selected one becomes a full pill, the rest stay tight. */
@Composable
fun ConnectedChoice(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 46.dp,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            val corner by animateDpAsState(
                targetValue = if (isSelected) 50.dp else 10.dp,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 450f),
                label = "choiceCorner",
            )
            val bg by animateColorAsState(
                targetValue = if (isSelected) scheme.primary else scheme.surfaceContainerHighest,
                label = "choiceBg",
            )
            val fg by animateColorAsState(
                targetValue = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                label = "choiceFg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(corner))
                    .background(bg)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = fg,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Wavy seek bar
// ---------------------------------------------------------------------------------------------

private val TWO_PI: Float = (2.0 * PI).toFloat()

@Composable
fun WavySeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    isPlaying: Boolean,
    wavy: Boolean,
    intensity: Float,
    activeColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue else progress.coerceIn(0f, 1f)

    val ampFactor by animateFloatAsState(
        targetValue = if (wavy && isPlaying) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "amp",
    )
    val grow by animateFloatAsState(
        targetValue = if (dragging) 1.35f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "grow",
    )

    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, wavy) {
        if (wavy && isPlaying) {
            var last = withFrameNanosCompat()
            while (true) {
                val now = withFrameNanosCompat()
                phase = (phase + (now - last) / 1_000_000_000f * 5.5f) % TWO_PI
                last = now
            }
        }
    }

    val currentOnSeek by rememberUpdatedState(onSeek)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    currentOnSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
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
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        dragValue = (dragValue + delta / size.width).coerceIn(0f, 1f)
                    },
                )
            },
    ) {
        val w = size.width
        val cy = size.height / 2f
        val strokeW = (if (wavy) 4.dp else 7.dp).toPx()
        val gap = 6.dp.toPx()
        val x = w * shown

        // Remaining track
        val inactiveStart = (x + gap).coerceAtMost(w)
        if (inactiveStart < w) {
            drawLine(
                color = trackColor,
                start = Offset(inactiveStart, cy),
                end = Offset(w, cy),
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )
        }

        // Played part: a travelling sine wave, or a flat bar
        val activeEnd = (x - gap).coerceAtLeast(0f)
        val ampPx = 4.5.dp.toPx() * intensity * ampFactor
        if (activeEnd > 0f) {
            if (ampPx < 0.15f) {
                drawLine(
                    color = activeColor,
                    start = Offset(0f, cy),
                    end = Offset(activeEnd, cy),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round,
                )
            } else {
                val wavelength = 30.dp.toPx()
                val taper = 24.dp.toPx()
                val step = 2.5.dp.toPx()
                val path = Path()
                var xx = 0f
                var first = true
                while (xx < activeEnd) {
                    val k = (xx / taper).coerceAtMost(1f)
                    val y = cy + ampPx * k * sin(xx / wavelength * TWO_PI - phase)
                    if (first) {
                        path.moveTo(xx, y)
                        first = false
                    } else {
                        path.lineTo(xx, y)
                    }
                    xx += step
                }
                val ke = (activeEnd / taper).coerceAtMost(1f)
                path.lineTo(activeEnd, cy + ampPx * ke * sin(activeEnd / wavelength * TWO_PI - phase))
                drawPath(
                    path = path,
                    color = activeColor,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }

        // Handle
        val hw = 5.dp.toPx()
        val hh = 24.dp.toPx() * grow
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(x - hw / 2f, cy - hh / 2f),
            size = Size(hw, hh),
            cornerRadius = CornerRadius(hw / 2f, hw / 2f),
        )
    }
}

private suspend fun withFrameNanosCompat(): Long =
    androidx.compose.runtime.withFrameNanos { it }

// ---------------------------------------------------------------------------------------------
// Artwork
// ---------------------------------------------------------------------------------------------

@Composable
fun rememberArtBitmap(track: Track?, px: Int): State<Bitmap?> {
    val context = LocalContext.current.applicationContext
    return produceState<Bitmap?>(
        if (track != null) ArtLoader.peek(track, px) else null,
        track?.id,
        px,
    ) {
        value = if (track == null) null else ArtLoader.load(context, track, px)
    }
}

@Composable
fun Artwork(
    track: Track?,
    modifier: Modifier,
    shape: Shape,
    px: Int = 256,
) {
    val bmp by rememberArtBitmap(track, px)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = bmp,
            modifier = Modifier.fillMaxSize(),
            animationSpec = tween(220),
            label = "art",
        ) { b ->
            if (b != null) {
                Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxSize(0.42f),
                    )
                }
            }
        }
    }
}

/** Cover for an album made of several songs: tries songs in turn until one of them has artwork. */
@Composable
fun AlbumArtwork(
    tracks: List<Track>,
    modifier: Modifier,
    shape: Shape,
    px: Int = 256,
) {
    val context = LocalContext.current.applicationContext
    val first = tracks.firstOrNull()
    val bmp by produceState<Bitmap?>(
        if (first != null) ArtLoader.peek(first, px) else null,
        first?.id,
        tracks.size,
        px,
    ) {
        var found: Bitmap? = null
        for (t in tracks.take(8)) {
            found = ArtLoader.load(context, t, px)
            if (found != null) break
        }
        value = found
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = bmp,
            modifier = Modifier.fillMaxSize(),
            animationSpec = tween(220),
            label = "albumArt",
        ) { b ->
            if (b != null) {
                Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxSize(0.42f),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Small pieces
// ---------------------------------------------------------------------------------------------

@Composable
fun EqBars(color: Color, animate: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq")
    val a by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
        label = "a",
    )
    val b by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
        label = "b",
    )
    val c by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "c",
    )
    val heights = if (animate) listOf(a, b, c) else listOf(0.3f, 0.3f, 0.3f)
    Row(
        modifier = modifier.size(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEach { h ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}
