package com.fiangg20.wavelength.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.media3.common.Player
import com.fiangg20.wavelength.MainActivity
import com.fiangg20.wavelength.PlaybackService
import com.fiangg20.wavelength.R

/** The home screen widget: artwork, title, and previous / play / next. */
class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Draw whatever was playing last, the service sends a fresh picture as soon as it runs.
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        render(
            context,
            sp.getString("title", "") ?: "",
            sp.getString("artist", "") ?: "",
            playing = false,
            art = null,
        )
    }

    companion object {
        private const val PREFS = "wavelength_widget"
        const val ACTION_TOGGLE = "com.fiangg20.wavelength.widget.TOGGLE"
        const val ACTION_NEXT = "com.fiangg20.wavelength.widget.NEXT"
        const val ACTION_PREV = "com.fiangg20.wavelength.widget.PREV"

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, WidgetActionReceiver::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** Called by the playback service whenever the song or play state changes. */
        fun update(context: Context, title: String, artist: String, playing: Boolean, art: Bitmap?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", title)
                .putString("artist", artist)
                .apply()
            render(context, title, artist, playing, art)
        }

        private fun render(context: Context, title: String, artist: String, playing: Boolean, art: Bitmap?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
            if (ids.isEmpty()) return

            val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
            views.setTextViewText(R.id.widget_title, title.ifEmpty { "Wavelength" })
            views.setTextViewText(R.id.widget_artist, if (title.isEmpty()) "Tap to open" else artist)
            views.setImageViewResource(
                R.id.widget_play,
                if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            )
            if (art != null) {
                views.setImageViewBitmap(R.id.widget_art, art)
            } else {
                views.setImageViewResource(R.id.widget_art, android.R.color.transparent)
            }
            views.setOnClickPendingIntent(R.id.widget_play, broadcast(context, ACTION_TOGGLE, 1))
            views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 2))
            views.setOnClickPendingIntent(R.id.widget_prev, broadcast(context, ACTION_PREV, 3))
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    4,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            manager.updateAppWidget(ids, views)
        }
    }
}

/** Handles the three widget buttons by talking to the running player. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val player: Player? = PlaybackService.sharedPlayer
        if (player == null) {
            // Nothing is loaded yet, so open the app instead.
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        when (intent.action) {
            NowPlayingWidget.ACTION_TOGGLE -> if (player.isPlaying) player.pause() else player.play()
            NowPlayingWidget.ACTION_NEXT -> player.seekToNext()
            NowPlayingWidget.ACTION_PREV -> player.seekToPrevious()
        }
    }
}
