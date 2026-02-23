import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import data.DatabaseFactory
import data.DayEntry
import data.Sticker
import data.VisualLogRepository
import kotlinx.coroutines.launch
import ui.CalendarGrid
import ui.StickerEditorDialog
import java.time.YearMonth
import java.time.LocalDate

@Composable
@Preview
fun App(
    repository: VisualLogRepository,
    startDateState: MutableState<LocalDate?>,
    forceReloadTriggerState: MutableState<Int>
) {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        
        // Hoist Grid State to persist layout/scroll position across navigation/animations
        val gridScrollState = rememberLazyGridState()

        // State for navigation
        var currentScreen by remember { mutableStateOf("grid") } // "grid" or "whiteboard"
        
        var dayEntries by remember { mutableStateOf<Map<String, DayEntry>>(emptyMap()) }
        var selectedDay by remember { mutableStateOf<DayEntry?>(null) }
        
        // Use lifted state
        var startDate by startDateState
        var forceReloadTrigger by forceReloadTriggerState

        // Load initialization date and data
        LaunchedEffect(forceReloadTrigger) {
            // Get or create the user's initialization date
            if (startDate == null) {
                startDate = repository.getOrCreateInitDate()
            }
            
            // Load all day entries from init date to today
            val today = LocalDate.now()
            val totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, today).toInt() + 1
            val entries = mutableMapOf<String, DayEntry>()
            
            (0 until totalDays).forEach { i ->
                val date = startDate!!.plusDays(i.toLong()).toString()
                val entry = repository.getDayEntry(date)
                if (entry != null) {
                    entries[date] = entry
                }
            }
            dayEntries = entries
        }

        // Initial Load and automatic date change detection - smart midnight timer
        LaunchedEffect(Unit) {
            ui.GlobalBackgroundState.load()
            while (true) {
                val now = java.time.LocalDateTime.now()
                val tomorrow = now.toLocalDate().plusDays(1).atStartOfDay()
                val millisUntilMidnight = java.time.Duration.between(now, tomorrow).toMillis()
                
                // Wait until midnight
                kotlinx.coroutines.delay(millisUntilMidnight)
                
                // Midnight reached! Trigger reload
                forceReloadTrigger++
                
                // Add a small delay to avoid edge cases
                kotlinx.coroutines.delay(1000)
            }
        }

        Scaffold {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        if (targetState == "whiteboard") {
                             // OPEN: Sheet enters (Target) -> Needs to be ON TOP
                             (fadeIn(tween(250)) togetherWith fadeOut(tween(250)))
                                 .apply { targetContentZIndex = 1f } 
                        } else {
                            // CLOSE: Grid enters (Target) -> Needs to be BEHIND
                            (fadeIn(tween(250)) togetherWith fadeOut(tween(250)))
                                .apply { targetContentZIndex = 0f }
                        }.using(SizeTransform(clip = false) { _, _ -> keyframes { durationMillis = 0 } })
                    },
                    modifier = Modifier.fillMaxSize()
                ) { screen ->
                    if (screen == "grid" && startDate != null) {
                        ui.LifeGridScreen(
                            startDate = startDate!!,
                            dayEntries = dayEntries,
                            scrollState = gridScrollState,
                            onDayClick = { date, _, _ ->
                                var entry = dayEntries[date.toString()]
                                if (entry == null) {
                                    entry = DayEntry(date = date.toString())
                                }
                                selectedDay = entry
                                currentScreen = "whiteboard"
                            }
                        )
                    } else if (screen == "whiteboard" && selectedDay != null) {
                        // Background loading is handled inside WhiteboardScreen
                        ui.WhiteboardScreen(
                            dayEntry = selectedDay!!,
                            onBack = { 
                                currentScreen = "grid" 
                                selectedDay = null
                            },
                            onSave = { updatedEntry, stickers ->
                                scope.launch {
                                    println("Saving entry: ${updatedEntry.date}")
                                    
                                    // Save Day Entry
                                    val savedEntry = repository.saveOrUpdateDayEntry(
                                        date = updatedEntry.date, 
                                        backgroundPath = updatedEntry.backgroundPath,
                                        drawingPaths = updatedEntry.drawingPaths,
                                        journalText = updatedEntry.journalText,
                                        thumbnailPath = updatedEntry.thumbnailPath,
                                        mood = updatedEntry.mood,
                                        weather = updatedEntry.weather
                                    )
                                    
                                    // Save Stickers Logic
                                    val existingStickers = repository.getDayEntry(updatedEntry.date)?.stickers ?: emptyList()
                                    val currentIds = stickers.map { it.id }.toSet()
                                    
                                    // 1. Delete removed stickers
                                    existingStickers.forEach { s ->
                                        if (s.id !in currentIds) {
                                            repository.deleteSticker(s.id)
                                        }
                                    }
                                    
                                    // 2. Insert/Update stickers
                                    stickers.forEach { s ->
                                        if (s.id < 0) {
                                            repository.addSticker(savedEntry.id, s.x, s.y, s.scale, s.rotation, s.contentPath, s.type, s.layer)
                                        } else {
                                            repository.updateStickerPosition(s.id, s.x, s.y, s.scale, s.rotation, s.layer)
                                        }
                                    }
                                    
                                    // 3. Reload data
                                    val finalEntry = repository.getDayEntry(updatedEntry.date)
                                    if (finalEntry != null) {
                                        val newMap = dayEntries.toMutableMap()
                                        newMap[updatedEntry.date] = finalEntry
                                        dayEntries = newMap
                                    }
                                    
                                    // 4. Optimize Storage (Cleanup unused sticker files)
                                    launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val cleaned = repository.cleanupOrphanedStickers()
                                        if (cleaned > 0) println("Cleaned up $cleaned orphaned sticker files.")
                                    }
                                    
                                    currentScreen = "grid"
                                    selectedDay = null
                                }
                            }
                        )
                    }
                }
            }
    }
}


