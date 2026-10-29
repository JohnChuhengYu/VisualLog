package com.visuallog.engine.layer

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import com.visuallog.data.model.Stroke as LogStroke

/**
 * Manages the "Baked Layer" - a bitmap that stores all finished strokes.
 * This allows O(1) rendering of past strokes, regardless of how many strokes exist.
 */
class LayerManager {
    // Fixed resolution for the backing store (High Quality)
    private val LISTEN_DIMENSION = 2160
    private val BUFFER_SCALE = LISTEN_DIMENSION.toFloat() // Scale factor for 1.0 normalized to 2160 pixels

    // The backing bitmaps (One per layer: 0=Bottom, 1=Middle, 2=Top)
    private val LAYER_COUNT = 3
    val buffers: List<ImageBitmap> = List(LAYER_COUNT) { 
        ImageBitmap(LISTEN_DIMENSION, LISTEN_DIMENSION, ImageBitmapConfig.Argb8888) 
    }
        
    private val canvases: List<Canvas> = buffers.map { Canvas(it) }
    
    // Helper to access the internal scaling factor
    val bufferScale: Float get() = BUFFER_SCALE

    init {
        clearAll()
    }

    /**
     * "Bakes" a path onto the bitmap layer.
     * The input path is expected to be in LOGICAL coordinates (0..1.0).
     */
    fun bakeStroke(path: Path, style: Stroke, color: Color, blendMode: BlendMode = BlendMode.SrcOver, layer: Int) {
        val targetLayer = layer.coerceIn(0, LAYER_COUNT - 1)
        
        val paint = Paint().apply {
            this.color = color
            this.style = PaintingStyle.Stroke
            this.strokeWidth = style.width * (BUFFER_SCALE / 1000f)
            this.strokeCap = style.cap
            this.strokeJoin = style.join
            this.isAntiAlias = true
            this.blendMode = blendMode
        }
        
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
     * Restores content from a list of strokes.
     */
    fun restoreFromPaths(strokes: List<LogStroke>) {
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
     * Bakes a sticker image onto an arbitrary target canvas.
     */
    fun bakeStickerToCanvas(
        targetCanvas: Canvas,
        image: ImageBitmap,
        centerX: Float,
        centerY: Float,
        sizeLogical: Float,
        rotation: Float,
        scale: Float,
        contentScale: Float = 0.9f
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
        }
        
        val bufCenterX = centerX * BUFFER_SCALE
        val bufCenterY = centerY * BUFFER_SCALE
        val bufSize = (sizeLogical / contentScale) * BUFFER_SCALE * scale
        
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
        paths: List<LogStroke>,
        drawStickers: (Canvas, Int) -> Unit
    ): ImageBitmap {
        val outBitmap = ImageBitmap(LISTEN_DIMENSION, LISTEN_DIMENSION)
        val outCanvas = Canvas(outBitmap)
        val paint = Paint().apply {
            isAntiAlias = true
            style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
            strokeJoin = StrokeJoin.Round
        }

        // 1. Clear Background (White)
        val bgPaint = Paint().apply { color = Color.White }
        outCanvas.drawRect(0f, 0f, LISTEN_DIMENSION.toFloat(), LISTEN_DIMENSION.toFloat(), bgPaint)

        // Setup Pad Transform parameters
        val cx = LISTEN_DIMENSION / 2f
        val cy = LISTEN_DIMENSION / 2f
        val paddingScale = 0.9f

        // Loop Layers
        for (layerId in 0 until LAYER_COUNT) {
            
            // --- DRAW STROKES for Layer ---
            outCanvas.save()
            outCanvas.translate(cx, cy)
            outCanvas.scale(paddingScale, paddingScale)
            outCanvas.translate(-cx, -cy)
            outCanvas.scale(BUFFER_SCALE, BUFFER_SCALE) 
            
            paths.filter { it.layer == layerId }
                 .forEach { stroke ->
                    if (!stroke.isEraser) {
                        paint.color = Color(stroke.color)
                        paint.strokeWidth = stroke.width / 1000f
                        outCanvas.drawPath(stroke.path, paint)
                    }
                 }
            outCanvas.restore()

            // --- DRAW STICKERS for Layer ---
            outCanvas.save()
            outCanvas.translate(cx, cy)
            outCanvas.scale(paddingScale, paddingScale)
            outCanvas.translate(-cx, -cy)
            
            drawStickers(outCanvas, layerId)
            
            outCanvas.restore()
        }
        
        return outBitmap
    }
}

