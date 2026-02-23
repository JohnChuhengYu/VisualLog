package ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import data.DayEntry
import data.Sticker
import data.SerializationUtils
import drawing.LayerManager
import drawing.StrokeEngine
import drawing.InkInputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.File

enum class EditorStatus {
    INITIALIZING,
    LOADING,
    READY,
    ERROR
}

/**
 * The Brain of the Editor.
 * Manages data, rendering references, and background operations.
 * Survives recomposition (Hoist this in the screen).
 */
class EditorState(
    val dayEntry: DayEntry,
    val scope: CoroutineScope // Scope tied to the Screen lifecycle
) {
    // 1. STATE
    var status by mutableStateOf(EditorStatus.INITIALIZING)
        private set
        
    // Data
    val strokes = mutableStateListOf<data.Stroke>()
    val stickers = mutableStateListOf<Sticker>()
    var journalText by mutableStateOf(dayEntry.journalText)
    var mood by mutableStateOf(dayEntry.mood)
    var weather by mutableStateOf(dayEntry.weather)
    
    // Systems
    val viewport = ViewportState()
    val layerManager = LayerManager()
    val strokeEngine = StrokeEngine()
    val inputManager = InkInputManager()
    val quadTree = drawing.QuadTree(Rect(0f, 0f, 1.0f, 1.0f))

    // Brush State
    var currentStrokeColor by mutableStateOf(androidx.compose.ui.graphics.Color(0xFF222222))
    var currentStrokeWidth by mutableStateOf(0.005f)
    var isMarkerMode by mutableStateOf(false)

    // 2. LIFECYCLE
    fun load() {
        if (status != EditorStatus.INITIALIZING) return
        status = EditorStatus.LOADING
        
        scope.launch(Dispatchers.IO) {
            try {
                // A. Load Stickers
                val loadedStickers = dayEntry.stickers
                
                // B. Load Strokes (Heavy)
                val loadedStrokes = SerializationUtils.deserializeStrokes(dayEntry.drawingPaths)
                
                // C. Pre-calculate Skia paths and Bake to Bitmap in background
                // This is the heavy lifting that previously blocked the Main thread.
                layerManager.restoreFromPaths(loadedStrokes)
                
                withContext(Dispatchers.Main) {
                    stickers.clear()
                    stickers.addAll(loadedStickers)
                    
                    strokes.clear()
                    strokes.addAll(loadedStrokes)
                    
                    // Rebuild Index
                    loadedStrokes.forEach { quadTree.insert(it) }
                    
                    // Ready to show! AnimatedContent transition will be smooth
                    // because the Main thread was never pegged by baking.
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
        val currentStickers = stickers.toList()
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
     * Generates a thumbnail by Temporarily Baking stickers into the layer buffer.
     * This guarantees 100% WYSIWYG because it uses the EXACT same rendering pipeline as strokes.
     */
    private fun generateThumbnail(currentStickers: List<Sticker>): String {
        println("[THUMBNAIL] Starting Unified Generation")
        
        // 1. BAKING PHASE
        // We bake stickers onto the current LayerManager buffer
        // usage of 'bakeSticker' aligns with how strokes are baked (scaled by 2.16)
        currentStickers.forEach { s ->
            if (s.contentPath != "LOADING") {
                val bitmap = ui.StickerRenderUtils.getOrGenerateBitmap(s.contentPath)
                if (bitmap != null) {
                    // Coordinate Mapping:
                    // Strokes are stored in Raw Screen Px and scaled by 2.16 in LayerManager.
                    // Stickers are stored in Raw Screen Px (relative to container).
                    // So passing raw x,y aligns perfectly with strokes.
                    
                    // Size Mapping:
                    // Visual size is now forced to 150 logical pixels (Density Independent).
                    // Size Mapping:
                    // Visual size is now forced to 150 logical pixels (Density Independent).
                    val logicalSize = ui.StickerRenderUtils.LOGICAL_SIZE.toFloat()
                    
                    // We don't bake to the persistent layer anymore for thumbnails.
                    // Instead, we will use the generatePaddedThumbnail API below.
                }
            }
        }
        
        // 2. CAPTURE PHASE
        // Use the new Padded Generation API in LayerManager
        val paddingStrokes = strokes.toList() // Snapshot for threading safety
        val paddedBitmap = layerManager.generatePaddedThumbnail(
            paths = paddingStrokes,
            drawStickers = { canvas, layerId ->
                currentStickers.filter { it.layer == layerId }.forEach { s ->
                   if (s.contentPath != "LOADING") {
                        val image = ui.StickerRenderUtils.getOrGenerateBitmap(s.contentPath)
                        if (image != null) {
                            val normalizedSize = 0.15f // 15% of canvas width
                            val centerX = s.x + normalizedSize / 2f
                            val centerY = s.y + normalizedSize / 2f
                            
                            layerManager.bakeStickerToCanvas(
                                targetCanvas = canvas,
                                image = image,
                                centerX = centerX,
                                centerY = centerY,
                                sizeLogical = normalizedSize,
                                rotation = s.rotation,
                                scale = s.scale
                            )
                        }
                   }
                }
            }
        )
        
        val thumbnailDir = File(System.getProperty("user.home"), ".visuallog/thumbnails")
        if (!thumbnailDir.exists()) thumbnailDir.mkdirs()
        
        // Optimize Storage: Delete old thumbnails for this DayEntry
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
        
        val bufferedImage = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        bufferedImage.setRGB(0, 0, w, h, pixels, 0, w)
        
        javax.imageio.ImageIO.write(bufferedImage, "png", file)
    }
}
