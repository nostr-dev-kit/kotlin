package com.example.chirp.features.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperToolsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToRelayMonitor: () -> Unit = {},
    onNavigateToNostrDBStats: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToOutboxMetrics: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Tools") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Debug & Monitoring",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DevToolItem(
                icon = Icons.Default.Cloud,
                title = "Relay Monitor",
                description = "Connection status, messages, bytes, NIP-11 info",
                onClick = onNavigateToRelayMonitor
            )

            DevToolItem(
                icon = Icons.Default.DataObject,
                title = "NostrDB Stats",
                description = "Database size, event counts by kind, index stats",
                onClick = onNavigateToNostrDBStats
            )

            DevToolItem(
                icon = Icons.Default.Subscriptions,
                title = "Subscriptions",
                description = "Active subscriptions, validation trust stats",
                onClick = onNavigateToSubscriptions
            )

            DevToolItem(
                icon = Icons.Default.Hub,
                title = "Outbox Metrics",
                description = "Cache hits, fetch timing, relay coverage",
                onClick = onNavigateToOutboxMetrics
            )
        }
    }
}

@Composable
private fun DevToolItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            headlineContent = { Text(title) },
            supportingContent = { Text(description) },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Navigate"
                )
            }
        )
    }
}
