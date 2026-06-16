package com.widgetforge.receiver

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.widgetforge.widget.CodeWidgetProvider
import com.widgetforge.widget.GifWidgetProvider
import com.widgetforge.widget.ImageWidgetProvider
import com.widgetforge.widget.TextWidgetProvider

/**
 * BootReceiver — restores all widget rendering after device reboot.
 * Triggers onUpdate on every registered AppWidgetProvider so the
 * homescreen widgets are re-populated immediately after boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        Log.d(TAG, "Boot/update detected — refreshing all widgets")

        val manager = AppWidgetManager.getInstance(context)
        val providers = listOf(
            TextWidgetProvider::class.java,
            ImageWidgetProvider::class.java,
            GifWidgetProvider::class.java,
            CodeWidgetProvider::class.java
        )

        providers.forEach { providerClass ->
            val ids = manager.getAppWidgetIds(ComponentName(context, providerClass))
            if (ids.isNotEmpty()) {
                val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    component = ComponentName(context, providerClass)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(updateIntent)
                Log.d(TAG, "Triggered update for ${providerClass.simpleName} ids=${ids.toList()}")
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
