package com.statusswipe.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.statusswipe.app.ui.theme.NDotFamily
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

    // Nothing OS Color Palette
    val bg = Color.Black
    val cardBg = Color(0xFF1D1E20)
    val cardBorder = Color(0xFF3B3B3B)
    val textPrimary = Color.White
    val textSecondary = Color(0xFFA3A3A3)
    val textMuted = Color(0xFF666666)
    val accent = Color(0xFFFF1A1A)

    val customSwitchColors = SwitchDefaults.colors(
        checkedThumbColor = accent,
        checkedTrackColor = Color.Transparent,
        checkedBorderColor = accent,
        uncheckedThumbColor = textSecondary,
        uncheckedTrackColor = Color.Transparent,
        uncheckedBorderColor = cardBorder
    )

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

        // App Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = "STATUS SWIPE",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 2.sp,
                    fontFamily = NDotFamily,
                    fontSize = 26.sp
                ),
                color = textPrimary
            )
            if (isRunning) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(accent, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- Master Switch Bar ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardBg,
            border = BorderStroke(0.5.dp, cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use StatusSwipe",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isRunning) "Swipe status bar to adjust brightness" else "Disabled",
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
                    },
                    colors = customSwitchColors
                )
            }
        }

        // Warning banner
        if (!isUsingRoot && !isA11yEnabledInSystem) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    },
                shape = RoundedCornerShape(16.dp),
                color = Color(0x33FF1A1A),
                border = BorderStroke(1.dp, accent)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accessibility service required",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = accent
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap to enable StatusSwipe in Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = textPrimary
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- SECTION: GESTURE ---
        SectionHeader("GESTURE")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardBg,
            border = BorderStroke(0.5.dp, cardBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                // 1. Gesture Zone Slider
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Gesture zone",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
                                color = textPrimary
                            )
                            Text(
                                text = "Swipe area from top edge",
                                style = MaterialTheme.typography.bodySmall,
                                color = textSecondary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF333333)
                        ) {
                            Text(
                                text = "$currentZonePx px",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = NDotFamily,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 1.sp
                                ),
                                color = textPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    NothingSlider(
                        value = gestureZone,
                        onValueChange = {
                            gestureZone = it
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

                HorizontalDivider(color = cardBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

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
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
                                color = textPrimary
                            )
                            Text(
                                text = "Rate of adjustment",
                                style = MaterialTheme.typography.bodySmall,
                                color = textSecondary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF333333)
                        ) {
                            Text(
                                text = String.format("%.1fx", sensitivity),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = NDotFamily,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 1.sp
                                ),
                                color = textPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    NothingSlider(
                        value = sensitivity,
                        onValueChange = { sensitivity = it },
                        onValueChangeFinished = {
                            preferences.sensitivity = sensitivity
                            updateActiveService()
                        },
                        valueRange = 0.5f..2.5f
                    )
                }

                HorizontalDivider(color = cardBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                // 3. Invert direction switch
                MinimalSwitchRow(
                    title = "Invert direction",
                    subtitle = if (invertDirection) "Swipe left to increase" else "Swipe right to increase",
                    checked = invertDirection,
                    onCheckedChange = {
                        invertDirection = it
                        preferences.invertDirection = it
                        updateActiveService()
                    },
                    switchColors = customSwitchColors
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- SECTION: FEEDBACK ---
        SectionHeader("FEEDBACK")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardBg,
            border = BorderStroke(0.5.dp, cardBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // Brightness pill switch
                MinimalSwitchRow(
                    title = "Brightness indicator",
                    subtitle = "Show percentage popup while swiping",
                    checked = showIndicator,
                    onCheckedChange = {
                        showIndicator = it
                        preferences.showBrightnessIndicator = it
                        if (it) BrightnessIndicatorView.showPreview(context, 65)
                    },
                    switchColors = customSwitchColors
                )

                HorizontalDivider(color = cardBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                // Adaptive override switch
                MinimalSwitchRow(
                    title = "Lock to manual",
                    subtitle = "Disables auto-brightness when adjusting",
                    checked = disableAdaptive,
                    onCheckedChange = {
                        disableAdaptive = it
                        preferences.disableAdaptiveOnGesture = it
                        updateActiveService()
                    },
                    switchColors = customSwitchColors
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- SECTION: SYSTEM ---
        SectionHeader("SYSTEM")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardBg,
            border = BorderStroke(0.5.dp, cardBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // Operation mode row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isRooted) {
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
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
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
                        shape = RoundedCornerShape(50),
                        color = if (isUsingRoot) accent else Color(0xFF333333)
                    ) {
                        Text(
                            text = if (isUsingRoot) "ROOT" else "NON-ROOT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 1.5.sp,
                                fontFamily = NDotFamily
                            ),
                            color = if (isUsingRoot) Color.Black else textPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = cardBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

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
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
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
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = 2.sp,
            fontFamily = NDotFamily
        ),
        color = Color(0xFFA3A3A3),
        modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)
    )
}

@Composable
private fun MinimalSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchColors: SwitchColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA3A3A3)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = switchColors
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NothingSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFFFF1A1A)
    val inactiveTrackColor = Color(0xFF27272A)

    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = modifier,
        thumb = {
            // Nothing Phone hardware tactile dial puck
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(accent, CircleShape)
                    .border(2.dp, Color.Black, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Center pin dot
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(Color.White.copy(alpha = 0.9f), CircleShape)
                )
            }
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(6.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor = accent,
                    inactiveTrackColor = inactiveTrackColor
                ),
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp
            )
        }
    )
}
