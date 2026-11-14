package com.fiangg20.wavelength.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.fiangg20.wavelength.data.DlStore
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDL.UpdateChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class DlStatus { QUEUED, RUNNING, DONE, FAILED, CANCELED }

data class DownloadItem(
    val id: String,
    val input: String,
    val trim: String = "",
    val title: String = "",
    val status: DlStatus = DlStatus.QUEUED,
    val progress: Float = 0f,
    val eta: Long = -1L,
    val line: String = "",
    val log: List<String> = emptyList(),
    val message: String = "",
    val saved: Int = 0,
)

data class EngineState(
    val ready: Boolean = false,
    val busy: Boolean = false,
    val version: String = "",
    val message: String = "",
)

/**
 * Runs yt-dlp on the phone. Downloads go to a private temp folder, are converted by ffmpeg,
 * then copied into Music/<folder> through MediaStore so they show up in the library.
 */
object DownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val _engine = MutableStateFlow(EngineState())
    val engine: StateFlow<EngineState> = _engine.asStateFlow()

    /** Emits once for every item that finished with saved files. */
    val finished = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    private val initMutex = Mutex()
    private var initialized = false
    private var autoUpdated = false

    private val queue = Channel<String>(Channel.UNLIMITED)
    private var workersStarted = false
    private val running = AtomicInteger(0)
    private val counter = AtomicInteger(0)
    private val canceled: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val audioExtensions = setOf(
        "mp3", "m4a", "opus", "ogg", "oga", "flac", "wav", "aac", "wma", "webm", "mka", "m4b",
    )
    private val mimeTypes = mapOf(
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "m4b" to "audio/mp4",
        "opus" to "audio/ogg",
        "ogg" to "audio/ogg",
        "oga" to "audio/ogg",
        "flac" to "audio/flac",
        "wav" to "audio/x-wav",
        "aac" to "audio/aac",
        "wma" to "audio/x-ms-wma",
        "webm" to "audio/webm",
        "mka" to "audio/x-matroska",
    )
    private val destinationRegex = Regex("""Destination:\s*(.+)""")

    private fun channelFor(index: Int): UpdateChannel = when (index) {
        1 -> UpdateChannel.NIGHTLY
        2 -> UpdateChannel.MASTER
        else -> UpdateChannel.STABLE
    }

    // ---- engine --------------------------------------------------------------------------

    /** Unpacks yt-dlp and ffmpeg on first use (takes a little while) and optionally updates yt-dlp. */
    suspend fun prepare(context: Context): Boolean = withContext(Dispatchers.IO) {
        initMutex.withLock {
            val app = context.applicationContext
            if (!initialized) {
                _engine.update { it.copy(busy = true, message = "Setting up yt-dlp. The first run takes a moment.") }
                try {
                    YoutubeDL.getInstance().init(app)
                    FFmpeg.getInstance().init(app)
                    initialized = true
                } catch (e: Throwable) {
                    _engine.update {
                        it.copy(busy = false, ready = false, message = "Could not start yt-dlp: ${e.message}")
                    }
                    return@withLock false
                }
            }
            val cfg = DlStore.get(app).state.value
            if (cfg.autoUpdate && !autoUpdated) {
                autoUpdated = true
                _engine.update { it.copy(busy = true, message = "Checking for a newer yt-dlp") }
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(app, channelFor(cfg.updateChannel))
                } catch (e: Throwable) {
                    // Offline or rate limited: the bundled version still works.
                }
            }
            val version = try {
                YoutubeDL.getInstance().version(app)
            } catch (e: Throwable) {
                null
            }
            _engine.value = EngineState(ready = true, busy = false, version = version ?: "", message = "")
            true
        }
    }

    /** Updates yt-dlp now and returns a short message for the user. */
    suspend fun updateEngine(context: Context): String {
        val app = context.applicationContext
        if (!prepare(app)) return _engine.value.message
        return withContext(Dispatchers.IO) {
            initMutex.withLock {
                _engine.update { it.copy(busy = true, message = "Updating yt-dlp") }
                val channel = channelFor(DlStore.get(app).state.value.updateChannel)
                val result = try {
                    val status = YoutubeDL.getInstance().updateYoutubeDL(app, channel)
                    when (status?.name) {
                        "DONE" -> "yt-dlp updated"
                        "ALREADY_UP_TO_DATE" -> "yt-dlp is already up to date"
                        else -> "Update finished"
                    }
                } catch (e: Throwable) {
                    "Update failed: ${e.message}"
                }
                val version = try {
                    YoutubeDL.getInstance().version(app)
                } catch (e: Throwable) {
                    null
                }
                _engine.value = EngineState(ready = true, busy = false, version = version ?: "", message = result)
                result
            }
        }
    }

    // ---- queue ---------------------------------------------------------------------------

    private fun patch(id: String, change: (DownloadItem) -> DownloadItem) {
        _items.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    private fun ensureWorkers(app: Context) {
        if (workersStarted) return
        workersStarted = true
        repeat(3) {
            scope.launch {
                for (id in queue) {
                    runItem(app, id)
                }
            }
        }
    }

    private fun startService(app: Context) {
        try {
            ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        } catch (e: Exception) {
            // Downloads still run; there is just no notification keeping the process alive.
        }
    }

    fun enqueue(context: Context, inputs: List<String>, trim: String = "") {
        val app = context.applicationContext
        val cleaned = inputs.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return
        val fresh = cleaned.map {
            DownloadItem(
                id = "dl-${System.currentTimeMillis()}-${counter.incrementAndGet()}",
                input = it,
                trim = trim,
            )
        }
        _items.update { it + fresh }
        ensureWorkers(app)
        startService(app)
        for (item in fresh) queue.trySend(item.id)
    }

    fun retry(context: Context, id: String) {
        val app = context.applicationContext
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.status != DlStatus.FAILED && item.status != DlStatus.CANCELED) return
        canceled.remove(id)
        patch(id) {
            it.copy(
                status = DlStatus.QUEUED, progress = 0f, eta = -1L, line = "", log = emptyList(),
                message = "", saved = 0,
            )
        }
        ensureWorkers(app)
        startService(app)
        queue.trySend(id)
    }

    fun cancel(id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        when (item.status) {
            DlStatus.QUEUED -> patch(id) { it.copy(status = DlStatus.CANCELED, message = "Canceled") }
            DlStatus.RUNNING -> {
                canceled.add(id)
                scope.launch {
                    try {
                        YoutubeDL.getInstance().destroyProcessById(id)
                    } catch (e: Throwable) {
                        // Already finished.
                    }
                }
            }
            else -> Unit
        }
    }

    fun cancelAll() {
        for (item in _items.value) cancel(item.id)
    }

    fun remove(id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.status == DlStatus.RUNNING || item.status == DlStatus.QUEUED) return
        _items.update { list -> list.filter { it.id != id } }
    }

    fun clearFinished() {
        _items.update { list ->
            list.filter { it.status == DlStatus.QUEUED || it.status == DlStatus.RUNNING }
        }
    }

    fun hasActive(): Boolean =
        _items.value.any { it.status == DlStatus.QUEUED || it.status == DlStatus.RUNNING }

    // ---- one download --------------------------------------------------------------------

    private fun onWifi(app: Context): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun fail(id: String, message: String) {
        patch(id) { it.copy(status = DlStatus.FAILED, message = message) }
    }

    private suspend fun runItem(app: Context, id: String) {
        val first = _items.value.firstOrNull { it.id == id } ?: return
        if (first.status != DlStatus.QUEUED) return

        // Wait for a free slot, honouring the "parallel downloads" setting.
        while (true) {
            val current = _items.value.firstOrNull { it.id == id }
            if (current == null || current.status != DlStatus.QUEUED) return
            val limit = DlStore.get(app).state.value.parallel.coerceIn(1, 3)
            val now = running.get()
            if (now < limit && running.compareAndSet(now, now + 1)) break
            delay(300)
        }

        val outDir = File(app.cacheDir, "ytdl/$id")
        try {
            val cfg = DlStore.get(app).state.value
            if (cfg.wifiOnly && !onWifi(app)) {
                fail(id, "Wi-Fi only is on. Connect to Wi-Fi and retry.")
                return
            }
            patch(id) { it.copy(status = DlStatus.RUNNING, message = "Starting") }
            if (!prepare(app)) {
                fail(id, _engine.value.message.ifEmpty { "yt-dlp is not ready" })
                return
            }

            outDir.deleteRecursively()
            outDir.mkdirs()
            val request = CommandBuilder.build(app, first.input, first.trim, outDir, cfg)

            var failure: Throwable? = null
            try {
                YoutubeDL.getInstance().execute(request, id) { progress, eta, line ->
                    val text = line.trim()
                    patch(id) { cur ->
                        var title = cur.title
                        val match = destinationRegex.find(text)
                        if (match != null) {
                            title = File(match.groupValues[1].trim()).nameWithoutExtension
                        }
                        cur.copy(
                            title = title,
                            progress = if (progress >= 0f) progress else cur.progress,
                            eta = eta,
                            line = if (text.isNotEmpty()) text.take(160) else cur.line,
                            log = if (text.isNotEmpty()) (cur.log + text).takeLast(80) else cur.log,
                            message = "",
                        )
                    }
                }
            } catch (e: Throwable) {
                failure = e
            }

            if (canceled.remove(id)) {
                patch(id) { it.copy(status = DlStatus.CANCELED, message = "Canceled") }
                return
            }

            // A playlist can partly succeed: keep whatever was produced.
            val files = outDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in audioExtensions }
                .toList()
            var saved = 0
            for (file in files) {
                if (saveToMusic(app, file, cfg.saveFolder)) saved++
            }

            if (saved > 0) {
                val note = if (failure != null) "Saved $saved, some items failed" else "Saved $saved"
                patch(id) { it.copy(status = DlStatus.DONE, progress = 100f, saved = saved, message = note) }
                if (cfg.autoRefresh) finished.tryEmit(Unit)
            } else {
                val reason = failure?.message?.lines()?.lastOrNull { it.isNotBlank() }
                    ?: "yt-dlp finished without producing an audio file"
                fail(id, reason.take(300))
            }
        } catch (e: Throwable) {
            fail(id, (e.message ?: "Unexpected error").take(300))
        } finally {
            running.decrementAndGet()
            outDir.deleteRecursively()
        }
    }

    /** Copies a finished file into Music/<folder>. MediaStore reads its tags and cover art. */
    private fun saveToMusic(app: Context, file: File, folder: String): Boolean {
        val mime = mimeTypes[file.extension.lowercase()] ?: "audio/mpeg"
        val sub = folder.trim().trim('/').ifEmpty { "Wavelength" }
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = app.contentResolver
                val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/" + sub)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection, values)
                if (uri == null) {
                    false
                } else {
                    resolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { input -> input.copyTo(out) }
                    }
                    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                    true
                }
            } else {
                val base = app.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                if (base == null) {
                    false
                } else {
                    val dir = File(base, sub)
                    dir.mkdirs()
                    val target = File(dir, file.name)
                    file.copyTo(target, overwrite = true)
                    MediaScannerConnection.scanFile(app, arrayOf(target.absolutePath), null, null)
                    true
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
