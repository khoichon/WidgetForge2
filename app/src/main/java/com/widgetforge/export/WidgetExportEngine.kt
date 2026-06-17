package com.widgetforge.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.gson.Gson
import com.widgetforge.data.models.*
import com.widgetforge.util.ImageMetadata
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object WidgetExportEngine {

    private val gson = Gson()

    // ── Text: .txt with metadata header ──────────────────────────────────────

    fun exportText(config: TextWidgetConfig, destDir: File, name: String): File {
        destDir.mkdirs()
        val file = File(destDir, "$name.txt")
        file.writeText(buildTextExport(config))
        return file
    }

    private fun buildTextExport(config: TextWidgetConfig): String = buildString {
        appendLine("---WIDGETFORGE_METADATA---")
        appendLine("widgetType = TEXT")
        appendLine("fontSize = ${config.fontSize}")
        appendLine("textColor = ${config.textColor}")
        appendLine("backgroundColor = ${config.backgroundColor}")
        appendLine("bold = ${config.bold}")
        appendLine("italic = ${config.italic}")
        appendLine("alignment = ${config.alignment.name}")
        appendLine("padding = ${config.padding}")
        appendLine("onClickAction = ${config.onClickAction}")
        appendLine("---END_METADATA---")
        appendLine()
        append(config.text)
    }

    fun importText(file: File): TextWidgetConfig {
        var fontSize = 14f; var textColor = "#FFFFFF"; var backgroundColor = "#CC000000"
        var bold = false; var italic = false; var alignment = TextAlignment.CENTER
        var padding = 8; var onClickAction = ""; var inHeader = false
        val bodyLines = mutableListOf<String>()

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
            text = bodyLines.joinToString("\n").trim(),
            fontSize = fontSize, textColor = textColor,
            backgroundColor = backgroundColor, alignment = alignment,
            bold = bold, italic = italic, padding = padding,
            onClickAction = onClickAction
        )
    }

    // ── Image: .png with embedded tEXt chunk metadata ────────────────────────

    /**
     * Export image to PNG. Metadata (label, onClick, cell size, etc.) is
     * embedded as a tEXt chunk with keyword "WidgetForge" so the file is
     * fully self-contained — no sidecar file needed.
     */
    fun exportImage(config: ImageWidgetConfig, destDir: File, name: String): File {
        destDir.mkdirs()
        val dest = File(destDir, "$name.png")
        val src  = File(config.imagePath)

        val meta = ImageMetadata.EmbeddedMeta(
            label         = config.label,
            onClickAction = config.onClickAction,
            cellWidth     = config.cellWidth,
            cellHeight    = config.cellHeight,
            cornerRadius  = config.cornerRadius,
            scaleType     = config.scaleType.name
        )

        if (src.extension.lowercase() == "png") {
            ImageMetadata.writePngFileWithMeta(src, meta, dest)
        } else {
            // Re-encode non-PNG sources to PNG first
            val bmp = BitmapFactory.decodeFile(config.imagePath)
                ?: throw IllegalStateException("Cannot decode image: ${config.imagePath}")
            ImageMetadata.writePngWithMeta(bmp, meta, dest)
            bmp.recycle()
        }
        return dest
    }

    /** Legacy overload: accepts a plain source path, no metadata fields */
    fun exportImage(sourcePath: String, destDir: File, name: String): File =
        exportImage(ImageWidgetConfig(imagePath = sourcePath), destDir, name)

    /** Read metadata from a PNG file */
    fun readImageMeta(file: File): ImageMetadata.EmbeddedMeta? =
        ImageMetadata.readPngMeta(file)

    // ── GIF: .gif with embedded comment extension metadata ───────────────────

    /**
     * Export GIF with metadata in a Comment Extension block.
     * The file remains a valid GIF playable by any viewer.
     */
    fun exportGif(config: GifWidgetConfig, destDir: File, name: String): File {
        destDir.mkdirs()
        val dest = File(destDir, "$name.gif")
        val src  = File(config.gifPath)

        val meta = ImageMetadata.EmbeddedMeta(
            label         = config.label,
            onClickAction = config.onClickAction,
            cellWidth     = config.cellWidth,
            cellHeight    = config.cellHeight,
            cornerRadius  = config.cornerRadius,
            scaleType     = config.scaleType.name
        )
        ImageMetadata.writeGifWithMeta(src, meta, dest)
        return dest
    }

    /** Legacy overload: accepts a plain source path */
    fun exportGif(sourcePath: String, destDir: File, name: String): File =
        exportGif(GifWidgetConfig(gifPath = sourcePath), destDir, name)

    /** Read metadata from a GIF file */
    fun readGifMeta(file: File): ImageMetadata.EmbeddedMeta? =
        ImageMetadata.readGifMeta(file)

    // ── Code Widget: .zip bundle ──────────────────────────────────────────────

    fun exportCodeWidget(
        manifest: CodeWidgetManifest,
        mainJsContent: String,
        assetFiles: List<File>,
        destDir: File,
        name: String
    ): File {
        destDir.mkdirs()
        val zipFile = File(destDir, "$name.zip")

        ZipOutputStream(FileOutputStream(zipFile).buffered()).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(gson.toJson(manifest).toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("main.js"))
            zos.write(mainJsContent.toByteArray())
            zos.closeEntry()

            assetFiles.forEach { asset ->
                if (!asset.exists()) return@forEach
                zos.putNextEntry(ZipEntry("assets/${asset.name}"))
                asset.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return zipFile
    }

    fun parseManifest(zipFile: File): CodeWidgetManifest? = try {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "manifest.json") {
                    val content = zis.readBytes().toString(Charsets.UTF_8)
                    return gson.fromJson(content, CodeWidgetManifest::class.java)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            null
        }
    } catch (e: Exception) { null }
}
