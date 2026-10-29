package com.visuallog.ui.whiteboard

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.visuallog.data.model.DayEntry
import com.visuallog.data.model.Sticker
import com.visuallog.engine.snapshot.PolaroidCardExporter
import com.visuallog.ui.common.JournalTab
import com.visuallog.ui.common.WhiteboardMode
import com.visuallog.ui.common.WhiteboardTool
import com.visuallog.ui.whiteboard.components.*
import kotlinx.coroutines.launch

@Composable
fun WhiteboardScreen(
    dayEntry: DayEntry,
    onSave: (DayEntry, List<Sticker>) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val editorState = remember(dayEntry.id) { EditorState(dayEntry, scope) }
    var journalText by remember(dayEntry) { mutableStateOf(dayEntry.journalText) }
    
    LaunchedEffect(dayEntry.id) {
        editorState.load()
    }
    
    var selectedTab by remember { mutableStateOf(JournalTab.VISUAL) }
    var currentMode by remember { mutableStateOf(WhiteboardMode.VIEW) }
    var currentTool by remember { mutableStateOf(WhiteboardTool.PEN) }
    var selectedStickerId by remember { mutableStateOf<Int?>(null) }
    var selectedLayer by remember { mutableStateOf(1) } // Default to Middle Layer

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEDE7DC))
    ) {
        // Dot Grid Pattern
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 24.dp.toPx()
            val radius = 1.dp.toPx()
            
            for (x in 0..size.width.toInt() step step.toInt()) {
                for (y in 0..size.height.toInt() step step.toInt()) {
                    drawCircle(
                        color = Color(0xFFD0C6B6),
                        radius = radius,
                        center = Offset(x.toFloat(), y.toFloat())
                    )
                }
            }
        }

        // A. CONTENT LAYER
        Crossfade(
            targetState = selectedTab,
            animationSpec = tween(300),
            modifier = Modifier.fillMaxSize()
        ) { tab ->
            if (tab == JournalTab.VISUAL) {
                Box(Modifier.fillMaxSize()) {
                    VisualCanvasPage(
                        state = editorState, 
                        mode = currentMode, 
                        currentTool = currentTool, 
                        selectedStickerId = selectedStickerId, 
                        onSelectSticker = { id ->
                            selectedStickerId = id
                            if (id != null) {
                                editorState.stickers.find { it.id == id }?.let { selectedLayer = it.layer }
                            }
                        }, 
                        selectedLayer = selectedLayer
                    )
                    
                    if (editorState.status == EditorStatus.LOADING) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.Black.copy(alpha = 0.5f))
                        }
                    }
                }
            } else {
                TextJournalPage(
                    text = journalText,
                    mood = editorState.mood,
                    weather = editorState.weather,
                    onTextChange = { journalText = it },
                    onMoodChange = { editorState.mood = it },
                    onWeatherChange = { editorState.weather = it }
                )
            }
        }

        // B. FLOATING HEADER
        var exportResult by remember { mutableStateOf<PolaroidCardExporter.ExportCardResult?>(null) }

        FloatingHeader(
            onBack = onBack,
            onSave = { 
                editorState.journalText = journalText
                editorState.save(onSave)
            },
            onExport = {
                scope.launch {
                    val result = PolaroidCardExporter.exportCardWithDialog(
                        dayEntry = editorState.dayEntry.copy(
                            journalText = journalText,
                            mood = editorState.mood,
                            weather = editorState.weather
                        ),
                        layerManager = editorState.layerManager,
                        strokes = editorState.strokes.toList(),
                        stickers = editorState.stickers.toList()
                    )
                    exportResult = result
                }
            }
        )

        // C. TABS (LEFT)
        LeftTabDock(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
        )

        // D. TOOLS OVERLAY (RIGHT)
        if (selectedTab == JournalTab.VISUAL && editorState.status == EditorStatus.READY) {
            RightToolDock(
                mode = currentMode,
                currentTool = currentTool, 
                dayEntryId = dayEntry.id,
                stickers = editorState.stickers,
                currentColor = editorState.currentStrokeColor,
                currentWidth = editorState.currentStrokeWidth,
                onModeChange = { currentMode = it },
                onToolChange = { tool ->
                    currentTool = tool
                    editorState.strokeEngine.strokeWidth = when (tool) {
                        WhiteboardTool.ERASER -> 40f
                        WhiteboardTool.SEGMENT_ERASER -> 10f 
                        else -> editorState.currentStrokeWidth
                    }
                },
                onColorChange = { 
                    editorState.currentStrokeColor = it 
                },
                onWidthChange = { 
                    editorState.currentStrokeWidth = it
                    if (currentTool == WhiteboardTool.PEN) {
                         editorState.strokeEngine.strokeWidth = it
                    }
                },
                onMarkerModeChange = { 
                    editorState.isMarkerMode = it
                    if (it && editorState.currentStrokeWidth < 0.015f) {
                        editorState.currentStrokeWidth = 0.02f
                        editorState.strokeEngine.strokeWidth = 0.02f
                    }
                },
                isMarkerMode = editorState.isMarkerMode,
                onStickerAdded = { selectedStickerId = it },
                selectedLayer = selectedLayer,
                onSelectLayer = { newLayer ->
                    selectedLayer = newLayer
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 96.dp, end = 24.dp, bottom = 24.dp)
            )
        }

        // E. POLAROID EXPORT MODAL
        val currentExport = exportResult
        if (currentExport != null) {
            PolaroidExportModal(
                cardImage = currentExport.image,
                initialFile = currentExport.file,
                defaultFileName = "VisualLog_${editorState.dayEntry.date}.png",
                onDismiss = { exportResult = null }
            )
        }
    }
}

