package com.statusswipe.app.util

import android.content.Context
import android.content.SharedPreferences
import com.statusswipe.app.brightness.BrightnessConfig
import com.statusswipe.app.gesture.GestureConfig

/**
 * Centralized preferences for all StatusSwipe settings.
 * Uses SharedPreferences for simplicity — this is a single-purpose utility app.
 */
class Preferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("statusswipe_prefs", Context.MODE_PRIVATE)

    // --- Gesture Settings ---

    var gestureEnabled: Boolean
        get() = prefs.getBoolean("gestureEnabled", false)
        set(value) = prefs.edit().putBoolean("gestureEnabled", value).apply()

    var sensitivity: Float
        get() = prefs.getFloat("sensitivity", 1.0f)
        set(value) = prefs.edit().putFloat("sensitivity", value.coerceIn(0.25f, 3.0f)).apply()

    var gestureZoneFraction: Float
        get() = prefs.getFloat("gestureZoneFraction", 0.04f)
        set(value) = prefs.edit().putFloat("gestureZoneFraction", value.coerceIn(0.01f, 0.10f)).apply()

    var invertDirection: Boolean
        get() = prefs.getBoolean("invertDirection", false)
        set(value) = prefs.edit().putBoolean("invertDirection", value).apply()

    // --- Brightness Settings ---

    var disableAdaptiveOnGesture: Boolean
        get() = prefs.getBoolean("disableAdaptiveOnGesture", true)
        set(value) = prefs.edit().putBoolean("disableAdaptiveOnGesture", value).apply()

    // --- Indicator Settings ---

    var showBrightnessIndicator: Boolean
        get() = prefs.getBoolean("showBrightnessIndicator", true)
        set(value) = prefs.edit().putBoolean("showBrightnessIndicator", value).apply()

    var showPercentage: Boolean
        get() = prefs.getBoolean("showPercentage", true)
        set(value) = prefs.edit().putBoolean("showPercentage", value).apply()

    var indicatorDurationMs: Long
        get() = prefs.getLong("indicatorDurationMs", 800L)
        set(value) = prefs.edit().putLong("indicatorDurationMs", value).apply()

    // --- Mode Settings ---

    var inputMode: String
        get() = prefs.getString("inputMode", "auto") ?: "auto"
        set(value) = prefs.edit().putString("inputMode", value).apply()

    // --- Setup ---

    var hasCompletedSetup: Boolean
        get() = prefs.getBoolean("hasCompletedSetup", false)
        set(value) = prefs.edit().putBoolean("hasCompletedSetup", value).apply()

    // --- Config Converters ---

    fun toGestureConfig(): GestureConfig = GestureConfig(
        topGestureZoneFraction = gestureZoneFraction,
        movementThresholdFraction = 0.02f,
        horizontalDominanceRatio = 1.5f,
        sensitivityFactor = sensitivity,
        invertDirection = invertDirection,
    )

    fun toBrightnessConfig(): BrightnessConfig = BrightnessConfig(
        disableAdaptiveOnGesture = disableAdaptiveOnGesture,
        minBrightness = 0.01f,
        gamma = 2.2f,
    )
}
