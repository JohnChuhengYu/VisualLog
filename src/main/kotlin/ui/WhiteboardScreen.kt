package ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material3.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import kotlin.random.Random
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import data.DayEntry
import data.SerializationUtils
import data.Sticker
import drawing.InkInputManager
import drawing.LayerManager
import drawing.RawPoint
import drawing.StrokeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- ENUMS ---
enum class WhiteboardMode {
    VIEW, DRAW, STICKER
}

enum class WhiteboardTool {
    PEN, ERASER, SEGMENT_ERASER
}

enum class JournalTab {
    VISUAL, TEXT
}

// --- MAIN SCREEN ---
@Composable
fun WhiteboardScreen(
    dayEntry: DayEntry,
    onSave: (DayEntry, List<Sticker>) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    // 1. HOST THE BRAIN
    val editorState = remember(dayEntry.id) { EditorState(dayEntry, scope) }
    
    // Local Text State (FIXED: journalText)
    var journalText by remember(dayEntry) { mutableStateOf(dayEntry.journalText) }
    
    // 2. LIFECYCLE
    LaunchedEffect(dayEntry.id) {
        editorState.load()
    }
    
    // State for Tabs
    var selectedTab by remember { mutableStateOf(JournalTab.VISUAL) }
    
    // State for Mode (Toolbar) - Lifted Up
    var currentMode by remember { mutableStateOf(WhiteboardMode.VIEW) }
    var currentTool by remember { mutableStateOf(WhiteboardTool.PEN) }
    var selectedStickerId by remember { mutableStateOf<Int?>(null) }
    var selectedLayer by remember { mutableStateOf(1) } // Default to Middle Layer

    // 3. UI LAYOUT
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F5F0)) // 暖白底色
    ) {
        // Dot Grid Pattern (Shared with LifeGrid)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 24.dp.toPx()
            val radius = 1.dp.toPx()
            
            for (x in 0..size.width.toInt() step step.toInt()) {
                for (y in 0..size.height.toInt() step step.toInt()) {
                    drawCircle(
                        color = Color(0xFFE0D8CC),
                        radius = radius,
                        center = Offset(x.toFloat(), y.toFloat())
                    )
                }
            }
        }
        // A. CONTENT LAYER (Tabs Switch)
        Crossfade(
            targetState = selectedTab,
            animationSpec = tween(300),
            modifier = Modifier.fillMaxSize()
        ) { tab ->
            if (tab == JournalTab.VISUAL) {
                // VISUAL TAB
                Box(Modifier.fillMaxSize()) {
                    // INTERACTIVE EDITOR - ALWAYS SHOW
                    VisualCanvasPage(editorState, currentMode, currentTool, selectedStickerId, { id ->
                        selectedStickerId = id
                        if (id != null) {
                              editorState.stickers.find { it.id == id }?.let { selectedLayer = it.layer }
                        }
                    }, selectedLayer, { selectedLayer = it })
                    
                    // Loading Overlay
                    if (editorState.status == EditorStatus.LOADING) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.Black.copy(alpha=0.5f))
                        }
                    }
                }
            } else {
                // TEXT TAB
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

        // B. FLOATING HEADER (Back / Save)
        FloatingHeader(
            onBack = onBack,
            onSave = { 
                editorState.journalText = journalText
                editorState.save(onSave)
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
                    editorState.strokeEngine.strokeWidth = when(tool) {
                        WhiteboardTool.ERASER -> 40f // 4% of canvas width (Scaled up for viewport)
                        WhiteboardTool.SEGMENT_ERASER -> 10f 
                        else -> editorState.currentStrokeWidth // Restore pen width
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
                    // Marker is thicker by default if switching, but user can override
                    if (it && editorState.currentStrokeWidth < 0.015f) {
                        editorState.currentStrokeWidth = 0.02f
                        editorState.strokeEngine.strokeWidth = 0.02f
                    }
                },
                isMarkerMode = editorState.isMarkerMode,
                onStickerAdded = { selectedStickerId = it },
                selectedStickerId = selectedStickerId,
                selectedLayer = selectedLayer,
                onSelectLayer = { newLayer ->
                    selectedLayer = newLayer
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 96.dp, end = 24.dp, bottom = 24.dp)
            )
    }
}
}

