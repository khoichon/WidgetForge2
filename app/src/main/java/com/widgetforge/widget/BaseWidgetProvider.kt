package com.widgetforge.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.room.Room
import com.widgetforge.data.db.WidgetForgeDatabase
import com.widgetforge.data.models.GifWidgetConfig
import com.widgetforge.data.models.ImageWidgetConfig
import com.widgetforge.data.models.TextAlignment
import com.widgetforge.data.models.TextWidgetConfig
import com.widgetforge.data.models.WidgetType
import com.widgetforge.data.repository.WidgetRegistry
import com.widgetforge.engine.code.BundleExtractor
import com.widgetforge.engine.code.CodeWidgetEngineManager
import com.widgetforge.engine.gif.GifWidgetEngineManager
import com.widgetforge.engine.image.ImageWidgetRenderer
import com.widgetforge.engine.text.TextWidgetRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * BaseWidgetProvider — shared onUpdate / onAppWidgetOptionsChanged / onDeleted logic.
 *
 * AppWidgetProviders are not Hilt-injectable (they must have a no-arg constructor for
 * the system to instantiate them). We therefore access the database through a
 * process-level singleton holder [DbHolder] rather than Hilt injection.
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    private val job   = SupervisorJob()
    protected val scope = CoroutineScope(Dispatchers.IO + job)

    // ── Update ──────────────────────────────────────────────────────────────

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val registry = registryFor(context)
        scope.launch {
            appWidgetIds.forEach { id ->
                val entry = registry.getEntry(id)
                if (entry == null) {
                    Log.w(TAG, "No registry entry for widget $id — skipping")
                    return@forEach
                }
                Log.d(TAG, "onUpdate: id=$id type=${entry.widgetType}")

                val w = entry.pixelWidth.takeIf  { it > 0 } ?: registry.dpToPx(entry.cellWidth  * CELL_DP)
                val h = entry.pixelHeight.takeIf { it > 0 } ?: registry.dpToPx(entry.cellHeight * CELL_DP)

                when (entry.widgetType) {
                    WidgetType.TEXT  -> renderText (context, appWidgetManager, id, entry.sourceFilePath, w, h)
                    WidgetType.IMAGE -> renderImage(context, appWidgetManager, id, entry.sourceFilePath, w, h)
                    WidgetType.GIF   -> renderGif  (context, id, entry.sourceFilePath, w, h)
                    WidgetType.CODE  -> renderCode (context, id, entry.sourceFilePath)
                }
            }
        }
    }

    // ── Resize ──────────────────────────────────────────────────────────────

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val minWidth  = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val registry  = registryFor(context)

        scope.launch {
            val widthPx  = registry.dpToPx(minWidth)
            val heightPx = registry.dpToPx(minHeight)
            registry.updateDimensions(appWidgetId, widthPx, heightPx)
            CodeWidgetEngineManager.updateDimensions(appWidgetId, widthPx, heightPx)
            GifWidgetEngineManager.updateDimensions(appWidgetId, widthPx, heightPx)
            Log.d(TAG, "Widget $appWidgetId resized → ${widthPx}×${heightPx}px")
            onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
        }
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val registry = registryFor(context)
        scope.launch {
            appWidgetIds.forEach { id ->
                registry.unregister(id)
                CodeWidgetEngineManager.stopEngine(id)
                GifWidgetEngineManager.stop(id)
                Log.d(TAG, "Widget $id deleted and unregistered")
            }
        }
    }

    // ── Rendering helpers ────────────────────────────────────────────────────

    private fun renderText(
        context: Context, manager: AppWidgetManager,
        id: Int, path: String, w: Int, h: Int
    ) {
        val config = parseTextConfig(path)
        val views  = TextWidgetRenderer.buildRemoteViews(context, config, w, h)
        manager.updateAppWidget(id, views)
    }

    private fun renderImage(
        context: Context, manager: AppWidgetManager,
        id: Int, path: String, w: Int, h: Int
    ) {
        val config = ImageWidgetConfig(imagePath = path)
        val views  = ImageWidgetRenderer.buildRemoteViews(context, config, w, h)
        manager.updateAppWidget(id, views)
    }

    private fun renderGif(context: Context, id: Int, path: String, w: Int, h: Int) {
        val config = GifWidgetConfig(gifPath = path)
        GifWidgetEngineManager.start(context, id, config, w, h)
    }

    private fun renderCode(context: Context, id: Int, zipPath: String) {
        if (CodeWidgetEngineManager.isRunning(id)) return
        val zipFile = File(zipPath)
        if (!zipFile.exists()) {
            Log.w(TAG, "ZIP bundle not found for widget $id: $zipPath")
            return
        }
        val (bundleDir, manifest) = BundleExtractor.extract(context, zipFile, id) ?: return
        CodeWidgetEngineManager.startEngine(context, id, bundleDir, manifest)
    }

    // ── Text config parser (.txt export format) ───────────────────────────────

    private fun parseTextConfig(path: String): TextWidgetConfig {
        val file = File(path)
        if (!file.exists()) return TextWidgetConfig("Widget")

        var fontSize        = 14f
        var textColor       = "#FFFFFF"
        var backgroundColor = "#CC000000"
        var bold            = false
        var italic          = false
        var alignment       = TextAlignment.CENTER
        var inHeader        = false
        val bodyLines       = mutableListOf<String>()

        file.readLines().forEach { line ->
            when {
                line == "---WIDGETFORGE_METADATA---" -> inHeader = true
                line == "---END_METADATA---"         -> inHeader = false
                inHeader -> {
                    val kv = line.split("=", limit = 2)
                    if (kv.size == 2) when (kv[0].trim()) {
                        "fontSize"        -> fontSize        = kv[1].trim().toFloatOrNull() ?: 14f
                        "textColor"       -> textColor       = kv[1].trim()
                        "backgroundColor" -> backgroundColor = kv[1].trim()
                        "bold"            -> bold            = kv[1].trim().toBoolean()
                        "italic"          -> italic          = kv[1].trim().toBoolean()
                        "alignment"       -> alignment       = runCatching {
                            TextAlignment.valueOf(kv[1].trim().uppercase())
                        }.getOrDefault(TextAlignment.CENTER)
                    }
                }
                else -> bodyLines.add(line)
            }
        }

        return TextWidgetConfig(
            text            = bodyLines.joinToString("\n").trim(),
            fontSize        = fontSize,
            textColor       = textColor,
            backgroundColor = backgroundColor,
            alignment       = alignment,
            bold            = bold,
            italic          = italic
        )
    }

    // ── Singleton DB / registry accessor ─────────────────────────────────────

    private fun registryFor(context: Context): WidgetRegistry =
        DbHolder.registryFor(context)

    companion object {
        private const val TAG     = "BaseWidgetProvider"
        private const val CELL_DP = 74 // approximate dp per standard launcher cell
    }
}

// ─── Process-level singleton for DB access from non-Hilt contexts ─────────────

private object DbHolder {
    @Volatile private var db:       WidgetForgeDatabase? = null
    @Volatile private var registry: WidgetRegistry?      = null

    fun registryFor(context: Context): WidgetRegistry {
        return registry ?: synchronized(this) {
            registry ?: run {
                val appCtx = context.applicationContext
                val newDb  = db ?: Room.databaseBuilder(
                    appCtx,
                    WidgetForgeDatabase::class.java,
                    WidgetForgeDatabase.DATABASE_NAME
                ).build().also { db = it }
                WidgetRegistry(appCtx, newDb).also { registry = it }
            }
        }
    }
}