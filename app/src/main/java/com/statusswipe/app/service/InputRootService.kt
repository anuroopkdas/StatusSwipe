package com.statusswipe.app.service

import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.statusswipe.app.IInputCallback
import com.statusswipe.app.IInputService
import com.topjohnwu.superuser.ipc.RootService

/**
 * Root-privileged service that reads touchscreen events from /dev/input/event*.
 *
 * Runs as a root daemon via libsu's RootService. Uses JNI to call into the native
 * C++ evdev reader, which passively observes touch events without consuming them.
 * Events are forwarded to the app's GestureService via AIDL Binder IPC.
 *
 * IMPORTANT: This service NEVER uses EVIOCGRAB. It is a purely passive observer.
 * Android's InputFlinger continues to receive all events normally through its own
 * file descriptor on the same /dev/input/event* node.
 */
class InputRootService : RootService() {

    companion object {
        private const val TAG = "StatusSwipe.Root"

        init {
            System.loadLibrary("evdev_reader")
        }
    }

    @Volatile
    private var callback: IInputCallback? = null

    @Volatile
    private var observing = false

    // Native methods — implemented in evdev_jni.cpp
    private external fun nativeDiscoverTouchDevice(): String?
    private external fun nativeStartReading(devicePath: String)
    private external fun nativeStopReading()

    private val binder = object : IInputService.Stub() {

        override fun startObserving() {
            if (observing) {
                Log.w(TAG, "Already observing")
                return
            }

            Thread(Runnable {
                try {
                    Log.i(TAG, "Discovering touch device...")

                    val path = nativeDiscoverTouchDevice()
                    if (path.isNullOrEmpty()) {
                        Log.e(TAG, "No touchscreen device found")
                        try {
                            callback?.onError("No touchscreen device found")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending error callback", e)
                        }
                        return@Runnable
                    }

                    Log.i(TAG, "Touch device discovered: $path. Starting observer...")
                    observing = true

                    // Start the blocking read loop (this blocks until stopReading is called)
                    nativeStartReading(path)

                    observing = false
                    Log.i(TAG, "Stopped reading from $path")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in input observer", e)
                    observing = false
                    try {
                        callback?.onError("Input observer error: ${e.message}")
                    } catch (cbErr: Exception) {
                        Log.e(TAG, "Error sending error callback", cbErr)
                    }
                }
            }, "evdev-reader").start()
        }

        override fun stopObserving() {
            Log.i(TAG, "Stop observing requested")
            observing = false
            nativeStopReading()
        }

        override fun isObserving(): Boolean = observing

        override fun registerCallback(cb: IInputCallback?) {
            Log.i(TAG, "Callback registered: ${cb != null}")
            callback = cb
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.i(TAG, "Root service bound")
        return binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "Root service unbound")
        binder.stopObserving()
        callback = null
        return false
    }

    /**
     * Called from JNI (evdev_jni.cpp) on the evdev reader thread.
     * Bridges native touch events to the AIDL callback.
     *
     * @param slot   MT slot index (0 = primary finger)
     * @param action 0 = DOWN, 1 = MOVE, 2 = UP
     * @param x      Normalized X coordinate (0.0 - 1.0)
     * @param y      Normalized Y coordinate (0.0 - 1.0)
     * @param timestamp Event timestamp in milliseconds
     */
    @Suppress("unused") // Called from JNI
    fun onNativeTouchEvent(slot: Int, action: Int, x: Float, y: Float, timestamp: Long) {
        try {
            val cb = callback ?: return
            when (action) {
                0 -> cb.onTouchDown(slot, x, y, timestamp)
                1 -> cb.onTouchMove(slot, x, y, timestamp)
                2 -> cb.onTouchUp(slot, timestamp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error forwarding touch event", e)
        }
    }
}
