package com.example.chirp.features.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.nostr.ndk.outbox.OutboxMetricsEvent
import io.nostr.ndk.outbox.OutboxMetricsSnapshot
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: DebugViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Outbox Debug") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onIntent(DebugIntent.RefreshMetrics) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { viewModel.onIntent(DebugIntent.ResetMetrics) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Reset")
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pool Status
            item {
                PoolStatusCard(state.poolStatus)
            }

            // Metrics Summary
            item {
                state.metricsSnapshot?.let { snapshot ->
                    MetricsSummaryCard(snapshot)
                }
            }

            // Auto-refresh toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-refresh (1s)", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.isAutoRefresh,
                        onCheckedChange = { viewModel.onIntent(DebugIntent.ToggleAutoRefresh(it)) }
                    )
                }
            }

            // Top Relays
            item {
                state.metricsSnapshot?.let { snapshot ->
                    TopRelaysCard(snapshot)
                }
            }

            // Recent Events Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Events (${state.recentEvents.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = { viewModel.onIntent(DebugIntent.ClearEvents) }) {
                        Text("Clear")
                    }
                }
            }

            // Event List
            items(state.recentEvents) { event ->
                EventCard(event)
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PoolStatusCard(status: PoolStatus) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Relay Pools", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Main Pool", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${status.mainPoolConnected}/${status.mainPoolTotal} connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status.mainPoolConnected > 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Outbox Pool", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${status.outboxPoolConnected}/${status.outboxPoolTotal} connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status.outboxPoolConnected > 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricsSummaryCard(snapshot: OutboxMetricsSnapshot) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Outbox Metrics", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            // Cache metrics
            MetricRow("Cache Hits", snapshot.cacheHits.toString())
            MetricRow("Cache Misses", snapshot.cacheMisses.toString())
            MetricRow(
                "Cache Hit Rate",
                "${(snapshot.cacheHitRate * 100).toInt()}%",
                highlight = snapshot.cacheHitRate > 0.5f
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Fetch metrics
            MetricRow("Fetches Started", snapshot.fetchesStarted.toString())
            MetricRow("Fetches Succeeded", snapshot.fetchesSucceeded.toString())
            MetricRow("Fetches Timed Out", snapshot.fetchesTimedOut.toString(), isError = snapshot.fetchesTimedOut > 0)
            MetricRow("Fetches No Relays", snapshot.fetchesNoRelays.toString(), isError = snapshot.fetchesNoRelays > 0)
            MetricRow("Avg Fetch Duration", "${snapshot.avgFetchDurationMs}ms")

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Subscription metrics
            MetricRow("Subscriptions Calculated", snapshot.subscriptionsCalculated.toString())
            MetricRow("Dynamic Relays Added", snapshot.dynamicRelaysAdded.toString())
            MetricRow("Authors Queried", snapshot.totalAuthorsQueried.toString())
            MetricRow("Authors Covered", snapshot.totalAuthorsCovered.toString())
            MetricRow(
                "Author Coverage",
                "${(snapshot.authorCoverageRate * 100).toInt()}%",
                highlight = snapshot.authorCoverageRate > 0.5f
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            MetricRow("Known Relay Lists", snapshot.knownRelayListCount.toString())
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    highlight: Boolean = false,
    isError: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isError -> MaterialTheme.colorScheme.error
                highlight -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun TopRelaysCard(snapshot: OutboxMetricsSnapshot) {
    val topRelays = snapshot.topRelays(5)
    if (topRelays.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Top Relays", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            topRelays.forEach { (url, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        url.removePrefix("wss://").take(30),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "$count",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: OutboxMetricsEvent) {
    val (label, detail, color) = when (event) {
        is OutboxMetricsEvent.RelayListCacheHit -> Triple(
            "CACHE HIT",
            event.pubkey.take(16) + "...",
            MaterialTheme.colorScheme.primary
        )
        is OutboxMetricsEvent.RelayListCacheMiss -> Triple(
            "CACHE MISS",
            event.pubkey.take(16) + "...",
            MaterialTheme.colorScheme.secondary
        )
        is OutboxMetricsEvent.RelayListFetchStarted -> Triple(
            "FETCH START",
            "${event.pubkey.take(12)}... (${event.pool})",
            MaterialTheme.colorScheme.tertiary
        )
        is OutboxMetricsEvent.RelayListFetchSuccess -> Triple(
            "FETCH OK",
            "${event.pubkey.take(12)}... (${event.durationMs}ms)",
            MaterialTheme.colorScheme.primary
        )
        is OutboxMetricsEvent.RelayListFetchTimeout -> Triple(
            "FETCH TIMEOUT",
            "${event.pubkey.take(12)}... (${event.timeoutMs}ms)",
            MaterialTheme.colorScheme.error
        )
        is OutboxMetricsEvent.RelayListFetchNoRelays -> Triple(
            "NO RELAYS",
            event.pubkey.take(16) + "...",
            MaterialTheme.colorScheme.error
        )
        is OutboxMetricsEvent.SubscriptionRelaysCalculated -> Triple(
            "SUB CALC",
            "${event.coveredAuthors}/${event.authorCount} authors, ${event.relayCount} relays",
            MaterialTheme.colorScheme.secondary
        )
        is OutboxMetricsEvent.SubscriptionRelayAdded -> Triple(
            "RELAY ADDED",
            event.relayUrl.removePrefix("wss://").take(20),
            MaterialTheme.colorScheme.primary
        )
        is OutboxMetricsEvent.RelayListTracked -> Triple(
            "TRACKED",
            "${event.pubkey.take(12)}... (${event.writeRelayCount}W/${event.readRelayCount}R)",
            MaterialTheme.colorScheme.primary
        )
    }

    val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    val timeStr = timeFormat.format(Date(event.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
        Text(
            timeStr,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
