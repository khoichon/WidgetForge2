package com.widgetforge.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32

/**
 * ImageMetadata — embeds and reads WidgetForge configuration directly
 * inside image files, eliminating the need for sidecar files.
 *
 *  PNG  → stored in a tEXt chunk with keyword "WidgetForge"
 *  GIF  → stored in a Comment Extension block (0x21 0xFE)
 *
 * The payload is a JSON string matching [EmbeddedMeta].
 */
object ImageMetadata {

    private const val PNG_KEYWORD = "WidgetForge"
    private val gson = Gson()

    // ── Embedded metadata schema ─────────────────────────────────────────────

    data class EmbeddedMeta(
        @SerializedName("label")         val label: String = "",
        @SerializedName("onClick")       val onClickAction: String = "",
        @SerializedName("cellW")         val cellWidth: Int = 2,
        @SerializedName("cellH")         val cellHeight: Int = 2,
        @SerializedName("cornerRadius")  val cornerRadius: Float = 12f,
        @SerializedName("scaleType")     val scaleType: String = "CENTER_CROP"
    )

    // ════════════════════════════════════════════════════════════════════════
    //  PNG
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Write [bitmap] to [dest] as PNG and embed [meta] in a tEXt chunk.
     */
    fun writePngWithMeta(bitmap: Bitmap, meta: EmbeddedMeta, dest: File) {
        // 1. Encode bitmap to raw PNG bytes
        val rawPng = ByteArrayOutputStream().also { bos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
        }.toByteArray()

        // 2. Parse PNG structure and inject tEXt chunk after IHDR
        dest.outputStream().buffered().use { out ->
            injectPngTextChunk(rawPng, PNG_KEYWORD, gson.toJson(meta), out)
        }
    }

    /**
     * Copy an existing PNG from [src] to [dest], injecting/replacing the
     * WidgetForge tEXt chunk.
     */
    fun writePngFileWithMeta(src: File, meta: EmbeddedMeta, dest: File) {
        val raw = src.readBytes()
        dest.outputStream().buffered().use { out ->
            injectPngTextChunk(raw, PNG_KEYWORD, gson.toJson(meta), out)
        }
    }

    /**
     * Read [EmbeddedMeta] from a PNG file's tEXt chunk.
     * Returns null if no WidgetForge chunk is present.
     */
    fun readPngMeta(file: File): EmbeddedMeta? = readPngMeta(file.readBytes())

    fun readPngMeta(bytes: ByteArray): EmbeddedMeta? {
        // Walk PNG chunks
        var pos = 8 // skip 8-byte signature
        while (pos + 12 <= bytes.size) {
            val length = bytes.readInt32(pos)
            val type   = String(bytes, pos + 4, 4, Charsets.ISO_8859_1)
            if (type == "tEXt" && pos + 8 + length <= bytes.size) {
                val data    = bytes.copyOfRange(pos + 8, pos + 8 + length)
                val nullIdx = data.indexOf(0.toByte())
                if (nullIdx >= 0) {
                    val keyword = String(data, 0, nullIdx, Charsets.ISO_8859_1)
                    if (keyword == PNG_KEYWORD) {
                        val text = String(data, nullIdx + 1, data.size - nullIdx - 1, Charsets.UTF_8)
                        return runCatching { gson.fromJson(text, EmbeddedMeta::class.java) }.getOrNull()
                    }
                }
            }
            if (type == "IEND") break
            pos += 12 + length
        }
        return null
    }

    private fun injectPngTextChunk(raw: ByteArray, keyword: String, text: String, out: OutputStream) {
        // PNG signature
        out.write(raw, 0, 8)

        var pos = 8
        var injected = false

        while (pos + 12 <= raw.size) {
            val length = raw.readInt32(pos)
            val type   = String(raw, pos + 4, 4, Charsets.ISO_8859_1)

            // Write this chunk
            out.write(raw, pos, 12 + length)

            // After IHDR, inject our tEXt chunk
            if (type == "IHDR" && !injected) {
                writePngTextChunk(out, keyword, text)
                injected = true
            }

            if (type == "IEND") break
            pos += 12 + length
        }
    }

    private fun writePngTextChunk(out: OutputStream, keyword: String, text: String) {
        val keyBytes  = keyword.toByteArray(Charsets.ISO_8859_1)
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val data      = keyBytes + byteArrayOf(0) + textBytes

        val crc = CRC32().also { crc ->
            crc.update("tEXt".toByteArray(Charsets.ISO_8859_1))
            crc.update(data)
        }.value

        out.writeInt32(data.size)
        out.write("tEXt".toByteArray(Charsets.ISO_8859_1))
        out.write(data)
        out.writeInt32(crc.toInt())
    }

    // ════════════════════════════════════════════════════════════════════════
    //  GIF
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Copy [src] GIF to [dest], replacing or adding a Comment Extension
     * block containing [meta] as JSON.
     */
    fun writeGifWithMeta(src: File, meta: EmbeddedMeta, dest: File) {
        val raw     = src.readBytes()
        val comment = gson.toJson(meta).toByteArray(Charsets.UTF_8)
        dest.outputStream().buffered().use { out ->
            injectGifComment(raw, comment, out)
        }
    }

    /**
     * Read [EmbeddedMeta] from a GIF Comment Extension.
     * Returns null if not present.
     */
    fun readGifMeta(file: File): EmbeddedMeta? = readGifMeta(file.readBytes())

