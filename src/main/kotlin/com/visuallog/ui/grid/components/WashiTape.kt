package com.visuallog.ui.grid.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun YearTapeSticker(year: Int, rotation: Float) {
    Box(
        modifier = Modifier
            .rotate(rotation - 1f)
            .background(Color(0xFFE0E0E0), RoundedCornerShape(2.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Color.DarkGray
        )
    }
}

@Composable
fun MonthTapeSticker(monthName: String, tapeColor: Color, rotation: Float) {
    Box(
        modifier = Modifier
            .rotate(rotation)
            .shadow(1.dp, RoundedCornerShape(4.dp))
            .background(tapeColor.copy(alpha = 0.9f))
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = monthName,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Color(0xFF555555)
        )
    }
}

@Composable
fun JournalTitleSticker(
    totalDays: Int,
    isFixed: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(if (isFixed) 6.dp else 4.dp, RoundedCornerShape(2.dp))
            .background(Color(0xFFFFFDF7))
            .padding(
                horizontal = if (isFixed) 20.dp else 24.dp, 
                vertical = if (isFixed) 16.dp else 20.dp
            )
            .scale(if (isFixed) 0.9f else 1f)
    ) {
        // Faux "Tape" at top
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = if (isFixed) (-24).dp else (-28).dp)
                .width(40.dp)
                .height(16.dp)
                .background(Color(0x88FFCCBC))
        )
        
        Column {
            Text(
                text = "My Journal",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isFixed) 28.sp else 32.sp
                ),
                color = Color(0xFF4E342E)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$totalDays Days",
                style = MaterialTheme.typography.labelLarge.copy(
                     fontFamily = FontFamily.Serif,
                     color = Color(0xFF8D6E63)
                )
            )
        }
    }
}

