package io.nostr.ndk

import io.nostr.ndk.cache.NDKCacheAdapter
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.outbox.NDKOutboxTracker
import io.nostr.ndk.relay.NDKPool
import io.nostr.ndk.subscription.NDKSubscription
import io.nostr.ndk.subscription.NDKSubscriptionManager

/**
 * Main entry point for the Nostr Development Kit (NDK).
 *
 * NDK manages relay connections, subscriptions, and event publishing.
 * It provides a streaming-first API where events are delivered via Kotlin Flows.
 *
 * Example usage:
 * ```kotlin
 * val ndk = NDK(
 *     explicitRelayUrls = setOf("wss://relay.damus.io", "wss://nos.lol"),
 *     signer = NDKPrivateKeySigner(keyPair),
 *     cacheAdapter = InMemoryCacheAdapter() // Optional persistent cache
 * )
 *
 * ndk.connect()
 *
 * val subscription = ndk.subscribe(NDKFilter(kinds = setOf(1), limit = 50))
 * subscription.events.collect { event ->
 *     println("New event: ${event.content}")
 * }
 * ```
 *
 * @param explicitRelayUrls Set of relay URLs to connect to
 * @param signer Optional signer for signing and publishing events
 * @param cacheAdapter Optional cache adapter for event persistence
 */
class NDK(
    val explicitRelayUrls: Set<String> = emptySet(),
    val signer: NDKSigner? = null,
    val cacheAdapter: NDKCacheAdapter? = null
) {
    /**
     * Lazy initialized relay pool.
     * The pool manages all relay connections and their lifecycle.
     */
    val pool: NDKPool by lazy { NDKPool(this) }

    /**
     * Lazy initialized subscription manager.
     * The manager coordinates all subscriptions and dispatches events.
     */
    internal val subscriptionManager: NDKSubscriptionManager by lazy { NDKSubscriptionManager(this) }

    /**
     * Lazy initialized outbox tracker.
     * Manages relay lists (NIP-65) and provides outbox model capabilities.
     */
    val outboxTracker: NDKOutboxTracker by lazy { NDKOutboxTracker(this) }

    /**
     * Connects to all explicit relays.
     * Adds each relay URL to the pool and initiates connections.
     *
     * @param timeoutMs Maximum time to wait for connections (default: 5000ms)
     */
    suspend fun connect(timeoutMs: Long = 5000) {
        explicitRelayUrls.forEach { url ->
            pool.addRelay(url, connect = true)
        }
        pool.connect(timeoutMs)
    }

    /**
     * Creates a subscription with a single filter.
     *
     * @param filter The filter to match events against
     * @return A new subscription that will emit matching events
     */
    fun subscribe(filter: NDKFilter): NDKSubscription {
        return subscribe(listOf(filter))
    }

    /**
     * Creates a subscription with multiple filters.
     * Events matching any of the filters will be emitted.
     *
     * If a cache adapter is configured, cached events are emitted first,
     * followed by events from relays (cache-first strategy).
     *
     * @param filters List of filters to match events against
     * @return A new subscription that will emit matching events
     */
    fun subscribe(filters: List<NDKFilter>): NDKSubscription {
        val subscription = subscriptionManager.subscribe(filters)

        // Load cached events first (cache-first strategy)
        subscription.loadFromCache()

        // Then subscribe to relays for new events
        subscription.start(pool.connectedRelays.value)

        return subscription
    }

    /**
     * Triggers reconnection for all disconnected relays.
     * Useful when network connectivity is restored.
     *
     * @param ignoreDelay If true, bypasses exponential backoff delays
     */
    fun reconnect(ignoreDelay: Boolean = false) {
        pool.reconnectAll(ignoreDelay)
    }

    /**
     * Closes the NDK instance and releases all resources.
     *
     * This method:
     * - Disconnects from all relays
     * - Cancels all reconnection attempts
     * - Clears all subscriptions
     * - Releases all coroutine scopes
     *
     * After calling close(), this NDK instance should not be used.
     * Create a new instance if needed.
     */
    fun close() {
        pool.close()
    }
}
