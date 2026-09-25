package com.statusswipe.app.capability

import android.os.Build

data class DeviceInfo(
    val androidVersion: Int = Build.VERSION.SDK_INT,
    val manufacturer: String = Build.MANUFACTURER,
    val model: String = Build.MODEL,
    val buildDisplay: String = Build.DISPLAY,
    val isRooted: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val selinuxStatus: String = "unknown",
    val canWriteSettings: Boolean = false,
    val displayWidth: Int = 0,
    val displayHeight: Int = 0,
    val displayDensity: Float = 0f,
    val touchDevicePath: String? = null,
    val touchDeviceName: String? = null,
)

data class CapabilityReport(
    val deviceInfo: DeviceInfo,
    val rootAvailable: Boolean,
    val accessibilityAvailable: Boolean = false,
    val inputObserverAvailable: Boolean,
    val brightnessControlAvailable: Boolean,
    val errors: List<String> = emptyList(),
)
