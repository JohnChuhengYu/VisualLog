package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

@Stable
class ViewportState(
    initialScale: Float = 1f,
    initialOffset: Offset = Offset.Zero
) {
    var scale by mutableFloatStateOf(initialScale)
    var offset by mutableStateOf(initialOffset)
    
    // The fixed size of the logical document (Standard 1000x1000 units)
    // This allows us to map everything to a consistent coordinate system regardless of screen DPI/Size.
    val documentSize = Size(1000f, 1000f)

    /**
     * Updates the viewport transform.
     */
    fun transform(zoomChange: Float, panChange: Offset, pivot: Offset) {
        val oldScale = scale
        val newScale = (scale * zoomChange).coerceIn(0.1f, 10f)
        
        // Calculate new offset to keep the pivot point fixed relative to the screen
        // Pivot in Model coordinates:
        // (PivotScreen - OldOffset) / OldScale = PivotModel
        // NewOffset = PivotScreen - (PivotModel * NewScale)
        
        val pivotModel = (pivot - offset) / oldScale
        val newOffset = pivot - (pivotModel * newScale) + panChange
        
        scale = newScale
        offset = newOffset
    }
    
    fun setViewport(newScale: Float, newOffset: Offset) {
        scale = newScale
        offset = newOffset
    }

    /**
     * Converts a point from Screen (Pixel) coordinates to Model (Document) coordinates.
     */
    fun screenToModel(screenPoint: Offset): Offset {
        return (screenPoint - offset) / scale
    }
    
    /**
     * Converts a point from Model (Document) coordinates to Screen (Pixel) coordinates.
     */
    fun modelToScreen(modelPoint: Offset): Offset {
        return (modelPoint * scale) + offset
    }

    fun getMatrix(): Matrix {
        return Matrix().apply {
            translate(offset.x, offset.y)
            scale(scale, scale, 1f)
        }
    }
}

@Composable
fun rememberViewportState(): ViewportState {
    return remember { ViewportState() }
}
