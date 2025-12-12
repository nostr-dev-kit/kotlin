package com.example.chirp.features.settings.relay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.nostr.ndk.compose.relay.cards.RelayCardList

/**
 * Relay settings screen.
 *
 * Shows all configured relays with:
 * - Connection status
 * - Statistics (messages, traffic)
 * - Toggle to connect/disconnect
 * - Add/remove relay functionality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelaySettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: RelaySettingsViewModel = hiltViewModel()
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
                title = { Text("Relay Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "Add Relay")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize()
                    )
                }
                state.relays.isEmpty() -> {
                    Text(
                        text = "No relays configured. Tap + to add a relay.",
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize()
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.relays, key = { it.url }) { relay ->
                            RelayCardList(
                                relay = relay,
                                onToggleConnection = { shouldConnect ->
                                    viewModel.toggleRelayConnection(relay.url, shouldConnect)
                                },
                                showStats = true,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }

        // Add relay dialog
        if (state.showAddDialog) {
            RelayAddDialog(
                onDismiss = { viewModel.hideAddDialog() },
                onAdd = { url -> viewModel.addRelay(url) }
            )
        }
    }
}
