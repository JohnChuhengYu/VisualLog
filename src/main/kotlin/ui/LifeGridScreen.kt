package ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.random.Random
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import data.DayEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.Locale

// --- DATA MODELS ---
data class ThumbnailInfo(
    val bitmap: ImageBitmap,
    val isTopLeftDark: Boolean,
    val isTopLeftBusy: Boolean,
    val isBottomRightDark: Boolean,
    val isBottomRightBusy: Boolean
)

data class CornerAnalysis(
    val isDark: Boolean,
    val isBusy: Boolean
)

// --- Thumnail cache and helper models remain ---

// --- GLOBAL CACHE (Singleton, JVM Compatible) ---
object ThumbnailCache {
    private val cache = Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, ThumbnailInfo>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ThumbnailInfo>?): Boolean {
                return size > 100 
            }
        }
    )
    
    fun get(path: String): ThumbnailInfo? = cache[path]
    fun put(path: String, info: ThumbnailInfo) { cache[path] = info }
}

@Composable
fun LifeGridScreen(
    startDate: LocalDate = LocalDate.of(2024, 1, 1),
    dayEntries: Map<String, DayEntry>,
    onDayClick: (LocalDate, androidx.compose.ui.geometry.Offset, androidx.compose.ui.unit.IntSize) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: LazyGridState = rememberLazyGridState()
) {
    // 1. OPTIMIZATION: Heavy Date Calculation Check.
    // Use remember with keys for reactive updates
    val gridState = remember(startDate, dayEntries.size) {
        val today = LocalDate.now()
        // Prevent negative days if startDate is in the future
        val daysBetween = ChronoUnit.DAYS.between(startDate, today)
        val safeDaysBetween = if (daysBetween < 0) 0 else daysBetween
        val totalDays = safeDaysBetween.toInt() + 1
        
        val list = (0 until totalDays).map { startDate.plusDays(it.toLong()) }
        list to totalDays
    }
    val (days, totalDays) = gridState
    
    // Background Edit States
    var isEditBackgroundMode by remember { mutableStateOf(false) }
    val bgState = GlobalBackgroundState
    val scope = rememberCoroutineScope()
    
    var selectedStickerId by remember { mutableStateOf<Int?>(null) }
    var selectedLayer by remember { mutableStateOf(1) }
    var isStickerBoxOpen by remember { mutableStateOf(false) }
    
    // Mode for background editing specifically
    var bgEditMode by remember { mutableStateOf(WhiteboardMode.DRAW) }

    // Canvas dimensions for normalized coordinate calculations
    var canvasWidth by remember { mutableStateOf(1f) }
    var canvasHeight by remember { mutableStateOf(1f) }
    var invalidator by remember { mutableStateOf(0) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF9F5F0)) 
    ) {
        val isNarrow = maxWidth < 600.dp
        
        // --- LAYER 1: FIXED BACKGROUND --- // Increased threshold to optimize blocking content
        
        // Dot Grid Pattern
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
        
        // Background Layers
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { 
                    canvasWidth = it.width.toFloat().coerceAtLeast(1f)
                    canvasHeight = it.height.toFloat().coerceAtLeast(1f)
                }
        ) {
            // 1. Drawing Layer (Baked Bitmaps)
            for (layerId in 0..2) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    @Suppress("UNUSED_VARIABLE")
                    val signal = invalidator // Trigger redraw
                    val buffer = bgState.layerManager.buffers[layerId]
                    
                    // Fix: Scale the High-Res buffer to fit the current screen size
                    // Background drawing is scaled back from 2160px buffer to screen px
                    val drawScaleX = size.width / bgState.layerManager.bufferScale
                    val drawScaleY = size.height / bgState.layerManager.bufferScale
                    
                    drawContext.canvas.save()
                    drawContext.canvas.scale(drawScaleX, drawScaleY)
                    drawImage(buffer)
                    drawContext.canvas.restore()
                    
                    // Live Stroke
                    if (isEditBackgroundMode && layerId == selectedLayer) { 
                        val color = when (bgState.currentTool) {
                            WhiteboardTool.ERASER -> Color.White.copy(alpha=0.5f)
                            WhiteboardTool.SEGMENT_ERASER -> Color.Red.copy(alpha=0.2f)
                            else -> if(bgState.isMarkerMode) bgState.currentStrokeColor.copy(alpha=0.4f) else bgState.currentStrokeColor
                        }
                        
                        drawContext.canvas.save()
                        drawContext.canvas.scale(size.width, size.height)
                        if (bgState.strokeEngine.currentPath.isEmpty.not()) {
                            drawPath(
                                bgState.strokeEngine.currentPath, 
                                color, 
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = bgState.strokeEngine.strokeWidth / 1000f,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                        }
                        drawContext.canvas.restore()
                    }
                }
            }
            
            // 2. Interaction Layer for Drawing (Above Canvas, Below Stickers)
            if (isEditBackgroundMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(bgState.currentTool, bgEditMode) {
                            if (bgEditMode != WhiteboardMode.DRAW) return@pointerInput
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val type = event.type
                                    val isDown = event.changes.any { it.pressed }
                                    
                                    if (isDown && (type == PointerEventType.Move || type == PointerEventType.Press)) {
                                        val pos = event.changes.last().position
                                        // Normalize based on actual box dimensions
                                        val nx = pos.x / canvasWidth
                                        val ny = pos.y / canvasHeight
                                        
                                        bgState.inputManager.onRawPoint(drawing.RawPoint(nx, ny, event.changes.last().uptimeMillis))
                                        bgState.strokeEngine.process(event.changes.map { 
                                            drawing.RawPoint(it.position.x / canvasWidth, it.position.y / canvasHeight, it.uptimeMillis) 
                                        })
                                        invalidator++
                                    }
                                    
                                    if (type == PointerEventType.Release) {
                                        val path = bgState.strokeEngine.getFinalPath()
                                        if (!path.isEmpty) {
                                            if (bgState.currentTool == WhiteboardTool.SEGMENT_ERASER) {
                                                // --- SEGMENT ERASER LOGIC ---
                                                val eraserPoints = bgState.strokeEngine.rawPoints.map { Offset(it.x, it.y) }
                                                
                                                if (eraserPoints.size >= 2) {
                                                    val eraserSegments = eraserPoints.windowed(2) { (a, b) -> a to b }
                                                    val eraserBounds = Rect(
                                                        eraserPoints.minOf { it.x }, eraserPoints.minOf { it.y },
                                                        eraserPoints.maxOf { it.x }, eraserPoints.maxOf { it.y }
                                                    ).inflate(0.005f)
                                                    
                                                    val toRemove = bgState.strokes.filter { s ->
                                                        s.layer == selectedLayer && !s.isEraser
                                                    }.filter { s ->
                                                        if (s.points.size < 2) return@filter false
                                                        if (!eraserBounds.overlaps(s.bounds)) return@filter false
                                                        val strokeSegments = s.points.windowed(2) { (a, b) -> a to b }
                                                        strokeSegments.any { sSeg ->
                                                            eraserSegments.any { eSeg ->
                                                                lineSegmentsIntersect(sSeg.first, sSeg.second, eSeg.first, eSeg.second)
                                                            }
                                                        }
                                                    }
                                                    
                                                    if (toRemove.isNotEmpty()) {
                                                        bgState.strokes.removeAll(toRemove)
                                                        bgState.layerManager.restoreFromPaths(bgState.strokes)
                                                    }
                                                }
                                                invalidator++ // Ensure red trail disappears
                                            } else {
                                                val style = bgState.strokeEngine.currentStyle
                                                val finalColor = if(bgState.isMarkerMode && bgState.currentTool != WhiteboardTool.ERASER) bgState.currentStrokeColor.copy(alpha=0.4f) else if (bgState.currentTool == WhiteboardTool.ERASER) Color.White else bgState.currentStrokeColor
                                                val bm = if(bgState.currentTool == WhiteboardTool.ERASER) androidx.compose.ui.graphics.BlendMode.Clear else androidx.compose.ui.graphics.BlendMode.SrcOver
                                                val isEraser = bgState.currentTool == WhiteboardTool.ERASER
                                                
                                                bgState.layerManager.bakeStroke(path, style, finalColor, bm, layer = selectedLayer)
                                                
                                                val savedPoints = bgState.strokeEngine.rawPoints.map { Offset(it.x, it.y) }
                                                bgState.strokes.add(data.Stroke(
                                                    points = savedPoints,
                                                    isEraser = isEraser,
                                                    width = bgState.strokeEngine.strokeWidth,
                                                    color = if(isEraser) 0 else finalColor.toArgb(),
                                                    layer = selectedLayer
                                                ))
                                            }
                                        }
                                        bgState.strokeEngine.clear()
                                        invalidator++ // Trigger redraw to clear trail
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                )
            }

            // 3. Sticker Layer (Top-most in background)
            bgState.stickers.forEach { sticker ->
                key(sticker.id) {
                    SamsungSticker(
                        sticker = sticker,
                        canvasWidth = canvasWidth,
                        canvasHeight = canvasHeight,
                        useFixedSize = true,
                        isSelected = isEditBackgroundMode && (selectedStickerId == sticker.id),
                        enabled = isEditBackgroundMode, // Always allow interaction if in edit mode
                        onClick = { 
                            selectedStickerId = sticker.id 
                            selectedLayer = sticker.layer
                            bgEditMode = WhiteboardMode.STICKER // Switch to sticker mode on click
                        },
                        onTransform = { x, y, s, r ->
                            val idx = bgState.stickers.indexOfFirst { it.id == sticker.id }
                            if (idx != -1) {
                                val current = bgState.stickers[idx]
                                bgState.stickers[idx] = current.copy(x=x, y=y, scale=s, rotation=r)
                            }
                        },
                        onDelete = {
                            bgState.stickers.removeAll { it.id == sticker.id }
                            if (selectedStickerId == sticker.id) selectedStickerId = null
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !isEditBackgroundMode,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // --- PREMIUM GRID WITH YEAR AND MONTH HEADERS ---
            LazyVerticalGrid(
                state = scrollState,
                columns = GridCells.Adaptive(minSize = 160.dp), // Significantly larger cells
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { clip = true }
                    .padding(horizontal = 24.dp), 
                contentPadding = PaddingValues(
                    top = if (isNarrow) 24.dp else 140.dp, // Dynamic padding
                    bottom = 100.dp
                ), // Top padding for sticker space
                horizontalArrangement = Arrangement.spacedBy(24.dp), // More space for rotation
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 0. RESPONIVE HEADER (Inline for narrow screens)
                if (isNarrow) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            JournalTitleSticker(totalDays = totalDays, modifier = Modifier.rotate(-2f))
                        }
                    }
                }

                // Group days by year first, then by month
                val daysByYear = days.groupBy { it.year }
                
                daysByYear.forEach { (year, yearDays) ->
                    // Group months within this year
                    val monthsInYear = yearDays.groupBy { it.monthValue }
                    val monthsList = monthsInYear.toList()
                    
                    monthsList.forEachIndexed { monthIndex, (month, monthDays) ->
                        // Header with Washi Tape style
                        item(
                            key = "header_${year}_$month",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            val firstDay = monthDays.first()
                            
                            // Random tape color derived from month/year
                            val tapeColors = listOf(
                                Color(0xFFFFB7B2), // Pastel Red
                                Color(0xFFFFDAC1), // Pastel Orange
                                Color(0xFFE2F0CB), // Pastel Green
                                Color(0xFFB5EAD7), // Pastel Mint
                                Color(0xFFC7CEEA)  // Pastel Purple
                            )
                            val colorIndex = (year + month) % tapeColors.size
                            val tapeColor = tapeColors[colorIndex]
                            
                            // Random rotation for tape
                            val rotation = remember(year, month) {
                                Random((year + month).hashCode()).nextFloat() * 2f - 1f
                            }
                            
                            val headerIsNarrow = this@BoxWithConstraints.maxWidth < 450.dp
                                
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = if (year == daysByYear.keys.first() && monthIndex == 0) 0.dp 
                                              else if (monthIndex == 0) 48.dp 
                                              else 24.dp,
                                        bottom = 16.dp
                                    )
                            ) {
                                if (headerIsNarrow) {
                                    // STACKED for narrow screens
                                    Column(
                                        modifier = Modifier.padding(start = 16.dp)
                                    ) {
                                        if (monthIndex == 0) {
                                            YearTapeSticker(year, rotation)
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                        MonthTapeSticker(firstDay.month.name, tapeColor, rotation)
                                    }
                                } else {
                                    // CENTERED ROW for wide screens
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (monthIndex == 0) {
                                            YearTapeSticker(year, rotation)
                                            Spacer(modifier = Modifier.width(16.dp))
                                        }
                                        MonthTapeSticker(firstDay.month.name, tapeColor, rotation)
                                    }
                                }
                            }
                        }
                        
                        // Day cells for this month
                        items(
                            items = monthDays,
                            key = { it.toString() }, 
                            contentType = { "day_cell" } 
                        ) { date ->
                            LifeDayCell(
                                date = date,
                                dayEntry = dayEntries[date.toString()],
                                onClick = { centerOffset, size ->
                                     onDayClick(date, centerOffset, size)
                                }
                            )
                        }
                    }
                }
            }
            
            // --- HEADER STICKER (Fixed Overlay for Wide Screens) ---
            if (!isNarrow) {
                JournalTitleSticker(
                    totalDays = totalDays,
                    isFixed = true,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 24.dp, top = 20.dp)
                        .rotate(-2f)
                )
            }
        }
    }

        // --- LAYER 3: CONTROLS ---
        if (isEditBackgroundMode) {
             // BG Toolbar (Adapted RightToolDock logic)
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                 // Sticker Box
                AnimatedVisibility(
                    visible = isStickerBoxOpen,
                    enter = scaleIn(transformOrigin = TransformOrigin(1f, 0.5f)) + fadeIn(),
                    exit = scaleOut(transformOrigin = TransformOrigin(1f, 0.5f)) + fadeOut(),
                    modifier = Modifier.padding(top = 96.dp, end = 124.dp) // Increased end padding
                ) {
                    StickerPickerBox(
                        onEmojiSelected = { emoji ->
                            val newId = -(System.currentTimeMillis().toInt())
                            // Initialize at top-left 20% by default (Normalized)
                            bgState.stickers.add(data.Sticker(id = newId, dayEntryId = -1, x=0.2f, y=0.2f, scale=1f, rotation=0f, contentPath="emoji:$emoji", type="emoji", layer = selectedLayer))
                            selectedStickerId = newId
                            bgEditMode = WhiteboardMode.STICKER
                        },
                        onPhotoImportCall = {
                            val fileDialog = java.awt.FileDialog(null as java.awt.Frame?, "Select Image", java.awt.FileDialog.LOAD)
                            fileDialog.isVisible = true 
                            if (fileDialog.file != null) {
                                val originalFile = java.io.File(fileDialog.directory, fileDialog.file)
                                val newId = -(System.currentTimeMillis().toInt())
                                // Initialize at top-left 20% by default (Normalized)
                                bgState.stickers.add(data.Sticker(id = newId, dayEntryId = -1, x=0.2f, y=0.2f, scale=1f, rotation=0f, contentPath="LOADING", type="image", layer = selectedLayer))
                                selectedStickerId = newId
                                bgEditMode = WhiteboardMode.STICKER
                                
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val stickersDir = java.io.File(System.getProperty("user.home"), ".visuallog/stickers")
                                        if (!stickersDir.exists()) stickersDir.mkdirs()
                                        val fallbackFile = java.io.File(stickersDir, "bg_sticker_${System.currentTimeMillis()}.${originalFile.extension}")
                                        originalFile.copyTo(fallbackFile, overwrite = true)
                                        
                                        val finalPath = fallbackFile.absolutePath
                                        data.RecentStickerManager.add(finalPath)
                                        
                                        withContext(Dispatchers.Main) {
                                            val idx = bgState.stickers.indexOfFirst { it.id == newId }
                                            if (idx != -1) bgState.stickers[idx] = bgState.stickers[idx].copy(contentPath = finalPath)
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                        },
                        onRecentSelected = { path ->
                            val newId = -(System.currentTimeMillis().toInt())
                            bgState.stickers.add(data.Sticker(id = newId, dayEntryId = -1, x=0.2f, y=0.2f, scale=1f, rotation=0f, contentPath=path, type="image", layer = selectedLayer))
                            selectedStickerId = newId
                            bgEditMode = WhiteboardMode.STICKER
                        }
                    )
                }

                // Main Tool Dock
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFE6DBC6),
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(top = 96.dp, end = 24.dp, bottom = 24.dp)
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .verticalScroll(scrollState)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. PEN
                        ToolIcon(
                            icon = Icons.Default.Edit,
                            label = "Pen",
                            isActive = bgEditMode == WhiteboardMode.DRAW && bgState.currentTool == WhiteboardTool.PEN,
                            activeColor = bgState.currentStrokeColor,
                            onClick = { 
                                bgEditMode = WhiteboardMode.DRAW
                                bgState.currentTool = WhiteboardTool.PEN
                                bgState.strokeEngine.strokeWidth = bgState.currentStrokeWidth
                                isStickerBoxOpen = false
                            }
                        )
                        
                        AnimatedVisibility(visible = bgEditMode == WhiteboardMode.DRAW && bgState.currentTool == WhiteboardTool.PEN) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                BrushTypeSelector(bgState.isMarkerMode) { bgState.isMarkerMode = it }
                                ColorPalette(bgState.currentStrokeColor) { bgState.currentStrokeColor = it }
                                
                                Spacer(Modifier.height(4.dp))
                                Text("Size", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8D6E63))
                                BrushSizeSelector(
                                    currentWidth = bgState.currentStrokeWidth * 1000f,
                                    onWidthChange = { 
                                        bgState.currentStrokeWidth = it / 1000f 
                                        bgState.strokeEngine.strokeWidth = it
                                    },
                                    color = bgState.currentStrokeColor
                                )
                            }
                        }

                        // Layers
                        HorizontalDivider(modifier = Modifier.width(32.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Layer", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8D6E63))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                (0..2).forEach { id ->
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(if(selectedLayer == id) Color(0xFF5D4E3C) else Color.White.copy(alpha=0.5f))
                                            .clickable { selectedLayer = id },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = id.toString(),
                                            color = if(selectedLayer == id) Color.White else Color(0xFF5D4E3C),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.wrapContentSize(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.width(32.dp))

                        // 2. ERASER
                        ToolIcon(
                            icon = Icons.Default.AutoFixHigh,
                            label = "Eraser",
                            isActive = bgEditMode == WhiteboardMode.DRAW && bgState.currentTool == WhiteboardTool.ERASER,
                            onClick = { 
                                bgEditMode = WhiteboardMode.DRAW
                                bgState.currentTool = WhiteboardTool.ERASER
                                bgState.strokeEngine.strokeWidth = 40f
                                isStickerBoxOpen = false
                            }
                        )
                        
                        // 2.5 SWEEP (SEGMENT ERASER)
                        ToolIcon(
                            icon = Icons.Default.DeleteSweep,
                            label = "Sweep",
                            isActive = bgEditMode == WhiteboardMode.DRAW && bgState.currentTool == WhiteboardTool.SEGMENT_ERASER,
                            activeColor = Color(0xFFD84315),
                            onClick = { 
                                bgEditMode = WhiteboardMode.DRAW
                                bgState.currentTool = WhiteboardTool.SEGMENT_ERASER
                                bgState.strokeEngine.strokeWidth = 10f
                                isStickerBoxOpen = false
                            }
                        )
                        
                        // 3. STICKER
                        ToolIcon(
                            icon = Icons.Default.EmojiEmotions,
                            label = "Sticker",
                            isActive = bgEditMode == WhiteboardMode.STICKER,
                            onClick = { 
                                bgEditMode = WhiteboardMode.STICKER
                                isStickerBoxOpen = !isStickerBoxOpen
                            }
                        )

                        // 4. DONE
                        HorizontalDivider(modifier = Modifier.width(32.dp))
                        FloatingActionButton(
                            onClick = { 
                                bgState.save()
                                isEditBackgroundMode = false
                                isStickerBoxOpen = false 
                            },
                            containerColor = Color(0xFF5D4E3C),
                            contentColor = Color.White,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Check, "Done")
                        }
                    }
                }
             }
        } else {
            // Edit Toggle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                FloatingActionButton(
                    onClick = { isEditBackgroundMode = true },
                    containerColor = Color(0xFFE6DBC6),
                    contentColor = Color(0xFF5D4E3C)
                ) {
                    Icon(Icons.Default.Brush, "Edit Background")
                }
            }
        }
    }
}

