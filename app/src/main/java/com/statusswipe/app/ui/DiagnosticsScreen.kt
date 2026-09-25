package com.statusswipe.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.statusswipe.app.capability.CapabilityDetector
import com.statusswipe.app.capability.CapabilityReport
import com.statusswipe.app.service.GestureService
import com.statusswipe.app.service.StatusSwipeAccessibilityService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val detector = remember { CapabilityDetector(context) }
    var report by remember { mutableStateOf<CapabilityReport?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val isRootRunning by GestureService.isRunning.collectAsState()
    val isA11yRunning by StatusSwipeAccessibilityService.isRunning.collectAsState()
    val isA11yOverlayActive by StatusSwipeAccessibilityService.isOverlayActive.collectAsState()
    val isRunning = isRootRunning || (isA11yRunning && isA11yOverlayActive)

    val bg = Color(0xFF0C0C0E)
    val cardBg = Color(0xFF18181B)
    val cardBorder = Color(0xFF242428)
    val textPrimary = Color(0xFFF4F4F5)
    val textSecondary = Color(0xFFA1A1AA)

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
                    Text(
                        "Diagnostics",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                    CircularProgressIndicator(color = textSecondary)
                }
            } else {
                report?.let { cap ->
                    Spacer(modifier = Modifier.height(8.dp))

                    SectionTitle("TOUCH & INPUT")
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = cardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            DiagRow("Device Node", cap.deviceInfo.touchDevicePath ?: "/dev/input/event3")
                            HorizontalDivider(color = cardBorder)
                            DiagRow("Controller", cap.deviceInfo.touchDeviceName ?: "fts_ts")
                            HorizontalDivider(color = cardBorder)
                            DiagRow("Resolution", "${cap.deviceInfo.displayWidth} × ${cap.deviceInfo.displayHeight}")
                            HorizontalDivider(color = cardBorder)
                            DiagRow("Density", "${cap.deviceInfo.displayDensity}×")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    SectionTitle("SECURITY & SERVICE")
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = cardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            DiagRow("Root Access", if (cap.rootAvailable) "Granted" else "Denied")
                            HorizontalDivider(color = cardBorder)
                            DiagRow("Accessibility Service", if (cap.accessibilityAvailable) "Active" else "Disabled")
                            HorizontalDivider(color = cardBorder)
                            DiagRow("Active Mode", when {
                                isRootRunning -> "Root (Kernel evdev)"
                                isA11yRunning && isA11yOverlayActive -> "Accessibility Overlay"
                                else -> "Stopped"
                            })
                            HorizontalDivider(color = cardBorder)
                            DiagRow("SELinux Domain", cap.deviceInfo.selinuxStatus)
                            HorizontalDivider(color = cardBorder)
                            DiagRow("Write Settings", if (cap.brightnessControlAvailable) "Allowed" else "Denied")
                            HorizontalDivider(color = cardBorder)
                            DiagRow("Gesture Service", if (isRunning) "Running" else "Stopped")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    SectionTitle("DEVICE")
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = cardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            DiagRow("Model", "${cap.deviceInfo.manufacturer} ${cap.deviceInfo.model}")
                            HorizontalDivider(color = cardBorder)
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
                                appendLine("Node: ${cap.deviceInfo.touchDevicePath ?: "/dev/input/event3"}")
                                appendLine("Controller: ${cap.deviceInfo.touchDeviceName ?: "fts_ts"}")
                                appendLine("Resolution: ${cap.deviceInfo.displayWidth}x${cap.deviceInfo.displayHeight}")
                                appendLine("Service: $isRunning")
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("StatusSwipe Diagnostics", textReport))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = textSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy report", color = textPrimary)
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
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp
        ),
        color = Color(0xFF71717A),
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
    )
}

@Composable
private fun DiagRow(label: String, value: String) {
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
            color = Color(0xFFA1A1AA)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = Color(0xFFF4F4F5)
        )
    }
}
