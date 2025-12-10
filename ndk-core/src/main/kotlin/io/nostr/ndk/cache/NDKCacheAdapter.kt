package io.nostr.ndk.cache

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.models.EventId
import kotlinx.coroutines.flow.Flow

/**
 * Interface for NDK event cache implementations.
 *
 * Cache adapters handle persistent storage of Nostr events, enabling offline access
 * and reducing relay queries. Implementations must handle:
 * - Regular events: Stored by event ID
 * - Replaceable events (kind 0, 3, 10000-19999): Latest event per kind+pubkey
 * - Parameterized replaceable (kind 30000-39999): Latest per kind+pubkey+d-tag
 * - Ephemeral events (kind 20000-29999): Never cached
 *
 * All operations are suspend functions to support async database access.
 */
interface NDKCacheAdapter {

    /**
     * Store an event in the cache.
     *
     * Handles replacement logic for replaceable/parameterized replaceable events:
     * - If a newer event exists with same dedup key, the store is ignored
     * - If this event is newer, it replaces the existing one
     * - Ephemeral events (kind 20000-29999) are never stored
     *
     * @param event The event to store
     */
    suspend fun store(event: NDKEvent)

    /**
     * Query events matching a filter.
     *
     * Returns events as a Flow, emitting matches as they're found.
     * Results are ordered by createdAt descending (newest first).
     *
     * @param filter The filter criteria to match
     * @return Flow of matching events
     */
    fun query(filter: NDKFilter): Flow<NDKEvent>

    /**
     * Get a single event by ID.
     *
     * @param id The event ID to look up
     * @return The event if found, null otherwise
     */
    suspend fun getEvent(id: EventId): NDKEvent?

    /**
     * Delete an event from the cache.
     *
     * @param id The event ID to delete
     */
    suspend fun delete(id: EventId)

    /**
     * Clear all events from the cache.
     */
    suspend fun clear()

    /**
     * Get the profile (kind 0) event for a pubkey.
     *
     * @param pubkey The public key to look up
     * @return The profile event if found, null otherwise
     */
    suspend fun getProfile(pubkey: PublicKey): NDKEvent?

    /**
     * Get the contacts (kind 3) event for a pubkey.
     *
     * @param pubkey The public key to look up
     * @return The contacts event if found, null otherwise
     */
    suspend fun getContacts(pubkey: PublicKey): NDKEvent?

    /**
     * Get the relay list (kind 10002, NIP-65) event for a pubkey.
     *
     * @param pubkey The public key to look up
     * @return The relay list event if found, null otherwise
     */
    suspend fun getRelayList(pubkey: PublicKey): NDKEvent?
}
