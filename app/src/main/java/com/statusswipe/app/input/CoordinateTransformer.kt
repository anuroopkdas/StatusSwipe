package com.statusswipe.app.input

import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import java.util.concurrent.atomic.AtomicInteger

class CoordinateTransformer(private val displayManager: DisplayManager) {

    private val currentRotation = AtomicInteger(Surface.ROTATION_0)

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            val display = displayManager.getDisplay(displayId)
            if (display != null && display.displayId == Display.DEFAULT_DISPLAY) {
                currentRotation.set(display.rotation)
            }
        }
    }

    init {
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            currentRotation.set(display.rotation)
        }
        displayManager.registerDisplayListener(displayListener, null)
    }

    fun transform(rawX: Float, rawY: Float): Pair<Float, Float> {
        return transformRotation(rawX, rawY, currentRotation.get())
    }

    fun destroy() {
        displayManager.unregisterDisplayListener(displayListener)
    }

    companion object {
        fun transformRotation(rawX: Float, rawY: Float, rotation: Int): Pair<Float, Float> {
            return when (rotation) {
                Surface.ROTATION_0 -> Pair(rawX, rawY)
                Surface.ROTATION_90 -> Pair(rawY, 1.0f - rawX)
                Surface.ROTATION_180 -> Pair(1.0f - rawX, 1.0f - rawY)
                Surface.ROTATION_270 -> Pair(1.0f - rawY, rawX)
                else -> Pair(rawX, rawY)
            }
        }
    }
}
