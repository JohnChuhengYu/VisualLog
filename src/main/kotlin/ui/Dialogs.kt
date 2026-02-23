package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import data.DayEntry
import data.Sticker
import java.io.File
import kotlin.math.roundToInt

@Composable
fun StickerEditorDialog(
    dayEntry: DayEntry,
    onDismiss: () -> Unit,
    onSave: (String?, List<Sticker>) -> Unit
) {
    var backgroundPath by remember { mutableStateOf(dayEntry.backgroundPath) }
    // We map stickers to a mutable list to track changes locally
    val stickers = remember { mutableStateListOf(*dayEntry.stickers.toTypedArray()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Edit Entry: ${dayEntry.date}", style = MaterialTheme.typography.titleLarge)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            // Mock File Picker: In real app use JFileChooser or similar
                            // For MVP, we'll just simulate picking a "new_bg.jpg"
                            // or ask user to provide path via a small prompt? 
                            // Let's just toggle a dummy path for demonstration if no file picker lib.
                            backgroundPath = if (backgroundPath == null) "/path/to/demo_image.jpg" else null
                        }) {
                            Icon(Icons.Default.Image, "Background")
                            Spacer(Modifier.width(4.dp))
                            Text("Toggle BG")
                        }
                        
                        Button(onClick = {
                            stickers.add(
                                Sticker(
                                    id = -(Math.random() * 10000).toInt(), // Temp Negative ID
                                    dayEntryId = dayEntry.id,
                                    x = 100f,
                                    y = 100f,
                                    type = "star"
                                )
                            )
                        }) {
                            Icon(Icons.Default.Add, "Add Sticker")
                            Spacer(Modifier.width(4.dp))
                            Text("Sticker")
                        }

                        FilledTonalButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Cancel")
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }

                        Button(onClick = { onSave(backgroundPath, stickers) }) {
                            Icon(Icons.Default.Check, "Save")
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                }

                // Editor Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.DarkGray) // Canvas background
                ) {
                    // Background Layer
                    if (backgroundPath != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.LightGray) // Placeholder
                        ) {
                            Text("Background Image: $backgroundPath", modifier = Modifier.align(Alignment.Center))
                        }
                    } else {
                        Text("No Background", modifier = Modifier.align(Alignment.Center), color = Color.White)
                    }

                    // Sticker Layer
                    stickers.forEachIndexed { index, sticker ->
                        DraggableSticker(
                            sticker = sticker,
                            onPositionChange = { x, y ->
                                stickers[index] = sticker.copy(x = x, y = y)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DraggableSticker(
    sticker: Sticker,
    onPositionChange: (Float, Float) -> Unit
) {
    var offsetX by remember { mutableStateOf(sticker.x) }
    var offsetY by remember { mutableStateOf(sticker.y) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    onPositionChange(offsetX, offsetY)
                }
            }
            .size(48.dp)
            .background(Color.Yellow, CircleShape) // Sticker visual
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Star, contentDescription = "Sticker", tint = Color.Black)
    }
}
