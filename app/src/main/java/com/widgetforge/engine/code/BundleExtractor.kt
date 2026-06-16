package com.widgetforge.engine.code

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.widgetforge.data.models.CodeWidgetManifest
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * BundleExtractor — unzips a code widget .zip bundle to the app's
 * internal files directory and parses manifest.json.
 *
 * Expected ZIP structure:
 *   manifest.json
 *   main.js
 *   assets/
 *     image.png
 *     font.ttf
 *     ...
 */
object BundleExtractor {

    private const val TAG = "BundleExtractor"
    private val gson = Gson()

    /**
     * Extract [zipFile] to [context.filesDir]/widgets/[widgetId]/
     * Returns the extraction directory and the parsed manifest, or null on failure.
     */
    fun extract(context: Context, zipFile: File, widgetId: Int): Pair<File, CodeWidgetManifest>? {
        val destDir = File(context.filesDir, "widgets/$widgetId").also {
            it.deleteRecursively()
            it.mkdirs()
        }

        return try {
            ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val manifestFile = File(destDir, "manifest.json")
            val manifest = if (manifestFile.exists()) {
                gson.fromJson(manifestFile.readText(), CodeWidgetManifest::class.java)
            } else {
                Log.w(TAG, "manifest.json not found; using defaults")
                CodeWidgetManifest()
            }

            Log.d(TAG, "Extracted bundle for widget $widgetId → ${destDir.absolutePath}")
            Pair(destDir, manifest)
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed: ${e.message}")
            null
        }
    }

    /**
     * Retrieve the already-extracted bundle directory for a widget.
     */
    fun getBundleDir(context: Context, widgetId: Int): File =
        File(context.filesDir, "widgets/$widgetId")

    fun isExtracted(context: Context, widgetId: Int): Boolean =
        File(context.filesDir, "widgets/$widgetId/main.js").exists()
}
