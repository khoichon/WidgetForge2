package com.widgetforge.util

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FileUtils {

    /**
     * Copy a Uri (content://) to an internal File.
     * Returns the destination file or null on failure.
     */
    fun copyUriToFile(context: Context, uri: Uri, destDir: File, fileName: String): File? {
        return try {
            destDir.mkdirs()
            val dest = File(destDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest
        } catch (e: Exception) { null }
    }

    /**
     * Get the file extension from a Uri using the content resolver MIME type.
     */
    fun getExtension(context: Context, uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri) ?: return ""
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: ""
    }

    /**
     * Format file size in human-readable form.
     */
    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
    }

    /**
     * Generate a timestamped filename.
     */
    fun timestampedName(prefix: String, ext: String): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${prefix}_$ts.$ext"
    }

    /**
     * Recursively delete a directory.
     */
    fun deleteDir(dir: File) {
        if (dir.isDirectory) dir.listFiles()?.forEach { deleteDir(it) }
        dir.delete()
    }

    /**
     * Get the total size of a directory tree.
     */
    fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }
}
