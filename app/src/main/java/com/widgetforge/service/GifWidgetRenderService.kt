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
import com.widgetforge.receiver.ScreenStateReceiver
import com.widgetforge.util.ImageMetadata
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject

/**
 * GifWidgetRenderService — foreground service managing all GIF animation loops.
 *
 * See CodeWidgetRenderService's class doc for the full rationale on why a
 * foreground service (rather than starting the loop directly from
 * AppWidgetProvider.onUpdate()) is required, and why this service does not
 * self-stop when the registry is momentarily empty.
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
        ScreenStateReceiver.register(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { syncEngines() }
        return START_STICKY
    }

    override fun onDestroy() {
        ScreenStateReceiver.unregister(this)
        GifWidgetEngineManager.pauseAll()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun syncEngines() {
        val gifWidgets = registry.getEntriesByType(WidgetType.GIF)
        Log.d(TAG, "Syncing ${gifWidgets.size} GIF widget(s)")

        gifWidgets.forEach { entry ->
            if (GifWidgetEngineManager.isRunning(entry.appWidgetId)) return@forEach

            val file = File(entry.sourceFilePath)
            if (!file.exists()) {
                Log.w(TAG, "GIF file not found for widget ${entry.appWidgetId}")
                return@forEach
            }

            val meta = ImageMetadata.readGifMeta(file)
            val config = GifWidgetConfig(
                gifPath       = entry.sourceFilePath,
                cornerRadius  = meta?.cornerRadius ?: 12f,
                onClickAction = meta?.onClickAction ?: entry.onClickAction,
                label         = meta?.label ?: entry.label,
                cellWidth     = meta?.cellWidth ?: entry.cellWidth,
                cellHeight    = meta?.cellHeight ?: entry.cellHeight
            )
            val w = entry.pixelWidth.takeIf { it > 0 } ?: 300
            val h = entry.pixelHeight.takeIf { it > 0 } ?: 300

            withContext(Dispatchers.Main) {
                GifWidgetEngineManager.start(this@GifWidgetRenderService, entry.appWidgetId, config, w, h)
            }
        }

        updateNotification(GifWidgetEngineManager.activeCount())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID, "GIF Animation", NotificationManager.IMPORTANCE_LOW
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

    private fun updateNotification(count: Int) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIF_ID,
            NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setContentTitle("WidgetForge")
                .setContentText("$count GIF widget${if (count != 1) "s" else ""} animating")
                .setSmallIcon(R.drawable.ic_widget_notification)
                .setOngoing(true)
                .setSilent(true)
                .build()
        )
    }

    companion object {
        private const val TAG = "GifWidgetRenderService"

        fun start(context: android.content.Context) {
            context.startForegroundService(Intent(context, GifWidgetRenderService::class.java))
        }
    }
}
