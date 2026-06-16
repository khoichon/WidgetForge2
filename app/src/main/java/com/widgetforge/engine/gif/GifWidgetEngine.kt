package com.widgetforge.engine.gif

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.widget.RemoteViews
import com.widgetforge.R
import com.widgetforge.data.models.GifWidgetConfig
import com.widgetforge.data.models.ImageScaleType
import com.widgetforge.receiver.WidgetVisibilityTracker
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * GifWidgetEngine — frame-by-frame GIF decoder using android.graphics.Movie.
 *
 * For each active GIF widget, a coroutine loop extracts frames at the
 * GIF's native delay and pushes them as Bitmaps to RemoteViews.
 * Pauses immediately when WidgetVisibilityTracker.isVisible = false.
 */
class GifWidgetEngine(
    private val context: Context,
    val appWidgetId: Int,
    private val config: GifWidgetConfig,
    private var widthPx: Int,
    private var heightPx: Int
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    private var movie: Movie? = null

    @Volatile private var running = false

    fun start() {
        if (running) return
        val gifFile = File(config.gifPath)
        if (!gifFile.exists()) return
        movie = Movie.decodeFile(config.gifPath) ?: return
        running = true
        job = scope.launch { runLoop() }
    }

    fun stop() {
        running = false
        job?.cancel()
        scope.cancel()
        movie = null
    }

    fun updateDimensions(w: Int, h: Int) {
        widthPx = w; heightPx = h
    }

    private suspend fun runLoop() {
        val m = movie ?: return
        val duration = m.duration().coerceAtLeast(100) // ms
        val frameDelay = (duration / 30L).coerceAtLeast(16L) // ~30fps or GIF native

        var relTime = 0L
        var lastTick = System.currentTimeMillis()

        while (running) {
            if (!WidgetVisibilityTracker.isVisible) {
                delay(500)
                lastTick = System.currentTimeMillis()
                continue
            }

            val now = System.currentTimeMillis()
            relTime = (relTime + (now - lastTick)) % duration.toLong()
            lastTick = now

            val bmp = renderFrame(m, relTime.toInt(), widthPx, heightPx, config)
            deliverFrame(bmp)

            delay(frameDelay)
        }
    }

    private fun renderFrame(
        movie: Movie, relTime: Int,
        w: Int, h: Int, config: GifWidgetConfig
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Rounded clip
        val path = Path().apply {
            addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()),
                config.cornerRadius, config.cornerRadius, Path.Direction.CW)
        }
        canvas.clipPath(path)
        canvas.drawColor(Color.TRANSPARENT)

        // Scale GIF frame to widget size
        val mW = movie.width().toFloat()
        val mH = movie.height().toFloat()
        val scale = when (config.scaleType) {
            ImageScaleType.CENTER_CROP -> maxOf(w / mW, h / mH)
            ImageScaleType.CENTER_INSIDE -> minOf(w / mW, h / mH)
            ImageScaleType.FIT_XY -> 1f
        }
        val tx = (w - mW * scale) / 2
        val ty = (h - mH * scale) / 2
        canvas.save()
        canvas.translate(tx, ty)
        canvas.scale(scale, scale)
        movie.setTime(relTime)
        movie.draw(canvas, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
        canvas.restore()

        return bmp
    }

    private fun deliverFrame(bmp: Bitmap) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val views = RemoteViews(context.packageName, R.layout.widget_image_frame).apply {
                setImageViewBitmap(R.id.widget_image_view, bmp)
            }
            manager.updateAppWidget(appWidgetId, views)
        } catch (_: Exception) {}
    }
}

// ─── Manager ────────────────────────────────────────────────────────────────

object GifWidgetEngineManager {
    private val engines = ConcurrentHashMap<Int, GifWidgetEngine>()

    fun start(
        context: Context, appWidgetId: Int,
        config: GifWidgetConfig, widthPx: Int, heightPx: Int
    ) {
        stop(appWidgetId)
        val engine = GifWidgetEngine(context.applicationContext, appWidgetId, config, widthPx, heightPx)
        engines[appWidgetId] = engine
        engine.start()
    }

    fun stop(appWidgetId: Int) { engines.remove(appWidgetId)?.stop() }
    fun stopAll() { engines.keys.toList().forEach { stop(it) } }
    fun pauseAll() { WidgetVisibilityTracker.onScreenOff() }
    fun resumeAll() { WidgetVisibilityTracker.onUserPresent() }
    fun updateDimensions(id: Int, w: Int, h: Int) { engines[id]?.updateDimensions(w, h) }
}
