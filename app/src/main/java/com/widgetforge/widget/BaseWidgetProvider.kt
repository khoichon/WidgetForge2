package com.widgetforge.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.widgetforge.data.db.WidgetForgeDatabase
import com.widgetforge.data.models.GifWidgetConfig
import com.widgetforge.data.models.ImageWidgetConfig
import com.widgetforge.data.models.TextWidgetConfig
import com.widgetforge.data.models.TextAlignment
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
 * BaseWidgetProvider — shared logic for all four widget types.
 *
 * Routes onUpdate() to the correct rendering sub-module based on
 * the WidgetRegistry entry for each appWidgetId. Handles
 * onAppWidgetOptionsChanged for responsive resizing.
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    private val job = SupervisorJob()
    protected val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val db = WidgetForgeDatabase.getInstance(context)
        val registry = WidgetRegistry(context, db)

        scope.launch {
            appWidgetIds.forEach { id ->
                val entry = registry.getEntry(id)
                if (entry == null) {
                    Log.w(TAG, "No registry entry for widget $id — skipping")
                    return@forEach
                }
                Log.d(TAG, "onUpdate: id=$id type=${entry.widgetType}")

                val w = entry.pixelWidth.takeIf { it > 0 }
                    ?: registry.dpToPx(entry.cellWidth * CELL_DP)
                val h = entry.pixelHeight.takeIf { it > 0 }
                    ?: registry.dpToPx(entry.cellHeight * CELL_DP)

                when (entry.widgetType) {
                    WidgetType.TEXT -> renderTextWidget(context, appWidgetManager, id, entry.sourceFilePath, w, h)
                    WidgetType.IMAGE -> renderImageWidget(context, appWidgetManager, id, entry.sourceFilePath, w, h)
                    WidgetType.GIF -> renderGifWidget(context, id, entry.sourceFilePath, w, h)
                    WidgetType.CODE -> renderCodeWidget(context, id, entry.sourceFilePath, w, h)
                }
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val minWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

        val db = WidgetForgeDatabase.getInstance(context)
        val registry = WidgetRegistry(context, db)

        scope.launch {
            val widthPx = registry.dpToPx(minWidth)
            val heightPx = registry.dpToPx(minHeight)
            registry.updateDimensions(appWidgetId, widthPx, heightPx)

            // Push new size to active engines
            CodeWidgetEngineManager.updateDimensions(appWidgetId, widthPx, heightPx)
            GifWidgetEngineManager.updateDimensions(appWidgetId, widthPx, heightPx)

            Log.d(TAG, "Widget $appWidgetId resized → ${widthPx}x${heightPx}px")

            // Re-render with new dimensions
            onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val db = WidgetForgeDatabase.getInstance(context)
        val registry = WidgetRegistry(context, db)
        scope.launch {
            appWidgetIds.forEach { id ->
                registry.unregister(id)
                CodeWidgetEngineManager.stopEngine(id)
                GifWidgetEngineManager.stop(id)
                Log.d(TAG, "Widget $id deleted and unregistered")
            }
        }
    }

    // ── Rendering Dispatch ──────────────────────────────────────────────────

    private fun renderTextWidget(
        context: Context, manager: AppWidgetManager,
        id: Int, path: String, w: Int, h: Int
    ) {
        val config = parseTextConfig(path)
        val views = TextWidgetRenderer.buildRemoteViews(context, config, w, h)
        manager.updateAppWidget(id, views)
    }

    private fun renderImageWidget(
        context: Context, manager: AppWidgetManager,
        id: Int, path: String, w: Int, h: Int
    ) {
        val config = ImageWidgetConfig(imagePath = path)
        val views = ImageWidgetRenderer.buildRemoteViews(context, config, w, h)
        manager.updateAppWidget(id, views)
    }

    private fun renderGifWidget(
        context: Context, id: Int, path: String, w: Int, h: Int
    ) {
        val config = GifWidgetConfig(gifPath = path)
        GifWidgetEngineManager.start(context, id, config, w, h)
    }

    private fun renderCodeWidget(
        context: Context, id: Int, zipPath: String, w: Int, h: Int
    ) {
        if (!CodeWidgetEngineManager.isRunning(id)) {
            val zipFile = File(zipPath)
            if (!zipFile.exists()) {
                Log.w(TAG, "ZIP bundle not found for widget $id: $zipPath")
                return
            }
            val (bundleDir, manifest) = BundleExtractor.extract(context, zipFile, id) ?: return
            CodeWidgetEngineManager.startEngine(context, id, bundleDir, manifest)
        }
    }

    // ── Text Config Parser (from .txt export format) ────────────────────────

    private fun parseTextConfig(path: String): TextWidgetConfig {
        val file = File(path)
        if (!file.exists()) return TextWidgetConfig("Widget")

        val lines = file.readLines()
        var text = ""
        var fontSize = 14f
        var textColor = "#FFFFFF"
        var backgroundColor = "#CC000000"
        var bold = false
        var italic = false
        var alignment = TextAlignment.CENTER

        var inHeader = false
        val bodyLines = mutableListOf<String>()

        lines.forEach { line ->
            when {
                line == "---WIDGETFORGE_METADATA---" -> inHeader = true
                line == "---END_METADATA---" -> inHeader = false
                inHeader -> {
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        when (parts[0].trim()) {
                            "fontSize" -> fontSize = parts[1].trim().toFloatOrNull() ?: 14f
                            "textColor" -> textColor = parts[1].trim()
                            "backgroundColor" -> backgroundColor = parts[1].trim()
                            "bold" -> bold = parts[1].trim().toBoolean()
                            "italic" -> italic = parts[1].trim().toBoolean()
                            "alignment" -> alignment = TextAlignment.valueOf(
                                parts[1].trim().uppercase()
                            )
                        }
                    }
                }
                else -> bodyLines.add(line)
            }
        }
        text = bodyLines.joinToString("\n").trim()

        return TextWidgetConfig(text, fontSize, textColor, backgroundColor, alignment, bold, italic)
    }

    companion object {
        private const val TAG = "BaseWidgetProvider"
        private const val CELL_DP = 74 // approximate dp per launcher cell
    }
}

// ─── WidgetForgeDatabase singleton extension ─────────────────────────────────

private fun WidgetForgeDatabase.Companion.getInstance(context: Context): WidgetForgeDatabase {
    return androidx.room.Room.databaseBuilder(
        context.applicationContext,
        WidgetForgeDatabase::class.java,
        WidgetForgeDatabase.DATABASE_NAME
    ).allowMainThreadQueries().build()
}
