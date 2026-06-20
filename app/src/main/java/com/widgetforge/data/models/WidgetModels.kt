package com.widgetforge.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─── Enums ──────────────────────────────────────────────────────────────────

enum class WidgetType { TEXT, IMAGE, GIF, CODE }

// ─── Widget Registry Entry ───────────────────────────────────────────────────
// Each placed widget instance on the homescreen maps to one entry.
// templateId links back to a user-created "template" (source file path).
// onClickAction is a URI: "app://" opens the app, "https://..." opens browser,
// "intent://..." fires a custom Intent, or "" for no action.

@Entity(tableName = "widget_registry")
data class WidgetRegistryEntry(
    @PrimaryKey val appWidgetId: Int,
    val widgetType: WidgetType,
    val sourceFilePath: String,       // abs path to .txt / .png / .gif / .zip
    val label: String,
    val cellWidth: Int = 2,
    val cellHeight: Int = 2,
    val pixelWidth: Int = 0,
    val pixelHeight: Int = 0,
    val onClickAction: String = "",   // URI/action fired when widget is tapped
    val captureClickPosition: Boolean = false, // CODE widgets only: forward (x,y) to JS
    val clickGridResolution: Int = 6,          // CODE widgets only: tap grid density
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ─── Widget Template (user-created, stored in DB) ────────────────────────────
// Templates are the "source of truth" the user designs in the app.
// When placing a widget the user picks a template; the placed instance
// gets its own appWidgetId but shares the same sourceFilePath.

@Entity(tableName = "widget_templates")
data class WidgetTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val widgetType: WidgetType,
    val sourceFilePath: String,
    val label: String,
    val cellWidth: Int = 2,
    val cellHeight: Int = 2,
    val onClickAction: String = "",
    val captureClickPosition: Boolean = false,
    val clickGridResolution: Int = 6,
    val createdAt: Long = System.currentTimeMillis()
)

// ─── Code Widget Manifest ────────────────────────────────────────────────────

data class CodeWidgetManifest(
    val id: String = "",
    val name: String = "Untitled Widget",
    val version: String = "1.0.0",
    val author: String = "",
    val description: String = "",
    val cellWidth: Int = 2,
    val cellHeight: Int = 2,
    val fps: Int = 30,
    val onClickAction: String = "",          // URI fired on tap (whole-widget fallback)
    val captureClickPosition: Boolean = false, // if true, taps invoke onClick(x,y) in JS instead
    val clickGridResolution: Int = 6,         // NxN invisible tap grid (3-10); higher = more precise
    val channels: List<ChannelConfig> = emptyList(),
    val assets: List<String> = emptyList()
)

data class ChannelConfig(
    val name: String,
    val type: ChannelType = ChannelType.PUBLISH_SUBSCRIBE,
    val initialState: String = "{}"
)

enum class ChannelType { PUBLISH_SUBSCRIBE, BROADCAST_ONLY, SUBSCRIBE_ONLY }

// ─── Text Widget Config ──────────────────────────────────────────────────────

data class TextWidgetConfig(
    val text: String,
    val fontSize: Float = 14f,
    val textColor: String = "#FFFFFF",
    val backgroundColor: String = "#CC000000",
    val alignment: TextAlignment = TextAlignment.CENTER,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val padding: Int = 8,
    val onClickAction: String = ""
)

enum class TextAlignment { LEFT, CENTER, RIGHT }

// ─── Image Widget Config ─────────────────────────────────────────────────────
// Metadata (label, onClickAction, etc.) is embedded in the PNG tEXt chunk
// under the keyword "WidgetForge" as a JSON string.

data class ImageWidgetConfig(
    val imagePath: String,
    val scaleType: ImageScaleType = ImageScaleType.CENTER_CROP,
    val cornerRadius: Float = 12f,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FILL,
    // Metadata stored inside the PNG tEXt chunk:
    val label: String = "",
    val onClickAction: String = "",
    val cellWidth: Int = 2,
    val cellHeight: Int = 2
)

// ─── GIF Widget Config ───────────────────────────────────────────────────────
// Metadata stored inside the GIF comment extension block.

data class GifWidgetConfig(
    val gifPath: String,
    val scaleType: ImageScaleType = ImageScaleType.CENTER_CROP,
    val cornerRadius: Float = 12f,
    val playbackSpeed: Float = 1.0f,
    val loopCount: Int = 0,
    // Metadata stored inside the GIF comment extension:
    val label: String = "",
    val onClickAction: String = "",
    val cellWidth: Int = 2,
    val cellHeight: Int = 2
)

enum class ImageScaleType { CENTER_CROP, CENTER_INSIDE, FIT_XY }
enum class AspectRatioMode { FILL, FIT, ORIGINAL }

// ─── Widget Channel Message ──────────────────────────────────────────────────

data class WidgetChannelMessage(
    val sourceWidgetId: Int,
    val channel: String,
    val payload: String
)

// ─── Export Descriptor ───────────────────────────────────────────────────────

data class ExportDescriptor(
    val widgetType: WidgetType,
    val outputPath: String,
    val widgetId: Int,
    val timestamp: Long = System.currentTimeMillis()
)