@Composable
fun YearTapeSticker(year: Int, rotation: Float) {
    Box(
        modifier = Modifier
            .rotate(rotation - 1f)
            .background(Color(0xFFE0E0E0), RoundedCornerShape(2.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Color.DarkGray
        )
    }
}

@Composable
fun MonthTapeSticker(monthName: String, tapeColor: Color, rotation: Float) {
    Box(
        modifier = Modifier
            .rotate(rotation)
            .shadow(1.dp, RoundedCornerShape(4.dp))
            .background(tapeColor.copy(alpha = 0.9f))
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = monthName,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Color(0xFF555555)
        )
    }
}

@Composable
fun JournalTitleSticker(
    totalDays: Int,
    isFixed: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(if (isFixed) 6.dp else 4.dp, RoundedCornerShape(2.dp))
            .background(Color(0xFFFFFDF7)) // Stickery Warm White
            .padding(
                horizontal = if (isFixed) 20.dp else 24.dp, 
                vertical = if (isFixed) 16.dp else 20.dp
            )
            .scale(if (isFixed) 0.9f else 1f) // Slightly smaller when fixed to avoid blocking
    ) {
         // Faux "Tape" at top
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = if (isFixed) (-24).dp else (-28).dp)
                .width(40.dp)
                .height(16.dp)
                .background(Color(0x88FFCCBC)) // Translucent tape
        )
        
        Column {
            Text(
                text = "My Journal",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isFixed) 28.sp else 32.sp
                ),
                color = Color(0xFF4E342E)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$totalDays Days",
                style = MaterialTheme.typography.labelLarge.copy(
                     fontFamily = FontFamily.Serif,
                     color = Color(0xFF8D6E63)
                )
            )
        }
    }
}

