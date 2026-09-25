package com.statusswipe.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.util.Log
import com.statusswipe.app.IInputCallback
import com.statusswipe.app.IInputService
import com.statusswipe.app.MainActivity
import com.statusswipe.app.brightness.BrightnessIndicatorView
import com.statusswipe.app.brightness.SystemBrightnessController
import com.statusswipe.app.gesture.GestureConfig
import com.statusswipe.app.gesture.GestureRecognizer
import com.statusswipe.app.input.CoordinateTransformer
import com.statusswipe.app.input.TouchEvent
import com.statusswipe.app.util.Preferences
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Foreground service that orchestrates the brightness gesture pipeline.
 *
 * Architecture:
 *   InputRootService (root daemon) → AIDL IPC → GestureService (app process)
 *                                                     ↓
 *                                           CoordinateTransformer
 *                                                     ↓
 *                                            GestureRecognizer
 *                                                     ↓
 *                                          BrightnessController
 *                                                     ↓
 *                                           BrightnessIndicator
 */
class GestureService : Service() {

    companion object {
        private const val TAG = "StatusSwipe.Service"
        private const val CHANNEL_ID = "gesture_service"
        private const val NOTIFICATION_ID = 1

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            Log.i(TAG, "Starting GestureService")
            val intent = Intent(context, GestureService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            Log.i(TAG, "Stopping GestureService")
            context.stopService(Intent(context, GestureService::class.java))
        }
    }

    private var inputService: IInputService? = null
    private lateinit var coordinateTransformer: CoordinateTransformer
    private lateinit var gestureRecognizer: GestureRecognizer
    private lateinit var brightnessController: SystemBrightnessController
    private var brightnessIndicator: BrightnessIndicatorView? = null
    private lateinit var preferences: Preferences

    /**
     * AIDL callback that receives touch events from the root InputRootService.
     * Runs on the Binder thread pool — all processing must be thread-safe.
     */
    private val inputCallback = object : IInputCallback.Stub() {

        override fun onTouchDown(slot: Int, normalizedX: Float, normalizedY: Float, timestamp: Long) {
            processEvent(TouchEvent.Down(slot, normalizedX, normalizedY, timestamp))
        }

        override fun onTouchMove(slot: Int, normalizedX: Float, normalizedY: Float, timestamp: Long) {
            processEvent(TouchEvent.Move(slot, normalizedX, normalizedY, timestamp))
        }

        override fun onTouchUp(slot: Int, timestamp: Long) {
            processEvent(TouchEvent.Up(slot, timestamp))
        }

        override fun onDeviceInfo(path: String?, name: String?, minX: Int, maxX: Int, minY: Int, maxY: Int) {
            Log.i(TAG, "Touch device connected: $name ($path) X=$minX..$maxX Y=$minY..$maxY")
        }

        override fun onError(message: String?) {
            Log.e(TAG, "Input error from root service: $message")
        }
    }

    /**
     * Process a touch event through the full pipeline:
     * Raw coordinates → Rotation transform → Gesture recognition → Brightness change
     */
    private fun processEvent(event: TouchEvent) {
        // Step 1: Transform raw hardware coordinates to display coordinates
        val transformed = when (event) {
            is TouchEvent.Down -> {
                val (x, y) = coordinateTransformer.transform(event.x, event.y)
                TouchEvent.Down(event.slot, x, y, event.timestamp)
            }
            is TouchEvent.Move -> {
                val (x, y) = coordinateTransformer.transform(event.x, event.y)
                TouchEvent.Move(event.slot, x, y, event.timestamp)
            }
            is TouchEvent.Up -> event
        }

        // Step 2: Feed through gesture recognizer
        gestureRecognizer.onTouchEvent(transformed)
    }

    private val rootServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "Root service connected via Binder")
            inputService = IInputService.Stub.asInterface(service)
            try {
                inputService?.registerCallback(inputCallback)
                inputService?.startObserving()
                Log.i(TAG, "Registered callback and requested startObserving()")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start observing on root service", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Root service disconnected")
            inputService = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "GestureService created")

        preferences = Preferences(this)

        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        coordinateTransformer = CoordinateTransformer(displayManager)

        brightnessController = SystemBrightnessController(this, preferences.toBrightnessConfig())
        brightnessIndicator = BrightnessIndicatorView(this)

        updateGestureRecognizer()

        createNotificationChannel()
    }

    private fun updateGestureRecognizer() {
        val gestureConfig = GestureConfig(
            topGestureZoneFraction = preferences.gestureZoneFraction,
            movementThresholdFraction = 0.02f,
            horizontalDominanceRatio = 1.0f,
            sensitivityFactor = preferences.sensitivity,
            invertDirection = preferences.invertDirection,
        )
        gestureRecognizer = GestureRecognizer(
            config = gestureConfig,
            logger = { Log.d(TAG, it) }
        ) { delta ->
            if (delta > 0) {
                brightnessController.increase(delta)
            } else {
                brightnessController.decrease(-delta)
            }

            if (preferences.showBrightnessIndicator) {
                val percent = (brightnessController.getBrightness() * 100).roundToInt()
                brightnessIndicator?.show(percent, preferences.indicatorDurationMs)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateGestureRecognizer()

        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("StatusSwipe Active")
            .setContentText("Swipe top edge to adjust brightness")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        val bindIntent = Intent(this, InputRootService::class.java).apply {
            addCategory(RootService.CATEGORY_DAEMON_MODE)
        }
        Log.i(TAG, "Binding to InputRootService in daemon mode...")
        RootService.bind(bindIntent, rootServiceConnection)

        _isRunning.value = true
        Log.i(TAG, "GestureService is now running")

        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "GestureService destroying")
        try {
            inputService?.stopObserving()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping observation", e)
        }

        try {
            RootService.unbind(rootServiceConnection)
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding root service", e)
        }

        coordinateTransformer.destroy()
        brightnessIndicator?.destroy()
        brightnessIndicator = null
        _isRunning.value = false

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Gesture Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification for brightness gesture observation"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
