package com.widgetforge.engine.code

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.RemoteViews
import com.widgetforge.R
import com.widgetforge.data.models.WidgetChannelMessage
import com.widgetforge.widget.CodeClickGridBuilder
import com.widgetforge.widget.RemoteViewsCompat
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
 *  - Click bookkeeping: on API < 31 (no partiallyUpdateAppWidget), whichever
 *    click handling a widget uses — the tap grid OR a single whole-widget
 *    PendingIntent — must be re-attached on every single frame, since each
 *    frame is delivered via a full updateAppWidget() call that would
 *    otherwise silently replace the whole view tree and drop it.
 */
object CodeWidgetEngineManager {

    private val engines = ConcurrentHashMap<Int, CodeWidgetEngine>()

    /** Per-widget click config, set by BaseWidgetProvider.renderCode(). */
    private data class ClickConfig(
        val gridEnabled: Boolean,
        val gridResolution: Int,
        val wholeWidgetClickIntent: PendingIntent?
    )
    private val clickConfigs = ConcurrentHashMap<Int, ClickConfig>()

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
        // Seed click config from the manifest immediately so the very first
        // frame — which may arrive before BaseWidgetProvider's next
        // onUpdate() explicitly calls setClickGridConfig — already knows
        // whether to reattach click handling on pre-API-31 devices.
        clickConfigs[appWidgetId] = ClickConfig(
            gridEnabled = manifest.captureClickPosition,
            gridResolution = manifest.clickGridResolution,
            wholeWidgetClickIntent = clickConfigs[appWidgetId]?.wholeWidgetClickIntent
        )
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
        clickConfigs.remove(appWidgetId)
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

    /**
     * Called by BaseWidgetProvider whenever a CODE widget is (re)rendered
     * in tap-grid mode, so frame delivery knows whether/how to maintain it
     * on pre-API-31 devices. Clears any whole-widget click intent, since
     * the two click modes are mutually exclusive.
     */
    fun setClickGridConfig(appWidgetId: Int, enabled: Boolean, resolution: Int) {
        clickConfigs[appWidgetId] = ClickConfig(enabled, resolution, wholeWidgetClickIntent = null)
    }

    /**
     * Called by BaseWidgetProvider whenever a CODE widget is rendered in
     * whole-widget click mode (captureClickPosition = false but an
     * onClickAction URI is set), so frame delivery can reattach that single
     * PendingIntent on pre-API-31 devices.
     */
    fun setWholeWidgetClickIntent(appWidgetId: Int, intent: PendingIntent?) {
        clickConfigs[appWidgetId] = ClickConfig(
            gridEnabled = false, gridResolution = 0, wholeWidgetClickIntent = intent
        )
    }

    // ── Click Delivery ───────────────────────────────────────────────────────

    /**
     * Forward a tap's normalized (x, y) position — recovered from the
     * invisible grid overlay by WidgetClickReceiver — into the widget's
     * running JS engine. No-ops if the widget isn't currently running
     * (e.g. tapped right after a reboot before the service catches up).
     */
    fun deliverClick(appWidgetId: Int, normX: Float, normY: Float) {
        val engine = engines[appWidgetId]
        if (engine == null) {
            Log.w(TAG, "deliverClick: no running engine for widget $appWidgetId")
            return
        }
        engine.deliverClick(normX, normY)
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

            if (RemoteViewsCompat.supportsPartialUpdate) {
                // API 31+: partial update merges the bitmap in without
                // touching whatever click handling was already attached
                // (grid PendingIntents or the whole-widget one).
                manager.partiallyUpdateAppWidget(appWidgetId, views)
            } else {
                // Pre-API 31: every update is a full replace, so reattach
                // this widget's click handling on this exact RemoteViews
                // before sending, or it would be silently dropped.
                val config = clickConfigs[appWidgetId]
                when {
                    config?.gridEnabled == true ->
                        CodeClickGridBuilder.apply(context, views, appWidgetId, config.gridResolution)
                    config?.wholeWidgetClickIntent != null ->
                        views.setOnClickPendingIntent(R.id.widget_image_view, config.wholeWidgetClickIntent)
                }
                manager.updateAppWidget(appWidgetId, views)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame delivery failed for widget $appWidgetId: ${e.message}")
        }
    }

    // ── Query ───────────────────────────────────────────────────────────────

    fun isRunning(appWidgetId: Int): Boolean = engines.containsKey(appWidgetId)
    fun activeCount(): Int = engines.size
}