@Composable
private fun VisualCanvasPage(
    state: EditorState, 
    mode: WhiteboardMode, 
    currentTool: WhiteboardTool,
    selectedStickerId: Int?,
    onSelectSticker: (Int?) -> Unit,
    selectedLayer: Int,
    onSelectLayer: (Int) -> Unit
) {
    val viewport = state.viewport
    val layer = state.layerManager
    val strokes = state.strokes
    val stickers = state.stickers
    
    // Input-driven invalidation (No more 60fps loop)
    var invalidator by remember { mutableStateOf(0) }

    // Auto-Center Viewport (Fixed Canvas)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            // Canvas Locked: No user transform gestures
    ) {

        LaunchedEffect(constraints.maxWidth, constraints.maxHeight) {
            val winW = constraints.maxWidth.toFloat()
            val winH = constraints.maxHeight.toFloat()
            val paperSize = 1.0f // Normalized Unit
            
            // Scale to Fit (maintain aspect ratio, 95% fill)
            val scaleX = winW / paperSize
            val scaleY = winH / paperSize
            val fitScale = kotlin.math.min(scaleX, scaleY) * 0.95f
            
            viewport.scale = fitScale
            
            // Center with new scale
            viewport.offset = Offset(
                (winW - paperSize * fitScale) / 2f,
                (winH - paperSize * fitScale) / 2f
            )
        }

            // Trigger recomposition on invalidator change
            invalidator.let {}
            
            val vScale = viewport.scale
            val vOffset = viewport.offset
            
            // 1. PAPER (Model: 0..1000)
            Canvas(modifier = Modifier.fillMaxSize()) {
                translate(vOffset.x, vOffset.y) {
                    scale(vScale, pivot = Offset.Zero) {
                         // Shadow (Soft & Diffuse)
                         drawRoundRect(
                             color = Color(0xFF8B8B8B).copy(alpha=0.15f),
                             topLeft = Offset(0.004f, 0.008f),
                             size = androidx.compose.ui.geometry.Size(1.0f, 1.0f),
                             cornerRadius = CornerRadius(0.002f, 0.002f)
                         )
                         
                         // Paper (Creamy Journal Paper)
                         drawRoundRect(
                            color = Color(0xFFFFFDF7), 
                            size = androidx.compose.ui.geometry.Size(1.0f, 1.0f),
                            cornerRadius = CornerRadius(0.002f, 0.002f),
                            style = androidx.compose.ui.graphics.drawscope.Fill
                         )
                         // Border (Subtle Edge)
                         drawRoundRect(
                            color = Color(0xFFE0E0E0), 
                            size = androidx.compose.ui.geometry.Size(1.0f, 1.0f),
                            cornerRadius = CornerRadius(0.002f, 0.002f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.001f)
                         )
                    }
                }
            }
            
            // 2. INPUT HANDLER (Background Layer)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(mode, currentTool, selectedLayer) {
                         if (mode == WhiteboardMode.DRAW) {
                             awaitPointerEventScope {
                                     while (true) {
                                         val event = awaitPointerEvent()
                                         val type = event.type
                                         val changes = event.changes
                                         
                                         // 1. Process Movement / Press (Only if pointer is actually down)
                                         val isDown = changes.any { it.pressed }
                                         if (isDown && (type == PointerEventType.Move || type == PointerEventType.Press)) {
                                             val logicalChanges = changes.map { c ->
                                                 val localPos = (c.position - viewport.offset) / viewport.scale
                                                 drawing.RawPoint(localPos.x, localPos.y, c.uptimeMillis)
                                             }
                                             state.inputManager.onRawPoint(logicalChanges.last()) 
                                             state.strokeEngine.process(logicalChanges)
                                             invalidator++
                                         }
                                         
                                         changes.forEach { it.consume() }
                                         
                                         // 2. Process Release (Bake or Erase)
                                         if (type == PointerEventType.Release) {
                                             println("Whiteboard: Pointer Release. Tool: $currentTool, Points: ${state.strokeEngine.rawPoints.size}")
                                             
                                             if (currentTool == WhiteboardTool.SEGMENT_ERASER) {
                                                 // --- SEGMENT ERASER LOGIC ---
                                                 val eraserPoints = state.strokeEngine.rawPoints.map { Offset(it.x, it.y) }
                                                 
                                                 if (eraserPoints.size >= 2) {
                                                     val eraserSegments = eraserPoints.windowed(2) { (a, b) -> a to b }
                                                     val eraserBounds = Rect(
                                                         eraserPoints.minOf { it.x }, eraserPoints.minOf { it.y },
                                                         eraserPoints.maxOf { it.x }, eraserPoints.maxOf { it.y }
                                                     ).inflate(0.005f)
                                                     
                                                     val toRemove = state.strokes.filter { s ->
                                                         s.layer == selectedLayer && !s.isEraser
                                                     }.filter { s ->
                                                         if (s.points.size < 2) return@filter false
                                                         
                                                         // Fast bounding box check
                                                         if (!eraserBounds.overlaps(s.bounds)) return@filter false
                                                         
                                                         // Robust segment-to-segment intersection
                                                         val strokeSegments = s.points.windowed(2) { (a, b) -> a to b }
                                                         
                                                         strokeSegments.any { sSeg ->
                                                             eraserSegments.any { eSeg ->
                                                                 lineSegmentsIntersect(sSeg.first, sSeg.second, eSeg.first, eSeg.second)
                                                             }
                                                         }
                                                     }
                                                     
                                                     if (toRemove.isNotEmpty()) {
                                                         state.strokes.removeAll(toRemove)
                                                         layer.restoreFromPaths(state.strokes)
                                                     }
                                                 }
                                                 state.strokeEngine.clear()
                                                 invalidator++ // Ensure red trail disappears
                                                 
                                             } else {
                                                 // --- NORMAL DRAW/ERASER LOGIC ---
                                                 val path = state.strokeEngine.getFinalPath()
                                                 if (!path.isEmpty) {
                                                      val style = state.strokeEngine.currentStyle
                                                      val baseColor = if (currentTool == WhiteboardTool.ERASER) Color.White else state.currentStrokeColor
                                                      val finalColor = if (state.isMarkerMode && currentTool != WhiteboardTool.ERASER) baseColor.copy(alpha=0.4f) else baseColor
                                                      val bm = if(currentTool == WhiteboardTool.ERASER) androidx.compose.ui.graphics.BlendMode.Clear else androidx.compose.ui.graphics.BlendMode.SrcOver
                                                      val isEraser = currentTool == WhiteboardTool.ERASER
                                                      
                                                      layer.bakeStroke(path, style, finalColor, bm, selectedLayer)
                                                      
                                                      val savedPoints = state.strokeEngine.rawPoints.map { Offset(it.x, it.y) }
                                                      
                                                      val s = data.Stroke(
                                                         points = savedPoints,
                                                         isEraser = isEraser,
                                                         width = state.strokeEngine.strokeWidth,
                                                         color = if(isEraser) 0 else finalColor.toArgb(),
                                                         layer = selectedLayer
                                                      )
                                                      state.strokes.add(s)
                                                 }
                                                 state.strokeEngine.clear()
                                                 invalidator++ 
                                             }
                                         }
                                     }
                             }
                         } else {
                             // MOVE mode: Accidental canvas panning is disabled for single-pointer drags.
                         }
                    }
                    .pointerInput(mode) {
                        if (mode != WhiteboardMode.DRAW) {
                            detectTapGestures {
                                onSelectSticker(null)
                            }
                        }
                    }
            )

            // 3. LAYERS (Foreground: Bitmaps + Stickers)
            for (layerId in 0..2) {
                // Baked Bitmap
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val buffer = layer.buffers[layerId]
                    val drawScale = vScale / layer.bufferScale
                    
                    translate(vOffset.x, vOffset.y) {
                        scale(drawScale, pivot = Offset.Zero) {
                            drawImage(buffer)
                        }
                    }
                    
                    // Live Stroke (Only if layer matches)
                    if (layerId == selectedLayer) {
                         translate(vOffset.x, vOffset.y) {
                            scale(vScale, pivot = Offset.Zero) {
                                val color = when (currentTool) {
                                    WhiteboardTool.ERASER -> Color.White.copy(alpha=0.5f)
                                    WhiteboardTool.SEGMENT_ERASER -> Color.Red.copy(alpha=0.2f)
                                    else -> if(state.isMarkerMode) state.currentStrokeColor.copy(alpha=0.4f) else state.currentStrokeColor
                                }
                                
                                if (state.strokeEngine.currentPath.isEmpty.not()) {
                                    drawPath(
                                        state.strokeEngine.currentPath, 
                                        color, 
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = state.strokeEngine.strokeWidth / 1000f,
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                
                Box(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer {
                            translationX = viewport.offset.x
                            translationY = viewport.offset.y
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                ) {
                    stickers.filter { it.layer == layerId }.forEach { sticker ->
                        key(sticker.id) {
                             SamsungSticker(
                                 sticker = sticker,
                                 canvasWidth = viewport.scale,
                                 canvasHeight = viewport.scale,
                                 useFixedSize = false,
                                 enabled = (mode == WhiteboardMode.STICKER) && (sticker.contentPath != "LOADING"),
                                 isSelected = (mode == WhiteboardMode.STICKER) && (sticker.id == selectedStickerId),
                                 onClick = { onSelectSticker(sticker.id) },
                                 onTransform = { x, y, s, r -> 
                                     val idx = stickers.indexOfFirst { it.id == sticker.id }
                                     if (idx != -1) {
                                         val current = stickers[idx]
                                         stickers[idx] = current.copy(x=x, y=y, scale=s, rotation=r) 
                                     }
                                     onSelectSticker(sticker.id)
                                 },
                                 onDelete = {
                                     stickers.removeAll { it.id == sticker.id }
                                     if (selectedStickerId == sticker.id) onSelectSticker(null)
                                 }
                             )
                        }
                    }
                }
            }
    }
}

@Composable
fun AsyncThumbnailFullscreen(path: String) {
    val bitmap by produceState<ImageBitmap?>(null) {
        withContext(Dispatchers.IO) {
            try {
                value = androidx.compose.ui.res.loadImageBitmap(java.io.File(path).inputStream())
            } catch(e: Exception) {}
        }
    }
    if (bitmap != null) {
        Image(bitmap!!, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}
@Composable
private fun TextJournalPage(
    text: String,
    mood: String?,
    weather: String?,
    onTextChange: (String) -> Unit,
    onMoodChange: (String?) -> Unit,
    onWeatherChange: (String?) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 96.dp, bottom = 24.dp, start = 84.dp, end = 24.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(2.dp), // Sharper corners for paper
            color = Color(0xFFFDFCF8), // Warm paper white
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // --- JOURNAL HEADER (Stamps) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title
                    Text(
                        text = "Daily Entry",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF5D4E3C).copy(alpha = 0.8f)
                    )
                    
                    // Stamps Row
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Weather Stamp
                        StampControl(
                            label = "Weather",
                            currentValue = weather,
                            options = StickerDefinitions.WeatherOptions,
                            color = Color(0xFFD84315), // Burnt Orange Ink
                            onValueChange = onWeatherChange
                        )
                        
                        // Mood Stamp
                        StampControl(
                            label = "Mood",
                            currentValue = mood,
                            options = StickerDefinitions.MoodOptions,
                            color = Color(0xFF1E88E5), // Blue Ink
                            onValueChange = onMoodChange
                        )
                    }
                }
                
                // Divider
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    thickness = 1.dp,
                    color = Color.Black.copy(alpha = 0.1f)
                )

                // Text Area
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color(0xFF5D4E3C)
                    ),
                    placeholder = { 
                        Text(
                            "Write your thoughts...",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                            ),
                            color = Color.Gray.copy(alpha=0.4f)
                        ) 
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontSize = 20.sp,
                        lineHeight = 34.sp,
                        color = Color.Black.copy(alpha = 0.87f)
                    )
                )
            }
        }
    }
}

