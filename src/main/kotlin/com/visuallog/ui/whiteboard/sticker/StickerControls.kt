package com.visuallog.ui.whiteboard.sticker

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.visuallog.engine.optimization.ImageOptimizationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

object StickerRamCache {
    private val cache = Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, ImageBitmap>(20, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
                return size > 20
            }
        }
    )
    fun get(path: String): ImageBitmap? = cache[path]
    fun put(path: String, bitmap: ImageBitmap) { cache[path] = bitmap }
}

@Composable
fun StickerVisuals(
    isSelected: Boolean,
    contentPath: String,
    sizeDp: Dp,
    currentScale: Float,
    modifier: Modifier = Modifier,
    overrideBitmap: ImageBitmap? = null
) {
    val isExplicitEmoji = contentPath.startsWith("emoji:")
    val isImplicitEmoji = contentPath.length < 10 && !contentPath.contains("/") && !contentPath.contains("\\") && contentPath != "LOADING"
    val isEmoji = isExplicitEmoji || isImplicitEmoji

    if (isEmoji) {
        val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = contentPath) {
            value = withContext(Dispatchers.IO) {
                StickerRenderUtils.getOrGenerateBitmap(contentPath)
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
                    bitmap = finalBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
             Box(modifier = modifier.size(sizeDp))
        }

    } else {
        val paddingDp = sizeDp * 0.04f
        val cornerDp = sizeDp * 0.03f 
        val cornerRadiusPx = with(LocalDensity.current) { cornerDp.toPx() }
        val borderWidth = 2.dp / currentScale
        val cornerShape = RoundedCornerShape(cornerDp)
        
        val visualBaseElevation = 12.dp
        val elevationPx = with(LocalDensity.current) { (visualBaseElevation / currentScale).toPx() }
        val shadowSigma = elevationPx * 0.6f
        val shadowColorArg = org.jetbrains.skia.Color.makeARGB(30, 0, 0, 0)

        Box(
            modifier = modifier
                .size(sizeDp)
                .drawWithCache {
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
                .background(Color.White, cornerShape)
                .border(borderWidth, Color(0xFFE0E0E0), cornerShape)
                .then(if (isSelected) Modifier.border(borderWidth, Color(0xFF2196F3), cornerShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingDp)
                    .clip(RoundedCornerShape(cornerDp * 0.5f))
                    .background(Color(0xFFF5F5F5))
            ) {
                 if (contentPath != "LOADING" && File(contentPath).exists()) {
                     val photoBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = contentPath) {
                          val cached = StickerRamCache.get(contentPath)
                          if (cached != null) {
                              value = cached
                              return@produceState
                          }

                          value = withContext(Dispatchers.IO) {
                              try {
                                   val file = File(contentPath)
                                   val buffered = ImageOptimizationUtils.loadDownsampledBitmap(file, maxDim = 1200)
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

@Composable
fun ControlNode(
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
fun ResizeHandle(
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
fun RotateHandle(
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
                    val delta = dragAmount.x + dragAmount.y
                    onDrag(delta)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
    }
}

