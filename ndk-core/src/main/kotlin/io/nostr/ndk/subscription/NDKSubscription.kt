package io.nostr.ndk.subscription

import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.relay.NDKRelay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Represents an active subscription to Nostr events matching a set of filters.
 *
 * NDKSubscription provides Flow-based event streaming where events are emitted
 * as they arrive from relays. This class tracks EOSE (End Of Stored Events)
 * status per relay and manages the subscription lifecycle.
 *
 * @property id Unique identifier for this subscription
 * @property filters List of filters that determine which events match
 * @property ndk Reference to the parent NDK instance (nullable for testing)
 */
class NDKSubscription(
    val id: String,
    val filters: List<NDKFilter>,
    private val ndk: NDK?
) {
    companion object {
        /**
         * Default replay buffer size for event streams.
         * Keeps the last 100 events for late subscribers.
         */
        const val DEFAULT_REPLAY_SIZE = 100

        /**
         * Extra buffer capacity for handling burst traffic.
         */
        const val DEFAULT_EXTRA_BUFFER = 500
    }

    // Internal MutableSharedFlow for emitting events
    // Using bounded replay buffer to prevent memory issues
    private val _events = MutableSharedFlow<NDKEvent>(
        replay = DEFAULT_REPLAY_SIZE,
        extraBufferCapacity = DEFAULT_EXTRA_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Flow of events matching the subscription filters.
     * This is the primary API for consuming events from the subscription.
     */
    val events: SharedFlow<NDKEvent> = _events.asSharedFlow()

    // Internal MutableStateFlow for tracking EOSE per relay
    private val _eosePerRelay = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /**
     * StateFlow tracking EOSE (End Of Stored Events) status per relay URL.
     * The map contains relay URLs as keys and true/false indicating EOSE received.
     */
    val eosePerRelay: StateFlow<Map<String, Boolean>> = _eosePerRelay.asStateFlow()

    // StateFlow tracking cache EOSE status
    private val _cacheEose = MutableStateFlow(false)

    /**
     * StateFlow tracking whether cached events have been loaded.
     * True once all cached events matching the filters have been emitted.
     */
    val cacheEose: StateFlow<Boolean> = _cacheEose.asStateFlow()

    // Coroutine scope for cache operations
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Set of relays this subscription is active on
    private var activeRelays: Set<NDKRelay> = emptySet()

    /**
     * Starts the subscription on the specified relays.
     * This sends the subscription filters to each relay.
     *
     * @param relays Set of relays to subscribe on
     */
    internal fun start(relays: Set<NDKRelay>) {
        activeRelays = relays
        relays.forEach { relay ->
            relay.subscribe(id, filters)
        }
    }

    /**
     * Stops the subscription and cancels all relay subscriptions.
     * After calling stop(), no more events will be emitted.
     */
    fun stop() {
        activeRelays.forEach { relay ->
            relay.unsubscribe(id)
        }
        activeRelays = emptySet()
    }

    /**
     * Emits an event into the subscription's event flow.
     * This is called internally by the subscription manager when a matching event arrives.
     *
     * @param event The event to emit
     * @param relay The relay that sent the event
     */
    internal fun emit(event: NDKEvent, relay: NDKRelay) {
        _events.tryEmit(event)
    }

    /**
     * Marks that a relay has sent EOSE (End Of Stored Events).
     * Updates the eosePerRelay state flow with the relay's EOSE status.
     *
     * @param relay The relay that sent EOSE
     */
    internal fun markEose(relay: NDKRelay) {
        val currentMap = _eosePerRelay.value
        _eosePerRelay.value = currentMap + (relay.url to true)
    }

    /**
     * Loads cached events matching the subscription filters.
     * Emits all matching cached events immediately, then marks cacheEose as true.
     * This is called automatically when starting a subscription with a cache.
     */
    internal fun loadFromCache() {
        val cache = ndk?.cacheAdapter ?: run {
            _cacheEose.value = true
            return
        }

        cacheScope.launch {
            try {
                // Query cache for each filter
                filters.forEach { filter ->
                    // Remove temporal constraints for cache query to get more matches
                    cache.query(filter).collect { event ->
                        _events.tryEmit(event)
                    }
                }
            } finally {
                _cacheEose.value = true
            }
        }
    }

    /**
     * Emits a single event directly (for cache events that don't need relay tracking).
     * This is used internally to emit cached events.
     *
     * @param event The event to emit
     */
    internal fun emitCached(event: NDKEvent) {
        _events.tryEmit(event)
    }
}
