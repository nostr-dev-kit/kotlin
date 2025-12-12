package com.example.chirp.features.debug

import io.nostr.ndk.outbox.OutboxMetricsEvent
import io.nostr.ndk.outbox.OutboxMetricsSnapshot

data class DebugState(
    val metricsSnapshot: OutboxMetricsSnapshot? = null,
    val recentEvents: List<OutboxMetricsEvent> = emptyList(),
    val poolStatus: PoolStatus = PoolStatus(),
    val isAutoRefresh: Boolean = true
)

data class PoolStatus(
    val mainPoolConnected: Int = 0,
    val mainPoolTotal: Int = 0,
    val outboxPoolConnected: Int = 0,
    val outboxPoolTotal: Int = 0
)

sealed class DebugIntent {
    data object RefreshMetrics : DebugIntent()
    data object ClearEvents : DebugIntent()
    data object ResetMetrics : DebugIntent()
    data class ToggleAutoRefresh(val enabled: Boolean) : DebugIntent()
}
