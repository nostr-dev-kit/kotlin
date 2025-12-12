package io.nostr.ndk.compose.relay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Displays relay statistics from NDKRelayStatistics.
 *
 * Must be used within [RelayRoot].
 *
 * @param modifier Modifier for the stats column
 * @param showMessages Whether to show message counts (default: true)
 * @param showTraffic Whether to show network traffic (default: true)
 * @param showValidation Whether to show validation stats (default: true)
 * @param showLastConnected Whether to show last connected time (default: true)
 */
@Composable
fun RelayStats(
    modifier: Modifier = Modifier,
    showMessages: Boolean = true,
    showTraffic: Boolean = true,
    showValidation: Boolean = true,
    showLastConnected: Boolean = true
) {
    val context = LocalRelayContext.current
        ?: error("RelayStats must be used within RelayRoot")

    val stats = remember(context.relay) {
        context.relay.getStatistics()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (showMessages) {
            Text(
                text = "Messages: ${stats.messagesReceived} received, ${stats.messagesSent} sent",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (stats.uniqueMessages > 0) {
                Text(
                    text = "Unique: ${stats.uniqueMessages} (${(stats.uniqueMessageRate * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showTraffic) {
            Text(
                text = "Traffic: ${formatBytes(stats.bytesReceived)} ↓ ${formatBytes(stats.bytesSent)} ↑",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showValidation && stats.validatedEvents > 0) {
            Text(
                text = "Events: ${stats.validatedEvents} validated, ${stats.nonValidatedEvents} invalid",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showLastConnected && stats.lastConnectedAt != null) {
            Text(
                text = "Last connected: ${formatTimestamp(stats.lastConnectedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
    val now = System.currentTimeMillis()
    val diff = (now - timestamp) / 1000 // seconds
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        else -> "${diff / 86400}d ago"
    }
}
