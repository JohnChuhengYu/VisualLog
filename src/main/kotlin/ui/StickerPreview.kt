package ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import data.Sticker
import java.io.File

@Composable
fun StickerPreview(
    sticker: Sticker,
    modifier: Modifier = Modifier
) {
    // Exact visual match for SamsungSticker internal structure.
    // We use a fixed size of 150.dp so that the padding(6.dp) and corner radius(2.dp)
    // are proportionally correct relative to the content, just like in the editor.
    // The PARENT is responsible for scaling this entire component down.
    
    Box(
        modifier = modifier
            .size(150.dp) // Fixed canonical size
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(2.dp))
            .background(Color.White, RoundedCornerShape(2.dp))
            .padding(6.dp) // Proportional border
            .clip(RoundedCornerShape(1.dp))
            .background(Color.LightGray)
    ) {
        if (sticker.contentPath.isNotEmpty()) {
            var bitmap by remember(sticker.contentPath) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
            
            LaunchedEffect(sticker.contentPath) {
                try {
                    val file = File(sticker.contentPath)
                    if (file.exists()) {
                        bitmap = loadImageBitmap(file.inputStream())
                    }
                } catch (e: Exception) {
                    // Ignore errors
                }
            }
            
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Err", style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            // Default Star
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFEEEEEE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star, 
                    contentDescription = null, 
                    tint = Color(0xFFFFB300), 
                    modifier = Modifier.fillMaxSize(0.5f)
                )
            }
        }
    }
}
