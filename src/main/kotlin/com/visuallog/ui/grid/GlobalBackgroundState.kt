package com.visuallog.ui.grid

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import com.visuallog.data.model.SerializationUtils
import com.visuallog.data.model.Sticker
import com.visuallog.data.model.Stroke
import com.visuallog.engine.layer.LayerManager
import com.visuallog.engine.stroke.InkInputManager
import com.visuallog.engine.stroke.StrokeEngine
import com.visuallog.ui.common.WhiteboardTool
import java.io.File
import javax.imageio.ImageIO

/**
 * Shared state for the Background Editing Mode.
 * Persists during the app session and is saved to disk in ~/.visuallog/background.
 */
object GlobalBackgroundState {
    val stickers = mutableStateListOf<Sticker>()
    
    val layerManager = LayerManager()
    val inputManager = InkInputManager()
    val strokeEngine = StrokeEngine()
    val strokes = mutableStateListOf<Stroke>()
    
    var currentStrokeColor by mutableStateOf(Color(0xFF222222))
    var currentStrokeWidth by mutableStateOf(0.005f)
    var isMarkerMode by mutableStateOf(false)
    var currentTool by mutableStateOf(WhiteboardTool.PEN)

    private val baseDir = File(System.getProperty("user.home"), ".visuallog/background")
    private val stickersFile = File(baseDir, "stickers.txt")
    private val strokesFile = File(baseDir, "strokes.txt")

    fun save() {
        try {
            if (!baseDir.exists()) baseDir.mkdirs()

            // 1. Save Stickers
            val stickerData = stickers.filter { it.contentPath != "LOADING" }.joinToString("#") { s ->
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

            // 2. Load Strokes and Restore Layers
            if (strokesFile.exists()) {
                val data = strokesFile.readText()
                if (data.isNotBlank()) {
                    val loadedStrokes = SerializationUtils.deserializeStrokes(data)
                    strokes.clear()
                    strokes.addAll(loadedStrokes)
                    layerManager.restoreFromPaths(loadedStrokes)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

