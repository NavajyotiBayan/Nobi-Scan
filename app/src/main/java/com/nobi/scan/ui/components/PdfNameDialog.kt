package com.nobi.scan.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PdfNameDialog(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    busy: Boolean,
    error: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    options: (@Composable () -> Unit)? = null
) {
    val haptics = rememberNobiHaptics()
    AlertDialog(
        onDismissRequest = haptics.onTapIfEnabled(!busy, onDismiss),
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth().hapticOnFocus(haptics),
                    label = { Text("Document name") },
                    suffix = { Text(".pdf") },
                    supportingText = error?.let { message -> { Text(message) } },
                    singleLine = true,
                    enabled = !busy,
                    isError = error != null
                )
                options?.invoke()
            }
        },
        dismissButton = {
            TextButton(onClick = haptics.onTap(onDismiss), enabled = !busy) { Text("Cancel") }
        },
        confirmButton = {
            Row {
                if (onShare != null) TextButton(onClick = haptics.onTap(onShare), enabled = !busy) { Text("Save & share") }
                TextButton(onClick = haptics.onTap(onConfirm), enabled = !busy) { Text(confirmLabel) }
            }
        }
    )
}
