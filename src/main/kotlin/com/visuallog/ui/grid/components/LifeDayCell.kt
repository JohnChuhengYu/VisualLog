package com.visuallog.ui.grid.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visuallog.data.model.DayEntry
import com.visuallog.ui.common.StickerDefinitions
import com.visuallog.ui.common.alphaClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*
import javax.imageio.ImageIO
import kotlin.random.Random

@Composable
fun LifeDayCell(
    date: LocalDate,
    dayEntry: DayEntry?,
    onClick: (Offset, IntSize) -> Unit
) {
    var centerPos by remember { mutableStateOf(Offset.Zero) }
    var itemSize by remember { mutableStateOf(IntSize.Zero) }

    val thumbnailInfo by produceState<ThumbnailInfo?>(initialValue = null, key1 = dayEntry?.thumbnailPath) {
        val path = dayEntry?.thumbnailPath
        if (path != null) {
            val cached = ThumbnailCache.get(path)
            if (cached != null) {
                value = cached
            } else {
                withContext(Dispatchers.IO) {
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            val awtImage = ImageIO.read(file)
                            if (awtImage != null) {
                                val pad = (awtImage.width * 0.12).toInt()
                                val sampleSize = (awtImage.width * 0.10).toInt().coerceAtLeast(1)
                                
                                val topRes = analyzeCorner(awtImage, pad, pad, sampleSize, sampleSize)
                                val botRes = analyzeCorner(awtImage, awtImage.width - pad - sampleSize, awtImage.height - pad - sampleSize, sampleSize, sampleSize)
                                
                                file.inputStream().use { stream ->
                                    val bmp = loadImageBitmap(stream)
                                    val newInfo = ThumbnailInfo(bmp, topRes.isDark, topRes.isBusy, botRes.isDark, botRes.isBusy)
                                    ThumbnailCache.put(path, newInfo)
                                    value = newInfo
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } else {
            value = null
        }
    }

    // Randomized rotation for "pasted" look
    val rotation = remember(date) {
        Random(date.hashCode()).nextFloat() * 4f - 2f
    }

    // Polaroid Card
    Box(
        modifier = Modifier
            .aspectRatio(0.82f)
            .onGloballyPositioned { coordinates ->
                val size = coordinates.size
                itemSize = size
                val position = coordinates.positionInWindow()
                centerPos = Offset(
                    position.x + size.width / 2f,
                    position.y + size.height / 2f
                )
            }
            .rotate(rotation)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(2.dp),
                spotColor = Color(0x35352515),
                ambientColor = Color(0x20352515)
            )
            .background(Color.White, RoundedCornerShape(2.dp))
            .border(1.dp, Color(0xFFDCD4C6), RoundedCornerShape(2.dp))
            .alphaClickable(onClick = { onClick(centerPos, itemSize) })
    ) {
        // 1. Image Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp, 10.dp, 10.dp, 36.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color(0xFFFFFDF9),
                        shape = RoundedCornerShape(1.dp)
                    )
                    .clip(RoundedCornerShape(1.dp))
                    .border(0.5.dp, Color(0xFFE0E0E0))
            ) {
                val currentInfo = thumbnailInfo
                if (currentInfo != null) {
                    Image(
                        bitmap = currentInfo.bitmap,
                        contentDescription = "Thumbnail for $date",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        
        // Weekday Tape Sticker
        val dayName = remember(date) { 
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase() 
        }
        val tapeRotate = remember(date) { Random(date.hashCode() + 2).nextFloat() * 10f - 5f }
        val tapeColor = remember(date) { 
            val colors = listOf(Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFB74D))
            colors[Random(date.hashCode() + 3).nextInt(colors.size)].copy(alpha = 0.85f)
        }

        Box(
            modifier = Modifier
                .padding(4.dp)
                .rotate(tapeRotate)
                .background(tapeColor, RoundedCornerShape(1.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
                .align(Alignment.TopEnd)
        ) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 9.sp,
                    color = Color.White,
                    letterSpacing = 0.2.sp
                )
            )
        }
        
        // 2. Bezel Area (Stamps & Date)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(36.dp),
            contentAlignment = Alignment.Center
        ) {
             Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically 
             ) {
                 // Weather Stamp (Left)
                 val weatherIcon = dayEntry?.weather?.let { StickerDefinitions.WeatherOptions[it] }
                 
                 if (weatherIcon != null) {
                     val wRotate = remember(date) { Random(date.hashCode()).nextFloat() * 20f - 10f }
                     val wOffsetY = remember(date) { Random(date.hashCode()).nextFloat() * 4f - 2f }
                     val wOffsetX = remember(date) { Random(date.hashCode()).nextFloat() * 6f - 3f }
                     
                     Icon(
                         imageVector = weatherIcon, 
                         contentDescription = null, 
                         modifier = Modifier
                             .size(20.dp) 
                             .offset(x = wOffsetX.dp, y = wOffsetY.dp)
                             .rotate(wRotate),
                         tint = Color(0xFFD84315).copy(alpha = 0.85f)
                     )
                 } else {
                     Spacer(Modifier.size(20.dp))
                 }

                 // Date Number (Center)
                 Text(
                     text = date.dayOfMonth.toString(),
                     style = MaterialTheme.typography.bodyLarge.copy(
                         fontFamily = FontFamily.Serif,
                         fontWeight = FontWeight.ExtraBold,
                         fontSize = 18.sp,
                         color = Color(0xFF333333)
                     )
                 )
                 
                 // Mood Stamp (Right)
                 val moodIcon = dayEntry?.mood?.let { StickerDefinitions.MoodOptions[it] }

                 if (moodIcon != null) {
                      val mRotate = remember(date) { Random(date.hashCode() + 1).nextFloat() * 20f - 10f }
                      val mOffsetY = remember(date) { Random(date.hashCode() + 1).nextFloat() * 4f - 2f }
                      val mOffsetX = remember(date) { Random(date.hashCode() + 1).nextFloat() * 6f - 3f }

                      Icon(
                          imageVector = moodIcon,
                          contentDescription = null,
                          modifier = Modifier
                              .size(20.dp)
                              .offset(x = mOffsetX.dp, y = mOffsetY.dp)
                              .rotate(mRotate),
                          tint = Color(0xFF1E88E5).copy(alpha = 0.85f)
                      )
                 } else {
                      Spacer(Modifier.size(20.dp))
                 }
             }
        }
    }
}

