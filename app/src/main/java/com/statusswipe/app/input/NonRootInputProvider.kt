package com.statusswipe.app.input

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Minimal non-root fallback using a thin overlay strip at the top of the screen.
 *
 * LIMITATIONS (inherent to the non-root approach):
 * - Does NOT work in fullscreen/immersive apps (overlay is hidden)
 * - May interfere with notification shade on some devices
 * - Cannot observe touches over other apps that have their own overlays
 * - The overlay strip itself consumes touches in its narrow region
 *
 * ROOT MODE IS STRONGLY PREFERRED for reliable, non-invasive operation.
 * This fallback exists only to provide basic functionality on non-rooted devices.
 */
class NonRootInputProvider(
    private val context: Context,
) : InputProvider {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var touchCallback: ((TouchEvent) -> Unit)? = null

    override var isActive: Boolean = false
        private set

    @SuppressLint("ClickableViewAccessibility")
    override fun start(callback: (TouchEvent) -> Unit) {
        if (overlayView != null) return

        touchCallback = callback
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        overlayView = View(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener { _, event ->
                val normalizedX = event.rawX / context.resources.displayMetrics.widthPixels
                val normalizedY = event.rawY / context.resources.displayMetrics.heightPixels
                val timestamp = event.eventTime

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchCallback?.invoke(TouchEvent.Down(0, normalizedX, normalizedY, timestamp))
                    }
                    MotionEvent.ACTION_MOVE -> {
                        touchCallback?.invoke(TouchEvent.Move(0, normalizedX, normalizedY, timestamp))
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        touchCallback?.invoke(TouchEvent.Up(0, timestamp))
                    }
                }
                // Return false to allow the event to pass through where possible
                false
            }
        }

        // 6dp height — minimal strip at top edge
        val heightPx = (6 * context.resources.displayMetrics.density).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        try {
            windowManager?.addView(overlayView, params)
            isActive = true
        } catch (e: Exception) {
            android.util.Log.e("StatusSwipe", "Failed to add overlay", e)
            isActive = false
        }
    }

    override fun stop() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                android.util.Log.e("StatusSwipe", "Failed to remove overlay", e)
            }
            overlayView = null
        }
        touchCallback = null
        isActive = false
    }
}
