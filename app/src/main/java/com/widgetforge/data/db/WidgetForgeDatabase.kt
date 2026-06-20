package com.widgetforge.data.db

import androidx.room.*
import com.widgetforge.data.models.WidgetRegistryEntry
import com.widgetforge.data.models.WidgetTemplate
import com.widgetforge.data.models.WidgetType
import kotlinx.coroutines.flow.Flow

// ─── Type Converters ─────────────────────────────────────────────────────────

class Converters {
    @TypeConverter fun fromWidgetType(v: WidgetType): String = v.name
    @TypeConverter fun toWidgetType(v: String): WidgetType = WidgetType.valueOf(v)
}

// ─── Widget Registry DAO ─────────────────────────────────────────────────────

@Dao
interface WidgetRegistryDao {
    @Query("SELECT * FROM widget_registry ORDER BY createdAt DESC")
    fun getAllWidgets(): Flow<List<WidgetRegistryEntry>>

    @Query("SELECT * FROM widget_registry WHERE appWidgetId = :id")
    suspend fun getWidget(id: Int): WidgetRegistryEntry?

    @Query("SELECT * FROM widget_registry WHERE widgetType = :type")
    suspend fun getWidgetsByType(type: WidgetType): List<WidgetRegistryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWidget(widget: WidgetRegistryEntry)

    @Update
    suspend fun updateWidget(widget: WidgetRegistryEntry)

    @Query("DELETE FROM widget_registry WHERE appWidgetId = :id")
    suspend fun deleteWidget(id: Int)

    @Query("SELECT COUNT(*) FROM widget_registry")
    suspend fun getCount(): Int

    @Query("UPDATE widget_registry SET pixelWidth=:w, pixelHeight=:h, updatedAt=:ts WHERE appWidgetId=:id")
    suspend fun updateDimensions(id: Int, w: Int, h: Int, ts: Long = System.currentTimeMillis())

    @Query("UPDATE widget_registry SET sourceFilePath=:path, updatedAt=:ts WHERE appWidgetId=:id")
    suspend fun updateSourcePath(id: Int, path: String, ts: Long = System.currentTimeMillis())
}

// ─── Widget Templates DAO ────────────────────────────────────────────────────

@Dao
interface WidgetTemplateDao {
    @Query("SELECT * FROM widget_templates ORDER BY createdAt DESC")
    fun getAllTemplates(): Flow<List<WidgetTemplate>>

    @Query("SELECT * FROM widget_templates WHERE id = :id")
    suspend fun getTemplate(id: Long): WidgetTemplate?

    @Query("SELECT * FROM widget_templates WHERE widgetType = :type ORDER BY createdAt DESC")
    fun getTemplatesByType(type: WidgetType): Flow<List<WidgetTemplate>>

    @Query("SELECT * FROM widget_templates ORDER BY createdAt DESC")
    suspend fun getAllTemplatesOnce(): List<WidgetTemplate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: WidgetTemplate): Long

    @Update
    suspend fun updateTemplate(template: WidgetTemplate)

    @Query("DELETE FROM widget_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("SELECT COUNT(*) FROM widget_templates")
    suspend fun getCount(): Int
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [WidgetRegistryEntry::class, WidgetTemplate::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WidgetForgeDatabase : RoomDatabase() {
    abstract fun widgetRegistryDao(): WidgetRegistryDao
    abstract fun widgetTemplateDao(): WidgetTemplateDao

    companion object {
        const val DATABASE_NAME = "widgetforge.db"
    }
}
