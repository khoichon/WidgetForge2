package com.widgetforge.engine.image

import android.content.Context
import android.graphics.*
import android.widget.RemoteViews
import com.widgetforge.R
import com.widgetforge.data.models.AspectRatioMode
import com.widgetforge.data.models.ImageScaleType
import com.widgetforge.data.models.ImageWidgetConfig
import java.io.File

/**
 * ImageWidgetRenderer — loads a PNG/JPG from [config.imagePath],
 * applies scale type + rounded corners, and returns a RemoteViews.
 */
object ImageWidgetRenderer {

    fun render(
        config: ImageWidgetConfig,
        widthPx: Int,
        heightPx: Int
    ): Bitmap? {
        val src = decodeBitmap(config.imagePath, widthPx, heightPx) ?: return null
        val out = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // Clip with rounded corners
        val path = Path().apply {
            addRoundRect(
                RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()),
                config.cornerRadius, config.cornerRadius,
                Path.Direction.CW
            )
        }
        canvas.clipPath(path)

        // Apply scale type
        val matrix = buildMatrix(src, widthPx, heightPx, config.scaleType, config.aspectRatioMode)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, matrix, paint)

        src.recycle()
        return out
    }

    fun buildRemoteViews(
        context: Context,
        config: ImageWidgetConfig,
        widthPx: Int,
        heightPx: Int
    ): RemoteViews {
        val bmp = render(config, widthPx, heightPx)
        return RemoteViews(context.packageName, R.layout.widget_image_frame).apply {
            if (bmp != null) {
                setImageViewBitmap(R.id.widget_image_view, bmp)
            } else {
                setImageViewResource(R.id.widget_image_view, R.drawable.widget_placeholder)
            }
        }
    }

    private fun decodeBitmap(path: String, maxW: Int, maxH: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            opts.inSampleSize = calculateInSampleSize(opts, maxW, maxH)
            opts.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, opts)
        } catch (e: Exception) { null }
    }

    private fun calculateInSampleSize(opts: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        val (h, w) = opts.outHeight to opts.outWidth
        var inSampleSize = 1
        if (h > reqH || w > reqW) {
            val halfH = h / 2; val halfW = w / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun buildMatrix(
        src: Bitmap, dstW: Int, dstH: Int,
        scale: ImageScaleType, aspect: AspectRatioMode
    ): Matrix {
        val matrix = Matrix()
        val srcW = src.width.toFloat(); val srcH = src.height.toFloat()
        val dstWf = dstW.toFloat(); val dstHf = dstH.toFloat()

        when (scale) {
            ImageScaleType.CENTER_CROP -> {
                val scaleX = dstWf / srcW; val scaleY = dstHf / srcH
                val s = maxOf(scaleX, scaleY)
                val tx = (dstWf - srcW * s) / 2
                val ty = (dstHf - srcH * s) / 2
                matrix.setScale(s, s); matrix.postTranslate(tx, ty)
            }
            ImageScaleType.CENTER_INSIDE -> {
                val s = minOf(dstWf / srcW, dstHf / srcH)
                val tx = (dstWf - srcW * s) / 2
                val ty = (dstHf - srcH * s) / 2
                matrix.setScale(s, s); matrix.postTranslate(tx, ty)
            }
            ImageScaleType.FIT_XY -> {
                matrix.setScale(dstWf / srcW, dstHf / srcH)
            }
        }
        return matrix
    }
}
