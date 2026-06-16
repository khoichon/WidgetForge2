package com.widgetforge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.widgetforge.data.models.WidgetChannelMessage
import com.widgetforge.engine.code.CodeWidgetEngineManager

/**
 * WidgetCommunicationBus — global Android BroadcastReceiver event hub.
 *
 * Allows code-powered widgets to publish events on named channels
 * defined in their manifest.json. Receiving widgets evaluate a small
 * JS snippet inside their running WebView to update their global state
 * object without requiring a full canvas reload.
 *
 * Protocol:
 *   Intent action  : "com.widgetforge.WIDGET_CHANNEL_MESSAGE"
 *   Extra SOURCE_ID : Int   — source appWidgetId
 *   Extra CHANNEL   : String — channel name matching manifest
 *   Extra PAYLOAD   : String — JSON payload string
 */
class WidgetCommunicationBus : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_WIDGET_CHANNEL_MESSAGE -> handleChannelMessage(intent)
            ACTION_WIDGET_EVENT -> handleWidgetEvent(context, intent)
        }
    }

    private fun handleChannelMessage(intent: Intent) {
        val sourceId = intent.getIntExtra(EXTRA_SOURCE_ID, -1)
        val channel = intent.getStringExtra(EXTRA_CHANNEL) ?: return
        val payload = intent.getStringExtra(EXTRA_PAYLOAD) ?: "{}"

        Log.d(LOG_TAG, "Channel message: source=$sourceId channel=$channel")

        val message = WidgetChannelMessage(
            sourceWidgetId = sourceId,
            channel = channel,
            payload = payload
        )

        // Deliver to all active code widget engines subscribed to this channel
        CodeWidgetEngineManager.deliverChannelMessage(message)
    }

    private fun handleWidgetEvent(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(EXTRA_SOURCE_ID, -1)
        val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE) ?: return
        Log.d(LOG_TAG, "Widget event: widgetId=$widgetId type=$eventType")
        // Trigger update on the specific widget
        CodeWidgetEngineManager.triggerUpdate(context, widgetId)
    }

    companion object {
        private const val LOG_TAG = "WidgetCommunicationBus"

        const val ACTION_WIDGET_CHANNEL_MESSAGE = "com.widgetforge.WIDGET_CHANNEL_MESSAGE"
        const val ACTION_WIDGET_EVENT = "com.widgetforge.WIDGET_EVENT"
        const val EXTRA_SOURCE_ID = "source_widget_id"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_PAYLOAD = "payload"
        const val EXTRA_EVENT_TYPE = "event_type"

        /**
         * Publish a message to a channel from any code widget.
         * Called via the JavascriptInterface from within the sandboxed WebView.
         */
        fun publish(context: Context, sourceId: Int, channel: String, payload: String) {
            val intent = Intent(ACTION_WIDGET_CHANNEL_MESSAGE).apply {
                putExtra(EXTRA_SOURCE_ID, sourceId)
                putExtra(EXTRA_CHANNEL, channel)
                putExtra(EXTRA_PAYLOAD, payload)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }
}