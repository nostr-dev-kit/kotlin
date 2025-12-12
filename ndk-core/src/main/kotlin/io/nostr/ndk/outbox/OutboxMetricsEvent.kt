package io.nostr.ndk.outbox

import io.nostr.ndk.models.PublicKey

/**
 * Events emitted by the outbox model for observability.
 * Subscribe to NDK.outboxEvents to receive these events in real-time.
 */
sealed class OutboxMetricsEvent {
    val timestamp: Long = System.currentTimeMillis()

    // ==========================================
    // Relay List Cache Events
    // ==========================================

    /**
     * Emitted when a relay list is found in cache.
     */
    data class RelayListCacheHit(val pubkey: PublicKey) : OutboxMetricsEvent()

    /**
     * Emitted when a relay list is not found in cache.
     */
    data class RelayListCacheMiss(val pubkey: PublicKey) : OutboxMetricsEvent()

    // ==========================================
    // Fetch Events
    // ==========================================

    /**
     * Emitted when a relay list fetch begins.
     * @param pool Which pool is being queried ("outbox" or "main")
     */
    data class RelayListFetchStarted(
        val pubkey: PublicKey,
        val pool: String
    ) : OutboxMetricsEvent()

    /**
     * Emitted when a relay list fetch succeeds.
     * @param durationMs How long the fetch took
     * @param source Where the relay list came from ("kind10002" or "kind3")
     */
    data class RelayListFetchSuccess(
        val pubkey: PublicKey,
        val durationMs: Long,
        val source: String
    ) : OutboxMetricsEvent()

    /**
     * Emitted when a relay list fetch times out.
     */
    data class RelayListFetchTimeout(
        val pubkey: PublicKey,
        val timeoutMs: Long
    ) : OutboxMetricsEvent()

    /**
     * Emitted when a relay list fetch fails because no relays are available.
     */
    data class RelayListFetchNoRelays(val pubkey: PublicKey) : OutboxMetricsEvent()

    // ==========================================
    // Subscription Relay Events
    // ==========================================

    /**
     * Emitted when relays are calculated for a subscription.
     */
    data class SubscriptionRelaysCalculated(
        val subscriptionId: String,
        val authorCount: Int,
        val relayCount: Int,
        val coveredAuthors: Int,
        val uncoveredAuthors: Int
    ) : OutboxMetricsEvent()

    /**
     * Emitted when a relay is dynamically added to a subscription
     * after a relay list is discovered.
     */
    data class SubscriptionRelayAdded(
        val subscriptionId: String,
        val relayUrl: String,
        val forPubkey: PublicKey
    ) : OutboxMetricsEvent()

    // ==========================================
    // Relay List Discovery Events
    // ==========================================

    /**
     * Emitted when a new relay list is tracked (stored in cache).
     */
    data class RelayListTracked(
        val pubkey: PublicKey,
        val readRelayCount: Int,
        val writeRelayCount: Int
    ) : OutboxMetricsEvent()
}
