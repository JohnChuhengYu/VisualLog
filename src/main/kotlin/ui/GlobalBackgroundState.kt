package ui

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import drawing.InkInputManager
import drawing.LayerManager
import drawing.StrokeEngine
import data.Sticker
import data.SerializationUtils

import java.io.File
import androidx.compose.ui.graphics.toAwtImage
import javax.imageio.ImageIO
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Shared state for the Background Editing Mode.
 * Persists during the app session and is saved to disk.
 */
object GlobalBackgroundState {
    val stickers = mutableStateListOf<Sticker>()
    
    val layerManager = LayerManager()
    val inputManager = InkInputManager()
    val strokeEngine = StrokeEngine()
    val strokes = mutableStateListOf<data.Stroke>()
    
    var currentStrokeColor by mutableStateOf(Color(0xFF222222))
    var currentStrokeWidth by mutableStateOf(0.005f) // Normalized default (0.5% of canvas)
    var isMarkerMode by mutableStateOf(false)
    var currentTool by mutableStateOf(WhiteboardTool.PEN)

    private val baseDir = File(System.getProperty("user.home"), ".visuallog/background")
    private val stickersFile = File(baseDir, "stickers.txt")
    private val strokesFile = File(baseDir, "strokes.txt")

    fun save() {
        try {
            if (!baseDir.exists()) baseDir.mkdirs()

            // 1. Save Stickers
            val stickerData = stickers.joinToString("#") { s ->
                "${s.id}|${s.dayEntryId}|${s.x}|${s.y}|${s.scale}|${s.rotation}|${s.contentPath}|${s.type}|${s.layer}"
            }
            stickersFile.writeText(stickerData)

            // 2. Save Drawing Layers
            layerManager.buffers.forEachIndexed { index, bitmap ->
                val file = File(baseDir, "layer_$index.png")
                val awtImage = bitmap.toAwtImage()
                ImageIO.write(awtImage, "png", file)
            }

            // 3. Save Strokes
            val strokeJson = SerializationUtils.serializeStrokes(strokes)
            strokesFile.writeText(strokeJson)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun load() {
        try {
            if (!baseDir.exists()) return

            // 1. Load Stickers
            if (stickersFile.exists()) {
                val data = stickersFile.readText()
                if (data.isNotBlank()) {
                    val loadedStickers = data.split("#").mapNotNull { part ->
                        val segs = part.split("|")
                        if (segs.size >= 9) {
                            val rawX = segs[2].toFloat()
                            val rawY = segs[3].toFloat()
                            
                            val isLegacy = rawX > 1.1f || rawY > 1.1f
                            val x = if (isLegacy) rawX / 1000f else rawX
                            val y = if (isLegacy) rawY / 1000f else rawY
                            
                            Sticker(
                                id = segs[0].toInt(),
                                dayEntryId = segs[1].toInt(),
                                x = x,
                                y = y,
                                scale = segs[4].toFloat(),
                                rotation = segs[5].toFloat(),
                                contentPath = segs[6],
                                type = segs[7],
                                layer = segs[8].toInt()
                            )
                        } else null
                    }
                    stickers.clear()
                    stickers.addAll(loadedStickers)
                }
            }

            // 2. Load Drawing Layers
            layerManager.clearAll()
            layerManager.buffers.forEachIndexed { index, bitmap ->
                val file = File(baseDir, "layer_$index.png")
                if (file.exists()) {
                    val loadedBitmap = loadImageBitmap(file.inputStream())
                    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
                    canvas.drawImageRect(
                        image = loadedBitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(loadedBitmap.width, loadedBitmap.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(bitmap.width, bitmap.height),
                        paint = androidx.compose.ui.graphics.Paint()
                    )
                }
            }

            // 3. Load Strokes
            if (strokesFile.exists()) {
                val json = strokesFile.readText()
                if (json.isNotBlank()) {
                    val loadedStrokes = SerializationUtils.deserializeStrokes(json)
                    strokes.clear()
                    strokes.addAll(loadedStrokes)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
