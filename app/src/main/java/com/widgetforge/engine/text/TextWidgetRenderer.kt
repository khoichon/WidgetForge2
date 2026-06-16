package com.widgetforge.engine.text

import android.content.Context
import android.graphics.*
import android.widget.RemoteViews
import androidx.core.graphics.toColorInt
import com.widgetforge.R
import com.widgetforge.data.models.TextAlignment
import com.widgetforge.data.models.TextWidgetConfig

/**
 * TextWidgetRenderer — converts a TextWidgetConfig into a Bitmap
 * that can be pushed to a RemoteViews ImageView.
 *
 * Using Bitmap+Canvas rather than a TextView RemoteViews to get
 * full control over rich text styling and rounded corners.
 */
object TextWidgetRenderer {

    fun render(
        context: Context,
        config: TextWidgetConfig,
        widthPx: Int,
        heightPx: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(
            widthPx.coerceAtLeast(1),
            heightPx.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)

        // Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parseColor(config.backgroundColor, Color.BLACK)
            style = Paint.Style.FILL
        }
        val cornerRadius = 16f
        canvas.drawRoundRect(
            0f, 0f, widthPx.toFloat(), heightPx.toFloat(),
            cornerRadius, cornerRadius,
            bgPaint
        )

        // Text paint
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parseColor(config.textColor, Color.WHITE)
            textSize = config.fontSize * context.resources.displayMetrics.scaledDensity
            typeface = buildTypeface(config.bold, config.italic)
            textAlign = when (config.alignment) {
                TextAlignment.LEFT -> Paint.Align.LEFT
                TextAlignment.CENTER -> Paint.Align.CENTER
                TextAlignment.RIGHT -> Paint.Align.RIGHT
            }
        }

        // Word-wrap and draw
        val paddingPx = (config.padding * context.resources.displayMetrics.density).toInt()
        val maxWidth = widthPx - paddingPx * 2
        val lines = wrapText(config.text, textPaint, maxWidth.toFloat())
        val lineHeight = textPaint.descent() - textPaint.ascent()
        val totalTextHeight = lineHeight * lines.size
        var y = (heightPx / 2f) - (totalTextHeight / 2f) - textPaint.ascent()

        val x = when (config.alignment) {
            TextAlignment.LEFT -> paddingPx.toFloat()
            TextAlignment.CENTER -> widthPx / 2f
            TextAlignment.RIGHT -> (widthPx - paddingPx).toFloat()
        }

        lines.forEach { line ->
            canvas.drawText(line, x, y, textPaint)
            y += lineHeight
        }

        return bmp
    }

    fun buildRemoteViews(
        context: Context,
        config: TextWidgetConfig,
        widthPx: Int,
        heightPx: Int
    ): RemoteViews {
        val bmp = render(context, config, widthPx, heightPx)
        return RemoteViews(context.packageName, R.layout.widget_image_frame).apply {
            setImageViewBitmap(R.id.widget_image_view, bmp)
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        words.forEach { word ->
            val test = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(test) <= maxWidth) {
                currentLine = StringBuilder(test)
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())

        // Also split on explicit newlines
        return lines.flatMap { it.split("\n") }
    }

    private fun buildTypeface(bold: Boolean, italic: Boolean): Typeface {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(Typeface.DEFAULT, style)
    }

    private fun parseColor(hex: String, fallback: Int): Int = try {
        hex.toColorInt()
    } catch (e: Exception) { fallback }
}
