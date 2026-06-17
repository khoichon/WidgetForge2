package com.widgetforge.data.repository

import android.content.Context
import com.widgetforge.data.db.WidgetForgeDatabase
import com.widgetforge.data.models.WidgetRegistryEntry
import com.widgetforge.data.models.WidgetTemplate
import com.widgetforge.data.models.WidgetType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WidgetForgeDatabase
) {
    private val dao         = db.widgetRegistryDao()
    private val templateDao = db.widgetTemplateDao()

    // ── Placed widget instances ──────────────────────────────────────────────

    fun observeAll(): Flow<List<WidgetRegistryEntry>> = dao.getAllWidgets()

    suspend fun getEntry(appWidgetId: Int): WidgetRegistryEntry? = dao.getWidget(appWidgetId)

    suspend fun getEntriesByType(type: WidgetType): List<WidgetRegistryEntry> =
        dao.getWidgetsByType(type)

    suspend fun getCount(): Int = dao.getCount()

    suspend fun register(entry: WidgetRegistryEntry) { dao.insertWidget(entry) }

    suspend fun unregister(appWidgetId: Int) { dao.deleteWidget(appWidgetId) }

    suspend fun updateDimensions(appWidgetId: Int, widthPx: Int, heightPx: Int) {
        dao.updateDimensions(appWidgetId, widthPx, heightPx)
    }

    suspend fun updateSourcePath(appWidgetId: Int, path: String) {
        dao.updateSourcePath(appWidgetId, path)
    }

    // ── Templates ────────────────────────────────────────────────────────────

    fun observeTemplates(): Flow<List<WidgetTemplate>> = templateDao.getAllTemplates()

    fun observeTemplatesByType(type: WidgetType): Flow<List<WidgetTemplate>> =
        templateDao.getTemplatesByType(type)

    suspend fun getAllTemplatesOnce(): List<WidgetTemplate> =
        templateDao.getAllTemplatesOnce()

    suspend fun getTemplate(id: Long): WidgetTemplate? = templateDao.getTemplate(id)

    suspend fun saveTemplate(template: WidgetTemplate): Long =
        templateDao.insertTemplate(template)

    suspend fun deleteTemplate(id: Long) { templateDao.deleteTemplate(id) }

    // ── Helpers ──────────────────────────────────────────────────────────────

    fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
