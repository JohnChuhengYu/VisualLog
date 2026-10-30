package com.visuallog.data.service

import androidx.compose.ui.graphics.ImageBitmap
import com.visuallog.data.model.SerializationUtils
import com.visuallog.data.repository.VisualLogRepository
import com.visuallog.engine.layer.LayerManager
import com.visuallog.ui.grid.components.ThumbnailCache
import com.visuallog.ui.whiteboard.sticker.StickerRenderUtils
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object ThumbnailMigrator {
    private const val MIGRATION_KEY = "thumbnail_emulsion_v5"

    fun migrateIfNeeded(repository: VisualLogRepository) {
        val migrated = repository.getAppSetting(MIGRATION_KEY)
        if (migrated == "true") return

        try {
            val entries = repository.getAllDayEntries()
            val thumbnailDir = File(System.getProperty("user.home"), ".visuallog/thumbnails")
            if (!thumbnailDir.exists()) thumbnailDir.mkdirs()

            val layerManager = LayerManager()

            for (entry in entries) {
                val strokes = SerializationUtils.deserializeStrokes(entry.drawingPaths)
                val stickers = entry.stickers

                // Only generate if there is drawing or sticker content
                if (strokes.isNotEmpty() || stickers.isNotEmpty()) {
                    val paddedBitmap = layerManager.generatePaddedThumbnail(
                        paths = strokes,
                        drawStickers = { canvas, layerId ->
                            stickers.filter { it.layer == layerId }.forEach { s ->
                                if (s.contentPath != "LOADING") {
                                    val image = StickerRenderUtils.getOrGenerateBitmap(s.contentPath)
                                    if (image != null) {
                                        val normalizedSize = 0.15f
                                        val cx = s.x + normalizedSize / 2f
                                        val cy = s.y + normalizedSize / 2f
                                        layerManager.bakeStickerToCanvas(
                                            targetCanvas = canvas,
                                            image = image,
                                            centerX = cx,
                                            centerY = cy,
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

                    val prefix = "${entry.id}_"
                    thumbnailDir.listFiles { _, name -> name.startsWith(prefix) && name.endsWith(".png") }
                        ?.forEach { it.delete() }

                    val filename = "${entry.id}_${System.currentTimeMillis()}.png"
                    val file = File(thumbnailDir, filename)

                    val w = paddedBitmap.width
                    val h = paddedBitmap.height
                    val pixels = IntArray(w * h)
                    paddedBitmap.readPixels(pixels)
                    val bufferedImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                    bufferedImage.setRGB(0, 0, w, h, pixels, 0, w)
                    ImageIO.write(bufferedImage, "png", file)

                    repository.saveOrUpdateDayEntry(
                        date = entry.date,
                        backgroundPath = entry.backgroundPath,
                        drawingPaths = entry.drawingPaths,
                        journalText = entry.journalText,
                        thumbnailPath = file.absolutePath,
                        mood = entry.mood,
                        weather = entry.weather
                    )
                }
            }

            ThumbnailCache.clear()
            repository.setAppSetting(MIGRATION_KEY, "true")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
