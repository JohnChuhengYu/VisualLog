package com.visuallog.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visuallog.i18n.AppLanguage
import com.visuallog.i18n.LanguageManager

@Composable
fun LanguageSwitchButton(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val current = LanguageManager.currentLanguage
    val effective = LanguageManager.effectiveLanguage

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFFDF9).copy(alpha = 0.95f),
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, Color(0xFFDFD5C5)),
            modifier = Modifier.alphaClickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Language",
                    tint = Color(0xFF5D4E3C),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = when (current) {
                        AppLanguage.SYSTEM -> if (effective == AppLanguage.CHINESE) "自动 (中)" else "Auto (EN)"
                        AppLanguage.ENGLISH -> "English"
                        AppLanguage.CHINESE -> "简体中文"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color(0xFF5D4E3C)
                    )
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color(0xFFFFFDF9))
                .border(1.dp, Color(0xFFE8DFD1), RoundedCornerShape(8.dp))
        ) {
            DropdownMenuItem(
                text = { Text("Auto / 跟随系统", fontSize = 13.sp, color = Color(0xFF3E3228)) },
                onClick = {
                    LanguageManager.setLanguage(AppLanguage.SYSTEM)
                    expanded = false
                },
                leadingIcon = {
                    if (current == AppLanguage.SYSTEM) {
                        Icon(Icons.Default.Check, null, tint = Color(0xFF5D4E3C), modifier = Modifier.size(16.dp))
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("English", fontSize = 13.sp, color = Color(0xFF3E3228)) },
                onClick = {
                    LanguageManager.setLanguage(AppLanguage.ENGLISH)
                    expanded = false
                },
                leadingIcon = {
                    if (current == AppLanguage.ENGLISH) {
                        Icon(Icons.Default.Check, null, tint = Color(0xFF5D4E3C), modifier = Modifier.size(16.dp))
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("简体中文", fontSize = 13.sp, color = Color(0xFF3E3228)) },
                onClick = {
                    LanguageManager.setLanguage(AppLanguage.CHINESE)
                    expanded = false
                },
                leadingIcon = {
                    if (current == AppLanguage.CHINESE) {
                        Icon(Icons.Default.Check, null, tint = Color(0xFF5D4E3C), modifier = Modifier.size(16.dp))
                    }
                }
            )
        }
    }
}
