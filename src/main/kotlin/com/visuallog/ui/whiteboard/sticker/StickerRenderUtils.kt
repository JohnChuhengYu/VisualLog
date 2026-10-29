package com.visuallog.ui.whiteboard.sticker

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposePaint
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.Typeface
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.awt.geom.RoundRectangle2D
import java.awt.RenderingHints

/**
 * Single Source of Truth for Sticker Visuals.
 * Generates the "Texture" (Frame + Shadow + Content) used by both the Editor and Thumbnail generator.
 */
object StickerRenderUtils {
    const val TEXTURE_SIZE = 1024
    const val LOGICAL_SIZE = 150.0
    const val CONTENT_SCALE = 0.9f
    
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun getOrGenerateBitmap(path: String): ImageBitmap? {
        if (path == "LOADING" || path.isEmpty()) return null
        
        cache[path]?.let { return it }
        
        val generated = generateBitmap(path)
        if (generated != null) {
            cache[path] = generated
        }
        return generated
    }

    private fun generateBitmap(path: String): ImageBitmap? {
        try {
            val isExplicitEmoji = path.startsWith("emoji:")
            val isImplicitEmoji = path.length < 10 && !path.contains("/") && !path.contains("\\") && path != "LOADING"
            val isEmoji = isExplicitEmoji || isImplicitEmoji
            
            val size = TEXTURE_SIZE
            val surface = Surface.makeRasterN32Premul(size, size)
            val canvas = surface.canvas
            
            if (isEmoji) {
                val emoji = if (isExplicitEmoji) path.removePrefix("emoji:") else path
                
                var typeface = Typeface.makeFromName("Apple Color Emoji", FontStyle.NORMAL)
                if (typeface == null) typeface = Typeface.makeFromName("Segoe UI Emoji", FontStyle.NORMAL)
                if (typeface == null) typeface = Typeface.makeFromName("Noto Color Emoji", FontStyle.NORMAL)
                if (typeface == null) typeface = Typeface.makeDefault()
                
                val fontSize = (size * 0.75f)
                val font = Font(typeface, fontSize)
                val textLine = TextLine.make(emoji, font)
                
                val contentSize = size * CONTENT_SCALE
                val margin = (size - contentSize) / 2f
                val contentCenter = margin + (contentSize / 2f)
                
                val x = contentCenter - (textLine.width / 2f)
                val y = contentCenter - (textLine.ascent + textLine.descent) / 2f
                
                val paint = org.jetbrains.skia.Paint().apply { isAntiAlias = true }
                canvas.drawTextLine(textLine, x, y, paint)
                
            } else {
                val file = File(path)
                if (!file.exists()) return null
                
                val originalImage = ImageIO.read(file) ?: return null
                
                val contentSize = size * CONTENT_SCALE
                val margin = (size - contentSize) / 2f
                val innerPad = contentSize * 0.05f 
                val drawSize = contentSize - (innerPad * 2f)
                
                val ratio = originalImage.width.toFloat() / originalImage.height.toFloat()
                val targetW: Float
                val targetH: Float
                
                if (ratio > 1) {
                    targetW = drawSize
                    targetH = drawSize / ratio
                } else {
                    targetH = drawSize
                    targetW = drawSize * ratio
                }
                
                val startX = margin + innerPad + (drawSize - targetW) / 2f
                val startY = margin + innerPad + (drawSize - targetH) / 2f
                
                val awtSurface = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
                val g2d = awtSurface.createGraphics()
                
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                
                // Draw Shadow
                val shadowOffset = size * 0.02f
                g2d.color = java.awt.Color(0, 0, 0, 50)
                g2d.fill(RoundRectangle2D.Float(
                    margin + shadowOffset, 
                    margin + shadowOffset, 
                    contentSize, 
                    contentSize, 
                    size * 0.04f, 
                    size * 0.04f
                ))
                
                // Draw Paper Frame
                g2d.color = java.awt.Color.WHITE
                g2d.fill(RoundRectangle2D.Float(
                    margin, 
                    margin, 
                    contentSize, 
                    contentSize, 
                    size * 0.03f, 
                    size * 0.03f
                ))
                
                // Draw Inner Content Background
                g2d.color = java.awt.Color(245, 245, 245)
                g2d.fill(RoundRectangle2D.Float(
                    margin + innerPad, 
                    margin + innerPad, 
                    drawSize, 
                    drawSize, 
                    size * 0.02f, 
                    size * 0.02f
                ))
                
                // Draw Photo
                g2d.drawImage(
                    originalImage, 
                    startX.toInt(), 
                    startY.toInt(), 
                    targetW.toInt(), 
                    targetH.toInt(), 
                    null
                )
                
                g2d.dispose()
                
                return awtSurface.toComposeImageBitmap()
            }
            
            val image = surface.makeImageSnapshot()
            val composeBitmap = ImageBitmap(size, size)
            val composeCanvas = Canvas(composeBitmap)
            val nativeCanvas = composeCanvas.nativeCanvas
            
            nativeCanvas.drawImage(image, 0f, 0f)
            
            return composeBitmap
            
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}

