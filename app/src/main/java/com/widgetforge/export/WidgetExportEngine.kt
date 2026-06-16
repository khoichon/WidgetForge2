package com.widgetforge.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.gson.Gson
import com.widgetforge.data.models.*
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * WidgetExportEngine — serializes each widget type to its canonical format.
 *
 *  TEXT   → .txt  with metadata header block
 *  IMAGE  → .png  (direct copy preserving original quality)
 *  GIF    → .gif  (direct copy)
 *  CODE   → .zip  bundle (manifest.json + main.js + assets/)
 */
object WidgetExportEngine {

    private val gson = Gson()

    // ── Text Export ─────────────────────────────────────────────────────────

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
        appendLine("---END_METADATA---")
        appendLine()
        append(config.text)
    }

    // ── Image Export ────────────────────────────────────────────────────────

    fun exportImage(sourcePath: String, destDir: File, name: String): File {
        destDir.mkdirs()
        val src = File(sourcePath)
        val dest = File(destDir, "$name.png")
        if (src.extension.lowercase() == "png") {
            src.copyTo(dest, overwrite = true)
        } else {
            // Re-encode to PNG preserving quality
            val bmp = BitmapFactory.decodeFile(sourcePath) ?: throw IllegalStateException("Cannot decode image")
            FileOutputStream(dest).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bmp.recycle()
        }
        return dest
    }

    // ── GIF Export ──────────────────────────────────────────────────────────

    fun exportGif(sourcePath: String, destDir: File, name: String): File {
        destDir.mkdirs()
        val dest = File(destDir, "$name.gif")
        File(sourcePath).copyTo(dest, overwrite = true)
        return dest
    }

    // ── Code Widget ZIP Export ──────────────────────────────────────────────

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
            // manifest.json
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(gson.toJson(manifest).toByteArray())
            zos.closeEntry()

            // main.js
            zos.putNextEntry(ZipEntry("main.js"))
            zos.write(mainJsContent.toByteArray())
            zos.closeEntry()

            // assets/
            assetFiles.forEach { asset ->
                if (!asset.exists()) return@forEach
                zos.putNextEntry(ZipEntry("assets/${asset.name}"))
                asset.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return zipFile
    }

    // ── Import Helpers ──────────────────────────────────────────────────────

    fun importText(file: File): TextWidgetConfig {
        val lines = file.readLines()
        var fontSize = 14f
        var textColor = "#FFFFFF"
        var backgroundColor = "#CC000000"
        var bold = false
        var italic = false
        var alignment = TextAlignment.CENTER
        var padding = 8
        var inHeader = false
        val bodyLines = mutableListOf<String>()

        lines.forEach { line ->
            when {
                line == "---WIDGETFORGE_METADATA---" -> inHeader = true
                line == "---END_METADATA---" -> inHeader = false
                inHeader -> {
                    val kv = line.split("=", limit = 2)
                    if (kv.size == 2) when (kv[0].trim()) {
                        "fontSize" -> fontSize = kv[1].trim().toFloatOrNull() ?: 14f
                        "textColor" -> textColor = kv[1].trim()
                        "backgroundColor" -> backgroundColor = kv[1].trim()
                        "bold" -> bold = kv[1].trim().toBoolean()
                        "italic" -> italic = kv[1].trim().toBoolean()
                        "alignment" -> alignment = runCatching {
                            TextAlignment.valueOf(kv[1].trim().uppercase())
                        }.getOrDefault(TextAlignment.CENTER)
                        "padding" -> padding = kv[1].trim().toIntOrNull() ?: 8
                    }
                }
                else -> bodyLines.add(line)
            }
        }
        return TextWidgetConfig(
            text = bodyLines.joinToString("\n").trim(),
            fontSize = fontSize, textColor = textColor,
            backgroundColor = backgroundColor, alignment = alignment,
            bold = bold, italic = italic, padding = padding
        )
    }

    fun parseManifest(zipFile: File): CodeWidgetManifest? {
        return try {
            val zis = java.util.zip.ZipInputStream(zipFile.inputStream())
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "manifest.json") {
                    val content = zis.readBytes().toString(Charsets.UTF_8)
                    zis.close()
                    return gson.fromJson(content, CodeWidgetManifest::class.java)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()
            null
        } catch (e: Exception) { null }
    }
}
