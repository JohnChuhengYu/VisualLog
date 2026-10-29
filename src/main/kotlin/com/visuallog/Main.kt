package com.visuallog

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.visuallog.app.App
import com.visuallog.app.DebugStartDateDialog
import com.visuallog.app.FPSCounter
import com.visuallog.data.local.DatabaseFactory
import com.visuallog.data.repository.VisualLogRepository
import java.time.LocalDate

fun main() {
    // Force GPU Acceleration (Metal on macOS)
    System.setProperty("skiko.renderApi", "METAL")
    System.setProperty("skiko.vsync.enabled", "true") 
    println("🚀 [VisualLog] Enforcing GPU Rendering: METAL")

    DatabaseFactory.init()
    val repository = VisualLogRepository()
    
    application {
        val startDateState = remember { mutableStateOf<LocalDate?>(null) }
        val forceReloadTriggerState = remember { mutableStateOf(0) }

        Window(onCloseRequest = ::exitApplication, title = "Visual Life Log") {
            var showDebugDialog by remember { mutableStateOf(false) }
            var debugClickCount by remember { mutableStateOf(0) }
            var lastDebugPressTime by remember { mutableStateOf(0L) }
            
            val focusRequester = remember { FocusRequester() }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent {
                        if (it.key == Key.K && it.type == KeyEventType.KeyUp) {
                            val now = System.currentTimeMillis()
                            if (now - lastDebugPressTime > 1000) {
                                debugClickCount = 0
                            }
                            debugClickCount++
                            lastDebugPressTime = now
                            if (debugClickCount >= 8) {
                                showDebugDialog = true
                                debugClickCount = 0
                            }
                            false
                        } else {
                            false
                        }
                    }
                    .focusRequester(focusRequester)
                    .focusable()
            ) {
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                if (showDebugDialog) {
                    DebugStartDateDialog(
                        startDate = startDateState.value,
                        repository = repository,
                        onDismiss = { showDebugDialog = false },
                        onApplied = { newDate ->
                            startDateState.value = newDate
                            forceReloadTriggerState.value++
                            showDebugDialog = false
                        }
                    )
                }

                App(
                    repository = repository,
                    startDateState = startDateState,
                    forceReloadTriggerState = forceReloadTriggerState
                )
                
                // Real-time FPS Monitor Overlay
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                    FPSCounter()
                }
            }
        }
    }
}

