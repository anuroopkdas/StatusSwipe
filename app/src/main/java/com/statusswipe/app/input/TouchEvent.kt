package com.statusswipe.app.input

sealed class TouchEvent {
    abstract val slot: Int
    abstract val timestamp: Long
    
    data class Down(override val slot: Int, val x: Float, val y: Float, override val timestamp: Long) : TouchEvent()
    data class Move(override val slot: Int, val x: Float, val y: Float, override val timestamp: Long) : TouchEvent()
    data class Up(override val slot: Int, override val timestamp: Long) : TouchEvent()
}
