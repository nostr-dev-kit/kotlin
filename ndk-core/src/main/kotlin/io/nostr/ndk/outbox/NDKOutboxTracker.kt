package io.nostr.ndk.outbox

import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.PublicKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tracks relay lists (NIP-65) for users and provides outbox model capabilities.
 *
 * The outbox model (NIP-65) optimizes relay usage by:
 * - Reading events FROM a user at their WRITE relays (their outbox)
 * - Publishing events TO a user's timeline at their READ relays (their inbox)
 *
 * This tracker caches relay lists and provides methods to determine
 * the optimal relays for querying and publishing.
 */
class NDKOutboxTracker(private val ndk: NDK) {

    private val _relayListUpdates = MutableSharedFlow<Pair<PublicKey, RelayList>>(replay = 0, extraBufferCapacity = 64)

    /**
     * Flow of relay list discovery events.
     * Emitted when a new relay list is tracked via [trackRelayList].
     */
    val onRelayListDiscovered: SharedFlow<Pair<PublicKey, RelayList>> = _relayListUpdates.asSharedFlow()

    /**
     * Gets the relay list for a pubkey from cache.
     *
     * @param pubkey The public key to look up
     * @return The relay list if found, null otherwise
     */
    suspend fun getRelayList(pubkey: PublicKey): RelayList? {
        val cache = ndk.cacheAdapter ?: return null
        val event = cache.getRelayList(pubkey) ?: return null
        return RelayList.fromEvent(event)
    }

    /**
     * Gets the write relays for a pubkey.
     * These are the relays where the user publishes their events.
     *
     * @param pubkey The public key to look up
     * @return Set of write relay URLs, empty if not found
     */
    suspend fun getWriteRelaysForPubkey(pubkey: PublicKey): Set<String> {
        return getRelayList(pubkey)?.writeRelays ?: emptySet()
    }

    /**
     * Gets the read relays for a pubkey.
     * These are the relays where the user reads events from their timeline.
     *
     * @param pubkey The public key to look up
     * @return Set of read relay URLs, empty if not found
     */
    suspend fun getReadRelaysForPubkey(pubkey: PublicKey): Set<String> {
        return getRelayList(pubkey)?.readRelays ?: emptySet()
    }

    /**
     * Gets combined write relays for multiple pubkeys.
     * Useful when querying for events from multiple users.
     *
     * @param pubkeys Set of public keys to look up
     * @return Combined set of write relay URLs (deduplicated)
     */
    suspend fun getWriteRelaysForPubkeys(pubkeys: Set<PublicKey>): Set<String> {
        return pubkeys.flatMap { pubkey ->
            getWriteRelaysForPubkey(pubkey)
        }.toSet()
    }

    /**
     * Gets combined read relays for multiple pubkeys.
     * Useful when publishing to multiple users' timelines.
     *
     * @param pubkeys Set of public keys to look up
     * @return Combined set of read relay URLs (deduplicated)
     */
    suspend fun getReadRelaysForPubkeys(pubkeys: Set<PublicKey>): Set<String> {
        return pubkeys.flatMap { pubkey ->
            getReadRelaysForPubkey(pubkey)
        }.toSet()
    }

    /**
     * Gets relays to QUERY for events FROM a pubkey (outbox model).
     * To find events created by a user, query their write relays.
     *
     * @param pubkey The author's public key
     * @return Set of relay URLs to query
     */
    suspend fun getRelaysToQueryForPubkey(pubkey: PublicKey): Set<String> {
        return getWriteRelaysForPubkey(pubkey)
    }

    /**
     * Gets relays to QUERY for events FROM multiple pubkeys (outbox model).
     *
     * @param pubkeys Set of author public keys
     * @return Combined set of relay URLs to query
     */
    suspend fun getRelaysToQueryForPubkeys(pubkeys: Set<PublicKey>): Set<String> {
        return getWriteRelaysForPubkeys(pubkeys)
    }

    /**
     * Gets relays to PUBLISH events TO a pubkey's timeline (inbox model).
     * To notify a user of an event (reply, mention), publish to their read relays.
     *
     * @param pubkey The recipient's public key
     * @return Set of relay URLs to publish to
     */
    suspend fun getRelaysToPublishForPubkey(pubkey: PublicKey): Set<String> {
        return getReadRelaysForPubkey(pubkey)
    }

    /**
     * Gets relays to PUBLISH events TO multiple pubkeys' timelines.
     *
     * @param pubkeys Set of recipient public keys
     * @return Combined set of relay URLs to publish to
     */
    suspend fun getRelaysToPublishForPubkeys(pubkeys: Set<PublicKey>): Set<String> {
        return getReadRelaysForPubkeys(pubkeys)
    }

    /**
     * Tracks a relay list event, storing it in cache and emitting update.
     * Call this when you receive a kind 10002 event.
     *
     * @param event The relay list event (kind 10002)
     */
    suspend fun trackRelayList(event: NDKEvent) {
        require(event.kind == 10002) { "Expected kind 10002, got ${event.kind}" }
        ndk.cacheAdapter?.store(event)

        // Emit update for subscriptions to react to
        val relayList = RelayList.fromEvent(event)
        _relayListUpdates.emit(event.pubkey to relayList)
    }

    /**
     * Fetches the relay list for a pubkey, checking cache first then querying outbox relays.
     *
     * The fetch strategy is:
     * 1. Check cache first
     * 2. Query outbox pool (purplepag.es, relay.nos.social) for kind 10002
     * 3. Fall back to main pool if outbox pool has no connected relays
     *
     * @param pubkey The public key to look up
     * @param timeoutMs Maximum time to wait for relay response (default: 5000ms)
     * @return The relay list if found, null otherwise
     */
    suspend fun fetchRelayList(pubkey: PublicKey, timeoutMs: Long = 5000): RelayList? {
        // 1. Check cache first
        getRelayList(pubkey)?.let { return it }

        // 2. Determine which pool to use
        val pool = if (ndk.outboxPool.connectedRelays.value.isNotEmpty()) {
            ndk.outboxPool
        } else if (ndk.pool.connectedRelays.value.isNotEmpty()) {
            ndk.pool
        } else {
            return null // No connected relays
        }

        // 3. Create filter for kind 10002 from this author
        val filter = NDKFilter(
            kinds = setOf(10002),
            authors = setOf(pubkey),
            limit = 1
        )

        // 4. Subscribe to connected relays
        val relays = pool.connectedRelays.value
        if (relays.isEmpty()) return null

        val subscriptionId = "fetch-relaylist-${pubkey.take(8)}"

        try {
            // Start subscription on relays
            relays.forEach { relay ->
                relay.subscribe(subscriptionId, listOf(filter))
            }

            // Wait for event with timeout
            val event = withTimeoutOrNull(timeoutMs) {
                ndk.subscriptionManager.allEvents.firstOrNull { (event, _) ->
                    event.kind == 10002 && event.pubkey == pubkey
                }?.first
            }

            // Track if found (will emit to onRelayListDiscovered)
            event?.let { trackRelayList(it) }

            return event?.let { RelayList.fromEvent(it) }
        } finally {
            // Clean up subscription
            relays.forEach { relay ->
                relay.unsubscribe(subscriptionId)
            }
        }
    }
}
