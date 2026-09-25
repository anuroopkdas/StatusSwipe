package com.statusswipe.app.brightness

data class BrightnessConfig(
    val disableAdaptiveOnGesture: Boolean = true,
    val minBrightness: Float = 0.01f,
    val gamma: Float = 2.2f,
)
