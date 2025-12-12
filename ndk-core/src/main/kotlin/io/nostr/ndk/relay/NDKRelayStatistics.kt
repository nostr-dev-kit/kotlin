package io.nostr.ndk.relay

import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe statistics tracker for an NDK relay.
 *
 * Tracks comprehensive metrics including:
 * - Connection metrics (attempts, successes, timestamps)
 * - Message counts (sent, received, unique)
 * - Network traffic (bytes sent/received)
 * - Event validation (validated, non-validated)
 * - Subscriptions (active, total)
 * - Authentication (attempts, successes)
 *
 * All metrics are thread-safe using AtomicLong and can be accessed
 * via immutable snapshots.
 */
class NDKRelayStatistics {
    // Connection metrics
    private val _connectionAttempts = AtomicLong(0)
    private val _successfulConnections = AtomicLong(0)
    private val _disconnections = AtomicLong(0)
    private var _lastConnectedAt: Long? = null
    private var _firstConnectedAt: Long? = null

    // Message metrics
    private val _messagesSent = AtomicLong(0)
    private val _messagesReceived = AtomicLong(0)
    private val _uniqueMessages = AtomicLong(0)  // Events this relay was first to send

    // Network traffic (bytes)
    private val _bytesSent = AtomicLong(0)
    private val _bytesReceived = AtomicLong(0)

    // Event validation
    private val _validatedEvents = AtomicLong(0)
    private val _nonValidatedEvents = AtomicLong(0)

    // Subscription metrics
    private val _activeSubscriptions = AtomicLong(0)
    private val _totalSubscriptions = AtomicLong(0)

    // Authentication metrics
    private val _authAttempts = AtomicLong(0)
    private val _authSuccesses = AtomicLong(0)

    // ==========================================
    // Recording methods (internal)
    // ==========================================

    internal fun recordConnectionAttempt() {
        _connectionAttempts.incrementAndGet()
    }

    internal fun recordSuccessfulConnection(timestamp: Long) {
        _successfulConnections.incrementAndGet()
        _lastConnectedAt = timestamp
        if (_firstConnectedAt == null) {
            _firstConnectedAt = timestamp
        }
    }

    internal fun recordDisconnection() {
        _disconnections.incrementAndGet()
    }

    internal fun recordMessageSent(bytes: Int) {
        _messagesSent.incrementAndGet()
        _bytesSent.addAndGet(bytes.toLong())
    }

    internal fun recordMessageReceived(bytes: Int) {
        _messagesReceived.incrementAndGet()
        _bytesReceived.addAndGet(bytes.toLong())
    }

    internal fun recordUniqueMessage() {
        _uniqueMessages.incrementAndGet()
    }

    internal fun recordValidatedEvent() {
        _validatedEvents.incrementAndGet()
    }

    internal fun recordNonValidatedEvent() {
        _nonValidatedEvents.incrementAndGet()
    }

    internal fun recordSubscriptionAdded() {
        _activeSubscriptions.incrementAndGet()
        _totalSubscriptions.incrementAndGet()
    }

    internal fun recordSubscriptionRemoved() {
        _activeSubscriptions.decrementAndGet()
    }

    internal fun recordAuthAttempt() {
        _authAttempts.incrementAndGet()
    }

    internal fun recordAuthSuccess() {
        _authSuccesses.incrementAndGet()
    }

    // ==========================================
    // Public API
    // ==========================================

    /**
     * Get an immutable snapshot of current statistics.
     */
    fun snapshot(): NDKRelayStatisticsSnapshot {
        return NDKRelayStatisticsSnapshot(
            connectionAttempts = _connectionAttempts.get(),
            successfulConnections = _successfulConnections.get(),
            disconnections = _disconnections.get(),
            lastConnectedAt = _lastConnectedAt,
            firstConnectedAt = _firstConnectedAt,
            messagesSent = _messagesSent.get(),
            messagesReceived = _messagesReceived.get(),
            uniqueMessages = _uniqueMessages.get(),
            bytesSent = _bytesSent.get(),
            bytesReceived = _bytesReceived.get(),
            validatedEvents = _validatedEvents.get(),
            nonValidatedEvents = _nonValidatedEvents.get(),
            activeSubscriptions = _activeSubscriptions.get(),
            totalSubscriptions = _totalSubscriptions.get(),
            authAttempts = _authAttempts.get(),
            authSuccesses = _authSuccesses.get()
        )
    }

    /**
     * Reset all statistics to zero.
     */
    fun reset() {
        _connectionAttempts.set(0)
        _successfulConnections.set(0)
        _disconnections.set(0)
        _lastConnectedAt = null
        _firstConnectedAt = null
        _messagesSent.set(0)
        _messagesReceived.set(0)
        _uniqueMessages.set(0)
        _bytesSent.set(0)
        _bytesReceived.set(0)
        _validatedEvents.set(0)
        _nonValidatedEvents.set(0)
        _activeSubscriptions.set(0)
        _totalSubscriptions.set(0)
        _authAttempts.set(0)
        _authSuccesses.set(0)
    }
}

/**
 * Immutable snapshot of relay statistics at a point in time.
 */
data class NDKRelayStatisticsSnapshot(
    val connectionAttempts: Long,
    val successfulConnections: Long,
    val disconnections: Long,
    val lastConnectedAt: Long?,
    val firstConnectedAt: Long?,
    val messagesSent: Long,
    val messagesReceived: Long,
    val uniqueMessages: Long,
    val bytesSent: Long,
    val bytesReceived: Long,
    val validatedEvents: Long,
    val nonValidatedEvents: Long,
    val activeSubscriptions: Long,
    val totalSubscriptions: Long,
    val authAttempts: Long,
    val authSuccesses: Long
) {
    /**
     * Connection success rate (0.0 to 1.0).
     */
    val connectionSuccessRate: Float
        get() = if (connectionAttempts > 0)
            successfulConnections.toFloat() / connectionAttempts else 0f

    /**
     * Event validation rate (0.0 to 1.0).
     */
    val validationRate: Float
        get() {
            val total = validatedEvents + nonValidatedEvents
            return if (total > 0) validatedEvents.toFloat() / total else 0f
        }

    /**
     * Authentication success rate (0.0 to 1.0).
     */
    val authSuccessRate: Float
        get() = if (authAttempts > 0)
            authSuccesses.toFloat() / authAttempts else 0f

    /**
     * Percentage of messages that were unique to this relay (0.0 to 1.0).
     */
    val uniqueMessageRate: Float
        get() = if (messagesReceived > 0)
            uniqueMessages.toFloat() / messagesReceived else 0f

    /**
     * Uptime in milliseconds (if ever connected).
     */
    val uptimeMs: Long?
        get() = if (firstConnectedAt != null && lastConnectedAt != null) {
            lastConnectedAt - firstConnectedAt
        } else null

    override fun toString(): String {
        return """
            RelayStatistics:
              Connections: $successfulConnections/$connectionAttempts (${(connectionSuccessRate * 100).toInt()}% success)
              Messages: sent=$messagesSent, received=$messagesReceived, unique=$uniqueMessages
              Traffic: sent=${formatBytes(bytesSent)}, received=${formatBytes(bytesReceived)}
              Events: validated=$validatedEvents, non-validated=$nonValidatedEvents (${(validationRate * 100).toInt()}% valid)
              Subscriptions: active=$activeSubscriptions, total=$totalSubscriptions
              Auth: $authSuccesses/$authAttempts (${(authSuccessRate * 100).toInt()}% success)
        """.trimIndent()
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)}KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))}MB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024 * 1024))}GB"
        }
    }
}
