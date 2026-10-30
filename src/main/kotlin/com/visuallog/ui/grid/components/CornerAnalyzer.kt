package com.visuallog.ui.grid.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File
import java.util.Collections
import javax.imageio.ImageIO

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
    fun clear() { cache.clear() }
}

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
                    val awtImage = ImageIO.read(file)
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
fun analyzeCorner(image: BufferedImage, x: Int, y: Int, w: Int, h: Int): CornerAnalysis {
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
    
    if (lums.isEmpty()) return CornerAnalysis(isDark = false, isBusy = false)
    
    val avg = lums.average()
    val variance = lums.map { Math.pow(it - avg, 2.0) }.average()
    val stdDev = Math.sqrt(variance)
    
    val isDark = avg < 0.52
    val isBusy = stdDev > 0.15 
    
    return CornerAnalysis(isDark, isBusy)
}

