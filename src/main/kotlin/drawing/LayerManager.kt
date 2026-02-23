package drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Manages the "Baked Layer" - a bitmap that stores all finished strokes.
 * This allows O(1) rendering of the past, regardless of how many strokes exist.
 */
class LayerManager {
    // Fixed resolution for the backing store (High Quality)
    private val LISTEN_DIMENSION = 2160
    // The logical coordinate space we map from (0.0 .. 1.0 normalized)
    private val LOGICAL_DIMENSION = 1.0f
    private val BUFFER_SCALE = LISTEN_DIMENSION.toFloat() // Scale factor for 1.0 normalized to 2160 pixels

    // The backing bitmaps (One per layer)
    private val LAYER_COUNT = 3
    val buffers: List<ImageBitmap> = List(LAYER_COUNT) { 
        ImageBitmap(LISTEN_DIMENSION, LISTEN_DIMENSION, ImageBitmapConfig.Argb8888) 
    }
        
    private val canvases: List<Canvas> = buffers.map { Canvas(it) }
    
    // Helper to access the internal scaling factor
    val bufferScale: Float get() = BUFFER_SCALE

    init {
        // Initialize clear
        clearAll()
    }

    /**
     * "Bakes" a path onto the bitmap layer.
     * The input path is expected to be in LOGICAL coordinates (0..1000).
     * We scale it up to the bitmap buffer size (0..2160).
     */
    fun bakeStroke(path: Path, style: Stroke, color: Color, blendMode: BlendMode = BlendMode.SrcOver, layer: Int) {
        // Clamp layer to valid range
        val targetLayer = layer.coerceIn(0, LAYER_COUNT - 1)
        
        val paint = Paint().apply {
            this.color = color
            this.style = PaintingStyle.Stroke
            this.strokeWidth = style.width * (BUFFER_SCALE / 1000f) // Normalized to 1000px coordinate space
            this.strokeCap = style.cap
            this.strokeJoin = style.join
            this.isAntiAlias = true
            this.blendMode = blendMode
        }
        
        // Transform path to buffer coordinates
        val bakePath = Path()
        bakePath.addPath(path)
        bakePath.transform(Matrix().apply { scale(BUFFER_SCALE, BUFFER_SCALE, 1f) })
        
        canvases[targetLayer].drawPath(bakePath, paint)
    }

    /**
     * Clears all layers.
     */
    fun clearAll() {
        val paint = Paint().apply { 
            blendMode = BlendMode.Clear 
            color = Color.Transparent
        }
        canvases.forEach { canvas ->
            canvas.drawRect(0f, 0f, LISTEN_DIMENSION.toFloat(), LISTEN_DIMENSION.toFloat(), paint)
        }
    }
    
    /**
     * Restores content from a list of paths.
     * Paths are expected to be in LOGICAL coordinates.
     */
    fun restoreFromPaths(strokes: List<data.Stroke>) {
        // Clear first
        clearAll()
        
        strokes.forEach { stroke ->
            val targetLayer = stroke.layer.coerceIn(0, LAYER_COUNT - 1)
            
             val paint = Paint().apply {
                this.color = if (stroke.isEraser) Color.Transparent else Color(stroke.color)
                this.style = PaintingStyle.Stroke
                this.strokeWidth = stroke.width * (BUFFER_SCALE / 1000f)
                this.strokeCap = StrokeCap.Round
                this.strokeJoin = StrokeJoin.Round
                this.isAntiAlias = true
                this.blendMode = if (stroke.isEraser) BlendMode.Clear else BlendMode.SrcOver
            }
            
            val bakePath = Path()
            bakePath.addPath(stroke.path)
            bakePath.transform(Matrix().apply { scale(BUFFER_SCALE, BUFFER_SCALE, 1f) })
            
            canvases[targetLayer].drawPath(bakePath, paint)
        }
    }
    
