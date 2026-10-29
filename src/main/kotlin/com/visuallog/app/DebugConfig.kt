package com.visuallog.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.visuallog.data.repository.VisualLogRepository
import java.time.LocalDate

@Composable
fun FPSCounter() {
    var fps by remember { mutableStateOf(0) }
    val frameTimes = remember { mutableStateListOf<Long>() }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos ->
                frameTimes.add(nanos)
                val oneSecondAgo = nanos - 1_000_000_000L
                while (frameTimes.isNotEmpty() && frameTimes.first() < oneSecondAgo) {
                    frameTimes.removeAt(0)
                }
                fps = frameTimes.size
            }
        }
    }

    Surface(
        modifier = Modifier.padding(8.dp).alpha(0.6f),
        color = Color.Black,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = "FPS: $fps",
            color = Color.Green,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DebugStartDateDialog(
    startDate: LocalDate?,
    repository: VisualLogRepository,
    onDismiss: () -> Unit,
    onApplied: (LocalDate) -> Unit
) {
    var dateText by remember { mutableStateOf((startDate ?: LocalDate.now()).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Debug: Set Start Date") },
        text = {
            Column {
                Text(
                    "Today is ${LocalDate.now()}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                TextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Start Date (YYYY-MM-DD)") },
                    supportingText = { Text("Set a PAST date to see more history.") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val newDate = LocalDate.parse(dateText)
                        repository.setAppSetting("start_date", newDate.toString())
                        onApplied(newDate)
                    } catch (e: Exception) {
                        // Invalid date format
                    }
                }
            ) { Text("Apply") }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

