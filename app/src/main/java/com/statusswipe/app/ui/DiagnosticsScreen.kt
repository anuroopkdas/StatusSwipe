package com.statusswipe.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.statusswipe.app.ui.theme.NDotFamily
import com.statusswipe.app.capability.CapabilityDetector
import com.statusswipe.app.capability.CapabilityReport
import com.statusswipe.app.service.GestureService
import com.statusswipe.app.service.StatusSwipeAccessibilityService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val detector = remember { CapabilityDetector(context) }
    var report by remember { mutableStateOf<CapabilityReport?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val isRootRunning by GestureService.isRunning.collectAsState()
    val isA11yRunning by StatusSwipeAccessibilityService.isRunning.collectAsState()
    val isA11yOverlayActive by StatusSwipeAccessibilityService.isOverlayActive.collectAsState()
    val isRunning = isRootRunning || (isA11yRunning && isA11yOverlayActive)

    val bg = Color(0xFF000000)
    val cardBg = Color(0xFF1D1E20)
    val cardBorder = Color(0xFF3B3B3B)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFFA3A3A3)
    val nothingRed = Color(0xFFFF1A1A)

    LaunchedEffect(refreshTrigger) {
        isLoading = true
        report = detector.detect()
        isLoading = false
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bg,
                    titleContentColor = textPrimary,
                    navigationIconContentColor = textPrimary,
                    actionIconContentColor = textSecondary
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "DIAGNOSTICS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Normal,
                                fontFamily = NDotFamily,
                                letterSpacing = 2.sp
                            )
                        )
                        if (isRunning) {
                            Spacer(modifier = Modifier.width(12.dp))
                            PulsingRedDot()
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = textSecondary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = nothingRed)
                }
            } else {
                report?.let { cap ->
                    Spacer(modifier = Modifier.height(8.dp))

                    SectionTitle("TOUCH & INPUT")
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = cardBg,
                        border = BorderStroke(0.5.dp, cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            DiagRow("Resolution", "${cap.deviceInfo.displayWidth} × ${cap.deviceInfo.displayHeight}")
                            HorizontalDivider(color = cardBorder, thickness = 0.5.dp)
                            DiagRow("Density", "${cap.deviceInfo.displayDensity}×")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    SectionTitle("SECURITY & SERVICE")
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = cardBg,
                        border = BorderStroke(0.5.dp, cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            DiagRow("Root Access", if (cap.rootAvailable) "Granted" else "Denied")
                            HorizontalDivider(color = cardBorder, thickness = 0.5.dp)
                            DiagRow("Accessibility Service", if (cap.accessibilityAvailable) "Active" else "Disabled")
                            HorizontalDivider(color = cardBorder, thickness = 0.5.dp)
                            DiagRow("Active Mode", when {
                                isRootRunning -> "Root (Kernel evdev)"
                                isA11yRunning && isA11yOverlayActive -> "Accessibility Overlay"
                                else -> "Stopped"
                            })
                            HorizontalDivider(color = cardBorder, thickness = 0.5.dp)
                            DiagRow("SELinux", cap.deviceInfo.selinuxStatus)
                            HorizontalDivider(color = cardBorder, thickness = 0.5.dp)
                            DiagRow("Write Settings", if (cap.brightnessControlAvailable) "Allowed" else "Denied")
                            HorizontalDivider(color = cardBorder, thickness = 0.5.dp)
                            DiagRow("Gesture Service", if (isRunning) "Running" else "Stopped")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    SectionTitle("DEVICE")
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = cardBg,
                        border = BorderStroke(0.5.dp, cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            DiagRow("Model", "${cap.deviceInfo.manufacturer} ${cap.deviceInfo.model}")
                            HorizontalDivider(color = cardBorder, thickness = 0.5.dp)
                            DiagRow("Android", "API ${cap.deviceInfo.androidVersion}")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedButton(
                        onClick = {
                            val textReport = buildString {
                                appendLine("Device: ${cap.deviceInfo.manufacturer} ${cap.deviceInfo.model}")
                                appendLine("Android: API ${cap.deviceInfo.androidVersion}")
                                appendLine("Root: ${cap.rootAvailable}")
                                appendLine("Accessibility: ${cap.accessibilityAvailable}")
                                appendLine("Mode: ${if (isRootRunning) "Root" else if (isA11yRunning && isA11yOverlayActive) "Accessibility" else "None"}")
                                appendLine("SELinux: ${cap.deviceInfo.selinuxStatus}")
                                appendLine("Resolution: ${cap.deviceInfo.displayWidth}x${cap.deviceInfo.displayHeight}")
                                appendLine("Service: $isRunning")
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("StatusSwipe Diagnostics", textReport))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(0.5.dp, cardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = textPrimary
                        )
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = textSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "COPY REPORT",
                            color = textPrimary,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = NDotFamily,
                                letterSpacing = 1.5.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Normal,
            fontFamily = NDotFamily,
            letterSpacing = 2.sp
        ),
        color = Color(0xFFA3A3A3),
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
    )
}

@Composable
private fun DiagRow(label: String, value: String) {
    val isActive = value.equals("Active", ignoreCase = true) ||
                   value.equals("Granted", ignoreCase = true) ||
                   value.equals("Running", ignoreCase = true) ||
                   value.equals("Root (Kernel evdev)", ignoreCase = true) ||
                   value.equals("Accessibility Overlay", ignoreCase = true) ||
                   value.equals("Allowed", ignoreCase = true)
    
    val isInactive = value.equals("Denied", ignoreCase = true) ||
                     value.equals("Stopped", ignoreCase = true) ||
                     value.equals("Disabled", ignoreCase = true) ||
                     value.equals("None", ignoreCase = true)

    val valueColor = when {
        isActive -> Color(0xFFFF1A1A)
        isInactive -> Color(0xFF666666)
        else -> Color.White
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFA3A3A3),
            fontWeight = FontWeight.Normal
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = valueColor
        )
    }
}

@Composable
fun PulsingRedDot(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = modifier
            .size(8.dp)
            .background(Color(0xFFFF1A1A).copy(alpha = alpha), CircleShape)
    )
}
