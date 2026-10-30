package com.visuallog.ui.whiteboard.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.visuallog.data.model.Sticker
import com.visuallog.data.repository.RecentStickerManager
import com.visuallog.engine.optimization.ImageOptimizationUtils
import com.visuallog.i18n.S
import com.visuallog.ui.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

@Composable
fun LeftTabDock(
    selectedTab: JournalTab,
    onTabSelected: (JournalTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(start = 0.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        BookmarkTab(
            icon = Icons.Default.Image,
            isSelected = selectedTab == JournalTab.VISUAL,
            color = Color(0xFF8D6E63),
            onClick = { onTabSelected(JournalTab.VISUAL) }
        )
        
        BookmarkTab(
            icon = Icons.Default.Description,
            isSelected = selectedTab == JournalTab.TEXT,
            color = Color(0xFF6D4C41),
            onClick = { onTabSelected(JournalTab.TEXT) }
        )
    }
}

@Composable
fun BookmarkTab(
    icon: ImageVector,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val width by animateDpAsState(if (isSelected) 64.dp else 48.dp)
    val offsetX by animateDpAsState(if (isSelected) 0.dp else (-8).dp)

    Surface(
        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
        color = color,
        shadowElevation = 4.dp,
        modifier = Modifier
            .width(width)
            .height(48.dp)
            .offset(x = offsetX)
            .alphaClickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.CenterEnd) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFEFEBE9),
                modifier = Modifier.padding(end = 12.dp).size(24.dp)
            )
        }
    }
}

