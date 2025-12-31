package com.odom.lullaby

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun SleepTimerDialog(
    show: Boolean,
    timerInputMinutes: String,
    onMinutesChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,

        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Set sleep timer (minutes)") },
        text = {
            OutlinedTextField(
                value = timerInputMinutes,
                onValueChange = onMinutesChange,
                label = { Text("Minutes (1-180)") },
                singleLine = true
            )
        }
    )
}