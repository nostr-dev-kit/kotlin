package com.example.chirp.features.settings.relay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Dialog for adding a new relay.
 *
 * @param onDismiss Called when the dialog is dismissed
 * @param onAdd Called when the user adds a relay (with the URL)
 */
@Composable
fun RelayAddDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Relay") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    label = { Text("Relay URL") },
                    placeholder = { Text("wss://relay.example.com") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Enter a relay WebSocket URL (wss://). The relay will be automatically connected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        url.isBlank() -> error = "URL cannot be empty"
                        else -> onAdd(url)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
