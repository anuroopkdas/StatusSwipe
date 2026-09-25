package com.statusswipe.app.gesture

import com.statusswipe.app.input.TouchEvent
import kotlin.math.abs
import kotlin.math.sqrt

class GestureRecognizer(
    private val config: GestureConfig,
    private val logger: ((String) -> Unit)? = null,
    private val onNotificationShadePull: (() -> Unit)? = null,
    private val onBrightnessChange: (Float) -> Unit
) {
    constructor(config: GestureConfig, onBrightnessChange: (Float) -> Unit) : this(config, null, null, onBrightnessChange)
    constructor(config: GestureConfig, logger: ((String) -> Unit)?, onBrightnessChange: (Float) -> Unit) : this(config, logger, null, onBrightnessChange)

    var currentState: GestureState = GestureState.Idle
        private set

    private fun log(msg: String) {
        logger?.invoke(msg)
    }

    fun onTouchEvent(event: TouchEvent) {
        val state = currentState
        
        // Timeout check for TouchDown
        if (state is GestureState.TouchDown) {
            if (event.timestamp - state.startTime > config.gestureTimeoutMs) {
                log("Gesture timed out -> Cancelled")
                currentState = GestureState.Cancelled
                return
            }
        }
        
        when (state) {
            is GestureState.Idle -> {
                if (event is TouchEvent.Down && event.slot == 0) {
                    if (event.y <= config.topGestureZoneFraction) {
                        log("TouchDown in zone: y=${event.y} <= ${config.topGestureZoneFraction}")
                        currentState = GestureState.TouchDown(event.x, event.y, event.timestamp)
                    }
                }
            }
            is GestureState.TouchDown -> {
                when (event) {
                    is TouchEvent.Move -> {
                        if (event.slot == 0) {
                            val dx = event.x - state.startX
                            val dy = event.y - state.startY
                            val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                            // Check if clearly vertical (Notification shade pull down)
                            if (dy > 0.02f && dy > abs(dx) * 1.2f) {
                                log("Vertical downward gesture (dx=$dx, dy=$dy) -> Cancelled")
                                currentState = GestureState.Cancelled
                                onNotificationShadePull?.invoke()
                                return
                            }

                            // Check if diagonal (dx and dy approximately equal)
                            if (distance >= config.movementThresholdFraction && abs(abs(dx) - abs(dy)) < 0.005f) {
                                log("Diagonal gesture (dx=$dx, dy=$dy) -> Cancelled")
                                currentState = GestureState.Cancelled
                                return
                            }

                            // Check if horizontal threshold reached
                            if (abs(dx) >= config.movementThresholdFraction && abs(dx) > abs(dy) * 1.0f) {
                                log("Horizontal gesture active (dx=$dx, dy=$dy)")
                                currentState = GestureState.BrightnessActive(state.startX, event.x, 0f)
                            } else if (distance > 0.06f) {
                                log("Distance exceeded without horizontal intent -> Cancelled")
                                currentState = GestureState.Cancelled
                            }
                        }
                    }
                    is TouchEvent.Down -> {
                        log("Multitouch detected -> Cancelled")
                        currentState = GestureState.Cancelled
                    }
                    is TouchEvent.Up -> {
                        if (event.slot == 0) {
                            currentState = GestureState.Idle
                        }
                    }
                }
            }
            is GestureState.Tracking -> {}
            is GestureState.BrightnessActive -> {
                when (event) {
                    is TouchEvent.Move -> {
                        if (event.slot == 0) {
                            val sign = if (config.invertDirection) -1f else 1f
                            val delta = (event.x - state.lastX) * config.sensitivityFactor * sign
                            if (abs(delta) > 0.0001f) {
                                onBrightnessChange(delta)
                                currentState = GestureState.BrightnessActive(state.startX, event.x, delta)
                            }
                        }
                    }
                    is TouchEvent.Down -> {
                        log("Multitouch in active -> Cancelled")
                        currentState = GestureState.Cancelled
                    }
                    is TouchEvent.Up -> {
                        if (event.slot == 0) {
                            log("Touch up -> Idle")
                            currentState = GestureState.Idle
                        }
                    }
                }
            }
            is GestureState.Cancelled -> {
                if (event is TouchEvent.Up && event.slot == 0) {
                    currentState = GestureState.Idle
                }
            }
        }
    }

    fun reset() {
        currentState = GestureState.Idle
    }
}
