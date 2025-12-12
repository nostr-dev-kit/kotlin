package com.example.chirp.features.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.nostr.ndk.relay.NDKRelayState
import io.nostr.ndk.relay.NDKRelayStatisticsSnapshot
import io.nostr.ndk.relay.nip11.Nip11RelayInformation
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayMonitorScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: RelayMonitorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relay Monitor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.reconnectAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reconnect All")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Summary Card
            item {
                SummaryCard(
                    totalRelays = state.totalRelays,
                    connectedRelays = state.connectedRelays
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Relays",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Relay List
            items(state.relays) { relay ->
                RelayRow(
                    relay = relay,
                    onClick = { viewModel.selectRelay(relay) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Bottom Sheet for Relay Details
    state.selectedRelay?.let { relay ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectRelay(null) },
            sheetState = sheetState
        ) {
            RelayDetailSheet(
                relay = relay,
                onDismiss = { viewModel.selectRelay(null) },
                onReconnect = { viewModel.reconnectRelay(relay.url) },
                onFetchNip11 = { viewModel.fetchNip11(relay.url) }
            )
        }
    }
}

@Composable
private fun SummaryCard(
    totalRelays: Int,
    connectedRelays: Int
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$totalRelays",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("Total", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$connectedRelays",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("Connected", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${totalRelays - connectedRelays}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (totalRelays - connectedRelays > 0)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text("Disconnected", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RelayRow(
    relay: RelayInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = relay.state.statusColor,
                        shape = MaterialTheme.shapes.small
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    relay.url.removePrefix("wss://"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "↓${relay.stats.messagesReceived}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "↑${relay.stats.messagesSent}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatBytes(relay.stats.bytesReceived + relay.stats.bytesSent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (relay.isTemporary) {
                Text(
                    "TEMP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun RelayDetailSheet(
    relay: RelayInfo,
    onDismiss: () -> Unit,
    onReconnect: () -> Unit,
    onFetchNip11: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Relay Details",
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Text(
            relay.url,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Connection Status
        SectionHeader("Connection")
        DetailRow("Status", relay.state.displayName, valueColor = relay.state.statusColor)
        relay.stats.lastConnectedAt?.let {
            DetailRow("Last Connected", formatTimestamp(it))
        }
        DetailRow("Connection Attempts", "${relay.stats.connectionAttempts}")
        DetailRow("Success Rate", "${(relay.stats.connectionSuccessRate * 100).toInt()}%")

        Spacer(modifier = Modifier.height(12.dp))

        // Messages
        SectionHeader("Messages")
        DetailRow("Received", "${relay.stats.messagesReceived}")
        DetailRow("Sent", "${relay.stats.messagesSent}")
        DetailRow("Unique", "${relay.stats.uniqueMessages} (${(relay.stats.uniqueMessageRate * 100).toInt()}%)")

        Spacer(modifier = Modifier.height(12.dp))

        // Traffic
        SectionHeader("Network Traffic")
        DetailRow("Downloaded", formatBytes(relay.stats.bytesReceived))
        DetailRow("Uploaded", formatBytes(relay.stats.bytesSent))

        Spacer(modifier = Modifier.height(12.dp))

        // Validation
        SectionHeader("Signature Verification")
        DetailRow("Validated", "${relay.stats.validatedEvents}")
        DetailRow("Skipped", "${relay.stats.nonValidatedEvents}")
        DetailRow("Validation Rate", "${(relay.stats.validationRate * 100).toInt()}%")

        Spacer(modifier = Modifier.height(12.dp))

        // Subscriptions
        SectionHeader("Subscriptions")
        DetailRow("Active", "${relay.stats.activeSubscriptions}")
        DetailRow("Total", "${relay.stats.totalSubscriptions}")

        // NIP-11 Info
        relay.nip11?.let { info ->
            Spacer(modifier = Modifier.height(12.dp))
            SectionHeader("Relay Info (NIP-11)")
            info.name?.let { DetailRow("Name", it) }
            info.description?.let {
                if (it.length < 100) DetailRow("Description", it)
            }
            info.software?.let { DetailRow("Software", it) }
            info.version?.let { DetailRow("Version", it) }
            info.supportedNips?.let { nips ->
                if (nips.isNotEmpty()) {
                    DetailRow("NIPs", nips.take(10).joinToString(", ") + if (nips.size > 10) "..." else "")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onReconnect,
                modifier = Modifier.weight(1f)
            ) {
                Text("Reconnect")
            }
            if (relay.nip11 == null) {
                OutlinedButton(
                    onClick = onFetchNip11,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Fetch NIP-11")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurface
        )
    }
}

private val NDKRelayState.statusColor: Color
    @Composable
    get() = when (this) {
        NDKRelayState.CONNECTED, NDKRelayState.AUTHENTICATED -> Color(0xFF4CAF50)
        NDKRelayState.CONNECTING, NDKRelayState.AUTHENTICATING -> Color(0xFFFFC107)
        NDKRelayState.DISCONNECTED -> Color(0xFF9E9E9E)
        NDKRelayState.AUTH_REQUIRED -> Color(0xFFFF9800)
        NDKRelayState.RECONNECTING -> Color(0xFF2196F3)
        NDKRelayState.FLAPPING -> Color(0xFFF44336)
    }

private val NDKRelayState.displayName: String
    get() = when (this) {
        NDKRelayState.CONNECTED -> "Connected"
        NDKRelayState.AUTHENTICATED -> "Authenticated"
        NDKRelayState.CONNECTING -> "Connecting..."
        NDKRelayState.AUTHENTICATING -> "Authenticating..."
        NDKRelayState.DISCONNECTED -> "Disconnected"
        NDKRelayState.AUTH_REQUIRED -> "Auth Required"
        NDKRelayState.RECONNECTING -> "Reconnecting..."
        NDKRelayState.FLAPPING -> "Flapping"
    }

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)}KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))}MB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024 * 1024))}GB"
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val format = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())
    return format.format(Date(timestamp))
}
