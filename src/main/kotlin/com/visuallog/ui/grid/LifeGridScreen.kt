package com.visuallog.ui.grid

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visuallog.data.model.DayEntry
import com.visuallog.data.model.Sticker
import com.visuallog.data.model.Stroke
import com.visuallog.data.repository.RecentStickerManager
import com.visuallog.engine.optimization.ImageOptimizationUtils
import com.visuallog.engine.spatial.GeometryUtils
import com.visuallog.engine.stroke.RawPoint
import com.visuallog.ui.common.ToolIcon
import com.visuallog.ui.common.WhiteboardMode
import com.visuallog.ui.common.WhiteboardTool
import com.visuallog.ui.grid.components.JournalTitleSticker
import com.visuallog.ui.grid.components.LifeDayCell
import com.visuallog.ui.grid.components.MonthTapeSticker
import com.visuallog.ui.grid.components.YearTapeSticker
import com.visuallog.ui.whiteboard.components.BrushSizeSelector
import com.visuallog.ui.whiteboard.components.BrushTypeSelector
import com.visuallog.ui.whiteboard.components.ColorPalette
import com.visuallog.ui.whiteboard.components.StickerPickerBox
import com.visuallog.ui.whiteboard.sticker.SamsungSticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

@Composable
fun LifeGridScreen(
    startDate: LocalDate = LocalDate.of(2024, 1, 1),
    dayEntries: Map<String, DayEntry>,
    onDayClick: (LocalDate, Offset, IntSize) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: LazyGridState = rememberLazyGridState()
) {
    val gridState = remember(startDate, dayEntries.size) {
        val today = LocalDate.now()
        val daysBetween = ChronoUnit.DAYS.between(startDate, today)
        val safeDaysBetween = if (daysBetween < 0) 0 else daysBetween
        val totalDays = safeDaysBetween.toInt() + 1
        
        val list = (0 until totalDays).map { startDate.plusDays(it.toLong()) }
        list to totalDays
    }
    val (days, totalDays) = gridState
    
    var isEditBackgroundMode by remember { mutableStateOf(false) }
    val bgState = GlobalBackgroundState
    val scope = rememberCoroutineScope()
    
    var selectedStickerId by remember { mutableStateOf<Int?>(null) }
    var selectedLayer by remember { mutableStateOf(1) }
    var isStickerBoxOpen by remember { mutableStateOf(false) }
    var bgEditMode by remember { mutableStateOf(WhiteboardMode.DRAW) }

    var canvasWidth by remember { mutableStateOf(1f) }
    var canvasHeight by remember { mutableStateOf(1f) }
    var invalidator by remember { mutableStateOf(0) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF9F5F0)) 
    ) {
        val isNarrow = maxWidth < 600.dp
        
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
                    val signal = invalidator
                    val buffer = bgState.layerManager.buffers[layerId]
                    
                    val drawScaleX = size.width / bgState.layerManager.bufferScale
                    val drawScaleY = size.height / bgState.layerManager.bufferScale
                    
                    drawContext.canvas.save()
                    drawContext.canvas.scale(drawScaleX, drawScaleY)
                    drawImage(buffer)
                    drawContext.canvas.restore()
                    
                    if (isEditBackgroundMode && layerId == selectedLayer) { 
                        val color = when (bgState.currentTool) {
                            WhiteboardTool.ERASER -> Color.White.copy(alpha = 0.5f)
                            WhiteboardTool.SEGMENT_ERASER -> Color.Red.copy(alpha = 0.2f)
                            else -> if (bgState.isMarkerMode) bgState.currentStrokeColor.copy(alpha = 0.4f) else bgState.currentStrokeColor
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
            
            // 2. Interaction Layer for Drawing
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
                                        val nx = pos.x / canvasWidth
                                        val ny = pos.y / canvasHeight
                                        
                                        bgState.inputManager.onRawPoint(RawPoint(nx, ny, event.changes.last().uptimeMillis))
                                        bgState.strokeEngine.process(event.changes.map { 
                                            RawPoint(it.position.x / canvasWidth, it.position.y / canvasHeight, it.uptimeMillis) 
                                        })
                                        invalidator++
                                    }
                                    
                                    if (type == PointerEventType.Release) {
                                        val path = bgState.strokeEngine.getFinalPath()
                                        if (!path.isEmpty) {
                                            if (bgState.currentTool == WhiteboardTool.SEGMENT_ERASER) {
                                                val eraserPoints = bgState.strokeEngine.rawPoints.map { Offset(it.x, it.y) }
                                                
                                                if (eraserPoints.size >= 2) {
                                                    val eraserSegments = eraserPoints.windowed(2) { (a, b) -> a to b }
                                                    val eraserBounds = Rect(
                                                        eraserPoints.minOf { it.x }, eraserPoints.minOf { it.y },
                                                        eraserPoints.maxOf { it.x }, eraserPoints.maxOf { it.y }
                                                    ).inflate(0.01f)
                                                    
                                                    val toRemove = bgState.strokes.filter { s ->
                                                        s.layer == selectedLayer && !s.isEraser
                                                    }.filter { s ->
                                                        if (s.points.size < 2) return@filter false
                                                        if (!eraserBounds.overlaps(s.bounds)) return@filter false
                                                        
                                                        val strokeSegments = s.points.windowed(2) { (a, b) -> a to b }
                                                        strokeSegments.any { sSeg ->
                                                            eraserSegments.any { eSeg ->
                                                                GeometryUtils.lineSegmentsIntersect(sSeg.first, sSeg.second, eSeg.first, eSeg.second)
                                                            }
                                                        }
                                                    }
                                                    
                                                    if (toRemove.isNotEmpty()) {
                                                        bgState.strokes.removeAll(toRemove)
                                                        bgState.layerManager.restoreFromPaths(bgState.strokes)
                                                    }
                                                }
                                                bgState.strokeEngine.clear()
                                                invalidator++
                                            } else {
                                                val style = bgState.strokeEngine.currentStyle
                                                val baseColor = if (bgState.currentTool == WhiteboardTool.ERASER) Color.White else bgState.currentStrokeColor
                                                val finalColor = if (bgState.isMarkerMode && bgState.currentTool != WhiteboardTool.ERASER) baseColor.copy(alpha = 0.4f) else baseColor
                                                val bm = if (bgState.currentTool == WhiteboardTool.ERASER) androidx.compose.ui.graphics.BlendMode.Clear else androidx.compose.ui.graphics.BlendMode.SrcOver
                                                val isEraser = bgState.currentTool == WhiteboardTool.ERASER
                                                
                                                bgState.layerManager.bakeStroke(path, style, finalColor, bm, selectedLayer)
                                                
                                                val savedPoints = bgState.strokeEngine.rawPoints.map { Offset(it.x, it.y) }
                                                bgState.strokes.add(
                                                    Stroke(
                                                        points = savedPoints,
                                                        isEraser = isEraser,
                                                        width = bgState.strokeEngine.strokeWidth,
                                                        color = if (isEraser) 0 else finalColor.toArgb(),
                                                        layer = selectedLayer
                                                    )
                                                )
                                                bgState.strokeEngine.clear()
                                                invalidator++
                                            }
                                        }
                                    }
                                }
                            }
                        }
                )
            }
            
            // 3. Stickers Layer
            for (layerId in 0..2) {
                Box(modifier = Modifier.fillMaxSize()) {
                    bgState.stickers.filter { it.layer == layerId }.forEach { sticker ->
                        key(sticker.id) {
                            SamsungSticker(
                                sticker = sticker,
                                canvasWidth = canvasWidth,
                                canvasHeight = canvasHeight,
                                useFixedSize = true,
                                enabled = isEditBackgroundMode && (bgEditMode == WhiteboardMode.STICKER) && (sticker.contentPath != "LOADING"),
                                isSelected = isEditBackgroundMode && (bgEditMode == WhiteboardMode.STICKER) && (sticker.id == selectedStickerId),
                                onClick = { 
                                    if (isEditBackgroundMode) {
                                        selectedStickerId = sticker.id
                                        selectedLayer = sticker.layer
                                    }
                                },
                                onTransform = { x, y, s, r -> 
                                    val idx = bgState.stickers.indexOfFirst { it.id == sticker.id }
                                    if (idx != -1) {
                                        val current = bgState.stickers[idx]
                                        bgState.stickers[idx] = current.copy(x = x, y = y, scale = s, rotation = r) 
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
            }
        }
        
        // --- LAYER 2: INTERACTIVE CONTENT GRID ---
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 135.dp),
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 36.dp, 
                    end = 36.dp, 
                    top = if (isNarrow) 24.dp else 40.dp, 
                    bottom = 100.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
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

                val daysByYear = days.groupBy { it.year }
                
                daysByYear.forEach { (year, yearDays) ->
                    val monthsInYear = yearDays.groupBy { it.monthValue }
                    val monthsList = monthsInYear.toList()
                    
                    monthsList.forEachIndexed { monthIndex, (month, monthDays) ->
                        item(
                            key = "header_${year}_$month",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            val firstDay = monthDays.first()
                            val tapeColors = listOf(
                                Color(0xFFFFB7B2),
                                Color(0xFFFFDAC1),
                                Color(0xFFE2F0CB),
                                Color(0xFFB5EAD7),
                                Color(0xFFC7CEEA)
                            )
                            val colorIndex = (year + month) % tapeColors.size
                            val tapeColor = tapeColors[colorIndex]
                            
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
                                    Column(modifier = Modifier.padding(start = 16.dp)) {
                                        if (monthIndex == 0) {
                                            YearTapeSticker(year, rotation)
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                        MonthTapeSticker(firstDay.month.name, tapeColor, rotation)
                                    }
                                } else {
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
                        
                        items(
                            items = monthDays,
                            key = { it.toString() }, 
                            contentType = { "day_cell" } 
                        ) { date ->
                            LifeDayCell(
                                date = date,
                                dayEntry = dayEntries[date.toString()],
                                onClick = { centerOffset, size ->
                                    if (!isEditBackgroundMode) {
                                        onDayClick(date, centerOffset, size)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // --- LAYER 3: FIXED FLOATING TITLE ---
        if (!isNarrow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 32.dp, top = 28.dp),
                contentAlignment = Alignment.TopStart
            ) {
                JournalTitleSticker(
                    totalDays = totalDays,
                    isFixed = true,
                    modifier = Modifier.rotate(-2f)
                )
            }
        }

        // --- LAYER 4: BACKGROUND EDIT CONTROLS ---
        if (isEditBackgroundMode) {
             Box(
                 modifier = Modifier.fillMaxSize().padding(end = 24.dp, bottom = 24.dp),
                 contentAlignment = Alignment.CenterEnd
             ) {
                 AnimatedVisibility(
                     visible = isStickerBoxOpen,
                     enter = scaleIn(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)) + fadeIn(),
                     exit = scaleOut(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)) + fadeOut(),
                     modifier = Modifier.padding(end = 80.dp)
                 ) {
                     StickerPickerBox(
                         onEmojiSelected = { emoji ->
                             val newId = -((System.currentTimeMillis() and 0x7FFFFFFF).toInt()) - 1
                             val newSticker = Sticker(
                                 id = newId, 
                                 dayEntryId = -999, 
                                 x = 0.45f, 
                                 y = 0.45f, 
                                 scale = 1f, 
                                 rotation = Random.nextFloat() * 20 - 10, 
                                 contentPath = "emoji:$emoji", 
                                 type = "emoji", 
                                 layer = selectedLayer
                             )
                             bgState.stickers.add(newSticker)
                             selectedStickerId = newId
                         },
                         onPhotoImportCall = {
                             val fileDialog = java.awt.FileDialog(null as java.awt.Frame?, "Select Image", java.awt.FileDialog.LOAD)
                             fileDialog.isVisible = true 
                             
                             if (fileDialog.file != null) {
                                 val originalFile = File(fileDialog.directory, fileDialog.file)
                                 val newId = -((System.currentTimeMillis() and 0x7FFFFFFF).toInt()) - 1
                                 bgState.stickers.add(Sticker(id = newId, dayEntryId = -999, x = 0.45f, y = 0.45f, scale = 1f, rotation = 0f, contentPath = "LOADING", type = "image", layer = selectedLayer))
                                 selectedStickerId = newId
                                 
                                 scope.launch(Dispatchers.IO) {
                                     try {
                                         val stickersDir = File(System.getProperty("user.home"), ".visuallog/stickers")
                                         if (!stickersDir.exists()) stickersDir.mkdirs()
                                         
                                         var finalFile: File? = null
                                         try {
                                             finalFile = ImageOptimizationUtils.optimizeImage(originalFile, stickersDir)
                                         } catch (e: Exception) { e.printStackTrace() }
                                         
                                         if (finalFile == null) {
                                             val ext = originalFile.extension.ifEmpty { "png" }
                                             val fallbackFile = File(stickersDir, "sticker_raw_${System.currentTimeMillis()}.$ext")
                                             originalFile.copyTo(fallbackFile, overwrite = true)
                                             finalFile = fallbackFile
                                         }
                                         
                                         val finalPath = finalFile.absolutePath
                                         RecentStickerManager.add(finalPath)
                                         
                                         withContext(Dispatchers.Main) {
                                             val idx = bgState.stickers.indexOfFirst { it.id == newId }
                                             if (idx != -1) bgState.stickers[idx] = bgState.stickers[idx].copy(contentPath = finalPath)
                                         }
                                     } catch (e: Exception) {
                                         e.printStackTrace()
                                         withContext(Dispatchers.Main) {
                                             val idx = bgState.stickers.indexOfFirst { it.id == newId }
                                             if (idx != -1) bgState.stickers.removeAt(idx)
                                         }
                                     }
                                 }
                             } 
                         },
                         onRecentSelected = { path ->
                             val newId = -((System.currentTimeMillis() and 0x7FFFFFFF).toInt()) - 1
                             bgState.stickers.add(Sticker(id = newId, dayEntryId = -999, x = 0.45f, y = 0.45f, scale = 1f, rotation = 0f, contentPath = path, type = "image", layer = selectedLayer))
                             selectedStickerId = newId
                         }
                     )
                 }

                 Surface(
                     shape = RoundedCornerShape(16.dp),
                     color = Color(0xFFE6DBC6),
                     shadowElevation = 8.dp,
                     border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD4C4B0)),
                     modifier = Modifier.padding(top = 96.dp)
                 ) {
                     Column(
                         modifier = Modifier.padding(8.dp),
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
                                             .background(if (selectedLayer == id) Color(0xFF5D4E3C) else Color.White.copy(alpha = 0.5f))
                                             .clickable { selectedLayer = id },
                                         contentAlignment = Alignment.Center
                                     ) {
                                         Text(
                                             text = id.toString(),
                                             color = if (selectedLayer == id) Color.White else Color(0xFF5D4E3C),
                                             fontSize = 12.sp,
                                             fontWeight = FontWeight.Bold
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
                         
                         // 3. SWEEP (SEGMENT ERASER)
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
                         
                         // 4. STICKER
                         ToolIcon(
                             icon = Icons.Default.EmojiEmotions,
                             label = "Sticker",
                             isActive = bgEditMode == WhiteboardMode.STICKER,
                             onClick = { 
                                 bgEditMode = WhiteboardMode.STICKER
                                 isStickerBoxOpen = !isStickerBoxOpen
                             }
                         )

                         // 5. DONE
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

private fun Color.toArgb(): Int {
    return (alpha * 255.0f + 0.5f).toInt() shl 24 or
           ((red * 255.0f + 0.5f).toInt() shl 16) or
           ((green * 255.0f + 0.5f).toInt() shl 8) or
           (blue * 255.0f + 0.5f).toInt()
}

