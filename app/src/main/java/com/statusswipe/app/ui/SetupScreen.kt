package com.statusswipe.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.statusswipe.app.capability.CapabilityDetector
import com.statusswipe.app.capability.CapabilityReport
import com.statusswipe.app.service.StatusSwipeAccessibilityService
import kotlinx.coroutines.delay

@Composable
fun SetupScreen(onSetupComplete: () -> Unit) {
    val context = LocalContext.current
    val detector = remember { CapabilityDetector(context) }

    val isA11yRunning by StatusSwipeAccessibilityService.isRunning.collectAsState()
    var isAccessibilityEnabled by remember { mutableStateOf(detector.isAccessibilityServiceEnabled()) }
    val isAccessibilityReady = isA11yRunning || isAccessibilityEnabled

    var report by remember { mutableStateOf<CapabilityReport?>(null) }
    var isChecking by remember { mutableStateOf(true) }
    var canWriteSettings by remember { mutableStateOf(Settings.System.canWrite(context)) }
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val bg = Color(0xFF0C0C0E)
    val cardBg = Color(0xFF18181B)
    val cardBorder = Color(0xFF242428)
    val textPrimary = Color(0xFFF4F4F5)
    val textSecondary = Color(0xFFA1A1AA)

    LaunchedEffect(Unit) {
        report = detector.detect()
        isChecking = false
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            canWriteSettings = Settings.System.canWrite(context)
            canDrawOverlays = Settings.canDrawOverlays(context)
            isAccessibilityEnabled = detector.isAccessibilityServiceEnabled()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "StatusSwipe",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp
                ),
                color = textPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Swipe status bar to control screen brightness.",
                style = MaterialTheme.typography.bodyMedium,
                color = textSecondary
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (isChecking) {
                CircularProgressIndicator(color = textSecondary, modifier = Modifier.size(24.dp))
            } else {
                report?.let { cap ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = cardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            if (cap.rootAvailable) {
                                SetupRow(
                                    title = "Root access",
                                    subtitle = "Granted — Hardware touch observation",
                                    isReady = true,
                                    onGrant = null
                                )
                            } else {
                                SetupRow(
                                    title = "Accessibility service",
                                    subtitle = if (isAccessibilityReady) "Active — Non-root overlay ready" else "Required for gesture detection without root",
                                    isReady = isAccessibilityReady,
                                    onGrant = {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                )
                            }

                            HorizontalDivider(color = cardBorder)

                            SetupRow(
                                title = "Modify system brightness",
                                subtitle = if (canWriteSettings) "Allowed" else "Permission required",
                                isReady = canWriteSettings,
                                onGrant = {
                                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            )

                            HorizontalDivider(color = cardBorder)

                            SetupRow(
                                title = "Display indicator overlay",
                                subtitle = if (canDrawOverlays) "Allowed" else "For brightness popup",
                                isReady = canDrawOverlays,
                                onGrant = {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        }

        val isReadyToStart = (report?.rootAvailable == true || isAccessibilityReady) && canWriteSettings

        Button(
            onClick = onSetupComplete,
            enabled = isReadyToStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
                disabledContainerColor = Color(0xFF27272A),
                disabledContentColor = Color(0xFF71717A)
            )
        ) {
            Text("Get started", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun SetupRow(
    title: String,
    subtitle: String,
    isReady: Boolean,
    onGrant: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFFF4F4F5)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA1A1AA)
            )
        }

        if (isReady) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(18.dp)
            )
        } else if (onGrant != null) {
            TextButton(onClick = onGrant) {
                Text("Allow", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        } else {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
