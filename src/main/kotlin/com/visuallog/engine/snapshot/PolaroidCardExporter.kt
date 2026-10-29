package com.visuallog.engine.snapshot

import com.visuallog.data.model.DayEntry
import com.visuallog.data.model.Sticker
import com.visuallog.data.model.Stroke
import com.visuallog.engine.layer.LayerManager
import com.visuallog.ui.whiteboard.sticker.StickerRenderUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.geom.AffineTransform
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*
import javax.imageio.ImageIO

object PolaroidCardExporter {

    private const val CARD_WIDTH = 1200
    private const val CARD_HEIGHT = 1500

    /**
     * Renders a publication-grade Polaroid share card with paper texture, stamps, and watermark.
     */
    fun createCardImage(
        dayEntry: DayEntry,
        thumbnailFile: File? = null,
        layerManager: LayerManager? = null,
        strokes: List<Stroke> = emptyList(),
        stickers: List<Sticker> = emptyList()
    ): BufferedImage {
        val image = BufferedImage(CARD_WIDTH, CARD_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        // 1. Warm Neutral Studio Background (#F4F1EA)
        g.color = java.awt.Color(0xF4, 0xF0, 0xE8)
        g.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT)

        // Subtle background dot pattern
        g.color = java.awt.Color(0xDF, 0xD9, 0xCE)
        val dotSpacing = 36
        for (x in 0 until CARD_WIDTH step dotSpacing) {
            for (y in 0 until CARD_HEIGHT step dotSpacing) {
                g.fillOval(x, y, 3, 3)
            }
        }

        // 2. Polaroid Paper Card Frame
        val cardMarginX = 120
        val cardMarginY = 100
        val cardW = CARD_WIDTH - (cardMarginX * 2) // 960
        val cardH = CARD_HEIGHT - (cardMarginY * 2) // 1300
        val cardCorner = 16f

        // Soft Diffuse Drop Shadow
        g.color = java.awt.Color(0, 0, 0, 25)
        g.fill(RoundRectangle2D.Float(
            cardMarginX.toFloat() + 4f, 
            cardMarginY.toFloat() + 10f, 
            cardW.toFloat(), 
            cardH.toFloat(), 
            cardCorner, 
            cardCorner
        ))
        g.color = java.awt.Color(0, 0, 0, 15)
        g.fill(RoundRectangle2D.Float(
            cardMarginX.toFloat() + 8f, 
            cardMarginY.toFloat() + 18f, 
            cardW.toFloat(), 
            cardH.toFloat(), 
            cardCorner + 4f, 
            cardCorner + 4f
        ))

        // White Paper Card
        g.color = java.awt.Color.WHITE
        g.fill(RoundRectangle2D.Float(
            cardMarginX.toFloat(), 
            cardMarginY.toFloat(), 
            cardW.toFloat(), 
            cardH.toFloat(), 
            cardCorner, 
            cardCorner
        ))

        // Subtle Card Border Line
        g.color = java.awt.Color(0xEA, 0xE7, 0xE1)
        g.stroke = BasicStroke(1.5f)
        g.draw(RoundRectangle2D.Float(
            cardMarginX.toFloat(), 
            cardMarginY.toFloat(), 
            cardW.toFloat(), 
            cardH.toFloat(), 
            cardCorner, 
            cardCorner
        ))

        // 3. Inner Photo / Whiteboard Frame
        val photoPadding = 56
        val photoX = cardMarginX + photoPadding
        val photoY = cardMarginY + photoPadding
        val photoSize = cardW - (photoPadding * 2) // 848 x 848 (Square Polaroid Canvas)

        // Photo Paper Background (Warm Retro Emulsion #F5EFE6)
        g.color = java.awt.Color(0xF5, 0xEF, 0xE6)
        g.fillRect(photoX, photoY, photoSize, photoSize)

        // Draw Content Image (From rendered thumbnail or freshly baked buffer)
        var contentImg: BufferedImage? = null
        if (thumbnailFile != null && thumbnailFile.exists()) {
            try {
                contentImg = ImageIO.read(thumbnailFile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        if (contentImg == null && layerManager != null) {
            try {
                val paddedBitmap = layerManager.generatePaddedThumbnail(
                    paths = strokes,
                    drawStickers = { canvas, layerId ->
                        stickers.filter { it.layer == layerId }.forEach { s ->
                            if (s.contentPath != "LOADING") {
                                val stickerBitmap = StickerRenderUtils.getOrGenerateBitmap(s.contentPath)
                                if (stickerBitmap != null) {
                                    val normalizedSize = 0.15f
                                    val cx = s.x + normalizedSize / 2f
                                    val cy = s.y + normalizedSize / 2f
                                    layerManager.bakeStickerToCanvas(
                                        targetCanvas = canvas,
                                        image = stickerBitmap,
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
                val w = paddedBitmap.width
                val h = paddedBitmap.height
                val pixels = IntArray(w * h)
                paddedBitmap.readPixels(pixels)
                val buf = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                buf.setRGB(0, 0, w, h, pixels, 0, w)
                contentImg = buf
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (contentImg != null) {
            g.drawImage(contentImg, photoX, photoY, photoSize, photoSize, null)
        }

        // Tactile Die-cut Inset Bezel & Recessed Shadow (Clearly separates film from white frame)
        // 1. Defined Die-cut Bezel Line
        g.color = java.awt.Color(0xD0, 0xC6, 0xB6)
        g.stroke = BasicStroke(2.5f)
        g.drawRect(photoX, photoY, photoSize, photoSize)

        // 2. Realistic Top & Left Inner Shadow (Simulates physical cardboard cutout depth)
        for (i in 0 until 10) {
            val alpha = ((10 - i) * 3).coerceIn(0, 255)
            g.color = java.awt.Color(35, 25, 15, alpha)
            g.fillRect(photoX, photoY + i, photoSize, 1)
            g.fillRect(photoX + i, photoY, 1, photoSize)
        }

        // 3. Subtle bottom & right inner highlight
        g.color = java.awt.Color(255, 255, 255, 130)
        g.stroke = BasicStroke(1.2f)
        g.drawLine(photoX, photoY + photoSize, photoX + photoSize, photoY + photoSize)
        g.drawLine(photoX + photoSize, photoY, photoX + photoSize, photoY + photoSize)

        // 4. Weekday Tape Sticker (Top-Right of Photo)
        val dateObj = try { LocalDate.parse(dayEntry.date) } catch (e: Exception) { LocalDate.now() }
        val dayName = dateObj.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase()
        
        val tapeW = 160
        val tapeH = 34
        val tapeX = photoX + photoSize - tapeW + 16
        val tapeY = photoY - 14

        val oldTransform = g.transform
        g.translate(tapeX + tapeW / 2.0, tapeY + tapeH / 2.0)
        g.rotate(Math.toRadians(3.5))
        
        g.color = java.awt.Color(0xE5, 0x73, 0x73, 210) // Coral washi tape
        g.fillRoundRect(-tapeW / 2, -tapeH / 2, tapeW, tapeH, 4, 4)
        
        g.color = java.awt.Color.WHITE
        g.font = Font("Monospaced", Font.BOLD, 15)
        val tapeFm = g.fontMetrics
        val tapeTextX = -tapeFm.stringWidth(dayName) / 2
        val tapeTextY = (tapeFm.ascent - tapeFm.descent) / 2
        g.drawString(dayName, tapeTextX, tapeTextY)
        
        g.transform = oldTransform

        // 5. Bezel Area (Date & Stamps)
        val bezelY = photoY + photoSize + 32
        
        // Date Text (Classic Serif Extra Bold)
        val formattedDate = dateObj.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)).uppercase()
        g.color = java.awt.Color(0x33, 0x33, 0x33)
        g.font = Font("Serif", Font.BOLD, 36)
        val dateFm = g.fontMetrics
        val dateX = cardMarginX + (cardW - dateFm.stringWidth(formattedDate)) / 2
        g.drawString(formattedDate, dateX, bezelY + 60)

        // Weather & Mood Indicators (Text Stamps)
        val metaParts = mutableListOf<String>()
        dayEntry.weather?.let { metaParts.add("WEATHER: $it") }
        dayEntry.mood?.let { metaParts.add("MOOD: $it") }

        if (metaParts.isNotEmpty()) {
            val metaString = metaParts.joinToString("  •  ")
            g.color = java.awt.Color(0x8D, 0x6E, 0x63)
            g.font = Font("SansSerif", Font.BOLD, 16)
            val metaFm = g.fontMetrics
            val metaX = cardMarginX + (cardW - metaFm.stringWidth(metaString)) / 2
            g.drawString(metaString, metaX, bezelY + 110)
        }

        // 6. Watermark at Bottom of Card
        val watermark = "✦ VISUAL LIFE LOG · MEMORIES NEVER FADE ✦"
        g.color = java.awt.Color(0xB0, 0xA4, 0x98)
        g.font = Font("Serif", Font.PLAIN, 15)
        val wmFm = g.fontMetrics
        val wmX = cardMarginX + (cardW - wmFm.stringWidth(watermark)) / 2
        g.drawString(watermark, wmX, cardMarginY + cardH - 36)

        g.dispose()
        return image
    }

    /**
     * Copies the given image to the system clipboard.
     */
    fun copyToClipboard(image: BufferedImage) {
        val transferable = object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == DataFlavor.imageFlavor
            override fun getTransferData(flavor: DataFlavor?): Any {
                if (flavor == DataFlavor.imageFlavor) return image
                throw UnsupportedFlavorException(flavor)
            }
        }
        Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
    }

    data class ExportCardResult(
        val image: BufferedImage,
        val file: File?
    )

    /**
     * Prompts the user with a native Save File Dialog and saves the image to disk.
     * Also automatically copies the card to the clipboard.
     */
    suspend fun exportCardWithDialog(
        dayEntry: DayEntry,
        layerManager: LayerManager? = null,
        strokes: List<Stroke> = emptyList(),
        stickers: List<Sticker> = emptyList()
    ): ExportCardResult {
        return withContext(Dispatchers.IO) {
            val cardImage = createCardImage(
                dayEntry = dayEntry,
                thumbnailFile = dayEntry.thumbnailPath?.let { File(it) },
                layerManager = layerManager,
                strokes = strokes,
                stickers = stickers
            )

            // 1. Copy to clipboard immediately
            try {
                copyToClipboard(cardImage)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Open Save File Dialog
            val defaultFileName = "VisualLog_${dayEntry.date}.png"
            val file = saveCardImageWithDialog(cardImage, defaultFileName)
            ExportCardResult(cardImage, file)
        }
    }

    /**
     * Prompts the user with a native Save File Dialog and writes the image to disk.
     */
    fun saveCardImageWithDialog(cardImage: BufferedImage, defaultFileName: String): File? {
        val fileDialog = FileDialog(null as Frame?, "Export Polaroid Card", FileDialog.SAVE).apply {
            file = defaultFileName
            setFilenameFilter { _, name -> name.lowercase().endsWith(".png") }
        }
        fileDialog.isVisible = true

        val selectedFile = fileDialog.file
        val selectedDir = fileDialog.directory

        return if (selectedFile != null && selectedDir != null) {
            val destFile = if (selectedFile.endsWith(".png", ignoreCase = true)) {
                File(selectedDir, selectedFile)
            } else {
                File(selectedDir, "$selectedFile.png")
            }
            ImageIO.write(cardImage, "png", destFile)
            destFile
        } else {
            null
        }
    }

    /**
     * Reveals the file in macOS Finder or Windows Explorer.
     */
    fun revealInFileManager(file: File) {
        try {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                    desktop.browseFileDirectory(file)
                    return
                }
                if (file.parentFile != null && desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(file.parentFile)
                    return
                }
            }
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("mac")) {
                Runtime.getRuntime().exec(arrayOf("open", "-R", file.absolutePath))
            } else if (os.contains("windows")) {
                Runtime.getRuntime().exec(arrayOf("explorer.exe", "/select,", file.absolutePath))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
