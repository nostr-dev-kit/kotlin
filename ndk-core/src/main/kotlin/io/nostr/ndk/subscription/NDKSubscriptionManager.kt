package io.nostr.ndk.subscription

import io.nostr.ndk.NDK
import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.Timestamp
import io.nostr.ndk.relay.NDKRelay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * Manages all subscriptions and provides a single dispatch point for events.
 *
 * This class is the central coordinator for subscription management in NDK. It:
 * - Creates and tracks all active subscriptions
 * - Dispatches incoming events from relays to matching subscriptions
 * - Deduplicates events across subscriptions
 * - Groups similar subscriptions to reduce relay load
 * - Provides trust-based signature verification sampling
 * - Provides a global event stream (allEvents) for cross-subscription reactivity
 *
 * @property ndk Reference to the parent NDK instance
 * @property groupingDelayMs Delay in ms before processing pending subscriptions for grouping
 */
internal class NDKSubscriptionManager(
    private val ndk: NDK,
    groupingDelayMs: Long = 100
) {
    // Coroutine scope for cache operations
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Subscription grouper for combining similar subscriptions
    internal val grouper = NDKSubscriptionGrouper(ndk, groupingDelayMs)

    // Validation ratio tracker for trust-based signature verification
    val validationTracker = NDKValidationRatioTracker()

    // Active subscriptions indexed by subscription ID
    private val subscriptions = ConcurrentHashMap<String, NDKSubscription>()

    // Single dispatch point for all events
    private val _allEvents = MutableSharedFlow<Pair<NDKEvent, NDKRelay>>(
        replay = 0,
        extraBufferCapacity = 1000
    )

    /**
     * Global event stream containing all events from all subscriptions.
     * Each emission is a pair of (event, relay) indicating which relay sent the event.
     */
    val allEvents: SharedFlow<Pair<NDKEvent, NDKRelay>> = _allEvents.asSharedFlow()

    // LRU-like cache for event deduplication
    // Maps event deduplication key to timestamp when first seen
    private val seenEvents = java.util.Collections.synchronizedMap(LRUCache<String, Timestamp>(maxSize = 10_000))

    /**
     * Creates a new subscription with the given filters.
     *
     * @param filters List of filters to match events against
     * @return The created subscription
     */
    fun subscribe(filters: List<NDKFilter>): NDKSubscription {
        val subscriptionId = generateSubscriptionId()
        val subscription = NDKSubscription(
            id = subscriptionId,
            filters = filters,
            ndk = ndk
        )

        subscriptions[subscriptionId] = subscription
        return subscription
    }

    /**
     * Removes a subscription and stops delivering events to it.
     *
     * @param subscriptionId The ID of the subscription to remove
     */
    fun unsubscribe(subscriptionId: String) {
        subscriptions.remove(subscriptionId)?.stop()
        // Also remove from any grouped subscription
        grouper.remove(subscriptionId)
    }

    /**
     * Enqueues a subscription for potential grouping with similar subscriptions.
     *
     * Subscriptions with the same filter fingerprint will be grouped together to
     * reduce relay load. The grouping happens after a short delay to batch
     * subscriptions created around the same time.
     *
     * @param subscription The subscription to enqueue
     * @param relays The relays to subscribe on
     */
    fun enqueueForGrouping(subscription: NDKSubscription, relays: Set<NDKRelay>) {
        grouper.enqueue(subscription, relays)
    }

    /**
     * Dispatches an event from a relay to matching subscriptions.
     *
     * This is the single dispatch point for all events. It:
     * 1. Checks if the event has been seen before (deduplication)
     * 2. Stores the event in the cache (if configured)
     * 3. Emits to the global allEvents flow
     * 4. Routes to grouped subscriptions (if subscription ID starts with "group-")
     * 5. Routes to individual subscriptions with matching filters
     *
     * @param event The event to dispatch
     * @param relay The relay that sent the event
     * @param subscriptionId The subscription ID this event is for (may route to multiple)
     */
    internal fun dispatchEvent(event: NDKEvent, relay: NDKRelay, subscriptionId: String) {
        // Deduplication check
        val dedupKey = event.deduplicationKey()
        synchronized(seenEvents) {
            if (seenEvents.containsKey(dedupKey)) {
                // Already seen this event, ignore
                return
            }
            // Mark as seen
            seenEvents.put(dedupKey, System.currentTimeMillis() / 1000)
        }

        // Store in cache (non-blocking, fire and forget)
        ndk.cacheAdapter?.let { cache ->
            cacheScope.launch {
                cache.store(event)
            }
        }

        // Auto-track relay lists for outbox model (NIP-65)
        if (event.kind == 10002 && ndk.enableOutboxModel) {
            cacheScope.launch {
                ndk.outboxTracker.trackRelayList(event)
            }
        }

        // Emit to global event stream
        _allEvents.tryEmit(event to relay)

        // Check if this is a grouped subscription
        if (subscriptionId.startsWith("group-")) {
            grouper.dispatchToGroup(event, relay, subscriptionId)
        }

        // Dispatch to all individual subscriptions with matching filters
        subscriptions.values.forEach { subscription ->
            if (subscription.filters.any { filter -> filter.matches(event) }) {
                subscription.emit(event, relay)
            }
        }
    }

    /**
     * Dispatches an EOSE (End Of Stored Events) notification to a subscription.
     *
     * @param relay The relay that sent EOSE
     * @param subscriptionId The subscription ID that received EOSE
     */
    internal fun dispatchEose(relay: NDKRelay, subscriptionId: String) {
        subscriptions[subscriptionId]?.markEose(relay)
    }

    /**
     * Generates a unique subscription ID.
     */
    private fun generateSubscriptionId(): String {
        return "sub-${UUID.randomUUID()}"
    }
}

/**
 * Simple LRU cache implementation using LinkedHashMap.
 *
 * @param maxSize Maximum number of entries to store
 */
private class LRUCache<K, V>(private val maxSize: Int) : LinkedHashMap<K, V>(
    16,
    0.75f,
    true // accessOrder = true for LRU behavior
) {
    override fun removeEldestEntry(eldest: Map.Entry<K, V>?): Boolean {
        return size > maxSize
    }

}
