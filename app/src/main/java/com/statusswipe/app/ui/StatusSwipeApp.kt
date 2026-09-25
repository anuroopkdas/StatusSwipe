package com.statusswipe.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.statusswipe.app.util.Preferences

@Composable
fun StatusSwipeApp() {
    val context = LocalContext.current
    val preferences = remember { Preferences(context) }
    var hasCompletedSetup by remember { mutableStateOf(preferences.hasCompletedSetup) }
    var showDiagnostics by remember { mutableStateOf(false) }

    when {
        showDiagnostics -> {
            DiagnosticsScreen(onBack = { showDiagnostics = false })
        }
        hasCompletedSetup -> {
            MainScreen(onNavigateToDiagnostics = { showDiagnostics = true })
        }
        else -> {
            SetupScreen(
                onSetupComplete = {
                    preferences.hasCompletedSetup = true
                    hasCompletedSetup = true
                }
            )
        }
    }
}
