package com.widgetforge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.widgetforge.engine.code.CodeWidgetEngineManager

/**
 * ScreenStateReceiver — listens to system-level screen events and
 * propagates pause/resume signals to all active CodeWidget runtime engines.
 *
 * Registered in AndroidManifest for ACTION_SCREEN_ON, ACTION_SCREEN_OFF,
 * and ACTION_USER_PRESENT (device unlocked and launcher visible).
 *
 * Battery Optimization Contract:
 *  - SCREEN_OFF  → pause all JS animation loops immediately
 *  - SCREEN_ON   → screen lit but possibly locked; keep paused
 *  - USER_PRESENT → launcher is now visible; resume rendering
 */
class ScreenStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen OFF → pausing all code widget engines")
                WidgetVisibilityTracker.onScreenOff()
                CodeWidgetEngineManager.pauseAll()
            }
            Intent.ACTION_SCREEN_ON -> {
                // Screen turned on but may still be locked; wait for USER_PRESENT
                Log.d(TAG, "Screen ON (waiting for USER_PRESENT to resume)")
                WidgetVisibilityTracker.onScreenOn()
            }
            Intent.ACTION_USER_PRESENT -> {
                // Device unlocked → launcher is now in foreground
                Log.d(TAG, "User present → resuming all code widget engines")
                WidgetVisibilityTracker.onUserPresent()
                CodeWidgetEngineManager.resumeAll()
            }
        }
    }

    companion object {
        private const val TAG = "ScreenStateReceiver"
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
        isVisible = true
    }

}
