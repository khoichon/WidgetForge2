package com.widgetforge.receiver

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.widgetforge.widget.*

/**
 * BootReceiver — re-triggers onUpdate for every registered provider after
 * device reboot or app self-update so homescreen widgets are repopulated.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        Log.d(TAG, "Boot/update — refreshing all widget providers")
        val manager = AppWidgetManager.getInstance(context)

        ALL_PROVIDERS.forEach { cls ->
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                        component = ComponentName(context, cls)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                )
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"

        /** All 100 provider classes (4 types × 25 sizes). */
        val ALL_PROVIDERS: List<Class<*>> = listOf(
            // TEXT
            TextWidget1x1Provider::class.java, TextWidget1x2Provider::class.java,
            TextWidget1x3Provider::class.java, TextWidget1x4Provider::class.java,
            TextWidget1x5Provider::class.java, TextWidget2x1Provider::class.java,
            TextWidget2x2Provider::class.java, TextWidget2x3Provider::class.java,
            TextWidget2x4Provider::class.java, TextWidget2x5Provider::class.java,
            TextWidget3x1Provider::class.java, TextWidget3x2Provider::class.java,
            TextWidget3x3Provider::class.java, TextWidget3x4Provider::class.java,
            TextWidget3x5Provider::class.java, TextWidget4x1Provider::class.java,
            TextWidget4x2Provider::class.java, TextWidget4x3Provider::class.java,
            TextWidget4x4Provider::class.java, TextWidget4x5Provider::class.java,
            TextWidget5x1Provider::class.java, TextWidget5x2Provider::class.java,
            TextWidget5x3Provider::class.java, TextWidget5x4Provider::class.java,
            TextWidget5x5Provider::class.java,
            // IMAGE
            ImageWidget1x1Provider::class.java, ImageWidget1x2Provider::class.java,
            ImageWidget1x3Provider::class.java, ImageWidget1x4Provider::class.java,
            ImageWidget1x5Provider::class.java, ImageWidget2x1Provider::class.java,
            ImageWidget2x2Provider::class.java, ImageWidget2x3Provider::class.java,
            ImageWidget2x4Provider::class.java, ImageWidget2x5Provider::class.java,
            ImageWidget3x1Provider::class.java, ImageWidget3x2Provider::class.java,
            ImageWidget3x3Provider::class.java, ImageWidget3x4Provider::class.java,
            ImageWidget3x5Provider::class.java, ImageWidget4x1Provider::class.java,
            ImageWidget4x2Provider::class.java, ImageWidget4x3Provider::class.java,
            ImageWidget4x4Provider::class.java, ImageWidget4x5Provider::class.java,
            ImageWidget5x1Provider::class.java, ImageWidget5x2Provider::class.java,
            ImageWidget5x3Provider::class.java, ImageWidget5x4Provider::class.java,
            ImageWidget5x5Provider::class.java,
            // GIF
            GifWidget1x1Provider::class.java, GifWidget1x2Provider::class.java,
            GifWidget1x3Provider::class.java, GifWidget1x4Provider::class.java,
            GifWidget1x5Provider::class.java, GifWidget2x1Provider::class.java,
            GifWidget2x2Provider::class.java, GifWidget2x3Provider::class.java,
            GifWidget2x4Provider::class.java, GifWidget2x5Provider::class.java,
            GifWidget3x1Provider::class.java, GifWidget3x2Provider::class.java,
            GifWidget3x3Provider::class.java, GifWidget3x4Provider::class.java,
            GifWidget3x5Provider::class.java, GifWidget4x1Provider::class.java,
            GifWidget4x2Provider::class.java, GifWidget4x3Provider::class.java,
            GifWidget4x4Provider::class.java, GifWidget4x5Provider::class.java,
            GifWidget5x1Provider::class.java, GifWidget5x2Provider::class.java,
            GifWidget5x3Provider::class.java, GifWidget5x4Provider::class.java,
            GifWidget5x5Provider::class.java,
            // CODE
            CodeWidget1x1Provider::class.java, CodeWidget1x2Provider::class.java,
            CodeWidget1x3Provider::class.java, CodeWidget1x4Provider::class.java,
            CodeWidget1x5Provider::class.java, CodeWidget2x1Provider::class.java,
            CodeWidget2x2Provider::class.java, CodeWidget2x3Provider::class.java,
            CodeWidget2x4Provider::class.java, CodeWidget2x5Provider::class.java,
            CodeWidget3x1Provider::class.java, CodeWidget3x2Provider::class.java,
            CodeWidget3x3Provider::class.java, CodeWidget3x4Provider::class.java,
            CodeWidget3x5Provider::class.java, CodeWidget4x1Provider::class.java,
            CodeWidget4x2Provider::class.java, CodeWidget4x3Provider::class.java,
            CodeWidget4x4Provider::class.java, CodeWidget4x5Provider::class.java,
            CodeWidget5x1Provider::class.java, CodeWidget5x2Provider::class.java,
            CodeWidget5x3Provider::class.java, CodeWidget5x4Provider::class.java,
            CodeWidget5x5Provider::class.java
        )
    }
}
