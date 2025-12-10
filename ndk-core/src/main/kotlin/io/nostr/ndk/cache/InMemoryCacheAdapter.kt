package io.nostr.ndk.cache

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.models.EventId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of NDKCacheAdapter.
 *
 * Useful for testing and applications that don't need persistent storage.
 * Events are stored in memory and lost when the app closes.
 *
 * Thread-safe using ConcurrentHashMap for storage.
 */
class InMemoryCacheAdapter : NDKCacheAdapter {

    // Main storage: eventId -> event
    private val events = ConcurrentHashMap<EventId, NDKEvent>()

    // Index for replaceable events: "kind:pubkey" -> eventId
    private val replaceableIndex = ConcurrentHashMap<String, EventId>()

    // Index for parameterized replaceable: "kind:pubkey:d-tag" -> eventId
    private val parameterizedIndex = ConcurrentHashMap<String, EventId>()

    override suspend fun store(event: NDKEvent) {
        // Don't cache ephemeral events (kind 20000-29999)
        if (event.isEphemeral) {
            return
        }

        val dedupKey = event.deduplicationKey()

        when {
            event.isReplaceable -> {
                val indexKey = "${event.kind}:${event.pubkey}"
                val existingId = replaceableIndex[indexKey]
                val existingEvent = existingId?.let { events[it] }

                // Only store if newer than existing
                if (existingEvent == null || event.createdAt > existingEvent.createdAt) {
                    // Remove old event if exists
                    existingId?.let { events.remove(it) }
                    // Store new event
                    events[event.id] = event
                    replaceableIndex[indexKey] = event.id
                }
            }
            event.isParameterizedReplaceable -> {
                val indexKey = dedupKey // "kind:pubkey:d-tag"
                val existingId = parameterizedIndex[indexKey]
                val existingEvent = existingId?.let { events[it] }

                // Only store if newer than existing
                if (existingEvent == null || event.createdAt > existingEvent.createdAt) {
                    // Remove old event if exists
                    existingId?.let { events.remove(it) }
                    // Store new event
                    events[event.id] = event
                    parameterizedIndex[indexKey] = event.id
                }
            }
            else -> {
                // Regular event - just store by ID
                events[event.id] = event
            }
        }
    }

    override fun query(filter: NDKFilter): Flow<NDKEvent> = flow {
        val matches = events.values
            .filter { filter.matches(it) }
            .sortedByDescending { it.createdAt }

        val limited = filter.limit?.let { matches.take(it) } ?: matches

        limited.forEach { emit(it) }
    }

    override suspend fun getEvent(id: EventId): NDKEvent? {
        return events[id]
    }

    override suspend fun delete(id: EventId) {
        val event = events.remove(id) ?: return

        // Clean up indexes
        if (event.isReplaceable) {
            val indexKey = "${event.kind}:${event.pubkey}"
            replaceableIndex.remove(indexKey)
        } else if (event.isParameterizedReplaceable) {
            val indexKey = event.deduplicationKey()
            parameterizedIndex.remove(indexKey)
        }
    }

    override suspend fun clear() {
        events.clear()
        replaceableIndex.clear()
        parameterizedIndex.clear()
    }

    override suspend fun getProfile(pubkey: PublicKey): NDKEvent? {
        val indexKey = "0:$pubkey"
        val eventId = replaceableIndex[indexKey] ?: return null
        return events[eventId]
    }

    override suspend fun getContacts(pubkey: PublicKey): NDKEvent? {
        val indexKey = "3:$pubkey"
        val eventId = replaceableIndex[indexKey] ?: return null
        return events[eventId]
    }

    override suspend fun getRelayList(pubkey: PublicKey): NDKEvent? {
        val indexKey = "10002:$pubkey"
        val eventId = replaceableIndex[indexKey] ?: return null
        return events[eventId]
    }
}
