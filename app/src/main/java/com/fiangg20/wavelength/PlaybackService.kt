package com.fiangg20.wavelength

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.fiangg20.wavelength.audio.AudioFx
import com.fiangg20.wavelength.audio.BalanceProcessor
import com.fiangg20.wavelength.audio.ChannelBalance
import com.fiangg20.wavelength.data.ArtLoader
import com.fiangg20.wavelength.data.Prefs
import com.fiangg20.wavelength.data.SettingsStore
import com.fiangg20.wavelength.data.Track
import com.fiangg20.wavelength.widget.NowPlayingWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ReplayGain values found in the file that is playing, in dB. Null when the file has none.
    private var rgGainDb: Float? = null
    private var rgPeak: Float? = null
    private var currentPrefs: Prefs = Prefs()
    private var widgetJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        AudioFx.init(this)

        // A custom audio sink so the channel balance and ReplayGain can be applied to the decoded audio.
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf<AudioProcessor>(BalanceProcessor()))
                .build()
        }

        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        sharedPlayer = player

        // The equalizer follows the player's audio session.
        AudioFx.attach(player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AudioFx.attach(audioSessionId)
            }

            override fun onTracksChanged(tracks: Tracks) {
                readReplayGain(tracks)
                applyReplayGain()
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                    )
                ) {
                    pushWidget(player)
                }
            }
        })

        // Playback settings that live on the player itself.
        scope.launch {
            SettingsStore.get(this@PlaybackService).state
                .map { Pair(it.skipSilence, it.pauseOnUnplug) }
                .distinctUntilChanged()
                .collect { pair ->
                    player.setSkipSilenceEnabled(pair.first)
                    player.setHandleAudioBecomingNoisy(pair.second)
                }
        }

        scope.launch {
            SettingsStore.get(this@PlaybackService).state
                .map { listOf(it.balance, it.leftLevel, it.rightLevel, if (it.monoAudio) 1f else 0f) }
                .distinctUntilChanged()
                .collect { v -> ChannelBalance.set(v[0], v[1], v[2], v[3] > 0.5f) }
        }

        // ReplayGain follows its settings.
        scope.launch {
            SettingsStore.get(this@PlaybackService).state
                .collect { p ->
                    val changed = p.replayGain != currentPrefs.replayGain || p.rgPreamp != currentPrefs.rgPreamp
                    currentPrefs = p
                    if (changed) applyReplayGain()
                }
        }

        // Fade the end of a song out and the start of the next one in. This is a dip, not an overlap.
        scope.launch {
            var lastVolume = 1f
            while (isActive) {
                delay(90L)
                val fade = currentPrefs.fadeSec
                var target = 1f
                if (fade > 0.05f && player.isPlaying) {
                    val duration = player.duration
                    if (duration != C.TIME_UNSET && duration > 0L) {
                        // Short songs get a shorter fade so the middle is never silent.
                        val fadeMs = min(fade * 1000f, duration / 3f)
                        val pos = player.currentPosition.toFloat()
                        val left = (duration - player.currentPosition).toFloat()
                        val factor = min(1f, min(pos / fadeMs, left / fadeMs))
                        target = factor.coerceIn(0f, 1f).pow(1.5f)
                    }
                }
                if (target != lastVolume) {
                    player.volume = target
                    lastVolume = target
                }
            }
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()
    }

    // ---- ReplayGain -------------------------------------------------------------------------

    private fun parseDb(text: String?): Float? {
        if (text == null) return null
        val m = Regex("""[-+]?\d+(\.\d+)?""").find(text.replace(',', '.')) ?: return null
        return m.value.toFloatOrNull()
    }

    private fun readReplayGain(tracks: Tracks) {
        var trackGain: Float? = null
        var albumGain: Float? = null
        var peak: Float? = null
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.length) {
                val metadata = group.getTrackFormat(i).metadata ?: continue
                for (j in 0 until metadata.length()) {
                    var key: String? = null
                    var value: String? = null
                    when (val entry = metadata.get(j)) {
                        is TextInformationFrame -> if (entry.id == "TXXX") {
                            key = entry.description
                            value = entry.values.firstOrNull()
                        }
                        is VorbisComment -> {
                            key = entry.key
                            value = entry.value
                        }
                        else -> {}
                    }
                    when (key?.uppercase()) {
                        "REPLAYGAIN_TRACK_GAIN" -> trackGain = parseDb(value)
                        "REPLAYGAIN_ALBUM_GAIN" -> albumGain = parseDb(value)
                        "REPLAYGAIN_TRACK_PEAK" -> peak = value?.replace(',', '.')?.trim()?.toFloatOrNull()
                    }
                }
            }
        }
        rgGainDb = trackGain ?: albumGain
        rgPeak = peak
    }

    private fun applyReplayGain() {
        val p = currentPrefs
        val gain = rgGainDb
        if (!p.replayGain || gain == null) {
            ChannelBalance.trackGain = 1f
            return
        }
        var linear = 10f.pow((gain + p.rgPreamp) / 20f)
        val peak = rgPeak
        // Never boost past full scale: with a known peak the limit is exact, without one only cuts are applied.
        linear = if (peak != null && peak > 0f) min(linear, 1f / peak) else min(linear, 1f)
        ChannelBalance.trackGain = max(0.05f, linear)
    }

    // ---- Widget ---------------------------------------------------------------------------

    private fun pushWidget(player: Player) {
        widgetJob?.cancel()
        val item: MediaItem? = player.currentMediaItem
        val title = item?.mediaMetadata?.title?.toString() ?: ""
        val artist = item?.mediaMetadata?.artist?.toString() ?: ""
        val playing = player.isPlaying
        // Show the text straight away, then the artwork once it has loaded.
        NowPlayingWidget.update(this, title, artist, playing, null)
        if (item == null) return
        val id = item.mediaId.toLongOrNull() ?: return
        val uri: Uri = item.localConfiguration?.uri ?: return
        val albumId = item.mediaMetadata.artworkUri?.lastPathSegment?.toLongOrNull() ?: 0L
        widgetJob = scope.launch {
            val track = Track(
                id = id,
                title = title,
                artist = artist,
                album = item.mediaMetadata.albumTitle?.toString() ?: "",
                albumId = albumId,
                durationMs = 0L,
                dateAdded = 0L,
                trackNo = 0,
                uri = uri,
            )
            val bmp = ArtLoader.load(this@PlaybackService, track, 256)
            if (bmp != null) NowPlayingWidget.update(this@PlaybackService, title, artist, player.isPlaying, bmp)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        scope.cancel()
        AudioFx.release()
        ChannelBalance.trackGain = 1f
        sharedPlayer = null
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        /** The live player, so the home screen widget can control it. Only touched on the main thread. */
        @Volatile
        var sharedPlayer: Player? = null
    }
}
