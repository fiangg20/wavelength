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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
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
import kotlin.math.max

@Composable
fun NowPlayingScreen(vm: PlayerViewModel, onClose: () -> Unit) {
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
        PlayerContent(vm = vm, ui = ui, position = position, onClose = onClose)
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
) {
    val prefs = LocalPrefs.current
    val scheme = MaterialTheme.colorScheme
    val track = ui.current
    val sleepMinutes by vm.sleepMinutes.collectAsStateWithLifecycle()

    var showQueue by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
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
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val artSize = maxOf(150.dp, minOf(maxHeight - 440.dp, maxWidth - 56.dp))

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
                    Text(
                        text = "Now playing",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    ExpressiveButton(
                        onClick = { showQueue = true },
                        modifier = Modifier.size(52.dp),
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                        restCorner = 26.dp,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null)
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
                AnimatedContent(
                    targetState = track,
                    modifier = Modifier.fillMaxWidth(),
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

                // Seek bar
                val progress = if (ui.durationMs > 0L) position.toFloat() / ui.durationMs else 0f
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

                    Box {
                        ExpressiveButton(
                            onClick = { speedMenu = true },
                            modifier = Modifier
                                .height(52.dp)
                                .widthIn(min = 64.dp),
                            containerColor = if (ui.speed != 1f) scheme.primaryContainer else scheme.surfaceContainerHigh,
                            contentColor = if (ui.speed != 1f) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            restCorner = 26.dp,
                        ) {
                            Text(
                                text = speedLabel(ui.speed),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                        }
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(speedLabel(s)) },
                                    onClick = {
                                        vm.setSpeed(s)
                                        speedMenu = false
                                    },
                                )
                            }
                        }
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
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showQueue) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = sheetState,
            containerColor = scheme.surfaceContainerLow,
        ) {
            Text(
                text = "Up next",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = max(0, ui.index - 2))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            ) {
                itemsIndexed(ui.queue) { i, t ->
                    TrackRow(
                        track = t,
                        active = i == ui.index,
                        playing = ui.isPlaying,
                        onClick = { vm.skipTo(i) },
                    )
                }
            }
        }
    }
}
