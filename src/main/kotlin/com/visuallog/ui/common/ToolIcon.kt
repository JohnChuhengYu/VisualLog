package com.visuallog.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun FloatingActionIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    containerColor: Color = Color.White,
    contentColor: Color = Color.Black
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 6.dp, 
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f)),
        modifier = Modifier
            .size(56.dp)
            .alphaClickable(onClick = onClick) 
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun ToolIcon(
    icon: ImageVector,
    label: String? = null,
    isActive: Boolean,
    onClick: () -> Unit,
    activeColor: Color = Color(0xFF5D4E3C),
    inactiveColor: Color = Color(0xFFA1887F)
) {
    val containerColor by animateColorAsState(if (isActive) activeColor else Color.Transparent, label = "container")
    val contentColor by animateColorAsState(if (isActive) Color(0xFFF9F5F0) else inactiveColor, label = "content")
    val scale by animateFloatAsState(if (isActive) 1.1f else 1f, label = "scale")
    val shadow by animateDpAsState(if (isActive) 4.dp else 0.dp, label = "shadow")
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = shadow,
            border = if (!isActive) BorderStroke(1.dp, Color.Transparent) else null,
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .alphaClickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