@Composable
fun LifeDayCell(
    date: LocalDate,
    dayEntry: DayEntry?,
    onClick: (androidx.compose.ui.geometry.Offset, androidx.compose.ui.unit.IntSize) -> Unit
) {
    // Capture position and size
    var centerPos by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var itemSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    val thumbnailInfo by produceState<ThumbnailInfo?>(initialValue = null, key1 = dayEntry?.thumbnailPath) {
        val path = dayEntry?.thumbnailPath
        if (path != null) {
            val cached = ThumbnailCache.get(path)
            if (cached != null) {
                value = cached
            } else {
                withContext(Dispatchers.IO) {
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            val awtImage = javax.imageio.ImageIO.read(file)
                            if (awtImage != null) {
                                val pad = (awtImage.width * 0.12).toInt()
                                val sampleSize = (awtImage.width * 0.10).toInt().coerceAtLeast(1)
                                
                                val topRes = analyzeCorner(awtImage, pad, pad, sampleSize, sampleSize)
                                val botRes = analyzeCorner(awtImage, awtImage.width - pad - sampleSize, awtImage.height - pad - sampleSize, sampleSize, sampleSize)
                                
                                file.inputStream().use { stream ->
                                    val bmp = loadImageBitmap(stream)
                                    val newInfo = ThumbnailInfo(bmp, topRes.isDark, topRes.isBusy, botRes.isDark, botRes.isBusy)
                                    ThumbnailCache.put(path, newInfo)
                                    value = newInfo
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } else {
            value = null
        }
    }

    val hasContent = dayEntry != null
    
    // Randomized rotation for "pasted" look
    val rotation = remember(date) {
        Random(date.hashCode()).nextFloat() * 4f - 2f // Random between -2 and +2 degrees
    }

    // Polaroid Card
    Box(
        modifier = Modifier
            .aspectRatio(0.82f) // Classic Polaroid Ratio
            .onGloballyPositioned { coordinates ->
                // Calculate global center and size
                val size = coordinates.size
                itemSize = size
                val position = coordinates.positionInWindow()
                centerPos = androidx.compose.ui.geometry.Offset(
                    position.x + size.width / 2f,
                    position.y + size.height / 2f
                )
            }
            .rotate(rotation)
            .shadow(
                elevation = 8.dp, // Deeper shadow for more "lift"
                shape = RoundedCornerShape(1.dp), // Sharper corners for paper feel
                spotColor = Color(0xFF1A1A1A).copy(alpha = 0.25f)
            )
            .background(Color.White)
            .alphaClickable(onClick = { onClick(centerPos, itemSize) })
    ) {
        // 1. Image Area (Restricted by padding)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp, 10.dp, 10.dp, 36.dp) // Padding defines the image frame
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color(0xFFFFFDF9), // Unified Warm Paper White
                        shape = RoundedCornerShape(1.dp)
                    )
                    .clip(RoundedCornerShape(1.dp))
                    .border(0.5.dp, Color(0xFFE0E0E0))
            ) {
                val currentInfo = thumbnailInfo
                if (currentInfo != null) {
                    androidx.compose.foundation.Image(
                        bitmap = currentInfo.bitmap,
                        contentDescription = "Thumbnail for $date",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        
        // --- WEEKDAY TAPE STICKER (Separate, diagonally on corner) ---
        val dayName = remember(date) { 
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase() 
        }
        val tapeRotate = remember(date) { Random(date.hashCode() + 2).nextFloat() * 10f - 5f }
        val tapeColor = remember(date) { 
            // Random choice of soft tape colors
            val colors = listOf(Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFB74D))
            colors[Random(date.hashCode() + 3).nextInt(colors.size)].copy(alpha = 0.85f)
        }

        Box(
            modifier = Modifier
                .padding(4.dp)
                .rotate(tapeRotate)
                .background(tapeColor, RoundedCornerShape(1.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
                .align(Alignment.TopEnd)
        ) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 9.sp,
                    color = Color.White,
                    letterSpacing = 0.2.sp
                )
            )
        }
        
        // 2. Bezel Area (Stamps & Date) - Absolute Positioning at Bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(36.dp), // The height of the bottom bezel
            contentAlignment = Alignment.Center
        ) {
             Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically 
             ) {
                 // Weather Stamp (Left)
                 val weatherIcon = dayEntry?.weather?.let { StickerDefinitions.WeatherOptions[it] }
                 
                 if (weatherIcon != null) {
                     // Random "Stamp" visuals
                     val wRotate = remember(date) { Random(date.hashCode()).nextFloat() * 20f - 10f }
                     val wOffsetY = remember(date) { Random(date.hashCode()).nextFloat() * 4f - 2f } // Subtle visual offset, not layout
                     val wOffsetX = remember(date) { Random(date.hashCode()).nextFloat() * 6f - 3f }
                     
                     Icon(
                         imageVector = weatherIcon, 
                         contentDescription = null, 
                         modifier = Modifier
                             .size(20.dp) 
                             .offset(x = wOffsetX.dp, y = wOffsetY.dp)
                             .rotate(wRotate),
                         tint = Color(0xFFD84315).copy(alpha = 0.85f)
                     )
                 } else {
                     Spacer(Modifier.size(20.dp))
                 }

                 // Date Number (Center - Restored simple/bold)
                 Text(
                     text = date.dayOfMonth.toString(),
                     style = MaterialTheme.typography.bodyLarge.copy(
                         fontFamily = FontFamily.Serif,
                         fontWeight = FontWeight.ExtraBold,
                         fontSize = 18.sp,
                         color = Color(0xFF333333)
                     )
                 )
                 
                 // Mood Stamp (Right)
                 val moodIcon = dayEntry?.mood?.let { StickerDefinitions.MoodOptions[it] }

                 if (moodIcon != null) {
                      val mRotate = remember(date) { Random(date.hashCode() + 1).nextFloat() * 20f - 10f }
                      val mOffsetY = remember(date) { Random(date.hashCode() + 1).nextFloat() * 4f - 2f }
                      val mOffsetX = remember(date) { Random(date.hashCode() + 1).nextFloat() * 6f - 3f }

                      Icon(
                          imageVector = moodIcon,
                          contentDescription = null,
                          modifier = Modifier
                              .size(20.dp)
                              .offset(x = mOffsetX.dp, y = mOffsetY.dp)
                              .rotate(mRotate),
                          tint = Color(0xFF1E88E5).copy(alpha = 0.85f)
                      )
                 } else {
                      Spacer(Modifier.size(20.dp))
                 }
             }
        }
    }
}

// Obsolete DraggableStickerContent removed

@Composable
fun AsyncThumbnail(
    path: String,
    onLoaded: (ThumbnailInfo) -> Unit = {}
) {
    val info by produceState<ThumbnailInfo?>(initialValue = null, key1 = path) {
        val cached = ThumbnailCache.get(path)
        if (cached != null) {
            value = cached
            onLoaded(cached)
            return@produceState
        }

        withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (file.exists()) {
                    val awtImage = javax.imageio.ImageIO.read(file)
                    if (awtImage != null) {
                        // Surgical Sampling (12% padding location)
                        val pad = (awtImage.width * 0.12).toInt()
                        val sampleSize = (awtImage.width * 0.10).toInt().coerceAtLeast(1)
                        
                        val topRes = analyzeCorner(awtImage, pad, pad, sampleSize, sampleSize)
                        val botRes = analyzeCorner(awtImage, awtImage.width - pad - sampleSize, awtImage.height - pad - sampleSize, sampleSize, sampleSize)
                        
                        file.inputStream().use { stream ->
                            val bmp = loadImageBitmap(stream)
                            val newInfo = ThumbnailInfo(bmp, topRes.isDark, topRes.isBusy, botRes.isDark, botRes.isBusy)
                            ThumbnailCache.put(path, newInfo)
                            value = newInfo
                            onLoaded(newInfo)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (info != null) {
        Image(
            bitmap = info!!.bitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(Modifier.fillMaxSize().background(Color(0xFFF5F5F5)))
    }
}

/**
 * Analyzes a corner for both Luminance and Variance (Busyness).
 */
private fun analyzeCorner(image: java.awt.image.BufferedImage, x: Int, y: Int, w: Int, h: Int): CornerAnalysis {
    val lums = mutableListOf<Double>()
    
    val startX = x.coerceIn(0, image.width - 1)
    val startY = y.coerceIn(0, image.height - 1)
    val endX = (x + w).coerceIn(0, image.width)
    val endY = (y + h).coerceIn(0, image.height)
    val step = (w / 12).coerceAtLeast(1)

    for (i in startX until endX step step) {
        for (j in startY until endY step step) {
            val rgb = image.getRGB(i, j)
            val alpha = (rgb shr 24) and 0xFF
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            
            val normAlpha = alpha / 255.0
            val blR = (r * normAlpha) + (255 * (1.0 - normAlpha))
            val blG = (g * normAlpha) + (255 * (1.0 - normAlpha))
            val blB = (b * normAlpha) + (255 * (1.0 - normAlpha))
            
            lums.add((0.2126 * blR + 0.7152 * blG + 0.0722 * blB) / 255.0)
        }
    }
    
    if (lums.isEmpty()) return CornerAnalysis(false, false)
    
    val avg = lums.average()
    
    // Calculate Standard Deviation (Variance)
    val variance = lums.map { Math.pow(it - avg, 2.0) }.average()
    val stdDev = Math.sqrt(variance)
    
    // Logic:
    // 1. isDark: uses a threshold. 
    // 2. isBusy: stdDev > 0.15 indicates lot of detail (contrast conflict)
    val isDark = avg < 0.52 // Balanced threshold
    val isBusy = stdDev > 0.15 
    
    return CornerAnalysis(isDark, isBusy)
}

