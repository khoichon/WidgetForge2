package com.widgetforge.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.widgetforge.R
import com.widgetforge.data.models.GifWidgetConfig
import com.widgetforge.data.models.WidgetType
import com.widgetforge.data.repository.WidgetRegistry
import com.widgetforge.engine.gif.GifWidgetEngineManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * GifWidgetRenderService — foreground service managing all GIF animation loops.
 * Uses android.graphics.Movie for frame-accurate GIF decoding.
 */
@AndroidEntryPoint
class GifWidgetRenderService : Service() {

    @Inject lateinit var registry: WidgetRegistry

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val NOTIF_CHANNEL_ID = "gif_rendering"
    private val NOTIF_ID = 1002

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { startGifEngines() }
        return START_STICKY
    }

    override fun onDestroy() {
        GifWidgetEngineManager.stopAll()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun startGifEngines() {
        val gifWidgets = registry.getEntriesByType(WidgetType.GIF)
        gifWidgets.forEach { entry ->
            val config = GifWidgetConfig(gifPath = entry.sourceFilePath)
            val w = entry.pixelWidth.takeIf { it > 0 } ?: 300
            val h = entry.pixelHeight.takeIf { it > 0 } ?: 300
            GifWidgetEngineManager.start(
                this@GifWidgetRenderService, entry.appWidgetId, config, w, h
            )
            Log.d(TAG, "GIF engine started for widget ${entry.appWidgetId}")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            "GIF Animation",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Animates GIF widgets on your homescreen"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("WidgetForge")
            .setContentText("Animating GIF widgets")
            .setSmallIcon(R.drawable.ic_widget_notification)
            .setOngoing(true)
            .setSilent(true)
            .build()

    companion object {
        private const val TAG = "GifWidgetRenderService"

        fun start(context: android.content.Context) {
            context.startForegroundService(Intent(context, GifWidgetRenderService::class.java))
        }
    }
}
