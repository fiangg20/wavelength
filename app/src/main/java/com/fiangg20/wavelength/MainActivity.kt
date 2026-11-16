package com.fiangg20.wavelength

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.fiangg20.wavelength.audio.AudioFx
import com.fiangg20.wavelength.data.Track
import com.fiangg20.wavelength.ui.AddToPlaylistSheet
import com.fiangg20.wavelength.ui.CookieShape
import com.fiangg20.wavelength.ui.DownloadSettingsScreen
import com.fiangg20.wavelength.ui.FloatingNav
import com.fiangg20.wavelength.ui.HomeScreen
import com.fiangg20.wavelength.ui.DownloadsScreen
import com.fiangg20.wavelength.ui.EqualizerScreen
import com.fiangg20.wavelength.ui.DetailScreen
import com.fiangg20.wavelength.ui.ExpressiveButton
import com.fiangg20.wavelength.ui.LibraryScreen
import com.fiangg20.wavelength.ui.MiniPlayer
import com.fiangg20.wavelength.ui.NowPlayingScreen
import com.fiangg20.wavelength.ui.PlayerViewModel
import com.fiangg20.wavelength.ui.PlaylistScreen
import com.fiangg20.wavelength.ui.SettingsCategoryScreen
import com.fiangg20.wavelength.ui.SettingsScreen
import com.fiangg20.wavelength.ui.WavelengthTheme

class MainActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()

    /** Text shared into the app from another app (a video link, usually). */
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AudioFx.init(this)
        if (savedInstanceState == null) readShare(intent)
        setContent {
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            // Wait until the accent stops changing before touching the launcher icon.
            LaunchedEffect(prefs.hue, prefs.dynamicColor, prefs.adaptiveIcon) {
                delay(1500L)
                IconTheme.apply(applicationContext, prefs.hue, prefs.dynamicColor, prefs.adaptiveIcon)
            }
            WavelengthTheme(prefs) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root(vm, sharedText) { sharedText = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readShare(intent)
    }

    private fun readShare(i: Intent?) {
        if (i == null || i.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT)
        if (text.isNullOrBlank()) return
        val links = Regex("""https?://\S+""").findAll(text).map { it.value }.toList()
        sharedText = if (links.isEmpty()) text.trim() else links.joinToString("\n")
    }
}

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
private fun Root(vm: PlayerViewModel, shared: String?, onSharedHandled: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result[audioPermission()] == true
        if (granted) vm.loadLibrary()
    }

    LaunchedEffect(Unit) {
        if (granted) vm.loadLibrary()
    }

    if (!granted) {
        PermissionScreen {
            val wanted = mutableListOf(audioPermission())
            if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
            launcher.launch(wanted.toTypedArray())
        }
    } else {
        AppShell(vm, shared, onSharedHandled)
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(140.dp)
                .clip(CookieShape(9, 0.1f))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Let Wavelength read the music on your phone so it can build your library. Nothing leaves the device.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        ExpressiveButton(
            onClick = onGrant,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
            restCorner = 32.dp,
        ) {
            Text("Allow access", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private const val SEP = "\u001F"

@Composable
private fun AppShell(vm: PlayerViewModel, shared: String?, onSharedHandled: () -> Unit) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Which top-level page is showing when the stack is empty: 0 Home, 1 Library.
    var root by rememberSaveable { mutableIntStateOf(0) }
    // Navigation stack. "" is Home or Library (see root); others: settings, settings/<page>, eq, downloads, dlsettings, album:<id>, artist:<name>, playlist:<id>
    var stackRaw by rememberSaveable { mutableStateOf("") }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var dlPrefill by rememberSaveable { mutableStateOf("") }
    // Song waiting to be put in a playlist (opens the picker sheet).
    var addTarget by remember { mutableStateOf<Track?>(null) }
    val addToPlaylist: (Track) -> Unit = { addTarget = it }

    val stack = if (stackRaw.isEmpty()) emptyList() else stackRaw.split(SEP)
    val screen = stack.lastOrNull() ?: ""
    fun push(next: String) {
        stackRaw = if (stackRaw.isEmpty()) next else stackRaw + SEP + next
    }
    fun pop() {
        stackRaw = stack.dropLast(1).joinToString(SEP)
    }

    LaunchedEffect(shared) {
        if (!shared.isNullOrBlank()) {
            dlPrefill = shared
            playerOpen = false
            if (screen != "downloads") push("downloads")
            onSharedHandled()
        }
    }

    BackHandler(enabled = playerOpen) { playerOpen = false }
    BackHandler(enabled = !playerOpen && screen.isNotEmpty()) { pop() }
    BackHandler(enabled = !playerOpen && screen.isEmpty() && root == 1) { root = 0 }

    val miniVisible = ui.hasMedia && ui.current != null
    val navVisible = screen.isEmpty() && !playerOpen
    val bottomPad = (if (miniVisible) 120.dp else 32.dp) + (if (navVisible) 76.dp else 0.dp)
    val backdrop = MaterialTheme.colorScheme.background

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (targetState.length >= initialState.length && targetState.isNotEmpty()) {
                    (slideInHorizontally(spring(stiffness = 400f)) { it / 6 } + fadeIn(tween(220))) togetherWith
                        fadeOut(tween(150))
                } else {
                    fadeIn(tween(220)) togetherWith
                        (slideOutHorizontally(spring(stiffness = 400f)) { it / 6 } + fadeOut(tween(150)))
                }
            },
            label = "screen",
        ) { s ->
            when {
                s == "settings" -> SettingsScreen(
                    onBack = { pop() },
                    onOpenCategory = { push("settings/$it") },
                    onOpenEqualizer = { push("eq") },
                    onOpenDownloads = { push("downloads") },
                    onOpenDownloadSettings = { push("dlsettings") },
                    bottomPad = bottomPad,
                )
                s.startsWith("settings/") -> SettingsCategoryScreen(
                    category = s.removePrefix("settings/"),
                    prefs = prefs,
                    onChange = { vm.updatePrefs(it) },
                    onBack = { pop() },
                    bottomPad = bottomPad,
                )
                s == "eq" -> EqualizerScreen(onBack = { pop() }, bottomPad = bottomPad)
                s == "downloads" -> DownloadsScreen(
                    initialInput = dlPrefill,
                    onBack = { pop() },
                    onOpenSettings = { push("dlsettings") },
                    bottomPad = bottomPad,
                )
                s == "dlsettings" -> DownloadSettingsScreen(onBack = { pop() }, bottomPad = bottomPad)
                s.startsWith("album:") -> {
                    val id = s.removePrefix("album:").toLongOrNull()
                    val album = albums.firstOrNull { it.id == id }
                    if (album != null) {
                        DetailScreen(
                            vm = vm,
                            onAddToPlaylist = addToPlaylist,
                            title = album.title,
                            subtitle = "${album.artist} · ${album.tracks.size} songs",
                            isArtist = false,
                            tracks = album.tracks,
                            ui = ui,
                            bottomPad = bottomPad,
                            onBack = { pop() },
                            onPlay = { vm.playQueue(album.tracks, it) },
                            onPlayAll = { vm.playQueue(album.tracks, 0, shuffle = false) },
                            onShuffle = { vm.playShuffled(album.tracks) },
                        )
                    } else {
                        LaunchedEffect(s) { pop() }
                    }
                }
                s.startsWith("artist:") -> {
                    val name = s.removePrefix("artist:")
                    val artist = artists.firstOrNull { it.name == name }
                    if (artist != null) {
                        val sorted = artist.tracks.sortedWith(
                            compareBy({ it.album.lowercase() }, { it.trackNo }, { it.title.lowercase() })
                        )
                        DetailScreen(
                            vm = vm,
                            onAddToPlaylist = addToPlaylist,
                            title = artist.name,
                            subtitle = "${artist.tracks.size} songs · ${artist.albumCount} albums",
                            isArtist = true,
                            tracks = sorted,
                            ui = ui,
                            bottomPad = bottomPad,
                            onBack = { pop() },
                            onPlay = { vm.playQueue(sorted, it) },
                            onPlayAll = { vm.playQueue(sorted, 0, shuffle = false) },
                            onShuffle = { vm.playShuffled(sorted) },
                        )
                    } else {
                        LaunchedEffect(s) { pop() }
                    }
                }
                s.startsWith("playlist:") -> PlaylistScreen(
                    vm = vm,
                    id = s.removePrefix("playlist:"),
                    ui = ui,
                    bottomPad = bottomPad,
                    onBack = { pop() },
                    onAddToPlaylist = addToPlaylist,
                )
                else -> Crossfade(
                    targetState = root,
                    animationSpec = tween(220),
                    label = "root",
                ) { r ->
                    if (r == 0) {
                        HomeScreen(
                            vm = vm,
                            onOpenSettings = { push("settings") },
                            onOpenDownloads = { push("downloads") },
                            onOpenDetail = { push(it) },
                            onAddToPlaylist = addToPlaylist,
                            bottomPad = bottomPad,
                        )
                    } else {
                        LibraryScreen(
                            vm = vm,
                            tab = tab,
                            onTab = { tab = it },
                            onOpenSettings = { push("settings") },
                            onOpenDownloads = { push("downloads") },
                            onOpenDetail = { push(it) },
                            onAddToPlaylist = addToPlaylist,
                            bottomPad = bottomPad,
                        )
                    }
                }
            }
        }

        // Mini player and the Home / Library switch share one bar, with a fade behind it so rows never collide.
        AnimatedVisibility(
            visible = (miniVisible || navVisible) && !playerOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, backdrop)))
                    .padding(top = 48.dp)
                    .navigationBarsPadding()
            ) {
                AnimatedVisibility(
                    visible = miniVisible,
                    enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 350f)) { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    MiniPlayer(
                        ui = ui,
                        position = position,
                        onToggle = { vm.togglePlay() },
                        onNext = { vm.next() },
                        onOpen = { playerOpen = true },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                AnimatedVisibility(
                    visible = navVisible,
                    enter = slideInVertically(spring(stiffness = 400f)) { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    FloatingNav(
                        selected = root,
                        onSelect = { root = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // Full player
        AnimatedVisibility(
            visible = playerOpen,
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 300f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
        ) {
            NowPlayingScreen(
                vm = vm,
                onClose = { playerOpen = false },
                onOpenEqualizer = {
                    playerOpen = false
                    if (screen != "eq") push("eq")
                },
                onAddToPlaylist = addToPlaylist,
            )
        }

        val target = addTarget
        if (target != null) {
            AddToPlaylistSheet(vm = vm, track = target, onDismiss = { addTarget = null })
        }
    }
}
