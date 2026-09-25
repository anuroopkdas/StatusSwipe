package com.statusswipe.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.statusswipe.app.util.Preferences

/**
 * Starts the gesture service on device boot if the user had it enabled.
 *
 * Note: On Android 12+, starting a foreground service from BOOT_COMPLETED
 * may throw ForegroundServiceStartNotAllowedException on some devices.
 * This is acceptable — the user can re-enable from the app.
 * For more reliable boot persistence, a Magisk boot script can be used.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i("StatusSwipe", "Boot completed received")

        val prefs = Preferences(context)
        if (prefs.gestureEnabled) {
            Log.i("StatusSwipe", "Starting GestureService on boot")
            try {
                GestureService.start(context)
            } catch (e: Exception) {
                Log.e("StatusSwipe", "Failed to start GestureService on boot", e)
            }
        }
    }
}
