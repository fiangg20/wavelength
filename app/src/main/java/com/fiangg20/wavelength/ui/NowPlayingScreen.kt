@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fiangg20.wavelength.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fiangg20.wavelength.audio.Waveform
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun NowPlayingScreen(
    vm: PlayerViewModel,
    onClose: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onAddToPlaylist: (com.fiangg20.wavelength.data.Track) -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()
    val prefs = LocalPrefs.current
    val track = ui.current

    // Theme the whole screen from the artwork.
    val art by rememberArtBitmap(track, 900)
    val seed by produceState<Seed?>(null, art) {
        val b = art
        if (b == null) {
            delay(400)
            value = null
        } else {
            value = withContext(Dispatchers.Default) { dominantSeed(b) }
        }
    }

    val base = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val dark = base.background.luminance() < 0.5f

    val target: ColorScheme = remember(seed, base, dark, prefs.artColors, prefs.vibrancy, prefs.pureBlack) {
        val s = seed
        if (prefs.artColors && s != null) {
            val chroma = (s.chroma * prefs.vibrancy.coerceIn(0.6f, 1.2f)).coerceIn(0.03f, 0.17f)
            var sc = Oklch.scheme(s.hue, chroma, dark)
            if (dark && prefs.pureBlack) sc = sc.asBlack()
            sc
        } else {
            base
        }
    }
    val scheme = target.animated()

    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
        PlayerContent(
            vm = vm,
            ui = ui,
            position = position,
            onClose = onClose,
            onOpenEqualizer = onOpenEqualizer,
            onAddToPlaylist = onAddToPlaylist,
        )
    }
}

@Composable
private fun anim(c: Color): Color =
    animateColorAsState(targetValue = c, animationSpec = tween(700), label = "scheme").value

@Composable
private fun ColorScheme.animated(): ColorScheme = copy(
    primary = anim(primary),
    onPrimary = anim(onPrimary),
    primaryContainer = anim(primaryContainer),
    onPrimaryContainer = anim(onPrimaryContainer),
    secondary = anim(secondary),
    onSecondary = anim(onSecondary),
    secondaryContainer = anim(secondaryContainer),
    onSecondaryContainer = anim(onSecondaryContainer),
    tertiaryContainer = anim(tertiaryContainer),
    onTertiaryContainer = anim(onTertiaryContainer),
    background = anim(background),
    onBackground = anim(onBackground),
    surface = anim(surface),
    onSurface = anim(onSurface),
    onSurfaceVariant = anim(onSurfaceVariant),
    surfaceContainerLow = anim(surfaceContainerLow),
    surfaceContainer = anim(surfaceContainer),
    surfaceContainerHigh = anim(surfaceContainerHigh),
    surfaceContainerHighest = anim(surfaceContainerHighest),
    outline = anim(outline),
    outlineVariant = anim(outlineVariant),
)

private fun speedLabel(speed: Float): String {
    val s = String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
    return "$s×"
}

