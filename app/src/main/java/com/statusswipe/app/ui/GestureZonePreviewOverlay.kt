package com.statusswipe.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.statusswipe.app.R
import kotlin.math.roundToInt

/**
 * Minimal on-screen preview of the gesture zone.
 * Shows a subtle, clean translucent overlay at the top edge with a 1px boundary line.
 */
class GestureZonePreviewOverlay(private val context: Context) {

    companion object {
        private const val TAG = "StatusSwipe.ZonePreview"
        private var instance: GestureZonePreviewOverlay? = null

        fun updateZone(context: Context, zoneFraction: Float, isDragging: Boolean) {
            val overlay = instance ?: GestureZonePreviewOverlay(context.applicationContext).also {
                instance = it
            }
            overlay.show(zoneFraction, isDragging)
        }

        fun dismiss() {
            instance?.hide()
        }
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rootView: FrameLayout? = null
    private var labelView: TextView? = null
    private var isShowing = false

    private val autoDismissRunnable = Runnable {
        hide()
    }

    @SuppressLint("SetTextI18n")
    fun show(zoneFraction: Float, isDragging: Boolean) {
        if (!Settings.canDrawOverlays(context)) {
            return
        }

        mainHandler.post {
            ensureView()

            val displayMetrics = context.resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val zoneHeightPx = (screenHeight * zoneFraction).roundToInt().coerceAtLeast(10)
            val percent = (zoneFraction * 100).roundToInt()

            labelView?.text = "$zoneHeightPx px ($percent%)"

            val params = rootView?.layoutParams as? WindowManager.LayoutParams
            if (params != null) {
                params.height = zoneHeightPx
                try {
                    windowManager.updateViewLayout(rootView, params)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update overlay height", e)
                }
            }

            if (!isShowing && rootView != null) {
                rootView?.apply {
                    visibility = View.VISIBLE
                    alpha = 0f
                    animate().alpha(1.0f).setDuration(80L).start()
                }
                isShowing = true
            }

            mainHandler.removeCallbacks(autoDismissRunnable)
            if (!isDragging) {
                mainHandler.postDelayed(autoDismissRunnable, 1200L)
            }
        }
    }

    fun hide() {
        mainHandler.post {
            if (isShowing && rootView != null) {
                rootView?.animate()
                    ?.alpha(0f)
                    ?.setDuration(150L)
                    ?.withEndAction {
                        rootView?.visibility = View.GONE
                        isShowing = false
                    }
                    ?.start()
            }
        }
    }

    private fun ensureView() {
        if (rootView != null) return

        val density = context.resources.displayMetrics.density

        // Subtle translucent fill
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#1AFF1A1A")) // Semi-transparent Nothing Red
        }

        rootView = FrameLayout(context).apply {
            this.background = background
            visibility = View.GONE
            alpha = 0f
        }

        // Boundary line at bottom
        val line = View(context).apply {
            setBackgroundColor(Color.parseColor("#66FF1A1A")) // 40% opacity Nothing Red
            val lineParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (0.5f * density).coerceAtLeast(1f).toInt() // 1px or closest
            ).apply {
                gravity = Gravity.BOTTOM
            }
            layoutParams = lineParams
        }
        rootView?.addView(line)

        // Minimal label pill
        val labelBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 50 * density
            setColor(Color.parseColor("#000000")) // True black
            setStroke((0.5f * density).coerceAtLeast(1f).toInt(), Color.parseColor("#3B3B3B"))
        }

        labelView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = ResourcesCompat.getFont(context, R.font.ndot) ?: Typeface.MONOSPACE
            gravity = Gravity.CENTER
            this.background = labelBg
            setPadding((10 * density).toInt(), (3 * density).toInt(), (10 * density).toInt(), (3 * density).toInt())

            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (4 * density).toInt()
            }
            layoutParams = lp
        }
        rootView?.addView(labelView)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (100 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            windowManager.addView(rootView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach zone preview overlay", e)
            rootView = null
        }
    }

    fun destroy() {
        mainHandler.removeCallbacks(autoDismissRunnable)
        mainHandler.post {
            if (rootView != null) {
                try {
                    windowManager.removeView(rootView)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing overlay", e)
                }
                rootView = null
                isShowing = false
            }
        }
    }
}
