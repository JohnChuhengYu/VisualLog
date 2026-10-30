package com.visuallog.app

import androidx.compose.animation.*
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.visuallog.data.model.DayEntry
import com.visuallog.data.repository.VisualLogRepository
import com.visuallog.data.service.ThumbnailMigrator
import com.visuallog.i18n.LanguageManager
import com.visuallog.ui.grid.GlobalBackgroundState
import com.visuallog.ui.grid.LifeGridScreen
import com.visuallog.ui.whiteboard.WhiteboardScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Composable
fun App(
    repository: VisualLogRepository,
    startDateState: MutableState<LocalDate?>,
    forceReloadTriggerState: MutableState<Int>
) {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val gridScrollState = rememberLazyGridState()

        var currentScreen by remember { mutableStateOf("grid") } // "grid" or "whiteboard"
        var dayEntries by remember { mutableStateOf<Map<String, DayEntry>>(emptyMap()) }
        var selectedDay by remember { mutableStateOf<DayEntry?>(null) }
        
        var startDate by startDateState
        var forceReloadTrigger by forceReloadTriggerState

        // Load initialization date and timeline entries
        LaunchedEffect(forceReloadTrigger) {
            LanguageManager.init(repository)
            withContext(Dispatchers.IO) {
                ThumbnailMigrator.migrateIfNeeded(repository)
            }

            if (startDate == null) {
                startDate = repository.getOrCreateInitDate()
            }
            
            val today = LocalDate.now()
            val totalDays = ChronoUnit.DAYS.between(startDate, today).toInt() + 1
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

        // Smart midnight timer: automatically reloads when passing 00:00
        LaunchedEffect(Unit) {
            GlobalBackgroundState.load()
            while (true) {
                val now = LocalDateTime.now()
                val tomorrow = now.toLocalDate().plusDays(1).atStartOfDay()
                val millisUntilMidnight = Duration.between(now, tomorrow).toMillis()
                
                delay(millisUntilMidnight)
                forceReloadTrigger++
                delay(1000)
            }
        }

        Scaffold {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    if (targetState == "whiteboard") {
                        (fadeIn(tween(250)) togetherWith fadeOut(tween(250)))
                            .apply { targetContentZIndex = 1f } 
                    } else {
                        (fadeIn(tween(250)) togetherWith fadeOut(tween(250)))
                            .apply { targetContentZIndex = 0f }
                    }.using(SizeTransform(clip = false) { _, _ -> keyframes { durationMillis = 0 } })
                },
                modifier = Modifier.fillMaxSize()
            ) { screen ->
                if (screen == "grid" && startDate != null) {
                    LifeGridScreen(
                        startDate = startDate!!,
                        dayEntries = dayEntries,
                        scrollState = gridScrollState,
                        onDayClick = { date, _, _ ->
                            val entry = dayEntries[date.toString()] ?: DayEntry(date = date.toString())
                            selectedDay = entry
                            currentScreen = "whiteboard"
                        }
                    )
                } else if (screen == "whiteboard" && selectedDay != null) {
                    WhiteboardScreen(
                        dayEntry = selectedDay!!,
                        onBack = { 
                            currentScreen = "grid" 
                            selectedDay = null
                        },
                        onSave = { updatedEntry, stickers ->
                            scope.launch {
                                // Delegate atomic transaction to Repository
                                val finalEntry = repository.saveDayEntryWithStickers(updatedEntry, stickers)
                                val newMap = dayEntries.toMutableMap()
                                newMap[updatedEntry.date] = finalEntry
                                dayEntries = newMap
                                
                                // Asynchronously cleanup orphaned sticker files on disk
                                launch(Dispatchers.IO) {
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