fun main() {
    // FORCE GPU ACCELERATION (Metal on macOS)
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
                     var dateText by remember { mutableStateOf((startDateState.value ?: LocalDate.now()).toString()) }
                     androidx.compose.material3.AlertDialog(
                         onDismissRequest = { showDebugDialog = false },
                         title = { androidx.compose.material3.Text("Debug: Set Start Date") },
                         text = {
                             androidx.compose.foundation.layout.Column {
                                 androidx.compose.material3.Text(
                                     "Today is ${LocalDate.now()}",
                                     style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                     modifier = Modifier.padding(bottom = 8.dp)
                                 )
                                 androidx.compose.material3.TextField(
                                     value = dateText,
                                     onValueChange = { dateText = it },
                                     label = { androidx.compose.material3.Text("Start Date (YYYY-MM-DD)") },
                                     supportingText = { androidx.compose.material3.Text("Set a PAST date to see more history.") }
                                 )
                             }
                         },
                         confirmButton = {
                             androidx.compose.material3.Button(
                                 onClick = {
                                     try {
                                         val newDate = LocalDate.parse(dateText)
                                         startDateState.value = newDate
                                         // Also update DB setting if we want persistence, 
                                         // but user said "debug mode", so transient might be fine.
                                         // Let's persist it to be helpful.
                                         repository.setAppSetting("start_date", newDate.toString())
                                         forceReloadTriggerState.value++ // Trigger reload in App
                                         showDebugDialog = false
                                     } catch (e: Exception) {
                                         // Invalid date
                                     }
                                 }
                             ) { androidx.compose.material3.Text("Apply") }
                         },
                         dismissButton = {
                             androidx.compose.material3.Button(onClick = { showDebugDialog = false }) {
                                 androidx.compose.material3.Text("Cancel")
                             }
                         }
                     )
                }
                App(repository, startDateState, forceReloadTriggerState)
                
                // FPS Monitor Overlay (Global)
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                    FPSCounter()
                }
            }
        }
    }
}

/**
 * Real-time FPS Monitor component.
 */
@Composable
fun FPSCounter() {
    var fps by remember { mutableStateOf(0) }
    val frameTimes = remember { mutableStateListOf<Long>() }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos ->
                frameTimes.add(nanos)
                // Keep only last 1 second of frames
                val oneSecondAgo = nanos - 1_000_000_000L
                while (frameTimes.isNotEmpty() && frameTimes.first() < oneSecondAgo) {
                    frameTimes.removeAt(0)
                }
                fps = frameTimes.size
            }
        }
    }

    Surface(
        modifier = Modifier.padding(8.dp).alpha(0.6f),
        color = Color.Black,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = "FPS: $fps",
            color = Color.Green,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