    fun readGifMeta(bytes: ByteArray): EmbeddedMeta? {
        // GIF header is 6 bytes, Logical Screen Descriptor is 7 bytes
        var pos = 6
        // Skip logical screen descriptor + optional global color table
        if (pos >= bytes.size) return null
        val packed = bytes[pos + 4].toInt() and 0xFF
        val hasGct = (packed and 0x80) != 0
        val gctSize = if (hasGct) 3 * (1 shl ((packed and 0x07) + 1)) else 0
        pos += 7 + gctSize

        while (pos < bytes.size) {
            val blockType = bytes[pos].toInt() and 0xFF
            if (blockType == 0x3B) break // trailer
            if (blockType == 0x21) {
                // Extension
                val extLabel = bytes.getOrNull(pos + 1)?.toInt()?.and(0xFF) ?: break
                if (extLabel == 0xFE) {
                    // Comment Extension — read sub-blocks
                    val sb = StringBuilder()
                    var subPos = pos + 2
                    while (subPos < bytes.size) {
                        val subSize = bytes[subPos].toInt() and 0xFF
                        if (subSize == 0) break
                        sb.append(String(bytes, subPos + 1, subSize, Charsets.UTF_8))
                        subPos += 1 + subSize
                    }
                    return runCatching { gson.fromJson(sb.toString(), EmbeddedMeta::class.java) }.getOrNull()
                }
                // Skip other extensions
                pos += 2
                while (pos < bytes.size) {
                    val sz = bytes[pos].toInt() and 0xFF
                    pos += 1 + sz
                    if (sz == 0) break
                }
            } else if (blockType == 0x2C) {
                // Image descriptor — skip
                val packed2 = bytes.getOrNull(pos + 9)?.toInt()?.and(0xFF) ?: break
                val hasLct  = (packed2 and 0x80) != 0
                val lctSize = if (hasLct) 3 * (1 shl ((packed2 and 0x07) + 1)) else 0
                pos += 10 + lctSize + 1 // +1 for LZW minimum code size
                // Skip sub-blocks
                while (pos < bytes.size) {
                    val sz = bytes[pos].toInt() and 0xFF
                    pos += 1 + sz
                    if (sz == 0) break
                }
            } else {
                pos++
            }
        }
        return null
    }

    private fun injectGifComment(raw: ByteArray, comment: ByteArray, out: OutputStream) {
        // Write header (6) + logical screen descriptor (7) + optional GCT
        var pos = 6
        val packed  = raw[pos + 4].toInt() and 0xFF
        val hasGct  = (packed and 0x80) != 0
        val gctSize = if (hasGct) 3 * (1 shl ((packed and 0x07) + 1)) else 0
        val headerEnd = 6 + 7 + gctSize
        out.write(raw, 0, headerEnd)

        // Write our comment extension immediately after header
        writeGifCommentExtension(out, comment)

        // Write remaining GIF data, skipping any existing comment extension
        pos = headerEnd
        while (pos < raw.size) {
            val blockType = raw[pos].toInt() and 0xFF
            if (blockType == 0x3B) { out.write(0x3B); break }

            if (blockType == 0x21) {
                val extLabel = raw.getOrNull(pos + 1)?.toInt()?.and(0xFF) ?: break
                if (extLabel == 0xFE) {
                    // Skip existing comment
                    pos += 2
                    while (pos < raw.size) {
                        val sz = raw[pos].toInt() and 0xFF
                        pos += 1 + sz
                        if (sz == 0) break
                    }
                    continue
                }
            }
            // Copy this block verbatim
            out.write(blockType)
            pos++
            if (blockType == 0x21) {
                val label = raw[pos].toInt() and 0xFF
                out.write(label); pos++
                while (pos < raw.size) {
                    val sz = raw[pos].toInt() and 0xFF
                    out.write(sz); pos++
                    if (sz == 0) break
                    out.write(raw, pos, sz); pos += sz
                }
            } else if (blockType == 0x2C) {
                // Image descriptor
                out.write(raw, pos, 9); pos += 9
                val p2 = raw[pos - 1].toInt() and 0xFF
                val hasLct  = (p2 and 0x80) != 0
                val lctSize = if (hasLct) 3 * (1 shl ((p2 and 0x07) + 1)) else 0
                if (lctSize > 0) { out.write(raw, pos, lctSize); pos += lctSize }
                out.write(raw[pos].toInt() and 0xFF); pos++ // LZW min code size
                while (pos < raw.size) {
                    val sz = raw[pos].toInt() and 0xFF
                    out.write(sz); pos++
                    if (sz == 0) break
                    out.write(raw, pos, sz); pos += sz
                }
            }
        }
    }

    private fun writeGifCommentExtension(out: OutputStream, comment: ByteArray) {
        out.write(0x21) // extension introducer
        out.write(0xFE) // comment label
        // Split into sub-blocks of max 255 bytes
        var offset = 0
        while (offset < comment.size) {
            val size = minOf(255, comment.size - offset)
            out.write(size)
            out.write(comment, offset, size)
            offset += size
        }
        out.write(0) // block terminator
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun ByteArray.readInt32(pos: Int): Int =
        ((this[pos].toInt() and 0xFF) shl 24) or
        ((this[pos+1].toInt() and 0xFF) shl 16) or
        ((this[pos+2].toInt() and 0xFF) shl 8) or
        (this[pos+3].toInt() and 0xFF)

    private fun OutputStream.writeInt32(v: Int) {
        write((v ushr 24) and 0xFF)
        write((v ushr 16) and 0xFF)
        write((v ushr 8)  and 0xFF)
        write(v and 0xFF)
    }
}
