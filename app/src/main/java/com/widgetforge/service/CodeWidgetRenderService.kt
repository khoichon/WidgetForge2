package com.widgetforge.service

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.widgetforge.R
import com.widgetforge.data.db.WidgetForgeDatabase
import com.widgetforge.data.models.WidgetType
import com.widgetforge.data.repository.WidgetRegistry
import com.widgetforge.engine.code.BundleExtractor
import com.widgetforge.engine.code.CodeWidgetEngineManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject

/**
 * CodeWidgetRenderService — foreground service that bootstraps and manages
 * all active CodeWidgetEngine instances.
 *
 * Started automatically when:
 *  - The device boots (via BootReceiver)
 *  - A new CODE widget is registered
 *  - The screen turns back on (via ScreenStateReceiver)
 *
 * Uses a persistent notification (channel: widget_rendering) to satisfy
 * Android foreground service requirements for long-running WebView operations.
 */
@AndroidEntryPoint
class CodeWidgetRenderService : Service() {

    @Inject lateinit var registry: WidgetRegistry

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val NOTIF_CHANNEL_ID = "widget_rendering"
    private val NOTIF_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification(0))
        Log.d(TAG, "CodeWidgetRenderService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { initializeEngines() }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        CodeWidgetEngineManager.pauseAll()
        scope.cancel()
        Log.d(TAG, "CodeWidgetRenderService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Engine Initialization ───────────────────────────────────────────────

    private suspend fun initializeEngines() {
        val codeWidgets = registry.getEntriesByType(WidgetType.CODE)
        Log.d(TAG, "Initializing ${codeWidgets.size} code widget engine(s)")

        codeWidgets.forEach { entry ->
            if (CodeWidgetEngineManager.isRunning(entry.appWidgetId)) return@forEach

            val zipFile = File(entry.sourceFilePath)
            if (!zipFile.exists()) {
                Log.w(TAG, "Bundle zip not found for widget ${entry.appWidgetId}")
                return@forEach
            }

            // Extract bundle (idempotent if already extracted)
            val (bundleDir, manifest) = BundleExtractor.extract(
                this@CodeWidgetRenderService, zipFile, entry.appWidgetId
            ) ?: return@forEach

            withContext(Dispatchers.Main) {
                CodeWidgetEngineManager.startEngine(
                    context = this@CodeWidgetRenderService,
                    appWidgetId = entry.appWidgetId,
                    bundleDir = bundleDir,
                    manifest = manifest
                )
            }
        }

        // Update notification with active engine count
        val count = CodeWidgetEngineManager.activeCount()
        updateNotification(count)
    }

    // ── Notification ────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            "Widget Rendering",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps code-powered widgets running"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(activeCount: Int): Notification {
        val contentIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE) }

        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("WidgetForge")
            .setContentText("$activeCount code widget${if (activeCount != 1) "s" else ""} active")
            .setSmallIcon(R.drawable.ic_widget_notification)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun updateNotification(count: Int) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(count))
    }

    companion object {
        private const val TAG = "CodeWidgetRenderService"

        fun start(context: android.content.Context) {
            val intent = Intent(context, CodeWidgetRenderService::class.java)
            context.startForegroundService(intent)
        }
    }
}
