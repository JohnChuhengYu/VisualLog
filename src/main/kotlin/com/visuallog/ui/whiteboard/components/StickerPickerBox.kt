package com.visuallog.ui.whiteboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visuallog.data.repository.RecentStickerManager
import com.visuallog.i18n.S
import com.visuallog.ui.common.alphaClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AsyncThumbnailFullscreen(path: String) {
    val bitmap by produceState<ImageBitmap?>(null) {
        withContext(Dispatchers.IO) {
            try {
                value = loadImageBitmap(File(path).inputStream())
            } catch (e: Exception) {}
        }
    }
    if (bitmap != null) {
        Image(bitmap!!, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

@Composable
fun StickerPickerBox(
    onEmojiSelected: (String) -> Unit,
    onPhotoImportCall: () -> Unit,
    onRecentSelected: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Emoji, 1: Recent

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFF8E1),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color(0xFFE0C090)),
        modifier = Modifier.width(260.dp).heightIn(max = 360.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with Tabs
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    S.stickerBox,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF795548)
                )

                // Simple Tab Switcher
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE0C090).copy(alpha = 0.3f))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(S.tabEmoji, S.tabRecent).forEachIndexed { index, title ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selectedTab == index) Color(0xFF8D6E63) else Color.Transparent)
                                .clickable { selectedTab = index }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                title, 
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selectedTab == index) Color.White else Color(0xFF795548)
                            )
                        }
                    }
                }
            }
            
            if (selectedTab == 0) {
                // Emoji Grid
                val emojis = listOf(
                    "⭐", "🌟", "✨", "❤️", "🧡", "💛", 
                    "🌿", "🌵", "🌻", "🌸", "🍔", "☕", 
                    "🍰", "🍎", "📌", "📎", "✈️", "📷", 
                    "🎵", "💡", "🗓️", "✅", "🎉", "🔥"
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                ) {
                    emojis.chunked(6).forEach { rowEmojis ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            rowEmojis.forEach { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .alphaClickable { onEmojiSelected(emoji) }
                                        .background(Color.White.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                // Recent Import Grid
                val recents = RecentStickerManager.recentStickers
                if (recents.isEmpty()) {
                    Box(
                         modifier = Modifier.weight(1f).fillMaxWidth(),
                         contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No recent stickers", 
                            style = MaterialTheme.typography.bodySmall, 
                            color = Color.Gray
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                    ) {
                        recents.chunked(3).forEach { rowPaths ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowPaths.forEach { path ->
                                    Box(
                                        modifier = Modifier
                                            .size(70.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .border(1.dp, Color(0xFFE0C090), RoundedCornerShape(4.dp))
                                            .background(Color.White)
                                            .clickable { onRecentSelected(path) }
                                    ) {
                                        AsyncThumbnailFullscreen(path)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFD7CCC8))
            Spacer(modifier = Modifier.height(8.dp))
            
            // Import Button
            Surface(
                color = Color(0xFF8D6E63),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .alphaClickable(onClick = onPhotoImportCall)
            ) {
                Row(
                   modifier = Modifier.padding(vertical = 12.dp),
                   horizontalArrangement = Arrangement.Center,
                   verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(S.importPhoto, color = Color.White)
                }
            }
        }
    }
}

