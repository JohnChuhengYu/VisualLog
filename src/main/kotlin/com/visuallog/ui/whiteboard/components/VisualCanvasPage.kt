package com.visuallog.ui.whiteboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.visuallog.data.model.Stroke
import com.visuallog.engine.spatial.GeometryUtils
import com.visuallog.engine.stroke.RawPoint
import com.visuallog.ui.common.WhiteboardMode
import com.visuallog.ui.common.WhiteboardTool
import com.visuallog.ui.whiteboard.EditorState
import com.visuallog.ui.whiteboard.sticker.SamsungSticker
import kotlin.math.min

@Composable
fun VisualCanvasPage(
    state: EditorState, 
    mode: WhiteboardMode, 
    currentTool: WhiteboardTool,
    selectedStickerId: Int?,
    onSelectSticker: (Int?) -> Unit,
    selectedLayer: Int
) {
    val viewport = state.viewport
    val layer = state.layerManager
    val stickers = state.stickers
    
    var invalidator by remember { mutableStateOf(0) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {

        LaunchedEffect(constraints.maxWidth, constraints.maxHeight) {
            val winW = constraints.maxWidth.toFloat()
            val winH = constraints.maxHeight.toFloat()
            
            // Total Polaroid card bounds (film is 0..1, outer card has borders & chin)
            val cardW = 1.11f
            val cardH = 1.255f
            val fitScale = min(winW / cardW, winH / cardH) * 0.88f
            
            viewport.scale = fitScale
            viewport.offset = Offset(
                winW / 2f - 0.5f * fitScale,
                winH / 2f - 0.5725f * fitScale
            )
        }

        invalidator.let {}
        
        val vScale = viewport.scale
        val vOffset = viewport.offset
        
        // 1. POLAROID CARD & FILM PAPER
        Canvas(modifier = Modifier.fillMaxSize()) {
            translate(vOffset.x, vOffset.y) {
                scale(vScale, pivot = Offset.Zero) {
                    // A. Polaroid Card Outer Drop Shadow
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.16f),
                        topLeft = Offset(-0.052f, -0.045f),
                        size = androidx.compose.ui.geometry.Size(1.11f, 1.255f),
                        cornerRadius = CornerRadius(0.012f, 0.012f)
                    )
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.07f),
                        topLeft = Offset(-0.048f, -0.038f),
                        size = androidx.compose.ui.geometry.Size(1.11f, 1.255f),
                        cornerRadius = CornerRadius(0.018f, 0.018f)
                    )
                    
                    // B. Crisp White Polaroid Paper Frame (#FFFFFF)
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(-0.055f, -0.055f),
                        size = androidx.compose.ui.geometry.Size(1.11f, 1.255f),
                        cornerRadius = CornerRadius(0.010f, 0.010f),
                        style = androidx.compose.ui.graphics.drawscope.Fill
                    )
                    drawRoundRect(
                        color = Color(0xFFE5DFD5),
                        topLeft = Offset(-0.055f, -0.055f),
                        size = androidx.compose.ui.geometry.Size(1.11f, 1.255f),
                        cornerRadius = CornerRadius(0.010f, 0.010f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.0012f)
                    )
                    
                    // C. Inner Film Emulsion Background (#F5EFE6)
                    drawRect(
                        color = Color(0xFFF5EFE6),
                        topLeft = Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(1.0f, 1.0f),
                        style = androidx.compose.ui.graphics.drawscope.Fill
                    )
                    // D. Tactile Die-cut Inset Bezel (#D0C6B6)
                    drawRect(
                        color = Color(0xFFD0C6B6),
                        topLeft = Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(1.0f, 1.0f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.0022f)
                    )
                    // E. Top & Left Inset Shadow (Physical cutout depth)
                    drawRect(
                        color = Color.Black.copy(alpha = 0.06f),
                        topLeft = Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(1.0f, 0.008f)
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.05f),
                        topLeft = Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(0.008f, 1.0f)
                    )
                }
            }
        }
        
        // 2. INPUT HANDLER
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
                                 
                                 val isDown = changes.any { it.pressed }
                                 if (isDown && (type == PointerEventType.Move || type == PointerEventType.Press)) {
                                     val logicalChanges = changes.map { c ->
                                         val localPos = (c.position - viewport.offset) / viewport.scale
                                         RawPoint(localPos.x, localPos.y, c.uptimeMillis)
                                     }
                                     state.inputManager.onRawPoint(logicalChanges.last()) 
                                     state.strokeEngine.process(logicalChanges)
                                     invalidator++
                                 }
                                 
                                 changes.forEach { it.consume() }
                                 
                                 if (type == PointerEventType.Release) {
                                     if (currentTool == WhiteboardTool.SEGMENT_ERASER) {
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
                                                 if (!eraserBounds.overlaps(s.bounds)) return@filter false
                                                 
                                                 val strokeSegments = s.points.windowed(2) { (a, b) -> a to b }
                                                 strokeSegments.any { sSeg ->
                                                     eraserSegments.any { eSeg ->
                                                         GeometryUtils.lineSegmentsIntersect(sSeg.first, sSeg.second, eSeg.first, eSeg.second)
                                                     }
                                                 }
                                             }
                                             
                                             if (toRemove.isNotEmpty()) {
                                                 state.strokes.removeAll(toRemove)
                                                 layer.restoreFromPaths(state.strokes)
                                             }
                                         }
                                         state.strokeEngine.clear()
                                         invalidator++
                                         
                                     } else {
                                         val path = state.strokeEngine.getFinalPath()
                                         if (!path.isEmpty) {
                                              val style = state.strokeEngine.currentStyle
                                              val baseColor = if (currentTool == WhiteboardTool.ERASER) Color.White else state.currentStrokeColor
                                              val finalColor = if (state.isMarkerMode && currentTool != WhiteboardTool.ERASER) baseColor.copy(alpha = 0.4f) else baseColor
                                              val bm = if (currentTool == WhiteboardTool.ERASER) BlendMode.Clear else BlendMode.SrcOver
                                              val isEraser = currentTool == WhiteboardTool.ERASER
                                              
                                              layer.bakeStroke(path, style, finalColor, bm, selectedLayer)
                                              
                                              val savedPoints = state.strokeEngine.rawPoints.map { Offset(it.x, it.y) }
                                              
                                              val s = Stroke(
                                                 points = savedPoints,
                                                 isEraser = isEraser,
                                                 width = state.strokeEngine.strokeWidth,
                                                 color = if (isEraser) 0 else finalColor.toArgb(),
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

        // 3. LAYERS (Foreground: Bitmaps + Live Stroke + Stickers)
        for (layerId in 0..2) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val buffer = layer.buffers[layerId]
                val drawScale = vScale / layer.bufferScale
                
                translate(vOffset.x, vOffset.y) {
                    scale(drawScale, pivot = Offset.Zero) {
                        drawImage(buffer)
                    }
                }
                
                if (layerId == selectedLayer) {
                     translate(vOffset.x, vOffset.y) {
                        scale(vScale, pivot = Offset.Zero) {
                            val color = when (currentTool) {
                                WhiteboardTool.ERASER -> Color.White.copy(alpha = 0.5f)
                                WhiteboardTool.SEGMENT_ERASER -> Color.Red.copy(alpha = 0.2f)
                                else -> if (state.isMarkerMode) state.currentStrokeColor.copy(alpha = 0.4f) else state.currentStrokeColor
                            }
                            
                            if (state.strokeEngine.currentPath.isEmpty.not()) {
                                drawPath(
                                    state.strokeEngine.currentPath, 
                                    color, 
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = state.strokeEngine.strokeWidth / 1000f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
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
                                     stickers[idx] = current.copy(x = x, y = y, scale = s, rotation = r) 
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

