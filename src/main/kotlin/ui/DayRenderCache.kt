package ui

import androidx.compose.ui.geometry.Rect
import data.DayEntry
import data.Sticker
import data.Stroke
import data.SerializationUtils
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min

// Data holder for pre-computed render info
data class DayRenderData(
    val dayEntryId: Int, // For validation
    val strokes: List<Stroke>,
    val contentBounds: Rect,
    val stickers: List<PreviewSticker>
)

data class PreviewSticker(
    val sticker: Sticker,
    val bounds: Rect // The axis-aligned bounding box of this sticker in Model Space
)

object DayRenderCache {
    private val cache = ConcurrentHashMap<String, DayRenderData>()
    
    // LOGICAL BASE SIZE (Model Space)
    // Matches the default visual size of stickers in the editor relative to strokes
    const val BASE_STICKER_SIZE = 150f

    fun getData(dayEntry: DayEntry, density: Float): DayRenderData {
        // Create a cache key that changes if content changes OR density changes.
        // density is critical because Stickers are Dp-based (Screen-dependent) while Strokes are Px-based.
        val key = "${dayEntry.id}_${dayEntry.drawingPaths.hashCode()}_${dayEntry.stickers.hashCode()}_d${density}"
        
        return cache.getOrPut(key) {
             computeData(dayEntry, density)
        }
    }

    private fun computeData(dayEntry: DayEntry, density: Float): DayRenderData {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var hasBounds = false

        // 1. Process Strokes
        val strokes = if (dayEntry.drawingPaths.isNotEmpty() && dayEntry.drawingPaths != "[]") {
            SerializationUtils.deserializeStrokes(dayEntry.drawingPaths)
        } else {
            emptyList()
        }

        strokes.forEach { stroke ->
            // Stroke paths are in Pixels
            val b = stroke.path.getBounds()
            if (b.left < minX) minX = b.left
            if (b.right > maxX) maxX = b.right
            if (b.top < minY) minY = b.top
            if (b.bottom > maxY) maxY = b.bottom
            hasBounds = true
        }

        // 2. Process Stickers & Compute their bounds
        val previewStickers = dayEntry.stickers.map { sticker ->
            // Calculate Axis-Aligned Bounding Box (AABB) for rotated/scaled sticker
            // Logic: 
            // Sticker Base Size is 150.dp.
            // In Pixels: 150f * density.
            val baseSizePx = BASE_STICKER_SIZE * density
            val baseHalfSize = baseSizePx / 2f
            
            // Pivot is at Center of the unscaled, unrotated box
            // Note: Sticker X/Y in data is the Top-Left of the *unrotated*, *unscaled* box in View coordinates.
            val pivotX = sticker.x + baseHalfSize
            val pivotY = sticker.y + baseHalfSize
            
            // Corners relative to pivot (scaled)
            val scaledHalf = baseHalfSize * sticker.scale
            
            // Corners: TL, TR, BR, BL relative to Pivot
            val corners = listOf(
                Pair(-scaledHalf, -scaledHalf),
                Pair(scaledHalf, -scaledHalf),
                Pair(scaledHalf, scaledHalf),
                Pair(-scaledHalf, scaledHalf)
            )
            
            val rad = Math.toRadians(sticker.rotation.toDouble())
            val cosTheta = cos(rad).toFloat()
            val sinTheta = sin(rad).toFloat()
            
            var sMinX = Float.POSITIVE_INFINITY
            var sMinY = Float.POSITIVE_INFINITY
            var sMaxX = Float.NEGATIVE_INFINITY
            var sMaxY = Float.NEGATIVE_INFINITY
            
            corners.forEach { (dx, dy) ->
                // Rotate
                val rx = dx * cosTheta - dy * sinTheta
                val ry = dx * sinTheta + dy * cosTheta
                
                // Translate back to world
                val wx = pivotX + rx
                val wy = pivotY + ry
                
                if (wx < sMinX) sMinX = wx
                if (wx > sMaxX) sMaxX = wx
                if (wy < sMinY) sMinY = wy
                if (wy > sMaxY) sMaxY = wy
            }
            
            val stickerBounds = Rect(sMinX, sMinY, sMaxX, sMaxY)
            
            if (stickerBounds.left < minX) minX = stickerBounds.left
            if (stickerBounds.right > maxX) maxX = stickerBounds.right
            if (stickerBounds.top < minY) minY = stickerBounds.top
            if (stickerBounds.bottom > maxY) maxY = stickerBounds.bottom
            hasBounds = true

            PreviewSticker(sticker, stickerBounds)
        }

        val finalBounds = if (hasBounds) {
            // Add padding to ensure content isn't touching edges
            val padding = 20f
            Rect(minX - padding, minY - padding, maxX + padding, maxY + padding)
        } else {
            // Default empty bounds
            Rect(0f, 0f, 1000f, 1000f)
        }

        return DayRenderData(
            dayEntryId = dayEntry.id,
            strokes = strokes,
            contentBounds = finalBounds,
            stickers = previewStickers
        )
    }
}
