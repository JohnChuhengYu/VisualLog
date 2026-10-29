package com.visuallog.ui.whiteboard.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.visuallog.engine.snapshot.PolaroidCardExporter
import java.awt.image.BufferedImage
import java.io.File

@Composable
fun PolaroidExportModal(
    cardImage: BufferedImage,
    initialFile: File? = null,
    defaultFileName: String,
    onDismiss: () -> Unit
) {
    var savedFile by remember { mutableStateOf(initialFile) }
    var copiedRecently by remember { mutableStateOf(true) }
    val imageBitmap = remember(cardImage) { cardImage.toComposeImageBitmap() }

    // Fullscreen scrim overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(200f)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        // Dialog Container (stops click propagation)
        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Surface(
                modifier = Modifier
                    .width(440.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(24.dp),
                        spotColor = Color(0x334A3E31),
                        ambientColor = Color(0x224A3E31)
                    ),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFFFFDF9), // Warm antique paper
                border = BorderStroke(1.dp, Color(0xFFE8DFD1))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Close 'X' Button at Top-Right
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF9E8F82),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    // 1. Polaroid Card Preview with authentic paper tilt & shadow
                    Box(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .rotate(-1.5f)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(8.dp),
                                spotColor = Color(0x40000000)
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFECE4D8), RoundedCornerShape(8.dp))
                            .background(Color(0xFFF4F0E8))
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Polaroid Card Preview",
                            modifier = Modifier
                                .width(180.dp)
                                .height(225.dp), // 4:5 aspect ratio
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Title & Typography
                    Text(
                        text = "Polaroid Card Exported",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF3E3228)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Clipboard Badge Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFEBF7ED))
                            .border(1.dp, Color(0xFFC7E6CB), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (copiedRecently) "已复制到剪贴板，随时可直接粘贴" else "已就绪",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Color(0xFF2E7D32)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "支持直接在 微信 / 朋友圈 / 小红书 / 即刻 / X 粘贴发送",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = Color(0xFF8C7E72)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. File Saved Capsule OR Save Button
                    if (savedFile != null) {
                        val currentFile = savedFile!!
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF7F3EA))
                                .border(0.5.dp, Color(0xFFDFD5C5), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = Color(0xFF7D6E5D),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = currentFile.name,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF3E3228)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${(currentFile.length() / 1024).coerceAtLeast(1)} KB · PNG",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            color = Color(0xFF9E8F82)
                                        )
                                    )
                                }
                            }

                            // Reveal in Finder / Folder Button
                            TextButton(
                                onClick = { PolaroidCardExporter.revealInFileManager(currentFile) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = Color(0xFF5D4E3C)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "在访达中显示",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        // Option to Save to Computer
                        OutlinedButton(
                            onClick = {
                                val file = PolaroidCardExporter.saveCardImageWithDialog(cardImage, defaultFileName)
                                if (file != null) {
                                    savedFile = file
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFDFCFC0)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFFF9F5EE),
                                contentColor = Color(0xFF5D4E3C)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "另外保存为图片文件...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. Action Buttons (Re-copy & Done)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                PolaroidCardExporter.copyToClipboard(cardImage)
                                copiedRecently = true
                            },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFDFCFC0)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFFF9F5EE),
                                contentColor = Color(0xFF5D4E3C)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "再次复制",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1.2f).height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5D4E3C),
                                contentColor = Color(0xFFFFFDF9)
                            )
                        ) {
                            Text(
                                text = "完成",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

            // Top Washi Tape Sticker (Overlapping Top Edge)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-10).dp)
                    .rotate(-1.8f)
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(2.dp))
                    .background(
                        color = Color(0xFFE5A988).copy(alpha = 0.92f), // Warm Terracotta Tape
                        shape = RoundedCornerShape(2.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "✦ POLAROID CARD ✦",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp,
                        letterSpacing = 1.5.sp,
                        color = Color.White
                    )
                )
            }
        }
    }
}
