package com.statusswipe.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

    val bg = Color(0xFF000000)
    val cardBg = Color(0xFF1D1E20)
    val cardBorder = Color(0xFF3B3B3B)
    val textPrimary = Color.White
    val textSecondary = Color(0xFFA3A3A3)
    val nothingRed = Color(0xFFFF1A1A)

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
                text = "STATUSSWIPE",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Normal,
                    fontFamily = NDotFamily,
                    letterSpacing = 4.sp
                ),
                color = textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Swipe status bar to control screen brightness.",
                style = MaterialTheme.typography.bodyMedium,
                color = textSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (isChecking) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = nothingRed, modifier = Modifier.size(24.dp))
                }
            } else {
                report?.let { cap ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = cardBg,
                        border = BorderStroke(0.5.dp, cardBorder)
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

                            HorizontalDivider(thickness = 0.5.dp, color = cardBorder)

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

                            HorizontalDivider(thickness = 0.5.dp, color = cardBorder)

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
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = nothingRed,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF333333),
                disabledContentColor = Color(0xFFA3A3A3)
            )
        ) {
            Text(
                text = "GET STARTED",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Normal,
                    fontFamily = NDotFamily,
                    letterSpacing = 2.sp
                )
            )
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
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA3A3A3)
            )
        }

        if (isReady) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFFFF1A1A), CircleShape)
            )
        } else if (onGrant != null) {
            OutlinedButton(
                onClick = onGrant,
                border = BorderStroke(1.dp, Color(0xFFFF1A1A)),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "ALLOW",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFFF1A1A)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .border(1.dp, Color(0xFF3B3B3B), CircleShape)
            )
        }
    }
}