@Composable
fun RightToolDock(
    mode: WhiteboardMode,
    currentTool: WhiteboardTool,
    dayEntryId: Int,
    stickers: MutableList<Sticker>,
    currentColor: Color,
    currentWidth: Float,
    onModeChange: (WhiteboardMode) -> Unit,
    onToolChange: (WhiteboardTool) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onMarkerModeChange: (Boolean) -> Unit,
    isMarkerMode: Boolean,
    onStickerAdded: (Int) -> Unit,
    selectedLayer: Int,
    onSelectLayer: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isStickerBoxOpen by remember { mutableStateOf(false) }
    
    LaunchedEffect(mode) {
        if (mode != WhiteboardMode.STICKER) isStickerBoxOpen = false
    }

    Box(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
        
        AnimatedVisibility(
            visible = isStickerBoxOpen,
            enter = scaleIn(transformOrigin = TransformOrigin(1f, 0.5f)) + fadeIn(),
            exit = scaleOut(transformOrigin = TransformOrigin(1f, 0.5f)) + fadeOut(),
            modifier = Modifier.padding(end = 90.dp)
        ) {
            StickerPickerBox(
                onEmojiSelected = { emoji ->
                    val newId = -((System.currentTimeMillis() and 0x7FFFFFFF).toInt()) - 1
                    val newSticker = Sticker(
                        id = newId, 
                        dayEntryId = dayEntryId, 
                        x = 0.425f, 
                        y = 0.425f, 
                        scale = 1f, 
                        rotation = Random.nextFloat() * 20 - 10, 
                        contentPath = "emoji:$emoji", 
                        type = "emoji", 
                        layer = selectedLayer
                    )
                    stickers.add(newSticker)
                    onStickerAdded(newSticker.id)
                },
                onPhotoImportCall = {
                    val fileDialog = java.awt.FileDialog(null as java.awt.Frame?, "Select Image", java.awt.FileDialog.LOAD)
                    fileDialog.isVisible = true 
                    
                    if (fileDialog.file != null) {
                        val originalFile = File(fileDialog.directory, fileDialog.file)
                        val newId = -((System.currentTimeMillis() and 0x7FFFFFFF).toInt()) - 1
                        stickers.add(Sticker(id = newId, dayEntryId = dayEntryId, x = 0.425f, y = 0.425f, scale = 1f, rotation = 0f, contentPath = "LOADING", type = "image", layer = selectedLayer))
                        onStickerAdded(newId)
                        
                        scope.launch(Dispatchers.IO) {
                            try {
                                val stickersDir = File(System.getProperty("user.home"), ".visuallog/stickers")
                                if (!stickersDir.exists()) stickersDir.mkdirs()
                                
                                var finalFile: File? = null
                                try {
                                    finalFile = ImageOptimizationUtils.optimizeImage(originalFile, stickersDir)
                                } catch (e: Exception) { e.printStackTrace() }
                                
                                if (finalFile == null) {
                                    val ext = originalFile.extension.ifEmpty { "png" }
                                    val fallbackFile = File(stickersDir, "sticker_raw_${System.currentTimeMillis()}.$ext")
                                    originalFile.copyTo(fallbackFile, overwrite = true)
                                    finalFile = fallbackFile
                                }
                                
                                val finalPath = finalFile.absolutePath
                                RecentStickerManager.add(finalPath)
                                
                                withContext(Dispatchers.Main) {
                                    val idx = stickers.indexOfFirst { it.id == newId }
                                    if (idx != -1) stickers[idx] = stickers[idx].copy(contentPath = finalPath)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    val idx = stickers.indexOfFirst { it.id == newId }
                                    if (idx != -1) stickers.removeAt(idx)
                                }
                            }
                        }
                    } 
                },
                onRecentSelected = { path ->
                    val newId = -((System.currentTimeMillis() and 0x7FFFFFFF).toInt()) - 1
                    stickers.add(Sticker(id = newId, dayEntryId = dayEntryId, x = 0.425f, y = 0.425f, scale = 1f, rotation = 0f, contentPath = path, type = "image", layer = selectedLayer))
                    onStickerAdded(newId)
                }
            )
        }

        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "ToolbarTransition"
        ) { currentMode ->
            if (currentMode == WhiteboardMode.VIEW) {
                FloatingActionIcon(
                    icon = Icons.Default.Edit,
                    contentDescription = S.edit,
                    onClick = { 
                        onModeChange(WhiteboardMode.DRAW)
                        onToolChange(WhiteboardTool.PEN) 
                    },
                    containerColor = Color(0xFF5D4E3C),
                    contentColor = Color(0xFFF9F5F0)
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFE6DBC6),
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD4C4B0))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        // 1. PEN
                        ToolIcon(
                            icon = Icons.Default.Edit,
                            label = S.pen,
                            isActive = currentTool == WhiteboardTool.PEN && currentMode == WhiteboardMode.DRAW,
                            activeColor = currentColor,
                            onClick = { onModeChange(WhiteboardMode.DRAW); onToolChange(WhiteboardTool.PEN) }
                        )

                        AnimatedVisibility(
                            visible = currentTool == WhiteboardTool.PEN && currentMode == WhiteboardMode.DRAW,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                BrushTypeSelector(
                                    isMarker = isMarkerMode,
                                    onModeChange = onMarkerModeChange
                                )

                                BrushSizeSelector(
                                    currentWidth = currentWidth,
                                    onWidthChange = onWidthChange,
                                    color = currentColor
                                )
                                 
                                ColorPalette(
                                    currentColor = currentColor,
                                    onColorChange = onColorChange
                                )
                                 
                                HorizontalDivider(modifier = Modifier.width(32.dp), color = Color.Black.copy(alpha = 0.1f))
                            }
                        }
                        
                        // LAYER SELECTOR
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp).width(32.dp), color = Color.Black.copy(alpha = 0.1f))
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(S.layer, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            
                            (2 downTo 0).forEach { i ->
                                val layerLabel = when (i) {
                                    2 -> S.layerTop
                                    1 -> S.layerMid
                                    else -> S.layerBot
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp, 32.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(1.dp, if (selectedLayer == i) Color(0xFF5D4E3C) else Color.Transparent, RoundedCornerShape(4.dp))
                                        .background(if (selectedLayer == i) Color(0xFFE6DBC6).copy(alpha = 0.5f) else Color.Transparent)
                                        .clickable { onSelectLayer(i) },
                                    contentAlignment = Alignment.Center
                                ) {
                                     Text(
                                         text = layerLabel,
                                         style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                         color = if (selectedLayer == i) Color(0xFF5D4E3C) else Color.Gray.copy(alpha = 0.6f)
                                     )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp).width(32.dp), color = Color.Black.copy(alpha = 0.1f))

                        // 2. ERASER
                        ToolIcon(
                            icon = Icons.Default.AutoFixHigh, 
                            label = S.eraser,
                            isActive = currentTool == WhiteboardTool.ERASER && currentMode == WhiteboardMode.DRAW,
                            activeColor = Color(0xFF8D6E63),
                            onClick = { onModeChange(WhiteboardMode.DRAW); onToolChange(WhiteboardTool.ERASER) }
                        )
                        
                        // 3. SWEEP
                        ToolIcon(
                            icon = Icons.Default.DeleteSweep,
                            label = S.sweep,
                            isActive = currentTool == WhiteboardTool.SEGMENT_ERASER && currentMode == WhiteboardMode.DRAW,
                            activeColor = Color(0xFFD84315), 
                            onClick = { onModeChange(WhiteboardMode.DRAW); onToolChange(WhiteboardTool.SEGMENT_ERASER) }
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.width(24.dp),
                            thickness = 1.dp,
                            color = Color(0xFFBCAAA4).copy(alpha = 0.5f)
                        )
    
                        // 4. SELECT / EDIT TOOL
                        ToolIcon(
                            icon = Icons.Default.TouchApp, 
                            label = S.select,
                            isActive = currentMode == WhiteboardMode.STICKER && !isStickerBoxOpen,
                            activeColor = Color(0xFF2196F3),
                            onClick = { 
                                onModeChange(WhiteboardMode.STICKER)
                                isStickerBoxOpen = false
                            }
                        )

                        // 5. ADD STICKER TOOL
                        ToolIcon(
                            icon = Icons.Default.EmojiEmotions, 
                            label = S.addSticker,
                            isActive = isStickerBoxOpen,
                            activeColor = Color(0xFFFBC02D),
                            onClick = { 
                                if (currentMode != WhiteboardMode.STICKER) {
                                    onModeChange(WhiteboardMode.STICKER)
                                    isStickerBoxOpen = true 
                                } else {
                                    isStickerBoxOpen = !isStickerBoxOpen 
                                }
                            }
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.width(24.dp),
                            thickness = 1.dp,
                            color = Color(0xFFBCAAA4).copy(alpha = 0.5f)
                        )
    
                        // CLOSE / DONE
                        ToolIcon(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            label = S.done,
                            isActive = false,
                            onClick = { onModeChange(WhiteboardMode.VIEW) },
                            activeColor = Color(0xFF5D4E3C),
                            inactiveColor = Color(0xFF8D6E63)
                        )
                    }
                }
            }
        }
    }
}

