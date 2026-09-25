package com.statusswipe.app.brightness

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
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.statusswipe.app.R
import kotlin.math.roundToInt

/**
 * Ultra-minimal brightness indicator popup.
 *
 * Appears briefly near the top of the screen: a tiny, clean, translucent pill showing "☀ 65%".
 * Never steals touches (FLAG_NOT_TOUCHABLE).
 */
class BrightnessIndicatorView(private val context: Context) {

    companion object {
        private const val TAG = "StatusSwipe.HUD"
        private var activeInstance: BrightnessIndicatorView? = null

        fun showPreview(context: Context, percent: Int) {
            val instance = activeInstance ?: BrightnessIndicatorView(context.applicationContext).also {
                activeInstance = it
            }
            instance.show(percent, 800L)
        }
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var textView: TextView? = null
    private var isShowing = false

    private val hideRunnable = Runnable {
        hide()
    }

    @SuppressLint("SetTextI18n")
    fun show(brightnessPercent: Int, durationMs: Long = 650L) {
        if (!Settings.canDrawOverlays(context)) {
            return
        }

        val clamped = brightnessPercent.coerceIn(0, 100)

        mainHandler.post {
            ensureView()

            textView?.text = "$clamped%"

            if (!isShowing && textView != null) {
                textView?.apply {
                    visibility = View.VISIBLE
                    alpha = 0f
                    animate()
                        .alpha(1.0f)
                        .setDuration(100L)
                        .start()
                }
                isShowing = true
            }

            mainHandler.removeCallbacks(hideRunnable)
            mainHandler.postDelayed(hideRunnable, durationMs)
        }
    }

    fun hide() {
        mainHandler.post {
            if (isShowing && textView != null) {
                textView?.animate()
                    ?.alpha(0.0f)
                    ?.setDuration(180L)
                    ?.withEndAction {
                        textView?.visibility = View.GONE
                        isShowing = false
                    }
                    ?.start()
            }
        }
    }

    private fun ensureView() {
        if (textView != null) return

        val density = context.resources.displayMetrics.density

        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 50f * density
            setColor(Color.parseColor("#E6000000")) // True black at 90% opacity
            setStroke((0.5f * density).coerceAtLeast(1f).toInt(), Color.parseColor("#3B3B3B"))
        }

        textView = TextView(context).apply {
            this.background = background
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = ResourcesCompat.getFont(context, R.font.ndot) ?: Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(
                (14 * density).toInt(),
                (6 * density).toInt(),
                (14 * density).toInt(),
                (6 * density).toInt()
            )
            visibility = View.GONE
            alpha = 0f
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (32 * density).toInt() // Just below notch / camera
        }

        try {
            windowManager.addView(textView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding minimal indicator view", e)
            textView = null
        }
    }

    fun destroy() {
        mainHandler.removeCallbacks(hideRunnable)
        mainHandler.post {
            if (textView != null) {
                try {
                    windowManager.removeView(textView)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing indicator view", e)
                }
                textView = null
                isShowing = false
            }
        }
    }
}
