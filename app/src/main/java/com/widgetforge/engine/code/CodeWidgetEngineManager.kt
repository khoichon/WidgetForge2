package com.widgetforge.engine.code

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.RemoteViews
import com.widgetforge.R
import com.widgetforge.data.models.WidgetChannelMessage
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * CodeWidgetEngineManager — global registry of active CodeWidgetEngine instances.
 *
 * Responsibilities:
 *  - Lifecycle management: start/stop/pause/resume per appWidgetId
 *  - Frame delivery: convert engine Bitmap frames → RemoteViews ImageView updates
 *  - Channel routing: fan-out incoming channel messages to all subscribed engines
 *  - Dimension sync: push new px sizes when launcher resizes a widget
 */
object CodeWidgetEngineManager {

    private val engines = ConcurrentHashMap<Int, CodeWidgetEngine>()
    private const val TAG = "CodeWidgetEngineManager"

    // ── Engine Lifecycle ────────────────────────────────────────────────────

    fun startEngine(
        context: Context,
        appWidgetId: Int,
        bundleDir: File,
        manifest: com.widgetforge.data.models.CodeWidgetManifest
    ) {
        stopEngine(appWidgetId)
        Log.d(TAG, "Starting engine for widget $appWidgetId (${manifest.name})")
        val engine = CodeWidgetEngine(
            context = context.applicationContext,
            appWidgetId = appWidgetId,
            bundleDir = bundleDir,
            manifest = manifest,
            onFrame = { bmp -> deliverFrame(context, appWidgetId, bmp) }
        )
        engines[appWidgetId] = engine
        engine.start()
    }

    fun stopEngine(appWidgetId: Int) {
        engines.remove(appWidgetId)?.stop()
    }

    fun pauseAll() {
        engines.values.forEach { it.pause() }
        Log.d(TAG, "Paused ${engines.size} engine(s)")
    }

    fun resumeAll() {
        engines.values.forEach { it.resume() }
        Log.d(TAG, "Resumed ${engines.size} engine(s)")
    }

    fun triggerUpdate(context: Context, widgetId: Int) {
        engines[widgetId]?.resume()
    }

    // ── Channel Bus ─────────────────────────────────────────────────────────

    fun deliverChannelMessage(message: WidgetChannelMessage) {
        var delivered = 0
        engines.forEach { (id, engine) ->
            if (id != message.sourceWidgetId) {
                engine.deliverChannelMessage(message)
                delivered++
            }
        }
        Log.d(TAG, "Channel '${message.channel}' delivered to $delivered widget(s)")
    }

    // ── Dimension Updates ───────────────────────────────────────────────────

    fun updateDimensions(appWidgetId: Int, widthPx: Int, heightPx: Int) {
        engines[appWidgetId]?.updateDimensions(widthPx, heightPx)
    }

    // ── Frame Delivery ──────────────────────────────────────────────────────

    private fun deliverFrame(context: Context, appWidgetId: Int, bitmap: Bitmap) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val views = RemoteViews(context.packageName, R.layout.widget_code).apply {
                setImageViewBitmap(R.id.widget_image_view, bitmap)
            }
            manager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "Frame delivery failed for widget $appWidgetId: ${e.message}")
        }
    }

    // ── Query ───────────────────────────────────────────────────────────────

    fun isRunning(appWidgetId: Int): Boolean = engines.containsKey(appWidgetId)
    fun activeCount(): Int = engines.size
}
