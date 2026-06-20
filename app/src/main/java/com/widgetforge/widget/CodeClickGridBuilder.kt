package com.widgetforge.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.widgetforge.R
import com.widgetforge.receiver.WidgetClickReceiver

/**
 * CodeClickGridBuilder — wires PendingIntents onto the NxN invisible tap
 * grid declared in res/layout/widget_code.xml for CODE widgets that opted
 * into [captureClickPosition].
 *
 * The layout always declares the maximum 10x10 grid of View cells; this
 * builder only activates the first [resolution] x [resolution] cells
 * (giving them equal GridLayout weight so they fill the widget) and wires
 * each with a PendingIntent encoding its (row, col). All remaining cells
 * are left at zero size and without a click intent, so they're inert.
 *
 * Resolution is clamped to [3, 10] — below 3 the position data is too
 * coarse to be useful, above 10 the per-cell PendingIntent count (up to
 * 100) starts to noticeably slow down RemoteViews construction.
 */
object CodeClickGridBuilder {

    private val CELL_IDS: Array<IntArray> by lazy { buildCellIdTable() }

    fun apply(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        resolution: Int
    ) {
        val res = resolution.coerceIn(3, 10)

        // Shrink the GridLayout's logical dimensions to exactly `res` so the
        // active cells expand to fill the widget, rather than occupying only
        // the top-left corner of a fixed 10x10 grid.
        views.setInt(R.id.click_grid, "setRowCount", res)
        views.setInt(R.id.click_grid, "setColumnCount", res)

        for (r in 0 until 10) {
            for (c in 0 until 10) {
                val viewId = CELL_IDS[r][c]
                if (viewId == 0) continue
                if (r < res && c < res) {
                    views.setViewVisibility(viewId, android.view.View.VISIBLE)
                    views.setOnClickPendingIntent(
                        viewId,
                        buildCellPendingIntent(context, appWidgetId, r, c, res)
                    )
                } else {
                    // Outside the active resolution — remove from layout.
                    views.setViewVisibility(viewId, android.view.View.GONE)
                }
            }
        }
    }

    private fun buildCellPendingIntent(
        context: Context, appWidgetId: Int, row: Int, col: Int, resolution: Int
    ): PendingIntent {
        val intent = Intent(context, WidgetClickReceiver::class.java).apply {
            action = WidgetClickReceiver.ACTION_WIDGET_CLICKED
            putExtra(WidgetClickReceiver.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(WidgetClickReceiver.EXTRA_ROW, row)
            putExtra(WidgetClickReceiver.EXTRA_COL, col)
            putExtra(WidgetClickReceiver.EXTRA_RESOLUTION, resolution)
        }
        // Unique request code per (widget, cell) so PendingIntents don't collide
        // and overwrite each other's extras.
        val requestCode = appWidgetId * 1000 + row * 10 + col
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Maps (row, col) → the R.id.click_cell_R_C identifiers generated in
     * widget_code.xml. Built via reflection over R.id so this stays in
     * sync automatically if the layout's grid size ever changes.
     */
    private fun buildCellIdTable(): Array<IntArray> {
        val table = Array(10) { IntArray(10) }
        val idClass = R.id::class.java
        for (r in 0 until 10) {
            for (c in 0 until 10) {
                val fieldName = "click_cell_${r}_${c}"
                table[r][c] = runCatching {
                    idClass.getField(fieldName).getInt(null)
                }.getOrDefault(0)
            }
        }
        return table
    }
}
