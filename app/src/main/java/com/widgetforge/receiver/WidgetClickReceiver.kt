package com.widgetforge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.widgetforge.engine.code.CodeWidgetEngineManager

/**
 * WidgetClickReceiver — handles taps on the invisible click-grid overlay
 * of CODE widgets that opted into [CodeWidgetManifest.captureClickPosition].
 *
 * Each cell of the NxN grid (see widget_code.xml / CodeClickGridBuilder)
 * carries a distinct PendingIntent broadcasting this receiver with the
 * cell's (row, col) and the grid resolution. From those three integers we
 * recover an approximate tap position normalized to [0, 1] across the
 * widget's bounds — the center of the tapped cell — and forward it into
 * the widget's running JS engine by calling its onClick(x, y) hook.
 *
 * This is the standard workaround for AppWidgets: RemoteViews has no API
 * for raw touch/MotionEvent data, only per-view PendingIntent clicks, so
 * precision is bounded by the grid resolution the widget author chose
 * (clickGridResolution, 3-10). Higher resolution = finer position data,
 * at the cost of more PendingIntents (one per visible cell).
 */
class WidgetClickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WIDGET_CLICKED) return

        val appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, -1)
        val row          = intent.getIntExtra(EXTRA_ROW, -1)
        val col          = intent.getIntExtra(EXTRA_COL, -1)
        val resolution   = intent.getIntExtra(EXTRA_RESOLUTION, 6).coerceAtLeast(1)

        if (appWidgetId == -1 || row == -1 || col == -1) return

        // Normalized center of the tapped cell, in [0, 1] across each axis.
        val normX = (col + 0.5f) / resolution
        val normY = (row + 0.5f) / resolution

        Log.d(TAG, "Widget $appWidgetId tapped at cell ($row,$col) → norm=($normX,$normY)")

        CodeWidgetEngineManager.deliverClick(appWidgetId, normX, normY)
    }

    companion object {
        private const val TAG = "WidgetClickReceiver"

        const val ACTION_WIDGET_CLICKED = "com.widgetforge.WIDGET_CLICKED"
        const val EXTRA_APPWIDGET_ID    = "appWidgetId"
        const val EXTRA_ROW             = "row"
        const val EXTRA_COL             = "col"
        const val EXTRA_RESOLUTION      = "resolution"
    }
}
