package com.visuallog.ui.whiteboard.sticker

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.visuallog.data.model.Sticker
import kotlin.math.roundToInt

@Composable
fun SamsungSticker(
    sticker: Sticker,
    canvasWidth: Float,
    canvasHeight: Float,
    onTransform: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    onDelete: () -> Unit = {},
    onClick: () -> Unit = {},
    isSelected: Boolean = false,
    enabled: Boolean = true,
    useFixedSize: Boolean = true
) {
    var offsetX by remember { mutableStateOf(sticker.x) }
    var offsetY by remember { mutableStateOf(sticker.y) }
    var scale by remember { mutableStateOf(sticker.scale) }
    var rotation by remember { mutableStateOf(sticker.rotation) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(sticker) {
        if (!isDragging) {
            offsetX = sticker.x
            offsetY = sticker.y
            scale = sticker.scale
            rotation = sticker.rotation
        }
    }

    val currentOnTransform by rememberUpdatedState(onTransform)
    val currentOnClick by rememberUpdatedState(onClick)
    val density = LocalDensity.current
    val controlRadius = 12.dp 
    
    val sizeDp = if (useFixedSize) {
        160.dp
    } else {
        with(density) { (canvasWidth * 0.15f).toDp() }
    }
    
    Box(
        modifier = Modifier
            .offset { 
                IntOffset(
                    (offsetX * canvasWidth).roundToInt(), 
                    (offsetY * canvasHeight).roundToInt()
                ) 
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
            .size(sizeDp)
            .then(if (enabled) {
                Modifier.pointerInput(canvasWidth, canvasHeight) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            val pressed = changes.any { it.pressed }
                            
                            if (pressed && !isDragging) {
                                isDragging = true
                            }
                            if (!pressed && isDragging) {
                                isDragging = false
                                currentOnTransform(offsetX, offsetY, scale, rotation)
                            }
                        }
                    }
                }.pointerInput(canvasWidth, canvasHeight) {
                    detectTransformGestures { _, pan, zoom, rotate ->
                        isDragging = true
                        
                        val normPanX = pan.x / canvasWidth
                        val normPanY = pan.y / canvasHeight
                        
                        val rad = (rotation * Math.PI / 180.0).toFloat()
                        val cos = kotlin.math.cos(rad)
                        val sin = kotlin.math.sin(rad)
                        
                        val dx = normPanX * cos - normPanY * sin
                        val dy = normPanX * sin + normPanY * cos
                        
                        offsetX += dx
                        offsetY += dy
                        scale = (scale * zoom).coerceIn(0.1f, 10f)
                        rotation += rotate
                    }
                }.pointerInput(Unit) {
                    detectTapGestures(onTap = { currentOnClick() })
                }
            } else Modifier)
    ) {
        val safeScale = if (scale < 0.01f) 0.01f else scale
        
        StickerVisuals(
            isSelected = isSelected,
            contentPath = sticker.contentPath,
            sizeDp = sizeDp,
            currentScale = safeScale
        )

        if (isSelected && enabled) {
            val invScale = 1f / safeScale
            val effectiveRadius = controlRadius * invScale
            
            ControlNode(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = effectiveRadius, y = -effectiveRadius)
                    .graphicsLayer { 
                        scaleX = invScale
                        scaleY = invScale
                    },
                icon = Icons.Default.Close,
                onClick = onDelete,
                tint = Color(0xFFEF5350)
            )
            
            ResizeHandle(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = effectiveRadius, y = effectiveRadius)
                    .graphicsLayer { 
                        scaleX = invScale
                        scaleY = invScale
                    },
                icon = Icons.Default.OpenInFull,
                tint = Color(0xFF2196F3),
                onDrag = { delta ->
                   val sensitivity = 0.005f / safeScale 
                   val newScale = (scale * (1 + delta * sensitivity)).coerceIn(0.1f, 10f)
                   scale = newScale
                   currentOnTransform(offsetX, offsetY, newScale, rotation)
                }
            )
            
            RotateHandle(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = -effectiveRadius, y = effectiveRadius)
                    .graphicsLayer { 
                        scaleX = invScale
                        scaleY = invScale
                    },
                icon = Icons.Default.Refresh,
                tint = Color(0xFF4CAF50),
                onDrag = { delta ->
                   val sensitivity = 0.5f
                   val newRotation = rotation - delta * sensitivity
                   rotation = newRotation
                   currentOnTransform(offsetX, offsetY, scale, newRotation)
                }
            )
        }
    }
}

