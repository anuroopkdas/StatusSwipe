package com.statusswipe.app.gesture

sealed class GestureState {
    data object Idle : GestureState()
    data class TouchDown(val startX: Float, val startY: Float, val startTime: Long) : GestureState()
    data class Tracking(val startX: Float, val startY: Float, val startTime: Long, val lastX: Float, val lastY: Float) : GestureState()
    data class BrightnessActive(val startX: Float, val lastX: Float, val lastBrightnessDelta: Float) : GestureState()
    data object Cancelled : GestureState()
}
