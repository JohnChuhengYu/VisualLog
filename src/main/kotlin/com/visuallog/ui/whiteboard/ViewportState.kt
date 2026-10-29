package com.visuallog.ui.whiteboard

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

@Stable
class ViewportState(
    initialScale: Float = 1f,
    initialOffset: Offset = Offset.Zero
) {
    var scale by mutableFloatStateOf(initialScale)
    var offset by mutableStateOf(initialOffset)
    
    // The fixed size of the logical document (Standard 1000x1000 units)
    val documentSize = Size(1000f, 1000f)

    fun transform(zoomChange: Float, panChange: Offset, pivot: Offset) {
        val oldScale = scale
        val newScale = (scale * zoomChange).coerceIn(0.1f, 10f)
        val pivotModel = (pivot - offset) / oldScale
        val newOffset = pivot - (pivotModel * newScale) + panChange
        
        scale = newScale
        offset = newOffset
    }
    
    fun setViewport(newScale: Float, newOffset: Offset) {
        scale = newScale
        offset = newOffset
    }

    fun screenToModel(screenPoint: Offset): Offset {
        return (screenPoint - offset) / scale
    }
    
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

