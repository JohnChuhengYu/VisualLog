package ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import data.Sticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import kotlin.math.roundToInt

// --- CACHE ---
object StickerRamCache {
    private val cache = Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, ImageBitmap>(20, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
                return size > 20 // Keep last 20 stickers in RAM
            }
        }
    )
    fun get(path: String): ImageBitmap? = cache[path]
    fun put(path: String, bitmap: ImageBitmap) { cache[path] = bitmap }
}

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
    // Local state for smooth Interaction (reconciled with parent via LaunchedEffect)
    // These now store NORMALIZED values (0.0 - 1.0)
    var offsetX by remember { mutableStateOf(sticker.x) }
    var offsetY by remember { mutableStateOf(sticker.y) }
    var scale by remember { mutableStateOf(sticker.scale) }
    var rotation by remember { mutableStateOf(sticker.rotation) }
    
    // We track if we are currently dragging to avoid fighting with parent updates
    var isDragging by remember { mutableStateOf(false) }

    // Sync from parent (Source of Truth) - ONLY if not dragging
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
    
    // Separation of Logic:
    // Background Mode: Fixed size (160dp)
    // Editor Mode: Scalable size (15% of canvas width)
    val sizeDp = if (useFixedSize) {
        160.dp
    } else {
        with(density) { (canvasWidth * 0.15f).toDp() }
    }
    
    Box(
        modifier = Modifier
            // Screen position = normalized coordinate * canvas dimension
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
                            
                            // 1. Detect Gesture Start
                            if (pressed && !isDragging) {
                                isDragging = true
                            }
                            // 2. Detect Gesture End
                            if (!pressed && isDragging) {
                                isDragging = false
                                // SYNC BACK TO PARENT ONLY ON END
                                currentOnTransform(offsetX, offsetY, scale, rotation)
                            }
                        }
                    }
                }.pointerInput(canvasWidth, canvasHeight) {
                    detectTransformGestures { _, pan, zoom, rotate ->
                        isDragging = true
                        
                        // Normalize the pan change
                        val normPanX = pan.x / canvasWidth
                        val normPanY = pan.y / canvasHeight
                        
                        // Apply rotation to pan for correct "stick to finger" feel if pivot is handled
                        // But since we are moving the top-left offset, we need to consider the current rotation
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
        // --- 1. VISUAL FRAME ---
        val safeScale = if (scale < 0.01f) 0.01f else scale
        
        StickerVisuals(
            isSelected = isSelected,
            contentPath = sticker.contentPath,
            sizeDp = sizeDp,
            currentScale = safeScale
        )

        // --- 2. CONTROLS ---
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

@Composable
fun StickerVisuals(
    isSelected: Boolean,
    contentPath: String,
    sizeDp: Dp,
    currentScale: Float, // Pass scale for counter-scaling
    modifier: Modifier = Modifier,
    overrideBitmap: ImageBitmap? = null
) {
    // --- SEPARATE LOGIC AS REQUESTED ---
    
    val isExplicitEmoji = contentPath.startsWith("emoji:")
    val isImplicitEmoji = contentPath.length < 10 && !contentPath.contains("/") && !contentPath.contains("\\") && contentPath != "LOADING"
    val isEmoji = isExplicitEmoji || isImplicitEmoji

    if (isEmoji) {
        // --- EMOJI MODE: Use Backend Bitmap for Consistency (Fixes Black Heart) ---
        val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = contentPath) {
            value = withContext(Dispatchers.IO) {
                ui.StickerRenderUtils.getOrGenerateBitmap(contentPath)
            }
        }
        val finalBitmap = overrideBitmap ?: bitmap

        if (finalBitmap != null) {
            Box(
                modifier = modifier
                    .size(sizeDp)
                    .then(if (isSelected) Modifier.border(1.dp / currentScale, Color(0xFF2196F3), CircleShape) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = finalBitmap!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
             Box(modifier = modifier.size(sizeDp)) // Placeholder
        }

    } else {
        // --- PHOTO MODE: Native Compose Implementation (Restored "Previous Way") ---
        // This ensures crisp borders and shadows regardless of zoom.
        
        // Config
        val paddingDp = sizeDp * 0.04f
        val cornerDp = sizeDp * 0.03f 
        val cornerRadiusPx = with(LocalDensity.current) { cornerDp.toPx() }
        val borderWidth = 2.dp / currentScale
        val cornerShape = RoundedCornerShape(cornerDp)
        
        // Shadow Config
        // Shadow Config (Softened)
        val visualBaseElevation = 12.dp // Increased base slightly for more spread
        val elevationPx = with(LocalDensity.current) { (visualBaseElevation / currentScale).toPx() }
        val shadowSigma = elevationPx * 0.6f // Much softer blur (was / 2f)
        val shadowColorArg = org.jetbrains.skia.Color.makeARGB(30, 0, 0, 0) // Very light (was 64)

        Box(
            modifier = modifier
                .size(sizeDp)
                // 1. Shadow (Optimized Caching)
                .drawWithCache {
                    // Cache the Paint and MaskFilter - these are ONLY recreated if size or scale changes
                    val paint = androidx.compose.ui.graphics.Paint()
                    val frameworkPaint = paint.asFrameworkPaint()
                    frameworkPaint.color = shadowColorArg
                    frameworkPaint.maskFilter = org.jetbrains.skia.MaskFilter.makeBlur(
                        org.jetbrains.skia.FilterBlurMode.NORMAL, 
                        shadowSigma
                    )
                    
                    onDrawBehind {
                        drawIntoCanvas { canvas ->
                            canvas.drawRoundRect(
                                0f, 0f, size.width, size.height,
                                cornerRadiusPx, cornerRadiusPx,
                                paint
                            )
                        }
                    }
                }
                // 2. White Frame & Border
                .background(Color.White, cornerShape)
                .border(borderWidth, Color(0xFFE0E0E0), cornerShape)
                // 3. Selection Border (Rounded)
                .then(if (isSelected) Modifier.border(borderWidth, Color(0xFF2196F3), cornerShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            // 4. Content (with inner padding and clip)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingDp)
                    .clip(RoundedCornerShape(cornerDp * 0.5f)) // Slightly tighter corner for content
                    .background(Color(0xFFF5F5F5))
            ) {
                 if (contentPath != "LOADING" && File(contentPath).exists()) {
                     // Async load for smooth scrolling
                     val photoBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = contentPath) {
                         // 1. Check RAM Cache
                         val cached = StickerRamCache.get(contentPath)
                         if (cached != null) {
                             value = cached
                             return@produceState
                         }

                         // 2. Load from Disk (Downsampled)
                         value = withContext(Dispatchers.IO) {
                             try {
                                  val file = File(contentPath)
                                  val buffered = drawing.ImageOptimizationUtils.loadDownsampledBitmap(file, maxDim = 1200)
                                  if (buffered != null) {
                                      val bmp = buffered.toComposeImageBitmap()
                                      StickerRamCache.put(contentPath, bmp)
                                      bmp
                                  } else null
                             } catch (e: Exception) { 
                                 e.printStackTrace()
                                 null 
                             }
                         }
                     }
                     
                     val displayBitmap = overrideBitmap ?: photoBitmap
                     
                     if (displayBitmap != null) {
                         Image(
                             bitmap = displayBitmap,
                             contentDescription = null,
                             modifier = Modifier.fillMaxSize(),
                             contentScale = ContentScale.Crop
                         )
                     }
                }
            }
        }
    }
}

// --- OPTIMIZED IMAGE COMPONENT ---
// Separated to prevent recomposition loops from affecting image loading
@Composable
private fun StickerImageContent(path: String, overrideBitmap: ImageBitmap? = null) {
    if (overrideBitmap != null) {
         Image(
             bitmap = overrideBitmap,
             contentDescription = "Sticker",
             modifier = Modifier.fillMaxSize(),
             contentScale = ContentScale.Crop
         )
         return
    }

    if (path == "LOADING") {
         val infiniteTransition = rememberInfiniteTransition(label = "pulse")
         val alpha by infiniteTransition.animateFloat(
             initialValue = 0.3f, targetValue = 0.6f,
             animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha"
         )
         Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = alpha)))
    } 
    else if (path.isNotEmpty()) {
         // Async Load with Cache
         val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
             // 1. Check RAM
             val cached = StickerRamCache.get(path)
             if (cached != null) {
                 value = cached
                 return@produceState
             }
             // 2. Load Disk
             withContext(Dispatchers.IO) {
                 try {
                     val file = File(path)
                     if (file.exists()) {
                         file.inputStream().use { stream ->
                             val bmp = loadImageBitmap(stream)
                             StickerRamCache.put(path, bmp)
                             value = bmp
                         }
                     }
                 } catch (e: Exception) { e.printStackTrace() }
             }
         }
         
         if (bitmap != null) {
             Image(
                 bitmap = bitmap!!,
                 contentDescription = "Sticker",
                 modifier = Modifier.fillMaxSize(),
                 contentScale = ContentScale.Crop
             )
         } else {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                 Text("!", color = Color.Gray)
             }
         }
    } else {
         Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFFF8E1)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(64.dp))
        }
    }
}

@Composable
private fun ControlNode(
    modifier: Modifier,
    icon: ImageVector,
    onClick: () -> Unit,
    rotation: Float = 0f,
    tint: Color
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .shadow(4.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.2f))
            .background(Color.White, CircleShape)
            .border(0.5.dp, Color(0xFFE0E0E0), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp).rotate(rotation))
    }
}

@Composable
private fun ResizeHandle(
    modifier: Modifier,
    icon: ImageVector,
    tint: Color,
    onDrag: (Float) -> Unit
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .shadow(4.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.2f))
            .background(Color.White, CircleShape)
            .border(0.5.dp, Color(0xFFE0E0E0), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Drag down/right = Grow. Up/left = Shrink.
                    val delta = dragAmount.x + dragAmount.y
                    onDrag(delta)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun RotateHandle(
    modifier: Modifier,
    icon: ImageVector,
    tint: Color,
    onDrag: (Float) -> Unit
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .shadow(4.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.2f))
            .background(Color.White, CircleShape)
            .border(0.5.dp, Color(0xFFE0E0E0), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Drag = Rotate
                    val delta = dragAmount.x + dragAmount.y
                    onDrag(delta)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
    }
}
