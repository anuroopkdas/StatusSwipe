package com.statusswipe.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.statusswipe.app.brightness.BrightnessIndicatorView
import com.statusswipe.app.capability.CapabilityDetector
import com.statusswipe.app.service.GestureService
import com.statusswipe.app.service.StatusSwipeAccessibilityService
import com.statusswipe.app.util.Preferences
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onNavigateToDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { Preferences(context) }
    val detector = remember { CapabilityDetector(context) }

    val isRootRunning by GestureService.isRunning.collectAsState()
    val isA11yServiceRunning by StatusSwipeAccessibilityService.isRunning.collectAsState()
    val isA11yOverlayActive by StatusSwipeAccessibilityService.isOverlayActive.collectAsState()

    var isRooted by remember { mutableStateOf(false) }
    var isA11yEnabledInSystem by remember { mutableStateOf(detector.isAccessibilityServiceEnabled()) }
    var inputMode by remember { mutableStateOf(preferences.inputMode) }

    LaunchedEffect(Unit) {
        val report = detector.detect()
        isRooted = report.rootAvailable
        isA11yEnabledInSystem = report.accessibilityAvailable
        while (true) {
            delay(1000)
            isA11yEnabledInSystem = detector.isAccessibilityServiceEnabled()
        }
    }

    val isUsingRoot = inputMode == "root" || (inputMode == "auto" && isRooted)
    val isRunning = if (isUsingRoot) isRootRunning else (isA11yServiceRunning && isA11yOverlayActive)

    fun updateActiveService() {
        if (!isRunning) return
        if (isUsingRoot) {
            GestureService.start(context)
        } else {
            StatusSwipeAccessibilityService.instance?.updateGestureRecognizer()
            StatusSwipeAccessibilityService.instance?.updateOverlayDimensions()
        }
    }

    var sensitivity by remember { mutableFloatStateOf(preferences.sensitivity) }
    var gestureZone by remember { mutableFloatStateOf(preferences.gestureZoneFraction) }
    var showIndicator by remember { mutableStateOf(preferences.showBrightnessIndicator) }
    var invertDirection by remember { mutableStateOf(preferences.invertDirection) }
    var disableAdaptive by remember { mutableStateOf(preferences.disableAdaptiveOnGesture) }

    val displayMetrics = context.resources.displayMetrics
    val screenHeightPx = displayMetrics.heightPixels
    val currentZonePx = (screenHeightPx * gestureZone).roundToInt()

    // Minimal palette: Deep clean dark surfaces
    val bg = Color(0xFF0C0C0E)
    val cardBg = Color(0xFF18181B)
    val cardBorder = Color(0xFF242428)
    val textPrimary = Color(0xFFF4F4F5)
    val textSecondary = Color(0xFFA1A1AA)
    val textMuted = Color(0xFF71717A)
    val accent = Color(0xFFE4E4E7) // Clean neutral white/silver accent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Large Title
        Text(
            text = "StatusSwipe",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp
            ),
            color = textPrimary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // --- Master Switch Bar (Android Settings Style) ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = if (isRunning) Color(0xFF27272A) else cardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use StatusSwipe",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRunning) "Swipe top edge to adjust brightness" else "Disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isRunning) textSecondary else textMuted
                    )
                }

                Switch(
                    checked = isRunning,
                    onCheckedChange = { enabled ->
                        preferences.gestureEnabled = enabled
                        if (isUsingRoot) {
                            if (enabled) {
                                GestureService.start(context)
                            } else {
                                GestureService.stop(context)
                            }
                        } else {
                            if (isA11yServiceRunning) {
                                StatusSwipeAccessibilityService.instance?.setOverlayEnabled(enabled)
                            } else if (enabled) {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            }
                        }
                    }
                )
            }
        }

        // Warning banner if non-root and accessibility service is not enabled
        if (!isUsingRoot && !isA11yEnabledInSystem) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    },
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF2E1A1A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7F1D1D))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accessibility service required",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFFFCA5A5)
                        )
                        Text(
                            text = "Tap to enable StatusSwipe in Accessibility settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF87171)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // --- SECTION: GESTURE ---
        SectionHeader("GESTURE")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = cardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // 1. Gesture Zone Slider with Preview
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Gesture zone",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = textPrimary
                            )
                            Text(
                                text = "Swipe area from top edge",
                                style = MaterialTheme.typography.bodySmall,
                                color = textSecondary
                            )
                        }

                        Text(
                            text = "$currentZonePx px (${(gestureZone * 100).roundToInt()}%)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Normal
                            ),
                            color = textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = gestureZone,
                        onValueChange = {
                            gestureZone = it
                            // Live minimal overlay preview across top edge
                            GestureZonePreviewOverlay.updateZone(context, it, isDragging = true)
                        },
                        onValueChangeFinished = {
                            preferences.gestureZoneFraction = gestureZone
                            updateActiveService()
                            GestureZonePreviewOverlay.updateZone(context, gestureZone, isDragging = false)
                        },
                        valueRange = 0.02f..0.10f
                    )
                }

                HorizontalDivider(color = cardBorder)

                // 2. Sensitivity Slider
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Sensitivity",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = textPrimary
                            )
                            Text(
                                text = "Rate of adjustment",
                                style = MaterialTheme.typography.bodySmall,
                                color = textSecondary
                            )
                        }

                        Text(
                            text = String.format("%.1f×", sensitivity),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Normal
                            ),
                            color = textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = sensitivity,
                        onValueChange = { sensitivity = it },
                        onValueChangeFinished = {
                            preferences.sensitivity = sensitivity
                            updateActiveService()
                        },
                        valueRange = 0.5f..2.5f
                    )
                }

                HorizontalDivider(color = cardBorder)

                // 3. Invert direction switch
                MinimalSwitchRow(
                    title = "Invert direction",
                    subtitle = if (invertDirection) "Swipe left to increase" else "Swipe right to increase",
                    checked = invertDirection,
                    onCheckedChange = {
                        invertDirection = it
                        preferences.invertDirection = it
                        updateActiveService()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // --- SECTION: FEEDBACK ---
        SectionHeader("FEEDBACK")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = cardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                // Brightness pill switch
                MinimalSwitchRow(
                    title = "Brightness indicator",
                    subtitle = "Show percentage popup while swiping",
                    checked = showIndicator,
                    onCheckedChange = {
                        showIndicator = it
                        preferences.showBrightnessIndicator = it
                        if (it) BrightnessIndicatorView.showPreview(context, 65)
                    }
                )

                HorizontalDivider(color = cardBorder)

                // Adaptive override switch
                MinimalSwitchRow(
                    title = "Lock to manual on gesture",
                    subtitle = "Disables auto-brightness when adjusting",
                    checked = disableAdaptive,
                    onCheckedChange = {
                        disableAdaptive = it
                        preferences.disableAdaptiveOnGesture = it
                        updateActiveService()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // --- SECTION: SYSTEM ---
        SectionHeader("SYSTEM")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = cardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                // Operation mode row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isRooted) {
                            // Cycle between Auto -> Root -> Accessibility if rooted
                            val nextMode = when (inputMode) {
                                "auto" -> "root"
                                "root" -> "accessibility"
                                else -> "auto"
                            }
                            inputMode = nextMode
                            preferences.inputMode = nextMode
                            if (isRunning) {
                                GestureService.stop(context)
                                StatusSwipeAccessibilityService.instance?.setOverlayEnabled(false)
                                if (nextMode == "root" || (nextMode == "auto" && isRooted)) {
                                    GestureService.start(context)
                                } else {
                                    StatusSwipeAccessibilityService.instance?.setOverlayEnabled(true)
                                }
                            }
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Touch detection mode",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = textPrimary
                        )
                        Text(
                            text = when {
                                isUsingRoot -> "Root (Kernel evdev reader)"
                                isA11yServiceRunning -> "Accessibility overlay (Non-Root)"
                                else -> "Accessibility service (Disabled in Settings)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecondary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isUsingRoot) Color(0xFF1E293B) else Color(0xFF1C2A1E),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isUsingRoot) Color(0xFF334155) else Color(0xFF2E4D34)
                        )
                    ) {
                        Text(
                            text = if (isUsingRoot) "ROOT" else "NON-ROOT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = if (isUsingRoot) Color(0xFF93C5FD) else Color(0xFF86EFAC),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = cardBorder)

                // Diagnostics row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToDiagnostics)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Diagnostics",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = textPrimary
                        )
                        Text(
                            text = "Touchscreen, root & accessibility status",
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecondary
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp
        ),
        color = Color(0xFF71717A),
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
    )
}

@Composable
private fun MinimalSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFFF4F4F5)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA1A1AA)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
