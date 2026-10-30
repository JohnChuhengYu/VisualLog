package com.visuallog.ui.whiteboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visuallog.i18n.S
import com.visuallog.ui.common.StickerDefinitions
import com.visuallog.ui.common.alphaClickable
import kotlin.random.Random

@Composable
fun TextJournalPage(
    text: String,
    mood: String?,
    weather: String?,
    onTextChange: (String) -> Unit,
    onMoodChange: (String?) -> Unit,
    onWeatherChange: (String?) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 96.dp, bottom = 24.dp, start = 84.dp, end = 24.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(2.dp),
            color = Color(0xFFFDFCF8),
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header (Stamps)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = S.dailyEntry,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF5D4E3C).copy(alpha = 0.8f)
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StampControl(
                            label = S.weather,
                            currentValue = weather,
                            options = StickerDefinitions.WeatherOptions,
                            color = Color(0xFFD84315),
                            onValueChange = onWeatherChange
                        )
                        
                        StampControl(
                            label = S.mood,
                            currentValue = mood,
                            options = StickerDefinitions.MoodOptions,
                            color = Color(0xFF1E88E5),
                            onValueChange = onMoodChange
                        )
                    }
                }
                
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    thickness = 1.dp,
                    color = Color.Black.copy(alpha = 0.1f)
                )

                // Text Area
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color(0xFF5D4E3C)
                    ),
                    placeholder = { 
                        Text(
                            S.writeYourStory,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = FontFamily.Serif
                            ),
                            color = Color.Gray.copy(alpha = 0.4f)
                        ) 
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontSize = 20.sp,
                        lineHeight = 34.sp,
                        color = Color.Black.copy(alpha = 0.87f)
                    )
                )
            }
        }
    }
}

@Composable
fun StampControl(
    label: String,
    currentValue: String?,
    options: Map<String, ImageVector>,
    color: Color,
    onValueChange: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation = remember { Random.nextFloat() * 10f - 5f } 
    
    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .rotate(rotation)
                .alphaClickable { expanded = true }
                .border(2.dp, if (currentValue != null) color.copy(alpha = 0.7f) else Color.LightGray.copy(alpha = 0.3f), CircleShape)
                .background(if (currentValue != null) color.copy(alpha = 0.1f) else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (currentValue != null && options.containsKey(currentValue)) {
                Icon(
                    imageVector = options[currentValue]!!,
                    contentDescription = currentValue,
                    tint = color.copy(alpha = 0.8f),
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Text(
                    "?",
                    color = Color.LightGray.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color(0xFFFFF8E1))
                .border(1.dp, Color(0xFFE0C090), RoundedCornerShape(4.dp))
                .heightIn(max = 240.dp)
        ) {
            val keys = options.keys.toList()
            val columns = 4
            
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .width(176.dp)
            ) {
                Text(
                    text = label, 
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
                
                keys.chunked(columns).forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowKeys.forEach { key ->
                            val icon = options[key]!!
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .alphaClickable {
                                        onValueChange(key)
                                        expanded = false
                                    }
                                    .background(if (currentValue == key) color.copy(alpha = 0.2f) else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = key,
                                    tint = if (currentValue == key) color else Color.Gray.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

