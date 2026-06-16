package com.widgetforge.data.repository

import android.content.Context
import com.widgetforge.data.db.WidgetForgeDatabase
import com.widgetforge.data.models.WidgetRegistryEntry
import com.widgetforge.data.models.WidgetType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WidgetRegistry — single source of truth mapping every appWidgetId
 * to its WidgetType, source file path, and dimensional metadata.
 *
 * The AppWidgetProvider.onUpdate() queries this to route rendering
 * to the correct sub-module without cross-contaminating widget instances.
 */
@Singleton
class WidgetRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WidgetForgeDatabase
) {
    private val dao = db.widgetRegistryDao()

    // ── Observe ────────────────────────────────────────────────────────────

    fun observeAll(): Flow<List<WidgetRegistryEntry>> = dao.getAllWidgets()

    // ── Read ───────────────────────────────────────────────────────────────

    suspend fun getEntry(appWidgetId: Int): WidgetRegistryEntry? =
        dao.getWidget(appWidgetId)

    suspend fun getEntriesByType(type: WidgetType): List<WidgetRegistryEntry> =
        dao.getWidgetsByType(type)

    suspend fun getCount(): Int = dao.getCount()

    // ── Write ──────────────────────────────────────────────────────────────

    suspend fun register(entry: WidgetRegistryEntry) {
        dao.insertWidget(entry)
    }

    suspend fun unregister(appWidgetId: Int) {
        dao.deleteWidget(appWidgetId)
    }

    /**
     * Called from onAppWidgetOptionsChanged to record the new launcher
     * cell dimensions so rendering modules can adapt their output resolution.
     */
    suspend fun updateDimensions(appWidgetId: Int, widthPx: Int, heightPx: Int) {
        dao.updateDimensions(appWidgetId, widthPx, heightPx)
    }

    suspend fun updateSourcePath(appWidgetId: Int, path: String) {
        dao.updateSourcePath(appWidgetId, path)
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Resolves dp-to-pixel using the current display density.
     * Called from onAppWidgetOptionsChanged with minWidth/minHeight values.
     */
    fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
