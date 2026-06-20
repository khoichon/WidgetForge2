package com.widgetforge.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.widgetforge.R
import com.widgetforge.data.models.WidgetType
import com.widgetforge.data.repository.WidgetRegistry
import com.widgetforge.engine.code.BundleExtractor
import com.widgetforge.engine.code.CodeWidgetEngineManager
import com.widgetforge.receiver.ScreenStateReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject

/**
 * CodeWidgetRenderService — foreground service that bootstraps and manages
 * all active CodeWidgetEngine (WebView/JS) instances.
 *
 * Started by:
 *  - BaseWidgetProvider whenever a CODE widget is placed, resized, or updated
 *  - BootReceiver after device reboot / app update
 *
 * Why a foreground service is mandatory (not optional):
 *  AppWidgetProvider.onUpdate() runs in a short-lived broadcast-receiver
 *  process with no persistence guarantee — Android can and will kill that
 *  process moments after onReceive() returns. If the WebView render loop
 *  were started directly from there, the animation would play for a few
 *  frames and then silently die, with nothing left alive to ever restart
 *  it. The service's ongoing notification keeps the process alive for as
 *  long as any code widget needs to animate, and is also the only valid
 *  place to dynamically register ScreenStateReceiver (SCREEN_ON/OFF/
 *  USER_PRESENT cannot be received by a manifest-declared receiver on
 *  API 26+).
 *
 * This service intentionally does NOT stop itself when the registry is
 * momentarily empty — doing so would unregister ScreenStateReceiver and
 * require another trigger (placement/reboot) to bring animations back.
 * It only stops via explicit System.exit-style teardown (never, in
 * practice) or when the OS reclaims it, in which case BootReceiver /
 * the next onUpdate() call restarts it.
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
        // Dynamic registration — required since this implicit broadcast
        // cannot be declared in the manifest on API 26+.
        ScreenStateReceiver.register(this)
        Log.d(TAG, "CodeWidgetRenderService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { syncEngines() }
        // START_STICKY: if the OS kills this process under memory pressure,
        // it will be recreated with a null intent, and onStartCommand will
        // re-run syncEngines() to resume any code widgets that should be
        // playing.
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        ScreenStateReceiver.unregister(this)
        CodeWidgetEngineManager.pauseAll()
        scope.cancel()
        Log.d(TAG, "CodeWidgetRenderService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Engine sync (idempotent — skips widgets already running) ─────────────

    private suspend fun syncEngines() {
        val codeWidgets = registry.getEntriesByType(WidgetType.CODE)
        Log.d(TAG, "Syncing ${codeWidgets.size} code widget(s)")

        codeWidgets.forEach { entry ->
            if (CodeWidgetEngineManager.isRunning(entry.appWidgetId)) return@forEach

            val zipFile = File(entry.sourceFilePath)
            if (!zipFile.exists()) {
                Log.w(TAG, "Bundle zip not found for widget ${entry.appWidgetId}")
                return@forEach
            }

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

        updateNotification(CodeWidgetEngineManager.activeCount())
        // Note: deliberately NOT calling stopSelf() here even when
        // codeWidgets is empty — see class doc for why.
    }

    // ── Notification ────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID, "Widget Rendering", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps code-powered widgets running"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(activeCount: Int): Notification {
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)
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
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(count))
    }

    companion object {
        private const val TAG = "CodeWidgetRenderService"

        fun start(context: android.content.Context) {
            context.startForegroundService(Intent(context, CodeWidgetRenderService::class.java))
        }
    }
}
