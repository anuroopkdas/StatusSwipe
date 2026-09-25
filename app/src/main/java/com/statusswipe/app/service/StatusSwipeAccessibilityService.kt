package com.statusswipe.app.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.statusswipe.app.brightness.BrightnessIndicatorView
import com.statusswipe.app.brightness.SystemBrightnessController
import com.statusswipe.app.gesture.GestureConfig
import com.statusswipe.app.gesture.GestureRecognizer
import com.statusswipe.app.input.TouchEvent
import com.statusswipe.app.util.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Non-Root Accessibility Service for StatusSwipe.
 *
 * Uses WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY to place a transparent
 * touch detection zone over the system status bar without requiring root access.
 *
 * Disambiguates gestures:
 * - Horizontal swipe: Adjusts screen brightness via SystemBrightnessController and shows HUD.
 * - Downward swipe: Invokes performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) to seamlessly
 *   expand the native Android notification shade.
 */
class StatusSwipeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "StatusSwipe.A11y"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _isOverlayActive = MutableStateFlow(false)
        val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

        var instance: StatusSwipeAccessibilityService? = null
            private set
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private lateinit var preferences: Preferences
    private lateinit var brightnessController: SystemBrightnessController
    private var brightnessIndicator: BrightnessIndicatorView? = null
    private lateinit var gestureRecognizer: GestureRecognizer

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "StatusSwipeAccessibilityService connected")
        instance = this
        _isRunning.value = true

        preferences = Preferences(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        brightnessController = SystemBrightnessController(this, preferences.toBrightnessConfig())
        brightnessIndicator = BrightnessIndicatorView(this)

        updateGestureRecognizer()

        // Automatically setup overlay if gesture is enabled in preferences
        if (preferences.gestureEnabled) {
            setupOverlay()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not inspecting window content — purely touch-driven overlay for user privacy & speed
    }

    override fun onInterrupt() {
        Log.w(TAG, "StatusSwipeAccessibilityService interrupted")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (overlayView != null) {
            updateOverlayDimensions()
        }
    }

    fun updateGestureRecognizer() {
        val gestureConfig = GestureConfig(
            topGestureZoneFraction = preferences.gestureZoneFraction,
            movementThresholdFraction = 0.02f,
            horizontalDominanceRatio = 1.0f,
            sensitivityFactor = preferences.sensitivity,
            invertDirection = preferences.invertDirection,
        )

        gestureRecognizer = GestureRecognizer(
            config = gestureConfig,
            logger = { Log.d(TAG, it) },
            onNotificationShadePull = {
                Log.i(TAG, "Downward swipe detected -> Pulling down notification shade")
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            }
        ) { delta ->
            if (delta > 0) {
                brightnessController.increase(delta)
            } else {
                brightnessController.decrease(-delta)
            }

            if (preferences.showBrightnessIndicator) {
                val percent = (brightnessController.getBrightness() * 100).roundToInt()
                brightnessIndicator?.show(percent, preferences.indicatorDurationMs)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setupOverlay() {
        if (overlayView != null) {
            updateOverlayDimensions()
            return
        }

        val wm = windowManager ?: return
        val displayMetrics = resources.displayMetrics

        val heightPx = (displayMetrics.heightPixels * preferences.gestureZoneFraction)
            .toInt()
            .coerceAtLeast((24 * displayMetrics.density).toInt())

        overlayView = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener { _, event ->
                val displayWidth = resources.displayMetrics.widthPixels
                val displayHeight = resources.displayMetrics.heightPixels

                val normalizedX = (event.rawX / displayWidth).coerceIn(0f, 1f)
                val normalizedY = (event.rawY / displayHeight).coerceIn(0f, 1f)
                val timestamp = event.eventTime

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        gestureRecognizer.onTouchEvent(TouchEvent.Down(0, normalizedX, normalizedY, timestamp))
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        gestureRecognizer.onTouchEvent(TouchEvent.Move(0, normalizedX, normalizedY, timestamp))
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        gestureRecognizer.onTouchEvent(TouchEvent.Up(0, timestamp))
                        true
                    }
                    else -> false
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        try {
            wm.addView(overlayView, params)
            _isOverlayActive.value = true
            Log.i(TAG, "Accessibility overlay attached (height: ${heightPx}px)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach accessibility overlay", e)
            _isOverlayActive.value = false
        }
    }

    fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                Log.i(TAG, "Accessibility overlay removed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove accessibility overlay", e)
            }
            overlayView = null
        }
        _isOverlayActive.value = false
    }

    fun setOverlayEnabled(enabled: Boolean) {
        if (enabled) {
            updateGestureRecognizer()
            setupOverlay()
        } else {
            removeOverlay()
        }
    }

    fun updateOverlayDimensions() {
        val view = overlayView ?: return
        val wm = windowManager ?: return
        val displayMetrics = resources.displayMetrics

        val heightPx = (displayMetrics.heightPixels * preferences.gestureZoneFraction)
            .toInt()
            .coerceAtLeast((24 * displayMetrics.density).toInt())

        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.height = heightPx

        try {
            wm.updateViewLayout(view, params)
            Log.i(TAG, "Accessibility overlay layout updated (new height: ${heightPx}px)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update overlay view layout", e)
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "StatusSwipeAccessibilityService destroying")
        removeOverlay()
        brightnessIndicator?.destroy()
        brightnessIndicator = null
        _isRunning.value = false
        instance = null
        super.onDestroy()
    }
}
