package com.widgetforge.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.room.Room
import com.widgetforge.data.db.WidgetForgeDatabase
import com.widgetforge.data.models.*
import com.widgetforge.data.repository.WidgetRegistry
import com.widgetforge.engine.code.BundleExtractor
import com.widgetforge.engine.code.CodeWidgetEngineManager
import com.widgetforge.engine.gif.GifWidgetEngineManager
import com.widgetforge.engine.image.ImageWidgetRenderer
import com.widgetforge.engine.text.TextWidgetRenderer
import com.widgetforge.util.ImageMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

abstract class BaseWidgetProvider : AppWidgetProvider() {

    private val job   = SupervisorJob()
    protected val scope = CoroutineScope(Dispatchers.IO + job)

    // ── onUpdate: route each ID to the correct rendering engine ──────────────

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val registry = DbHolder.registryFor(context)
        scope.launch {
            appWidgetIds.forEach { id ->
                val entry = registry.getEntry(id)
                if (entry == null) {
                    Log.w(TAG, "No registry entry for widget $id — skipping")
                    return@forEach
                }

                val w = entry.pixelWidth.takeIf  { it > 0 } ?: registry.dpToPx(entry.cellWidth  * CELL_DP)
                val h = entry.pixelHeight.takeIf { it > 0 } ?: registry.dpToPx(entry.cellHeight * CELL_DP)

                // Build click PendingIntent if configured
                val clickIntent = buildClickIntent(context, id, entry.onClickAction)

                when (entry.widgetType) {
                    WidgetType.TEXT  -> renderText (context, appWidgetManager, id, entry.sourceFilePath, w, h, clickIntent)
                    WidgetType.IMAGE -> renderImage(context, appWidgetManager, id, entry.sourceFilePath, w, h, clickIntent)
                    WidgetType.GIF   -> renderGif  (context, id, entry.sourceFilePath, w, h)
                    WidgetType.CODE  -> renderCode (context, id, entry.sourceFilePath)
                }
            }
        }
    }

    // ── onAppWidgetOptionsChanged: respond to launcher resize ─────────────────

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val minWidth  = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val registry  = DbHolder.registryFor(context)

        scope.launch {
            val widthPx  = registry.dpToPx(minWidth)
            val heightPx = registry.dpToPx(minHeight)
            registry.updateDimensions(appWidgetId, widthPx, heightPx)
            CodeWidgetEngineManager.updateDimensions(appWidgetId, widthPx, heightPx)
            GifWidgetEngineManager.updateDimensions(appWidgetId, widthPx, heightPx)
            onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
        }
    }

    // ── onDeleted: clean up ───────────────────────────────────────────────────

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val registry = DbHolder.registryFor(context)
        scope.launch {
            appWidgetIds.forEach { id ->
                registry.unregister(id)
                CodeWidgetEngineManager.stopEngine(id)
                GifWidgetEngineManager.stop(id)
                Log.d(TAG, "Widget $id deleted")
            }
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun renderText(
        context: Context, manager: AppWidgetManager,
        id: Int, path: String, w: Int, h: Int,
        clickIntent: PendingIntent?
    ) {
        val config = parseTextConfig(path)
        val views  = TextWidgetRenderer.buildRemoteViews(context, config, w, h)
        clickIntent?.let { views.setOnClickPendingIntent(com.widgetforge.R.id.widget_image_view, it) }
        manager.updateAppWidget(id, views)
    }

    private fun renderImage(
        context: Context, manager: AppWidgetManager,
        id: Int, path: String, w: Int, h: Int,
        clickIntent: PendingIntent?
    ) {
        // Read metadata embedded in the PNG tEXt chunk
        val meta = ImageMetadata.readPngMeta(File(path))
        val config = ImageWidgetConfig(
            imagePath       = path,
            cornerRadius    = meta?.cornerRadius ?: 12f,
            onClickAction   = meta?.onClickAction ?: "",
            label           = meta?.label ?: "",
            cellWidth       = meta?.cellWidth ?: 2,
            cellHeight      = meta?.cellHeight ?: 2
        )
        val views = ImageWidgetRenderer.buildRemoteViews(context, config, w, h)
        clickIntent?.let { views.setOnClickPendingIntent(com.widgetforge.R.id.widget_image_view, it) }
        manager.updateAppWidget(id, views)
    }

    private fun renderGif(context: Context, id: Int, path: String, w: Int, h: Int) {
        // Read metadata from GIF comment extension
        val meta = ImageMetadata.readGifMeta(File(path))
        val config = GifWidgetConfig(
            gifPath         = path,
            cornerRadius    = meta?.cornerRadius ?: 12f,
            onClickAction   = meta?.onClickAction ?: "",
            label           = meta?.label ?: "",
            cellWidth       = meta?.cellWidth ?: 2,
            cellHeight      = meta?.cellHeight ?: 2
        )
        GifWidgetEngineManager.start(context, id, config, w, h)
    }

    private fun renderCode(context: Context, id: Int, zipPath: String) {
        if (CodeWidgetEngineManager.isRunning(id)) return
        val zipFile = File(zipPath)
        if (!zipFile.exists()) {
            Log.w(TAG, "Bundle not found for widget $id: $zipPath")
            return
        }
        val (bundleDir, manifest) = BundleExtractor.extract(context, zipFile, id) ?: return
        CodeWidgetEngineManager.startEngine(context, id, bundleDir, manifest)
    }

    // ── onClick PendingIntent builder ─────────────────────────────────────────

    private fun buildClickIntent(
        context: Context,
        appWidgetId: Int,
        action: String
    ): PendingIntent? {
        if (action.isBlank()) return null
        return try {
            val intent: Intent = when {
                action == "app://"          -> context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                    ?: return null

                action.startsWith("https://") ||
                action.startsWith("http://")  ->
                    Intent(Intent.ACTION_VIEW, Uri.parse(action))

                action.startsWith("intent://") ->
                    Intent.parseUri(action, Intent.URI_INTENT_SCHEME)

                else ->
                    Intent(Intent.ACTION_VIEW, Uri.parse(action))
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            PendingIntent.getActivity(
                context,
                appWidgetId,          // unique requestCode per widget
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (e: Exception) {
            Log.e(TAG, "buildClickIntent failed: ${e.message}")
            null
        }
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
        var padding         = 8
        var onClickAction   = ""
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
                        "onClickAction"   -> onClickAction   = kv[1].trim()
                        "alignment"       -> alignment       = runCatching {
                            TextAlignment.valueOf(kv[1].trim().uppercase())
                        }.getOrDefault(TextAlignment.CENTER)
                        "padding"         -> padding         = kv[1].trim().toIntOrNull() ?: 8
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
            italic          = italic,
            padding         = padding,
            onClickAction   = onClickAction
        )
    }

    companion object {
        private const val TAG     = "BaseWidgetProvider"
        private const val CELL_DP = 74
    }
}

// ─── Process-level singleton DB/Registry for non-Hilt contexts ───────────────

object DbHolder {
    @Volatile private var db: WidgetForgeDatabase? = null
    @Volatile private var registry: WidgetRegistry? = null

    fun registryFor(context: Context): WidgetRegistry {
        return registry ?: synchronized(this) {
            registry ?: run {
                val appCtx = context.applicationContext
                val newDb  = db ?: Room.databaseBuilder(
                    appCtx, WidgetForgeDatabase::class.java,
                    WidgetForgeDatabase.DATABASE_NAME
                ).fallbackToDestructiveMigration().build().also { db = it }
                WidgetRegistry(appCtx, newDb).also { registry = it }
            }
        }
    }
}