    /**
     * Bakes a sticker image onto the buffer.
     * Coordinates are in LOGICAL space (0..1000).
     * The sticker is drawn at (x, y) with the specified size, rotation, and scale.
     */


    // Helper for arbitrary canvas
    fun bakeStickerToCanvas(
        targetCanvas: androidx.compose.ui.graphics.Canvas,
        image: ImageBitmap,
        centerX: Float,
        centerY: Float,
        sizeLogical: Float,
        rotation: Float,
        scale: Float
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
        }
        
        val bufCenterX = centerX * BUFFER_SCALE
        val bufCenterY = centerY * BUFFER_SCALE
        // We divide by CONTENT_SCALE because the sticker texture includes 10% margin for shadow.
        // This ensures the actual sticker visual content matches sizeLogical.
        val bufSize = (sizeLogical / ui.StickerRenderUtils.CONTENT_SCALE) * BUFFER_SCALE * scale
        
        targetCanvas.save()
        targetCanvas.translate(bufCenterX, bufCenterY)
        targetCanvas.rotate(rotation)
        val half = bufSize / 2f
        
        targetCanvas.drawImageRect(
            image = image,
            srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
            srcSize = androidx.compose.ui.unit.IntSize(image.width, image.height),
            dstOffset = androidx.compose.ui.unit.IntOffset((-half).toInt(), (-half).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(bufSize.toInt(), bufSize.toInt()),
            paint = paint
        )
        targetCanvas.restore()
    }

    /**
     * Generates a new Thumbnail Bitmap with padding applied.
     * Scales content down by 0.9x and centers it, ensuring content near edges is not clipped.
     */
    fun generatePaddedThumbnail(
        paths: List<data.Stroke>,
        drawStickers: (androidx.compose.ui.graphics.Canvas, Int) -> Unit // Accept Layer ID
    ): ImageBitmap {
        val outBitmap = ImageBitmap(LISTEN_DIMENSION, LISTEN_DIMENSION)
        val outCanvas = androidx.compose.ui.graphics.Canvas(outBitmap)
        val paint = Paint().apply {
            isAntiAlias = true
            style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
            strokeJoin = StrokeJoin.Round
        }

        // 1. Clear Background (White)
        val bgPaint = Paint().apply { color = androidx.compose.ui.graphics.Color.White }
        outCanvas.drawRect(0f, 0f, LISTEN_DIMENSION.toFloat(), LISTEN_DIMENSION.toFloat(), bgPaint)

        // Setup Pad Transform parameters
        val cx = LISTEN_DIMENSION / 2f
        val cy = LISTEN_DIMENSION / 2f
        val paddingScale = 0.9f

        // LOOP LAYERS
        for (layerId in 0 until LAYER_COUNT) {
            
            // --- DRAW STROKES for Layer ---
            outCanvas.save()
            // Apply Padding
            outCanvas.translate(cx, cy)
            outCanvas.scale(paddingScale, paddingScale)
            outCanvas.translate(-cx, -cy)
            
            // Enter Buffer Space
            outCanvas.scale(BUFFER_SCALE, BUFFER_SCALE) 
            
            paths.filter { it.layer == layerId || (layerId == 1 && it.layer == 0) /* Fallback or exact match? Exact match */ }
                 .filter { it.layer == layerId }
                 .forEach { stroke ->
                    if (!stroke.isEraser) {
                        paint.color = androidx.compose.ui.graphics.Color(stroke.color)
                        paint.strokeWidth = stroke.width / 1000f
                        outCanvas.drawPath(stroke.path, paint)
                    }
                 }
            outCanvas.restore()

            // --- DRAW STICKERS for Layer ---
            outCanvas.save()
            // Apply Padding
            outCanvas.translate(cx, cy)
            outCanvas.scale(paddingScale, paddingScale)
            outCanvas.translate(-cx, -cy)
            
            // Call callback to draw stickers for this layer
            drawStickers(outCanvas, layerId)
            
            outCanvas.restore()
        }
        
        return outBitmap
    }
}
