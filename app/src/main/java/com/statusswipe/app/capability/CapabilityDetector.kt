package com.statusswipe.app.capability

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityManager
import com.statusswipe.app.service.StatusSwipeAccessibilityService
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CapabilityDetector(private val context: Context) {

    fun isAccessibilityServiceEnabled(): Boolean {
        if (StatusSwipeAccessibilityService.isRunning.value || StatusSwipeAccessibilityService.instance != null) {
            return true
        }

        try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val myPackage = context.packageName
            val myService = StatusSwipeAccessibilityService::class.java.name
            if (enabledServices.contains("$myPackage/$myService") ||
                enabledServices.contains("$myPackage/.service.StatusSwipeAccessibilityService")) {
                return true
            }
        } catch (_: Exception) {}

        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val myPackage = context.packageName
        val myService = StatusSwipeAccessibilityService::class.java.name
        return enabledServices.any {
            val si = it.resolveInfo?.serviceInfo
            si != null && si.packageName == myPackage && (si.name == myService || si.name.endsWith("StatusSwipeAccessibilityService"))
        }
    }

    suspend fun detect(): CapabilityReport = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        // 1. Root
        val isRooted = Shell.isAppGrantedRoot() == true

        // 2. Accessibility Service (Non-Root Input)
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()

        // 3. SELinux
        var selinuxStatus = "unknown"
        if (isRooted) {
            val result = Shell.cmd("getenforce").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                selinuxStatus = result.out.first()
            } else {
                errors.add("Failed to get SELinux status")
            }
        }

        // 4. Write Settings
        val canWriteSettings = Settings.System.canWrite(context)

        // 5. Draw Overlays (for HUD indicator)
        val canDrawOverlays = Settings.canDrawOverlays(context)

        // 6. Display info
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = context.resources.displayMetrics
        
        val displayWidth = metrics.widthPixels
        val displayHeight = metrics.heightPixels
        val displayDensity = metrics.density

        val deviceInfo = DeviceInfo(
            isRooted = isRooted,
            isAccessibilityEnabled = isAccessibilityEnabled,
            selinuxStatus = selinuxStatus,
            canWriteSettings = canWriteSettings,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            displayDensity = displayDensity
        )

        CapabilityReport(
            deviceInfo = deviceInfo,
            rootAvailable = isRooted,
            accessibilityAvailable = isAccessibilityEnabled,
            inputObserverAvailable = isRooted || isAccessibilityEnabled,
            brightnessControlAvailable = canWriteSettings,
            errors = errors
        )
    }
}
