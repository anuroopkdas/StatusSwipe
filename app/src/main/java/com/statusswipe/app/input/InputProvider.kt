package com.statusswipe.app.input

interface InputProvider {
    fun start(callback: (TouchEvent) -> Unit)
    fun stop()
    val isActive: Boolean
}