@Composable
fun StampControl(
    label: String,
    currentValue: String?,
    options: Map<String, ImageVector>,
    color: Color,
    onValueChange: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation = remember { Random.nextFloat() * 10f - 5f } 
    
    Box {
        // The Stamp Itself
        Box(
            modifier = Modifier
                .size(48.dp)
                .rotate(rotation)
                .alphaClickable { expanded = true }
                .border(2.dp, if (currentValue != null) color.copy(alpha=0.7f) else Color.LightGray.copy(alpha=0.3f), CircleShape)
                .background(if (currentValue != null) color.copy(alpha=0.1f) else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (currentValue != null && options.containsKey(currentValue)) {
                Icon(
                    imageVector = options[currentValue]!!,
                    contentDescription = currentValue,
                    tint = color.copy(alpha = 0.8f),
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Text(
                    "?",
                    color = Color.LightGray.copy(alpha=0.5f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // The Dropdown "Box"
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color(0xFFFFF8E1))
                .border(1.dp, Color(0xFFE0C090), RoundedCornerShape(4.dp))
                .heightIn(max = 240.dp) // Limit height
        ) {
            // Render grid of options
            val keys = options.keys.toList()
            val columns = 4
            
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .width(176.dp) // 4 * 40 + spaces
            ) {
                Text(
                    text = label, 
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
                
                keys.chunked(columns).forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowKeys.forEach { key ->
                            val icon = options[key]!!
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .alphaClickable {
                                        onValueChange(key)
                                        expanded = false
                                    }
                                    .background(if(currentValue == key) color.copy(alpha=0.2f) else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = key,
                                    tint = if(currentValue == key) color else Color.Gray.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// --- COMPONENT: LEFT DOCK (TABS - "BOOKMARKS") ---
@Composable
private fun LeftTabDock(
    selectedTab: JournalTab,
    onTabSelected: (JournalTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(start = 0.dp), // Attach to edge
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Visual Bookmark
        BookmarkTab(
            icon = Icons.Default.Image,
            isSelected = selectedTab == JournalTab.VISUAL,
            color = Color(0xFF8D6E63), // Leather Brown
            onClick = { onTabSelected(JournalTab.VISUAL) }
        )
        
        // Text Bookmark
        BookmarkTab(
            icon = Icons.Default.Description,
            isSelected = selectedTab == JournalTab.TEXT,
            color = Color(0xFF6D4C41), // Darker Leather
            onClick = { onTabSelected(JournalTab.TEXT) }
        )
    }
}

@Composable
fun BookmarkTab(
    icon: ImageVector,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val width by animateDpAsState(if (isSelected) 64.dp else 48.dp)
    val offsetX by animateDpAsState(if (isSelected) 0.dp else (-8).dp)

    Surface(
        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
        color = color,
        shadowElevation = 4.dp,
        modifier = Modifier
            .width(width)
            .height(48.dp)
            .offset(x = offsetX)
            .alphaClickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.CenterEnd) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFEFEBE9),
                modifier = Modifier.padding(end = 12.dp).size(24.dp)
            )
        }
    }
}

// --- COMPONENT: RIGHT DOCK (TOOLS - "PENCIL CASE") ---
@Composable
private fun RightToolDock(
    mode: WhiteboardMode,
    currentTool: WhiteboardTool,
    dayEntryId: Int,
    stickers: MutableList<Sticker>,
    currentColor: Color,
    currentWidth: Float,
    onModeChange: (WhiteboardMode) -> Unit,
    onToolChange: (WhiteboardTool) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onMarkerModeChange: (Boolean) -> Unit,
    isMarkerMode: Boolean,
    onStickerAdded: (Int) -> Unit,
    selectedStickerId: Int?,
    selectedLayer: Int,
    onSelectLayer: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isStickerBoxOpen by remember { mutableStateOf(false) }
    
    // Auto-close box if mode changes away from STICKER
    LaunchedEffect(mode) {
        if (mode != WhiteboardMode.STICKER) isStickerBoxOpen = false
    }

    // Position it
    Box(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
        
        // STICKER BOX POPUP (Left of Dock)
        AnimatedVisibility(
            visible = isStickerBoxOpen,
            enter = scaleIn(transformOrigin = TransformOrigin(1f, 0.5f)) + fadeIn(),
            exit = scaleOut(transformOrigin = TransformOrigin(1f, 0.5f)) + fadeOut(),
            modifier = Modifier.padding(end = 90.dp) // Offset to left of dock
        ) {
            StickerPickerBox(
                onEmojiSelected = { emoji ->
                    val newId = -(System.currentTimeMillis().toInt())
                    // Center roughly at 0.425, 0.425 (Normalized center for 1.0 Document)
                    val newSticker = Sticker(id = newId, dayEntryId = dayEntryId, x=0.425f, y=0.425f, scale=1f, rotation=Random.nextFloat()*20-10, contentPath="emoji:$emoji", type="emoji", layer = selectedLayer)
                    stickers.add(newSticker)
                    onStickerAdded(newSticker.id) // Auto-select new sticker
                    // isStickerBoxOpen = false // Keep open
                },
                onPhotoImportCall = {
                   val fileDialog = java.awt.FileDialog(null as java.awt.Frame?, "Select Image", java.awt.FileDialog.LOAD)
                    fileDialog.isVisible = true 
                    
                    if (fileDialog.file != null) {
                        val originalFile = java.io.File(fileDialog.directory, fileDialog.file)
                        val newId = -(System.currentTimeMillis().toInt())
                        stickers.add(Sticker(id = newId, dayEntryId = dayEntryId, x=0.425f, y=0.425f, scale=1f, rotation=0f, contentPath="LOADING", type="image", layer = selectedLayer))
                        onStickerAdded(newId) // Auto-select placeholder
                        
                        scope.launch(Dispatchers.IO) {
                            try {
                                val stickersDir = java.io.File(System.getProperty("user.home"), ".visuallog/stickers")
                                if (!stickersDir.exists()) stickersDir.mkdirs()
                                
                                var finalFile: java.io.File? = null
                                try {
                                    finalFile = drawing.ImageOptimizationUtils.optimizeImage(originalFile, stickersDir)
                                } catch (e: Exception) { e.printStackTrace() }
                                
                                if (finalFile == null) {
                                    val ext = originalFile.extension.ifEmpty { "png" }
                                    val fallbackFile = java.io.File(stickersDir, "sticker_raw_${System.currentTimeMillis()}.$ext")
                                    originalFile.copyTo(fallbackFile, overwrite = true)
                                    finalFile = fallbackFile
                                }
                                
                                val finalPath = finalFile.absolutePath
                                data.RecentStickerManager.add(finalPath) // Add to recents
                                
                                withContext(Dispatchers.Main) {
                                    val idx = stickers.indexOfFirst { it.id == newId }
                                    if (idx != -1) stickers[idx] = stickers[idx].copy(contentPath = finalPath)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    val idx = stickers.indexOfFirst { it.id == newId }
                                    if (idx != -1) stickers.removeAt(idx)
                                }
                            }
                        }
                    } 
                },
                onRecentSelected = { path ->
                    val newId = -(System.currentTimeMillis().toInt())
                    stickers.add(Sticker(id = newId, dayEntryId = dayEntryId, x=0.425f, y=0.425f, scale=1f, rotation=0f, contentPath=path, type="image", layer = selectedLayer))
                    onStickerAdded(newId)
                }
            )
        }

        // View -> Edit Transition
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "ToolbarTransition"
        ) { currentMode ->
            if (currentMode == WhiteboardMode.VIEW) {
                // COMPACT: Edit Fab
                FloatingActionIcon(
                    icon = Icons.Default.Edit,
                    contentDescription = "Edit",
                    onClick = { 
                        onModeChange(WhiteboardMode.DRAW)
                        onToolChange(WhiteboardTool.PEN) 
                    },
                    containerColor = Color(0xFF5D4E3C), // Dark Wood
                    contentColor = Color(0xFFF9F5F0)
                )
            } else {
                // EXPANDED: "Pencil Case" Tray
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFE6DBC6), // Light Wood/Sand
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD4C4B0))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        // 1. PEN
                        ToolIcon(
                            icon = Icons.Default.Edit,
                            label = "Pen",
                            isActive = currentTool == WhiteboardTool.PEN && currentMode == WhiteboardMode.DRAW,
                            activeColor = currentColor, // Use current ink color
                            onClick = { onModeChange(WhiteboardMode.DRAW); onToolChange(WhiteboardTool.PEN) }
                        )

                         // --- PEN SETTINGS OVERLAY ---
                         AnimatedVisibility(
                             visible = currentTool == WhiteboardTool.PEN && currentMode == WhiteboardMode.DRAW,
                             enter = expandVertically() + fadeIn(),
                             exit = shrinkVertically() + fadeOut()
                         ) {
                             Column(
                                 verticalArrangement = Arrangement.spacedBy(8.dp),
                                 horizontalAlignment = Alignment.CenterHorizontally,
                                 modifier = Modifier.padding(bottom = 8.dp)
                             ) {
                                 // Type
                                 BrushTypeSelector(
                                     isMarker = isMarkerMode,
                                     onModeChange = onMarkerModeChange
                                 )

                                 // Size
                                 BrushSizeSelector(
                                     currentWidth = currentWidth,
                                     onWidthChange = onWidthChange,
                                     color = currentColor
                                 )
                                 
                                 // Colors
                                 ColorPalette(
                                     currentColor = currentColor,
                                     onColorChange = onColorChange
                                 )
                                 
                                 HorizontalDivider(modifier = Modifier.width(32.dp), color=Color.Black.copy(alpha=0.1f))
                             }
                         }
                        
                        // --- LAYER SELECTOR ---
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp).width(32.dp), color=Color.Black.copy(alpha=0.1f))
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title
                    Text("Layer", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    
                    // 3-2-1 (Ordered Top to Bottom)
                    (2 downTo 0).forEach { i ->
                        val layerLabel = when(i) {
                            2 -> "Top"
                            1 -> "Mid"
                            else -> "Bot"
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp, 32.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, if(selectedLayer == i) Color(0xFF5D4E3C) else Color.Transparent, RoundedCornerShape(4.dp))
                                .background(if(selectedLayer == i) Color(0xFFE6DBC6).copy(alpha=0.5f) else Color.Transparent)
                                .clickable { onSelectLayer(i) },
                            contentAlignment = Alignment.Center
                        ) {
                             Text(
                                 text = layerLabel,
                                 style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                 color = if(selectedLayer == i) Color(0xFF5D4E3C) else Color.Gray.copy(alpha=0.6f)
                             )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp).width(32.dp), color=Color.Black.copy(alpha=0.1f))

                // 2. ERASER
                        ToolIcon(
                            icon = Icons.Default.AutoFixHigh, 
                            label = "Eraser",
                            isActive = currentTool == WhiteboardTool.ERASER && currentMode == WhiteboardMode.DRAW,
                            activeColor = Color(0xFF8D6E63), // Eraser Brown
                            onClick = { onModeChange(WhiteboardMode.DRAW); onToolChange(WhiteboardTool.ERASER) }
                        )
                        
                        // 3. SWEEP
                        ToolIcon(
                            icon = Icons.Default.DeleteSweep,
                            label = "Sweep",
                            isActive = currentTool == WhiteboardTool.SEGMENT_ERASER && currentMode == WhiteboardMode.DRAW,
                            activeColor = Color(0xFFD84315), 
                            onClick = { onModeChange(WhiteboardMode.DRAW); onToolChange(WhiteboardTool.SEGMENT_ERASER) }
                        )
                        
                        // DIVIDER
                        HorizontalDivider(
                            modifier = Modifier.width(24.dp),
                            thickness = 1.dp,
                            color = Color(0xFFBCAAA4).copy(alpha = 0.5f)
                        )
    
                        // 4. SELECT / EDIT TOOL (Enter Mode, No Box)
                        ToolIcon(
                            icon = androidx.compose.material.icons.Icons.Default.TouchApp, 
                            label = "Select",
                            isActive = currentMode == WhiteboardMode.STICKER && !isStickerBoxOpen,
                            activeColor = Color(0xFF2196F3), // Selection Blue
                            onClick = { 
                                onModeChange(WhiteboardMode.STICKER)
                                isStickerBoxOpen = false
                            }
                        )

                        // 5. ADD STICKER TOOL (Opens Box)
                        ToolIcon(
                            icon = Icons.Default.EmojiEmotions, 
                            label = "Add",
                            isActive = isStickerBoxOpen, // Active only if box is open
                            activeColor = Color(0xFFFBC02D), // Sticker Yellow
                            onClick = { 
                                if (currentMode != WhiteboardMode.STICKER) {
                                    onModeChange(WhiteboardMode.STICKER)
                                    isStickerBoxOpen = true 
                                } else {
                                    isStickerBoxOpen = !isStickerBoxOpen 
                                }
                            }
                        )
                        
                        // DIVIDER
                        HorizontalDivider(
                            modifier = Modifier.width(24.dp),
                            thickness = 1.dp,
                            color = Color(0xFFBCAAA4).copy(alpha = 0.5f)
                        )
    
                        // CLOSE / DONE
                        ToolIcon(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            label = "Done",
                            isActive = false,
                            onClick = { onModeChange(WhiteboardMode.VIEW) },
                            activeColor = Color(0xFF5D4E3C),
                            inactiveColor = Color(0xFF8D6E63)
                        )
                    }
                }
            }
        }
    }
}

