package drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Density
import data.Sticker
import data.Stroke
import java.io.File
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.toAwtImage
import javax.imageio.ImageIO

object SnapshotUtils {
    // Standard size for thumbnails. High enough for quality, low enough for performance.
    private const val SNAPSHOT_SIZE = 1024
    private const val PADDING_PERCENT = 0.1f // 10% padding

    fun captureSnapshot(
        strokes: List<Stroke>,
        stickers: List<Sticker>,
        density: Density,
        effectiveScale: Float // local pixel per virtual unit
    ): ImageBitmap {
        val width = SNAPSHOT_SIZE
        val height = SNAPSHOT_SIZE
        
        // 1. Fixed "100% Editor Page" View
        // The user requested to see the exact 0..1000 virtual page, not a zoomed-in content crop.
        val virtualPageSize = 1000f
        
        // Calculate scale to fit the virtual page into the snapshot size (1024x1024)
        // We preserve aspect ratio (though 1000x1000 is square, as is 1024x1024)
        val fitScale = minOf(width.toFloat(), height.toFloat()) / virtualPageSize
        
        // Center the page in the snapshot (usually 0 if both are square)
        val totalTx = (width - virtualPageSize * fitScale) / 2f
        val totalTy = (height - virtualPageSize * fitScale) / 2f
        
        // Recalculate virtualBaseSize for sticker rendering
        // 150dp relative to screen width
        val stickerScreenPx = 150f * density.density
        val safePageScreenPx = if (effectiveScale <= 0f) 1000f else 1000f * effectiveScale
        val ratio = stickerScreenPx / safePageScreenPx
        val virtualBaseSize = 1000f * ratio
        

        val imageBitmap = ImageBitmap(width, height, ImageBitmapConfig.Argb8888)
        val canvas = Canvas(imageBitmap)
        val paint = Paint().apply { isAntiAlias = true }

        // 3. Draw White Background
        paint.color = Color.White
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), paint)

        // 4. Draw Strokes
        paint.style = PaintingStyle.Stroke
        paint.strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        paint.strokeJoin = androidx.compose.ui.graphics.StrokeJoin.Round

        strokes.forEach { stroke ->
            val p = Path()
            p.addPath(stroke.path)
            
            val m = Matrix()
            m.translate(totalTx, totalTy)
            m.scale(fitScale, fitScale, 1f)
            p.transform(m)

            paint.color = if (stroke.isEraser) Color.White else Color(stroke.color)
            paint.strokeWidth = stroke.width * fitScale
            paint.blendMode = BlendMode.SrcOver
            
            canvas.drawPath(p, paint)
        }

        // 5. Draw Stickers
        stickers.forEach { sticker ->
            canvas.save()
            
            val sSize = virtualBaseSize 
            val cx = sticker.x + sSize / 2f
            val cy = sticker.y + sSize / 2f
            
            // Map Center to Snapshot Space
            val visualCx = cx * fitScale + totalTx
            val visualCy = cy * fitScale + totalTy
            
            canvas.translate(visualCx, visualCy)
            canvas.rotate(sticker.rotation)
            canvas.scale(sticker.scale, sticker.scale) 
            
            // Base Size Canvas = virtualBaseSize * fitScale
            // Wait: sticker.scale is already applied via canvas.scale?
            // "virtualBaseSize" is base size. "virtualBaseSize * sticker.scale" is actual size.
            // If we canvas.scale(sticker.scale), we should draw box of size (virtualBaseSize * fitScale).
            // NO. `canvas.scale(sticker.scale)` scales the drawing coordinates.
            // So we should draw box of size (virtualBaseSize * fitScale) / sticker.scale? NO.
            // `canvas.scale` multiplies subsequent coordinates.
            // If we draw Rect(size), it becomes Rect(size * scale).
            // We want Final Size = (virtualBaseSize * scale) * fitScale.
            // Transform Chain: Translate(VisualCx) -> Rotate -> Scale(StickerScale) -> Draw Rec.
            // visualCx includes FitScale.
            // So we need Draw Rec Size * StickerScale = VirtualBaseSize * StickerScale * FitScale.
            // => Draw Rec Size = VirtualBaseSize * FitScale.
            
            val canvasBaseSize = virtualBaseSize * fitScale
            
            val half = canvasBaseSize / 2f
            val rect = Rect(-half, -half, half, half)
            
            // Shadow
            val shadowPaint = Paint().apply {
                color = Color.Black.copy(alpha = 0.2f)
                style = PaintingStyle.Fill
            }
            val shadowOffset = canvasBaseSize * 0.05f
            canvas.drawRect(rect.translate(shadowOffset, shadowOffset), shadowPaint)
            
            // White Border
            paint.color = Color.White
            paint.style = PaintingStyle.Fill
            paint.blendMode = BlendMode.SrcOver
            canvas.drawRect(rect, paint)
            
            // Content Area
            val padding = canvasBaseSize * (6f/150f)
            val contentRect = Rect(rect.left + padding, rect.top + padding, rect.right - padding, rect.bottom - padding)
            
            paint.color = Color.LightGray
            canvas.drawRect(contentRect, paint)
            
            if (sticker.contentPath.isNotEmpty()) {
                try {
                    val file = File(sticker.contentPath)
                    if (file.exists()) {
                        val stickerBitmap = loadImageBitmap(file.inputStream())
                        val srcSize = IntSize(stickerBitmap.width, stickerBitmap.height)
                        val dstSize = Size(contentRect.width, contentRect.height)
                        
                        val srcRatio = srcSize.width.toFloat() / srcSize.height
                        val dstRatio = dstSize.width / dstSize.height
                        
                        var srcW = srcSize.width
                        var srcH = srcSize.height
                        var srcX = 0
                        var srcY = 0
                        
                        if (srcRatio > dstRatio) {
                            srcW = (srcH * dstRatio).toInt()
                            srcX = (srcSize.width - srcW) / 2
                        } else {
                            srcH = (srcW / dstRatio).toInt()
                            srcY = (srcSize.height - srcH) / 2
                        }
                        
                        canvas.drawImageRect(
                            image = stickerBitmap,
                            srcOffset = IntOffset(srcX, srcY),
                            srcSize = IntSize(srcW, srcH),
                            dstOffset = IntOffset(contentRect.left.toInt(), contentRect.top.toInt()),
                            dstSize = IntSize(contentRect.width.toInt(), contentRect.height.toInt()),
                            paint = Paint()
                        )
                    }
                } catch (e: Exception) {
                    // Fail silently
                }
            } else {
                paint.color = Color(0xFFFFB300)
                canvas.drawCircle(Offset(0f, 0f), contentRect.width * 0.25f, paint)
            }
            
            canvas.restore()
        }
        
        return imageBitmap
    }
    
    fun saveSnapshot(bitmap: ImageBitmap, file: File) {
        try {
            val awtImage = bitmap.toAwtImage()
            ImageIO.write(awtImage, "png", file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