@Composable
private fun PlayerContent(
    vm: PlayerViewModel,
    ui: PlaybackUi,
    position: Long,
    onClose: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onAddToPlaylist: (com.fiangg20.wavelength.data.Track) -> Unit,
) {
    val prefs = LocalPrefs.current
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val backdrop by rememberArtBitmap(ui.current, 300)
    val scheme = MaterialTheme.colorScheme
    val track = ui.current
    val sleepMinutes by vm.sleepMinutes.collectAsStateWithLifecycle()

    var showQueue by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableFloatStateOf(20f) }
    var sleepMenu by remember { mutableStateOf(false) }

    val view = LocalView.current
    DisposableEffect(prefs.keepScreenOn, view) {
        view.keepScreenOn = prefs.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Slow spin while playing
    var angle by remember { mutableFloatStateOf(0f) }
    val canSpin = prefs.spinArt && prefs.artShape <= 1
    LaunchedEffect(ui.isPlaying, canSpin) {
        if (ui.isPlaying && canSpin) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                angle = (angle + (now - last) / 1_000_000_000f * 12f) % 360f
                last = now
            }
        }
    }

    // The cookie breathes: deeper scallops while playing, nearly round when paused.
    val depth by animateFloatAsState(
        targetValue = if (ui.isPlaying) 0.11f else 0.04f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 120f),
        label = "depth",
    )
    val artShape = artShapeFor(prefs, depth)

    Surface(modifier = Modifier.fillMaxSize(), color = scheme.background) {
        Box(Modifier.fillMaxSize()) {
        val glow = backdrop
        if (prefs.blurArt && android.os.Build.VERSION.SDK_INT >= 31 && glow != null) {
            Image(
                bitmap = glow.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(70.dp)
                    .graphicsLayer { alpha = 0.38f },
            )
        }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val artSize = maxOf(150.dp, minOf(maxHeight - 440.dp, maxWidth - 56.dp)) * prefs.artScale

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExpressiveButton(
                        onClick = onClose,
                        modifier = Modifier.size(52.dp),
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                        restCorner = 26.dp,
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, null)
                    }
                    Spacer(Modifier.weight(1f))
                    if (track != null) {
                        ExpressiveButton(
                            onClick = { showLyrics = true },
                            modifier = Modifier.size(48.dp),
                            containerColor = scheme.surfaceContainerHigh,
                            contentColor = scheme.onSurface,
                            restCorner = 24.dp,
                        ) {
                            Icon(Icons.Rounded.Lyrics, contentDescription = "Lyrics")
                        }
                        Spacer(Modifier.width(6.dp))
                        ExpressiveButton(
                            onClick = { onAddToPlaylist(track) },
                            modifier = Modifier.size(48.dp),
                            containerColor = scheme.surfaceContainerHigh,
                            contentColor = scheme.onSurface,
                            restCorner = 24.dp,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Add to playlist")
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    ExpressiveButton(
                        onClick = onOpenEqualizer,
                        modifier = Modifier.size(48.dp),
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                        restCorner = 24.dp,
                    ) {
                        Icon(Icons.Rounded.Equalizer, contentDescription = "Equalizer")
                    }
                    Spacer(Modifier.width(6.dp))
                    ExpressiveButton(
                        onClick = { showQueue = true },
                        modifier = Modifier.size(48.dp),
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                        restCorner = 24.dp,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Queue")
                    }
                }

                Spacer(Modifier.weight(1f))

                Artwork(
                    track = track,
                    modifier = Modifier
                        .size(artSize)
                        .graphicsLayer { rotationZ = angle },
                    shape = artShape,
                    px = 900,
                )

                Spacer(Modifier.weight(1f))

                // Title block
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = track,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        (slideInVertically { it / 3 } + fadeIn(tween(300))) togetherWith
                            (slideOutVertically { -it / 3 } + fadeOut(tween(200)))
                    },
                    label = "title",
                ) { t ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = t?.title ?: "Nothing playing",
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = t?.artist ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (track != null) {
                    val liked = track.id in favs
                    ExpressiveButton(
                        onClick = { vm.toggleFavorite(track.id) },
                        modifier = Modifier.size(52.dp),
                        containerColor = if (liked) scheme.primaryContainer else scheme.surfaceContainerHigh,
                        contentColor = if (liked) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        restCorner = if (liked) 16.dp else 26.dp,
                    ) {
                        Icon(
                            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Like",
                        )
                    }
                }
                }

                // Seek bar
                val progress = if (ui.durationMs > 0L) position.toFloat() / ui.durationMs else 0f
                if (prefs.progressStyle == 2) {
                    val context = LocalContext.current
                    val track = ui.current
                    val bars by produceState<FloatArray?>(null, track?.id) {
                        value = null
                        if (track != null) {
                            value = Waveform.load(context, track.id, track.uri, track.durationMs)
                        }
                    }
                    WaveformSeekBar(
                        progress = progress,
                        onSeek = { vm.seekToFraction(it) },
                        bars = bars,
                        activeColor = scheme.primary,
                        trackColor = scheme.secondaryContainer,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    WavySeekBar(
                        progress = progress,
                        onSeek = { vm.seekToFraction(it) },
                        isPlaying = ui.isPlaying,
                        wavy = prefs.progressStyle == 0,
                        intensity = prefs.waveIntensity,
                        activeColor = scheme.primary,
                        trackColor = scheme.secondaryContainer,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(position),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (prefs.showRemaining) {
                            "-" + formatTime(ui.durationMs - position)
                        } else {
                            formatTime(ui.durationMs)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { vm.updatePrefs { it.copy(showRemaining = !it.showRemaining) } }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }

                // Transport: connected group of three
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(88.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExpressiveButton(
                        onClick = { vm.previous() },
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp),
                        containerColor = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer,
                        restCorner = 12.dp,
                        pressedCorner = 4.dp,
                        shapeFor = { c ->
                            RoundedCornerShape(topStart = 36.dp, topEnd = c, bottomEnd = c, bottomStart = 36.dp)
                        },
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(32.dp))
                    }
                    ExpressiveButton(
                        onClick = { vm.togglePlay() },
                        modifier = Modifier
                            .weight(1.6f)
                            .height(88.dp),
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                        restCorner = if (ui.isPlaying) 28.dp else 44.dp,
                        pressedCorner = 14.dp,
                    ) {
                        Icon(
                            if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            null,
                            Modifier.size(40.dp),
                        )
                    }
                    ExpressiveButton(
                        onClick = { vm.next() },
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp),
                        containerColor = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer,
                        restCorner = 12.dp,
                        pressedCorner = 4.dp,
                        shapeFor = { c ->
                            RoundedCornerShape(topStart = c, topEnd = 36.dp, bottomEnd = 36.dp, bottomStart = c)
                        },
                    ) {
                        Icon(Icons.Rounded.SkipNext, null, Modifier.size(32.dp))
                    }
                }

                // Secondary controls
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExpressiveButton(
                        onClick = { vm.toggleShuffle() },
                        modifier = Modifier.size(52.dp),
                        containerColor = if (ui.shuffle) scheme.primaryContainer else scheme.surfaceContainerHigh,
                        contentColor = if (ui.shuffle) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        restCorner = if (ui.shuffle) 16.dp else 26.dp,
                    ) {
                        Icon(Icons.Rounded.Shuffle, null)
                    }

                    val repeatOn = ui.repeat != Player.REPEAT_MODE_OFF
                    ExpressiveButton(
                        onClick = { vm.cycleRepeat() },
                        modifier = Modifier.size(52.dp),
                        containerColor = if (repeatOn) scheme.primaryContainer else scheme.surfaceContainerHigh,
                        contentColor = if (repeatOn) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        restCorner = if (repeatOn) 16.dp else 26.dp,
                    ) {
                        Icon(
                            if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            null,
                        )
                    }

                    val tuned = ui.speed != 1f || ui.pitch != 1f
                    ExpressiveButton(
                        onClick = { showSpeed = true },
                        modifier = Modifier
                            .height(52.dp)
                            .widthIn(min = 64.dp),
                        containerColor = if (tuned) scheme.primaryContainer else scheme.surfaceContainerHigh,
                        contentColor = if (tuned) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        restCorner = 26.dp,
                    ) {
                        Text(
                            text = speedLabel(ui.speed),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                    }

                    Box {
                        ExpressiveButton(
                            onClick = { sleepMenu = true },
                            modifier = Modifier
                                .height(52.dp)
                                .widthIn(min = 52.dp),
                            containerColor = if (sleepMinutes > 0) scheme.primaryContainer else scheme.surfaceContainerHigh,
                            contentColor = if (sleepMinutes > 0) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            restCorner = 26.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Bedtime, null)
                                if (sleepMinutes > 0) {
                                    Text(
                                        text = " ${sleepMinutes}m",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                        DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                            listOf(0, 5, 10, 15, 30, 45, 60).forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(if (m == 0) "Sleep timer off" else "$m minutes") },
                                    onClick = {
                                        vm.setSleepTimer(m)
                                        sleepMenu = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Custom…") },
                                onClick = {
                                    sleepMenu = false
                                    showSleepDialog = true
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
        }
    }

    if (showSpeed) {
        SpeedSheet(vm = vm, ui = ui, onDismiss = { showSpeed = false })
    }

    if (showLyrics && track != null) {
        LyricsSheet(vm = vm, track = track, onDismiss = { showLyrics = false })
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Sleep timer") },
            text = {
                Column {
                    Text(
                        text = durationLabel(customMinutes.roundToInt()),
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.primary,
                    )
                    Slider(
                        value = customMinutes,
                        onValueChange = { customMinutes = it.roundToInt().toFloat() },
                        valueRange = 1f..240f,
                    )
                    Text(
                        text = "Playback pauses when the time is up.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.setSleepTimer(customMinutes.roundToInt())
                        showSleepDialog = false
                    },
                ) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { showSleepDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showQueue) {
        QueueSheet(vm = vm, ui = ui, onDismiss = { showQueue = false })
    }
}

private fun durationLabel(minutes: Int): String =
    if (minutes < 60) {
        "$minutes min"
    } else {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0) "$h h" else "$h h $m min"
    }

private val speedPresets = listOf(0.8f, 1f, 1.25f, 1.35f, 1.5f)
private val speedPresetLabels = listOf("0.8×", "1×", "1.25×", "1.35× (45 rpm)", "1.5×")

@Composable
private fun SpeedSheet(vm: PlayerViewModel, ui: PlaybackUi, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val prefs = LocalPrefs.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var speed by remember { mutableFloatStateOf(ui.speed) }
    var pitch by remember { mutableFloatStateOf(ui.pitch) }
    val sync = prefs.vinylSync

    fun apply(newSpeed: Float, newPitch: Float) {
        speed = newSpeed
        pitch = newPitch
        vm.setSpeedPitch(newSpeed, newPitch)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text("Speed and pitch", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Vinyl mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Speed and pitch move together, like a record on a turntable",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = sync,
                    onCheckedChange = { v ->
                        vm.updatePrefs { it.copy(vinylSync = v) }
                        if (v) apply(speed, speed)
                    },
                )
            }

            Spacer(Modifier.height(14.dp))
            SinglePills(
                options = speedPresetLabels,
                selected = speedPresets.indexOfFirst { abs(it - speed) < 0.01f },
                onSelect = { i ->
                    val v = speedPresets[i]
                    if (sync) apply(v, v) else apply(v, pitch)
                },
            )

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Speed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(speedLabel(speed), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            }
            Slider(
                value = speed,
                onValueChange = { v -> if (sync) apply(v, v) else apply(v, pitch) },
                valueRange = 0.5f..2f,
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Pitch", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(speedLabel(pitch), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            }
            Slider(
                value = pitch,
                onValueChange = { v -> if (sync) apply(v, v) else apply(speed, v) },
                valueRange = 0.5f..2f,
            )

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Skip silence", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Jumps over quiet gaps",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = prefs.skipSilence,
                    onCheckedChange = { v -> vm.updatePrefs { it.copy(skipSilence = v) } },
                )
            }

            Spacer(Modifier.height(16.dp))
            ExpressiveButton(
                onClick = { apply(1f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                containerColor = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
                restCorner = 26.dp,
            ) {
                Text("Reset", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
