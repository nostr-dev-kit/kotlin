package com.example.chirp.features.relaysets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Screen for creating or editing a relay set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelaySetEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: RelaySetEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error snackbar
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditMode) "Edit Relay Set" else "New Relay Set") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (state.isEditMode) {
                        IconButton(
                            onClick = { viewModel.delete(onNavigateBack) },
                            enabled = !state.isSaving
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { viewModel.save(onNavigateBack) },
                        enabled = !state.isSaving && state.identifier.isNotBlank()
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Identifier field
            item {
                OutlinedTextField(
                    value = state.identifier,
                    onValueChange = viewModel::updateIdentifier,
                    label = { Text("Identifier") },
                    placeholder = { Text("tech-relays") },
                    enabled = !state.isEditMode && !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Unique ID for this relay set (cannot be changed)") }
                )
            }

            // Title field
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::updateTitle,
                    label = { Text("Title") },
                    placeholder = { Text("Tech Relays") },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Display name for the relay set") }
                )
            }

            // Description field
            item {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::updateDescription,
                    label = { Text("Description (optional)") },
                    placeholder = { Text("Relays focused on technology content") },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }

            // Image field
            item {
                OutlinedTextField(
                    value = state.image,
                    onValueChange = viewModel::updateImage,
                    label = { Text("Image URL (optional)") },
                    placeholder = { Text("https://example.com/icon.png") },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Relays section header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Relays (${state.relays.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(
                        onClick = viewModel::showAddRelayDialog,
                        enabled = !state.isSaving
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Relay")
                    }
                }
            }

            // Relays list
            if (state.relays.isEmpty()) {
                item {
                    Text(
                        text = "No relays added yet. Tap + to add a relay.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(state.relays, key = { it }) { relay ->
                    RelayListItem(
                        url = relay,
                        onRemove = { viewModel.removeRelay(relay) },
                        enabled = !state.isSaving
                    )
                }
            }
        }

        // Add relay dialog
        if (state.showAddRelayDialog) {
            AddRelayDialog(
                onDismiss = viewModel::hideAddRelayDialog,
                onAdd = viewModel::addRelay
            )
        }
    }
}

@Composable
private fun RelayListItem(
    url: String,
    onRemove: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRemove,
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove relay",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AddRelayDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Relay") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Relay URL") },
                placeholder = { Text("wss://relay.example.com") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (url.isNotBlank()) {
                        onAdd(url)
                        url = ""
                    }
                },
                enabled = url.isNotBlank()
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
