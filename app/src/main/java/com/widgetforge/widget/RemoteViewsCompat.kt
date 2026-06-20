package com.widgetforge.widget

import android.appwidget.AppWidgetManager
import android.os.Build
import android.widget.RemoteViews

/**
 * RemoteViewsCompat — guards use of AppWidgetManager.partiallyUpdateAppWidget(),
 * which is only available on API 31+ (Android 12). On older OS versions we
 * fall back to a full updateAppWidget() call.
 *
 * This matters specifically for the code-widget click grid: a partial
 * update merges into the existing RemoteViews tree (preserving the per-cell
 * PendingIntents already attached), whereas a full update replaces the tree
 * wholesale. On pre-31 devices where only full updates are available, the
 * frame-delivery loop in CodeWidgetEngineManager must re-attach the grid's
 * PendingIntents on every single frame, or they'd be wiped out by the next
 * animation tick. See CodeWidgetEngineManager.deliverFrame().
 */
object RemoteViewsCompat {

    val supportsPartialUpdate: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S // API 31

    fun applyPartial(manager: AppWidgetManager, appWidgetId: Int, views: RemoteViews) {
        if (supportsPartialUpdate) {
            manager.partiallyUpdateAppWidget(appWidgetId, views)
        } else {
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
