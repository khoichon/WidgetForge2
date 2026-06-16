package com.widgetforge.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─── Enums ─────────────────────────────────────────────────────────────────

enum class WidgetType {
    TEXT, IMAGE, GIF, CODE
}

// ─── Widget Registry Entity ─────────────────────────────────────────────────

@Entity(tableName = "widget_registry")
data class WidgetRegistryEntry(
    @PrimaryKey val appWidgetId: Int,
    val widgetType: WidgetType,
    val sourceFilePath: String,       // Absolute path to .txt / .png / .gif / .zip
    val label: String,
    val cellWidth: Int = 2,           // Launcher grid cells (1–5)
    val cellHeight: Int = 2,
    val pixelWidth: Int = 0,          // Resolved dp width from onAppWidgetOptionsChanged
    val pixelHeight: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ─── Code Widget Manifest ───────────────────────────────────────────────────

data class CodeWidgetManifest(
    val id: String = "",
    val name: String = "Untitled Widget",
    val version: String = "1.0.0",
    val author: String = "",
    val description: String = "",
    val cellWidth: Int = 2,
    val cellHeight: Int = 2,
    val fps: Int = 30,
    val channels: List<ChannelConfig> = emptyList(),
    val assets: List<String> = emptyList()
)

data class ChannelConfig(
    val name: String,
    val type: ChannelType = ChannelType.PUBLISH_SUBSCRIBE,
    val initialState: String = "{}"
)

enum class ChannelType {
    PUBLISH_SUBSCRIBE, BROADCAST_ONLY, SUBSCRIBE_ONLY
}

// ─── Text Widget Config ─────────────────────────────────────────────────────

data class TextWidgetConfig(
    val text: String,
    val fontSize: Float = 14f,
    val textColor: String = "#FFFFFF",
    val backgroundColor: String = "#CC000000",
    val alignment: TextAlignment = TextAlignment.CENTER,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val padding: Int = 8
)

enum class TextAlignment { LEFT, CENTER, RIGHT }

// ─── Image Widget Config ────────────────────────────────────────────────────

data class ImageWidgetConfig(
    val imagePath: String,
    val scaleType: ImageScaleType = ImageScaleType.CENTER_CROP,
    val cornerRadius: Float = 12f,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FILL
)

enum class ImageScaleType { CENTER_CROP, CENTER_INSIDE, FIT_XY }
enum class AspectRatioMode { FILL, FIT, ORIGINAL }

// ─── GIF Widget Config ──────────────────────────────────────────────────────

data class GifWidgetConfig(
    val gifPath: String,
    val scaleType: ImageScaleType = ImageScaleType.CENTER_CROP,
    val cornerRadius: Float = 12f,
    val playbackSpeed: Float = 1.0f,
    val loopCount: Int = 0            // 0 = infinite
)

// ─── Widget Channel Message (for cross-widget bus) ──────────────────────────

data class WidgetChannelMessage(
    val sourceWidgetId: Int,
    val channel: String,
    val payload: String                // JSON string
)

// ─── Export Descriptor ──────────────────────────────────────────────────────

data class ExportDescriptor(
    val widgetType: WidgetType,
    val outputPath: String,
    val widgetId: Int,
    val timestamp: Long = System.currentTimeMillis()
)
