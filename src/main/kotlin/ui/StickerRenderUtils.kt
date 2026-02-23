package ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposePaint
import androidx.compose.ui.graphics.toComposeImageBitmap

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas as SkiaCanvas
import org.jetbrains.skia.Color as SkiaColor
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint as SkiaPaint
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
 * This guarantees 100% WYSIWYG fidelity.
 * 
 * COORDINATE SYSTEM:
 * - Canvas: 1000x1000 model units
 * - Sticker size: 150 model units (15% of canvas width)
 * - Sticker x, y: Model coordinates (0-1000), representing top-left corner
 * - Texture: 300x300 pixels (pre-rendered bitmap)
 */
object StickerRenderUtils {
    // Fixed Physical Size for the Texture (High Res for 4K / Printing)
    // 1024px ensures that even when baked onto 2160px canvas, it holds up.
    const val TEXTURE_SIZE = 1024
    
    // NEW: Canonical Model Size (Validation: 150 units on 1000 unit canvas = 15%)
    const val LOGICAL_SIZE = 150.0
    
    // Scale factor for the sticker content within the generated texture.
    const val CONTENT_SCALE = 0.9f
    
    // Cache: Path -> Bitmap
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    /**
     * Get (or generate) the Compose ImageBitmap for the UI.
     * This is blocking if not cached! Call from IO scope/LaunchedEffect.
     */
    fun getOrGenerateBitmap(path: String): ImageBitmap? {
        if (path == "LOADING" || path.isEmpty()) return null
        
        cache[path]?.let { return it }
        
        val generated = generateBitmap(path)
        if (generated != null) {
            cache[path] = generated
        }
        return generated
    }

    /**
     * Generates a Bitmap (Skia/Compose) using robust rendering for Emojis and Photos.
     */
    private fun generateBitmap(path: String): ImageBitmap? {
        try {
            // Updated Detection Logic
            val isExplicitEmoji = path.startsWith("emoji:")
            val isImplicitEmoji = path.length < 10 && !path.contains("/") && !path.contains("\\") && path != "LOADING"
            val isEmoji = isExplicitEmoji || isImplicitEmoji
            
            // Output Surface (Skia)
            val size = TEXTURE_SIZE
            val surface = Surface.makeRasterN32Premul(size, size)
            val canvas = surface.canvas
            
            if (isEmoji) {
                // --- EMOJI / ICON MODE (Using Skia directly for Color Font Support) ---
                val emoji = if (isExplicitEmoji) path.removePrefix("emoji:") else path
                
                // 1. Setup Font (Mac-safe fallback)
                // "Apple Color Emoji" is the key for MacOS
                var typeface = Typeface.makeFromName("Apple Color Emoji", FontStyle.NORMAL)
                if (typeface == null) typeface = Typeface.makeFromName("Segoe UI Emoji", FontStyle.NORMAL)
                if (typeface == null) typeface = Typeface.makeFromName("Noto Color Emoji", FontStyle.NORMAL)
                if (typeface == null) typeface = Typeface.makeDefault()
                
                var fontSize = (size * 0.75f)
                var font = Font(typeface, fontSize)
                
                // Measure
                var textLine = TextLine.make(emoji, font)
                var width = textLine.width
                var height = textLine.capHeight // Approximate cap height for centering
                
                // If width is too wide (compound emoji?), scale down
                if (width > size * 0.9f) {
                    val scale = (size * 0.9f) / width
                    fontSize *= scale
                    font = Font(typeface, fontSize)
                    textLine = TextLine.make(emoji, font)
                    width = textLine.width
                    height = textLine.capHeight
                }
                
                val cx = size / 2f
                val cy = size / 2f
                // Text origin is baseline.
                // We want to center the CapHeight + Descent? 
                // Simple centering:
                val x = cx - width / 2f
                val y = cy + height / 2f - (height * 0.1f) // heuristic adjustment
                
                // 2. Draw Shadow (Simple Blur simulation)
                // Skia ImageFilter is powerful but complex to interact with TextBlob directly in one go?
                // Compose uses Paint with Shadow.
                // We'll simulate shadow by drawing black text with blur.
                
                val shadowPaint = SkiaPaint().apply {
                    color = SkiaColor.BLACK
                    alpha = 60 // 0-255
                    maskFilter = org.jetbrains.skia.MaskFilter.makeBlur(org.jetbrains.skia.FilterBlurMode.NORMAL, size * 0.02f)
                }
                canvas.drawTextLine(textLine, x, y, shadowPaint)
                
                // 3. Draw Text
                val textPaint = SkiaPaint().apply {
                     color = SkiaColor.BLACK // Default, but Color Fonts ignore this usually
                }
                canvas.drawTextLine(textLine, x, y, textPaint)
                
            } else {
                // --- PHOTO / POLAROID MODE (Hybrid AWT loading -> Skia Draw) ---
                val file = File(path)
                if (!file.exists()) return null
                
                // Load Image data
                // Image.makeFromEncoded works with PNG/JPG bytes
                val bytes = file.readBytes()
                var skiaImage = Image.makeFromEncoded(bytes)
                if (skiaImage == null) return null // failed decode
                
                // Geometry
                val visualSize = (size * CONTENT_SCALE)
                val margin = (size - visualSize) / 2f
                val corner = (visualSize * 0.04f)
                val innerPad = (visualSize * 0.04f)
                val frameX = margin
                val frameY = margin
                
                // 2. Frame (White)
                val framePaint = SkiaPaint().apply {
                    color = SkiaColor.WHITE
                    isAntiAlias = true
                }
                val frameRect = Rect.makeXYWH(frameX, frameY, visualSize, visualSize)
                canvas.drawRRect(org.jetbrains.skia.RRect.makeXYWH(frameX, frameY, visualSize, visualSize, corner * 2), framePaint)
                
                // 3. Image Content (Center Crop)
                val contentSize = visualSize - innerPad * 2
                val contentX = frameX + innerPad
                val contentY = frameY + innerPad
                
                val imgW = skiaImage.width.toFloat()
                val imgH = skiaImage.height.toFloat()
                val srcSize = kotlin.math.min(imgW, imgH)
                val sx = (imgW - srcSize) / 2f
                val sy = (imgH - srcSize) / 2f
                
                val srcRect = Rect.makeXYWH(sx, sy, srcSize, srcSize)
                val dstRect = Rect.makeXYWH(contentX, contentY, contentSize, contentSize)
                
                // Clip
                canvas.save()
                val innerCorner = kotlin.math.max(0f, corner - innerPad)
                canvas.clipRRect(org.jetbrains.skia.RRect.makeXYWH(contentX, contentY, contentSize, contentSize, innerCorner * 2), true)
                
                canvas.drawImageRect(skiaImage, srcRect, dstRect, SkiaPaint().apply { isAntiAlias = true })
                canvas.restore()
            }
            
            // Convert Surface to ImageBitmap
            return surface.makeImageSnapshot().toComposeImageBitmap()
            
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    fun clearCache() {
        cache.clear()
    }
}