@Composable

fun StickerPickerBox(
    onEmojiSelected: (String) -> Unit,
    onPhotoImportCall: () -> Unit,
    onRecentSelected: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Emoji, 1: Recent

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFF8E1), // Creamy Yellow Box
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0C090)),
        modifier = Modifier.width(260.dp).heightIn(max = 360.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with Tabs
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Sticker Box",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF795548)
                )

                // Simple Tab Switcher
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE0C090).copy(alpha=0.3f))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Emoji", "Recent").forEachIndexed { index, title ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if(selectedTab == index) Color(0xFF8D6E63) else Color.Transparent)
                                .clickable { selectedTab = index }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                title, 
                                style = MaterialTheme.typography.labelSmall,
                                color = if(selectedTab == index) Color.White else Color(0xFF795548)
                            )
                        }
                    }
                }
            }
            
            if (selectedTab == 0) {
                // Emoji Grid
                val emojis = listOf(
                    "⭐", "🌟", "✨", "❤️", "🧡", "💛", 
                    "🌿", "🌵", "🌻", "🌸", "🍔", "☕", 
                    "🍰", "🍎", "📌", "📎", "✈️", "📷", 
                    "🎵", "💡", "🗓️", "✅", "🎉", "🔥"
                )
                
                // Lazy Grid (Simple Rows)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                ) {
                    emojis.chunked(6).forEach { rowEmojis ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            rowEmojis.forEach { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .alphaClickable { onEmojiSelected(emoji) }
                                        .background(Color.White.copy(alpha=0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                // Recent Import Grid
                val recents = data.RecentStickerManager.recentStickers
                if (recents.isEmpty()) {
                    Box(
                         modifier = Modifier.weight(1f).fillMaxWidth(),
                         contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No recent stickers", 
                            style = MaterialTheme.typography.bodySmall, 
                            color = Color.Gray
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                    ) {
                        recents.chunked(3).forEach { rowPaths ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowPaths.forEach { path ->
                                    Box(
                                        modifier = Modifier
                                            .size(70.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .border(1.dp, Color(0xFFE0C090), RoundedCornerShape(4.dp))
                                            .background(Color.White)
                                            .clickable { onRecentSelected(path) }
                                    ) {
                                        // Use AsyncThumbnail logic via a simpler composable for just the image
                                        AsyncThumbnailFullscreen(path)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFD7CCC8))
            Spacer(modifier = Modifier.height(8.dp))
            
            // Import Button
            Surface(
                color = Color(0xFF8D6E63),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .alphaClickable(onClick = onPhotoImportCall)
            ) {
                Row(
                   modifier = Modifier.padding(vertical = 12.dp),
                   horizontalArrangement = Arrangement.Center,
                   verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Import Photo", color = Color.White)
                }
            }
        }
    }
}

// --- SHARED COMPONENTS ---

@Composable
fun FloatingHeader(
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .zIndex(10f), // Ensure above everything
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingActionIcon(
            icon = Icons.AutoMirrored.Filled.ArrowBack, // Fixed Icon reference
            contentDescription = "Back",
            onClick = onBack,
            containerColor = Color(0xFFFAEBD7), // Antique White
            contentColor = Color(0xFF5D4E3C)
        )
        FloatingActionIcon(
            icon = Icons.Default.Check,
            contentDescription = "Save",
            containerColor = Color(0xFF5D4E3C), // Dark Wood
            contentColor = Color(0xFFF9F5F0), // Cream
            onClick = onSave
        )
    }
}

@Composable
fun FloatingActionIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    containerColor: Color = Color.White,
    contentColor: Color = Color.Black
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 6.dp, 
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha=0.1f)),
        modifier = Modifier
            .size(56.dp)
            .alphaClickable(onClick = onClick) 
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun ToolIcon(
    icon: ImageVector,
    label: String? = null,
    isActive: Boolean,
    onClick: () -> Unit,
    activeColor: Color = Color(0xFF5D4E3C),
    inactiveColor: Color = Color(0xFFA1887F)
) {
    val containerColor by animateColorAsState(if (isActive) activeColor else Color.Transparent, label = "container")
    val contentColor by animateColorAsState(if (isActive) Color(0xFFF9F5F0) else inactiveColor, label = "content")
    val scale by animateFloatAsState(if (isActive) 1.1f else 1f, label = "scale")
    val shadow by animateDpAsState(if (isActive) 4.dp else 0.dp, label = "shadow")
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = shadow,
            border = if (!isActive) androidx.compose.foundation.BorderStroke(1.dp, Color.Transparent) else null,
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .alphaClickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Returns true if line segment (p1, p2) intersects with (p3, p4).
 */
fun lineSegmentsIntersect(p1: Offset, p2: Offset, p3: Offset, p4: Offset): Boolean {
    fun ccw(a: Offset, b: Offset, c: Offset): Boolean {
        return (c.y - a.y) * (b.x - a.x) > (b.y - a.y) * (c.x - a.x)
    }
    return ccw(p1, p3, p4) != ccw(p2, p3, p4) && ccw(p1, p2, p3) != ccw(p1, p2, p4)
}

// --- NEW COMPONENTS: BRUSH CONTROLS ---

@Composable
fun BrushSizeSelector(
    currentWidth: Float,
    onWidthChange: (Float) -> Unit,
    color: Color
) {
    val sizes = listOf(5f, 15f, 30f)
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sizes.forEach { size ->
            val isSelected = kotlin.math.abs(currentWidth - size) < 1f
            Box(
                 modifier = Modifier
                     .size(24.dp)
                     .clip(CircleShape)
                     .clickable { onWidthChange(size) }
                     .background(if(isSelected) Color.Black.copy(alpha=0.05f) else Color.Transparent),
                 contentAlignment = Alignment.Center
            ) {
                 Canvas(modifier = Modifier.size(size.coerceAtMost(20f).dp)) {
                     drawCircle(color = color)
                 }
            }
        }
    }
}

@Composable
fun ColorPalette(
    currentColor: Color,
    onColorChange: (Color) -> Unit
) {
    // Elegant Palette
    val colors = listOf(
        Color(0xFF222222), // Black
        Color(0xFFFFFFFF), // White
        Color(0xFFD32F2F), // Red
        Color(0xFF1976D2), // Blue
        Color(0xFF388E3C), // Green
        Color(0xFFFBC02D), // Yellow
        Color(0xFF7B1FA2), // Purple
        Color(0xFFFF6D00), // Orange
        // Pastels
        Color(0xFFB0B0B0), // Grey
        Color(0xFFEF9A9A), // Pale Red
        Color(0xFF90CAF9), // Pale Blue
        Color(0xFFA5D6A7), // Pale Green
        Color(0xFFFFF59D), // Pale Yellow
        Color(0xFFCE93D8), // Pale Purple
        Color(0xFFFFCC80)  // Pale Orange
    )
    
    // We'll show them in a mini grid or column if space is tight, but here a wrapped row/column is best
    // Given the vertical tool dock, let's use a Column of Rows
    
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        colors.chunked(3).forEach { rowColors ->
             Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                 rowColors.forEach { color ->
                     val isSelected = currentColor == color
                     Box(
                         modifier = Modifier
                             .size(20.dp)
                             .clip(CircleShape)
                             .background(color)
                             .border(1.dp, if(isSelected) Color.White else Color.Transparent, CircleShape)
                             .border(2.dp, if(isSelected) Color.Black.copy(alpha=0.2f) else Color.Transparent, CircleShape)
                             .clickable { onColorChange(color) }
                     )
                 }
             }
        }
    }
}

@Composable
fun BrushTypeSelector(
    isMarker: Boolean,
    onModeChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha=0.5f))
            .border(1.dp, Color(0xFFD4C4B0), RoundedCornerShape(8.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Pen
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if(!isMarker) Color(0xFF5D4E3C) else Color.Transparent)
                .clickable { onModeChange(false) },
            contentAlignment = Alignment.Center
        ) {
             Icon(
                 Icons.Default.Edit, 
                 null, 
                 tint = if(!isMarker) Color(0xFFF9F5F0) else Color(0xFF8D6E63),
                 modifier = Modifier.size(18.dp)
             )
        }
        
        // Marker
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if(isMarker) Color(0xFF5D4E3C) else Color.Transparent)
                .clickable { onModeChange(true) },
            contentAlignment = Alignment.Center
        ) {
             Icon(
                 Icons.Default.Brush, // Or AutoFixNormal
                 null, 
                 tint = if(isMarker) Color(0xFFF9F5F0) else Color(0xFF8D6E63),
                 modifier = Modifier.size(18.dp)
             )
        }
    }
}
