package com.widgetforge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.widgetforge.engine.code.CodeWidgetEngineManager
import com.widgetforge.engine.gif.GifWidgetEngineManager

/**
 * ScreenStateReceiver — listens to system-level screen events and
 * propagates pause/resume signals to all active CodeWidget and GIF
 * widget runtime engines.
 *
 * IMPORTANT: ACTION_SCREEN_ON, ACTION_SCREEN_OFF, and ACTION_USER_PRESENT
 * are implicit broadcasts that CANNOT be received by a manifest-declared
 * <receiver> on API 26+ (these "implicit broadcast exceptions" were locked
 * down starting with Android O). This receiver MUST be registered at
 * runtime via Context.registerReceiver() from a long-running component —
 * in this app, that's the two foreground render services, which call
 * ScreenStateReceiver.register()/unregister() from their onCreate()/onDestroy().
 *
 * Battery Optimization Contract:
 *  - SCREEN_OFF   → pause all JS/GIF animation loops immediately
 *  - SCREEN_ON    → screen lit but possibly locked; stay paused
 *  - USER_PRESENT → device unlocked, launcher visible; resume rendering
 */
class ScreenStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen OFF → pausing all animation engines")
                WidgetVisibilityTracker.onScreenOff()
                CodeWidgetEngineManager.pauseAll()
                GifWidgetEngineManager.pauseAll()
            }
            Intent.ACTION_SCREEN_ON -> {
                // Screen lit but may still be locked; wait for USER_PRESENT
                Log.d(TAG, "Screen ON (waiting for USER_PRESENT to resume)")
                WidgetVisibilityTracker.onScreenOn()
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User present → resuming all animation engines")
                WidgetVisibilityTracker.onUserPresent()
                CodeWidgetEngineManager.resumeAll()
                GifWidgetEngineManager.resumeAll()
            }
        }
    }

    companion object {
        private const val TAG = "ScreenStateReceiver"

        @Volatile private var instance: ScreenStateReceiver? = null

        /**
         * Dynamically registers this receiver. Safe to call multiple times —
         * subsequent calls are no-ops while already registered.
         */
        fun register(context: Context) {
            if (instance != null) return
            val receiver = ScreenStateReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            context.applicationContext.registerReceiver(receiver, filter)
            instance = receiver
            // Device is presumed unlocked/visible at registration time
            // (services are only started while the app/widgets are active).
            WidgetVisibilityTracker.onUserPresent()
            Log.d(TAG, "Registered dynamically")
        }

        fun unregister(context: Context) {
            instance?.let {
                runCatching { context.applicationContext.unregisterReceiver(it) }
                instance = null
                Log.d(TAG, "Unregistered")
            }
        }
    }
}

// ─── WidgetVisibilityTracker ────────────────────────────────────────────────

/**
 * Singleton state tracker so any engine can query current screen visibility
 * without needing to register its own broadcast receiver.
 */
object WidgetVisibilityTracker {

    @Volatile
    var isVisible: Boolean = true
        private set

    @Volatile
    var isScreenOn: Boolean = true
        private set

    fun onScreenOff() {
        isScreenOn = false
        isVisible = false
    }

    fun onScreenOn() {
        isScreenOn = true
        // Don't set visible yet — wait for USER_PRESENT
    }

    fun onUserPresent() {
        isScreenOn = true
        isVisible = true
    }
}
