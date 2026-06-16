package com.widgetforge.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * SampleBundleManager — extracts the pre-packaged sample widget ZIP bundles
 * from assets/sample_bundles/ into the app's internal storage on first run.
 *
 * Bundles are assembled from the raw asset folder structure:
 *   assets/sample_bundles/clock_widget/{manifest.json, main.js, assets/}
 *   assets/sample_bundles/weather_widget/{manifest.json, main.js}
 */
object SampleBundleManager {

    private const val TAG = "SampleBundleManager"
    private val SAMPLE_BUNDLES = listOf("clock_widget", "weather_widget")

    /**
     * Package all sample bundles as ZIP files into [context.filesDir]/samples/
     * Called once on first app launch.
     */
    fun extractSampleBundles(context: Context): List<File> {
        val samplesDir = File(context.filesDir, "samples").also { it.mkdirs() }
        val results = mutableListOf<File>()

        SAMPLE_BUNDLES.forEach { bundleName ->
            val zipFile = File(samplesDir, "$bundleName.zip")
            if (zipFile.exists()) {
                results.add(zipFile)
                return@forEach
            }

            try {
                packBundleFromAssets(context, bundleName, zipFile)
                results.add(zipFile)
                Log.d(TAG, "Packed sample bundle: $bundleName → ${zipFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pack $bundleName: ${e.message}")
            }
        }
        return results
    }

    private fun packBundleFromAssets(context: Context, bundleName: String, outZip: File) {
        val assetManager = context.assets
        val assetBase = "sample_bundles/$bundleName"
        val filesToPack = listAllAssets(context, assetBase)

        ZipOutputStream(FileOutputStream(outZip).buffered()).use { zos ->
            filesToPack.forEach { assetPath ->
                val entryName = assetPath.removePrefix("$assetBase/")
                zos.putNextEntry(ZipEntry(entryName))
                assetManager.open(assetPath).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private fun listAllAssets(context: Context, path: String): List<String> {
        val assetManager = context.assets
        val result = mutableListOf<String>()
        val children = assetManager.list(path) ?: return result

        children.forEach { child ->
            val childPath = "$path/$child"
            val grandchildren = assetManager.list(childPath)
            if (!grandchildren.isNullOrEmpty()) {
                result.addAll(listAllAssets(context, childPath))
            } else {
                result.add(childPath)
            }
        }
        return result
    }

    fun getSampleBundleNames(): List<Pair<String, String>> = listOf(
        "clock_widget" to "Animated Clock",
        "weather_widget" to "Weather Widget"
    )
}
