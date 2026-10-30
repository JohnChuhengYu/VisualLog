package com.visuallog.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.visuallog.i18n.AppLanguage
import com.visuallog.i18n.LanguageManager

@Composable
fun LanguageSwitchButton(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val current = LanguageManager.currentLanguage
    val effective = LanguageManager.effectiveLanguage

    val currentLabel = remember(current, effective) {
        when (current) {
            AppLanguage.SYSTEM -> if (effective == AppLanguage.CHINESE) "自动 · 中文" else "Auto · EN"
            AppLanguage.ENGLISH -> "English"
            AppLanguage.CHINESE -> "简体中文"
        }
    }

    Box(modifier = modifier) {
        // Pill Button
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFFFFDF9), // Warm antique paper
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, Color(0xFFDFD5C5)),
            modifier = Modifier
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = Color(0x354A3E31),
                    ambientColor = Color(0x154A3E31)
                )
                .clip(RoundedCornerShape(20.dp))
                .clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Language",
                    tint = Color(0xFF5D4E3C),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF5D4E3C),
                        letterSpacing = 0.3.sp
                    )
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF8D7B68),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Custom Vintage Paper Dropdown Menu Card
        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, 46),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFFFFDF9),
                    shadowElevation = 18.dp,
                    border = BorderStroke(1.dp, Color(0xFFE5DDD0)),
                    modifier = Modifier
                        .width(220.dp)
                        .shadow(
                            elevation = 18.dp,
                            shape = RoundedCornerShape(18.dp),
                            spotColor = Color(0x403A2E20),
                            ambientColor = Color(0x253A2E20)
                        )
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Header label
                        Text(
                            text = "LANGUAGE / 语言",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                color = Color(0xFF9E8F82)
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )

                        // 1. Auto / Follow System
                        LanguageMenuItem(
                            title = "Auto / 跟随系统",
                            subtitle = if (effective == AppLanguage.CHINESE) "当前检测: 中文" else "Detected: English",
                            isSelected = current == AppLanguage.SYSTEM,
                            onClick = {
                                LanguageManager.setLanguage(AppLanguage.SYSTEM)
                                expanded = false
                            }
                        )

                        // 2. English
                        LanguageMenuItem(
                            title = "English",
                            subtitle = "English Interface",
                            isSelected = current == AppLanguage.ENGLISH,
                            onClick = {
                                LanguageManager.setLanguage(AppLanguage.ENGLISH)
                                expanded = false
                            }
                        )

                        // 3. 简体中文
                        LanguageMenuItem(
                            title = "简体中文",
                            subtitle = "中文界面",
                            isSelected = current == AppLanguage.CHINESE,
                            onClick = {
                                LanguageManager.setLanguage(AppLanguage.CHINESE)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageMenuItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) Color(0xFFF3ECE0) else Color.Transparent
    val textColor = if (isSelected) Color(0xFF4A3828) else Color(0xFF5D4E3C)
    val subtitleColor = if (isSelected) Color(0xFF7D6855) else Color(0xFF9E8F82)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = textColor
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = subtitleColor
                )
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF5D4E3C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFFFFFDF9),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
