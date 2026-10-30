package com.visuallog.ui.whiteboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.visuallog.i18n.S
import com.visuallog.ui.common.FloatingActionIcon

@Composable
fun FloatingHeader(
    onBack: () -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .zIndex(10f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingActionIcon(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = S.back,
            onClick = onBack,
            containerColor = Color(0xFFFAEBD7),
            contentColor = Color(0xFF5D4E3C)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FloatingActionIcon(
                icon = Icons.Default.Share,
                contentDescription = S.export,
                containerColor = Color(0xFFE6DBC6),
                contentColor = Color(0xFF5D4E3C),
                onClick = onExport
            )
            FloatingActionIcon(
                icon = Icons.Default.Check,
                contentDescription = S.save,
                containerColor = Color(0xFF5D4E3C),
                contentColor = Color(0xFFF9F5F0),
                onClick = onSave
            )
        }
    }
}

