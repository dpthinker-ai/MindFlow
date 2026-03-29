package com.mindflow.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ShareStyleDialog(
    onDismiss: () -> Unit,
    onLight: () -> Unit,
    onDark: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择分享样式") },
        text = { Text("生成一张适合转发的记录图片。") },
        confirmButton = {
            TextButton(onClick = onLight) {
                Text("浅色图")
            }
        },
        dismissButton = {
            TextButton(onClick = onDark) {
                Text("深色图")
            }
        },
    )
}
