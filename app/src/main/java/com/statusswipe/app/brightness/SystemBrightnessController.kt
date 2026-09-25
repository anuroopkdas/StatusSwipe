package com.statusswipe.app.brightness

import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlin.math.roundToInt

class SystemBrightnessController(
    private val context: Context,
    private val config: BrightnessConfig
) : BrightnessController {

    companion object {
        private const val TAG = "StatusSwipe.Brightness"
        private const val SCREEN_BRIGHTNESS_FLOAT_KEY = "screen_brightness_float"
    }

    override fun getBrightness(): Float {
        return try {
            val intVal = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            ((intVal - 1).toFloat() / 254.0f).coerceIn(0.0f, 1.0f)
        } catch (_: Exception) {
            try {
                val floatVal = Settings.System.getFloat(context.contentResolver, SCREEN_BRIGHTNESS_FLOAT_KEY)
                floatVal.coerceIn(0.0f, 1.0f)
            } catch (e: Exception) {
                Log.w(TAG, "Could not read brightness setting: ${e.message}")
                0.5f
            }
        }
    }

    override fun setBrightness(value: Float) {
        if (!Settings.System.canWrite(context)) {
            Log.w(TAG, "Cannot write settings, WRITE_SETTINGS permission not granted")
            return
        }

        if (config.disableAdaptiveOnGesture && isAdaptiveBrightnessEnabled()) {
            setAdaptiveBrightness(false)
        }

        val clamped = value.coerceIn(0.0f, 1.0f)

        // Convert normalized 0.0..1.0 to hardware integer 1..255 (never 0 so screen never goes black)
        val intValue = (clamped * 254.0f + 1.0f).roundToInt().coerceIn(1, 255)

        try {
            // Write standard SCREEN_BRIGHTNESS (primary across all Android versions & OEMs)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                intValue
            )

            // Also try writing float key for AOSP systems that observe it
            try {
                Settings.System.putFloat(
                    context.contentResolver,
                    SCREEN_BRIGHTNESS_FLOAT_KEY,
                    clamped
                )
            } catch (_: Exception) {
                // Not supported on all ROMs
            }

            Log.d(TAG, "Brightness updated: $intValue/255 (${(clamped * 100).roundToInt()}%)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set brightness", e)
        }
    }

    override fun increase(delta: Float) {
        val current = getBrightness()
        val newBrightness = (current + delta).coerceIn(0.0f, 1.0f)
        setBrightness(newBrightness)
    }

    override fun decrease(delta: Float) {
        val current = getBrightness()
        val newBrightness = (current - delta).coerceIn(0.0f, 1.0f)
        setBrightness(newBrightness)
    }

    override fun isAdaptiveBrightnessEnabled(): Boolean {
        return try {
            val mode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (_: Exception) {
            false
        }
    }

    override fun setAdaptiveBrightness(enabled: Boolean) {
        if (!Settings.System.canWrite(context)) return
        val mode = if (enabled) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } else {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        }
        try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
            Log.i(TAG, "Adaptive brightness set to: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set adaptive brightness mode", e)
        }
    }
}
