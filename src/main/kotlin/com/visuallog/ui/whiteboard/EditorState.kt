package com.visuallog.ui.whiteboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.visuallog.data.model.DayEntry
import com.visuallog.data.model.SerializationUtils
import com.visuallog.data.model.Sticker
import com.visuallog.data.model.Stroke
import com.visuallog.engine.layer.LayerManager
import com.visuallog.engine.spatial.QuadTree
import com.visuallog.engine.stroke.InkInputManager
import com.visuallog.engine.stroke.StrokeEngine
import com.visuallog.ui.whiteboard.sticker.StickerRenderUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

enum class EditorStatus {
    INITIALIZING,
    LOADING,
    READY,
    ERROR
}

/**
 * The Brain of the Editor.
 * Manages data, rendering references, and background operations.
 */
class EditorState(
    val dayEntry: DayEntry,
    val scope: CoroutineScope
) {
    var status by mutableStateOf(EditorStatus.INITIALIZING)
        private set
        
    val strokes = mutableStateListOf<Stroke>()
    val stickers = mutableStateListOf<Sticker>()
    var journalText by mutableStateOf(dayEntry.journalText)
    var mood by mutableStateOf(dayEntry.mood)
    var weather by mutableStateOf(dayEntry.weather)
    
    val viewport = ViewportState()
    val layerManager = LayerManager()
    val strokeEngine = StrokeEngine()
    val inputManager = InkInputManager()
    val quadTree = QuadTree(Rect(0f, 0f, 1.0f, 1.0f))

    var currentStrokeColor by mutableStateOf(Color(0xFF222222))
    var currentStrokeWidth by mutableStateOf(0.005f)
    var isMarkerMode by mutableStateOf(false)

    fun load() {
        if (status != EditorStatus.INITIALIZING) return
        status = EditorStatus.LOADING
        
        scope.launch(Dispatchers.IO) {
            try {
                val loadedStickers = dayEntry.stickers
                val loadedStrokes = SerializationUtils.deserializeStrokes(dayEntry.drawingPaths)
                
                layerManager.restoreFromPaths(loadedStrokes)
                
                withContext(Dispatchers.Main) {
                    stickers.clear()
                    stickers.addAll(loadedStickers)
                    
                    strokes.clear()
                    strokes.addAll(loadedStrokes)
                    
                    loadedStrokes.forEach { quadTree.insert(it) }
                    status = EditorStatus.READY
                }
            } catch (e: Exception) {
                e.printStackTrace()
                status = EditorStatus.ERROR
            }
        }
    }
    
    fun save(onComplete: (DayEntry, List<Sticker>) -> Unit) {
        val currentText = journalText
        val currentStickers = stickers.filter { it.contentPath != "LOADING" }.toList()
        val currentStrokes = strokes.toList()
        val currentMood = mood
        val currentWeather = weather
        
        scope.launch(Dispatchers.IO) {
            val json = SerializationUtils.serializeStrokes(currentStrokes)
            val savedPath = generateThumbnail(currentStickers)
            
            withContext(Dispatchers.Main) {
                onComplete(
                    dayEntry.copy(
                        journalText = currentText, 
                        drawingPaths = json,
                        thumbnailPath = savedPath,
                        mood = currentMood,
                        weather = currentWeather
                    ),
                    currentStickers
                )
            }
        }
    }

    /**
     * Generates a thumbnail by temporarily baking stickers into the layer buffer.
     */
    private fun generateThumbnail(currentStickers: List<Sticker>): String {
        val paddingStrokes = strokes.toList()
        val paddedBitmap = layerManager.generatePaddedThumbnail(
            paths = paddingStrokes,
            drawStickers = { canvas, layerId ->
                currentStickers.filter { it.layer == layerId }.forEach { s ->
                   if (s.contentPath != "LOADING") {
                        val image = StickerRenderUtils.getOrGenerateBitmap(s.contentPath)
                        if (image != null) {
                            val normalizedSize = 0.15f
                            val centerX = s.x + normalizedSize / 2f
                            val centerY = s.y + normalizedSize / 2f
                            
                            layerManager.bakeStickerToCanvas(
                                targetCanvas = canvas,
                                image = image,
                                centerX = centerX,
                                centerY = centerY,
                                sizeLogical = normalizedSize,
                                rotation = s.rotation,
                                scale = s.scale,
                                contentScale = StickerRenderUtils.CONTENT_SCALE
                            )
                        }
                   }
                }
            }
        )
        
        val thumbnailDir = File(System.getProperty("user.home"), ".visuallog/thumbnails")
        if (!thumbnailDir.exists()) thumbnailDir.mkdirs()
        
        val prefix = "${dayEntry.id}_"
        thumbnailDir.listFiles { _, name -> name.startsWith(prefix) && name.endsWith(".png") }
            ?.forEach { it.delete() }
            
        val filename = "${dayEntry.id}_${System.currentTimeMillis()}.png"
        val file = File(thumbnailDir, filename)
        
        saveBitmap(paddedBitmap, file)
        
        return file.absolutePath
    }

    private fun saveBitmap(bitmap: androidx.compose.ui.graphics.ImageBitmap, file: File) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.readPixels(buffer = pixels)
        
        val bufferedImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        bufferedImage.setRGB(0, 0, w, h, pixels, 0, w)
        
        ImageIO.write(bufferedImage, "png", file)
    }
}

