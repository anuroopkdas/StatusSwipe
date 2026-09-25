package com.statusswipe.app.gesture

data class GestureConfig(
    val topGestureZoneFraction: Float = 0.04f,
    val movementThresholdFraction: Float = 0.02f,
    val horizontalDominanceRatio: Float = 1.5f,
    val sensitivityFactor: Float = 1.0f,
    val invertDirection: Boolean = false,
    val gestureTimeoutMs: Long = 2000L
)
