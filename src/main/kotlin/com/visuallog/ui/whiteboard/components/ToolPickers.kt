package com.visuallog.ui.whiteboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BrushSizeSelector(
    currentWidth: Float,
    onWidthChange: (Float) -> Unit,
    color: Color
) {
    val sizes = listOf(5f, 15f, 30f)
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sizes.forEach { size ->
            val isSelected = kotlin.math.abs(currentWidth - size) < 1f
            Box(
                 modifier = Modifier
                     .size(24.dp)
                     .clip(CircleShape)
                     .clickable { onWidthChange(size) }
                     .background(if(isSelected) Color.Black.copy(alpha=0.05f) else Color.Transparent),
                 contentAlignment = Alignment.Center
            ) {
                 Canvas(modifier = Modifier.size(size.coerceAtMost(20f).dp)) {
                     drawCircle(color = color)
                 }
            }
        }
    }
}

@Composable
fun ColorPalette(
    currentColor: Color,
    onColorChange: (Color) -> Unit
) {
    val colors = listOf(
        Color(0xFF222222), // Black
        Color(0xFFFFFFFF), // White
        Color(0xFFD32F2F), // Red
        Color(0xFF1976D2), // Blue
        Color(0xFF388E3C), // Green
        Color(0xFFFBC02D), // Yellow
        Color(0xFF7B1FA2), // Purple
        Color(0xFFFF6D00), // Orange
        // Pastels
        Color(0xFFB0B0B0), // Grey
        Color(0xFFEF9A9A), // Pale Red
        Color(0xFF90CAF9), // Pale Blue
        Color(0xFFA5D6A7), // Pale Green
        Color(0xFFFFF59D), // Pale Yellow
        Color(0xFFCE93D8), // Pale Purple
        Color(0xFFFFCC80)  // Pale Orange
    )
    
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        colors.chunked(3).forEach { rowColors ->
             Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                 rowColors.forEach { color ->
                     val isSelected = currentColor == color
                     Box(
                         modifier = Modifier
                             .size(20.dp)
                             .clip(CircleShape)
                             .background(color)
                             .border(1.dp, if(isSelected) Color.White else Color.Transparent, CircleShape)
                             .border(2.dp, if(isSelected) Color.Black.copy(alpha=0.2f) else Color.Transparent, CircleShape)
                             .clickable { onColorChange(color) }
                     )
                 }
             }
        }
    }
}

@Composable
fun BrushTypeSelector(
    isMarker: Boolean,
    onModeChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha=0.5f))
            .border(1.dp, Color(0xFFD4C4B0), RoundedCornerShape(8.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Pen
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if(!isMarker) Color(0xFF5D4E3C) else Color.Transparent)
                .clickable { onModeChange(false) },
            contentAlignment = Alignment.Center
        ) {
             Icon(
                 Icons.Default.Edit, 
                 null, 
                 tint = if(!isMarker) Color(0xFFF9F5F0) else Color(0xFF8D6E63),
                 modifier = Modifier.size(18.dp)
             )
        }
        
        // Marker
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if(isMarker) Color(0xFF5D4E3C) else Color.Transparent)
                .clickable { onModeChange(true) },
            contentAlignment = Alignment.Center
        ) {
             Icon(
                 Icons.Default.Brush,
                 null, 
                 tint = if(isMarker) Color(0xFFF9F5F0) else Color(0xFF8D6E63),
                 modifier = Modifier.size(18.dp)
             )
        }
    }
}

