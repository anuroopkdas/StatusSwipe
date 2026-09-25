package com.statusswipe.app.brightness

interface BrightnessController {
    fun getBrightness(): Float
    fun setBrightness(value: Float)
    fun increase(delta: Float)
    fun decrease(delta: Float)
    fun isAdaptiveBrightnessEnabled(): Boolean
    fun setAdaptiveBrightness(enabled: Boolean)
}
