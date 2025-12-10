package io.nostr.ndk.cache.room

import android.content.Context
import io.nostr.ndk.cache.NDKCacheAdapter
import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.PublicKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map

/**
 * Room-based implementation of NDKCacheAdapter.
 *
 * Provides persistent storage for Nostr events using Android's Room database.
 * Handles replaceable event logic and efficient querying.
 *
 * Usage:
 * ```kotlin
 * val cacheAdapter = RoomCacheAdapter(context)
 * val ndk = NDK(cacheAdapter = cacheAdapter)
 * ```
 *
 * Or with custom database:
 * ```kotlin
 * val db = NDKDatabase.create(context, "custom.db")
 * val cacheAdapter = RoomCacheAdapter(db)
 * ```
 */
class RoomCacheAdapter : NDKCacheAdapter {

    private val database: NDKDatabase
    private val eventDao: EventDao

    /**
     * Create adapter with application context.
     * Uses default singleton database instance.
     */
    constructor(context: Context) {
        database = NDKDatabase.getInstance(context)
        eventDao = database.eventDao()
    }

    /**
     * Create adapter with custom database instance.
     * Useful for testing or multiple database instances.
     */
    constructor(database: NDKDatabase) {
        this.database = database
        eventDao = database.eventDao()
    }

    override suspend fun store(event: NDKEvent) {
        // Don't store ephemeral events
        if (event.isEphemeral) return

        // For replaceable events, check if we already have a newer version
        if (event.isReplaceable || event.isParameterizedReplaceable) {
            val existing = eventDao.getByDedupKey(event.deduplicationKey())
            if (existing != null && existing.createdAt >= event.createdAt) {
                // Existing event is newer or same age, don't replace
                return
            }
        }

        eventDao.insert(EventEntity.fromNDKEvent(event))
    }

    override fun query(filter: NDKFilter): Flow<NDKEvent> {
        val kinds = filter.kinds?.toList() ?: emptyList()
        val authors = filter.authors?.toList() ?: emptyList()
        val ids = filter.ids?.toList() ?: emptyList()
        val limit = filter.limit ?: 100

        val dbFlow = eventDao.query(
            kinds = kinds,
            kindsEmpty = if (kinds.isEmpty()) 1 else 0,
            authors = authors,
            authorsEmpty = if (authors.isEmpty()) 1 else 0,
            ids = ids,
            idsEmpty = if (ids.isEmpty()) 1 else 0,
            since = filter.since,
            until = filter.until,
            limit = limit
        )

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        return dbFlow.flatMapConcat { entities ->
            entities.asFlow()
        }.map { entity ->
            entity.toNDKEvent()
        }.filter { event ->
            // Apply tag filters that Room can't handle efficiently
            matchesTagFilters(event, filter)
        }
    }

    override suspend fun getEvent(id: EventId): NDKEvent? {
        return eventDao.getById(id)?.toNDKEvent()
    }

    override suspend fun delete(id: EventId) {
        eventDao.deleteById(id)
    }

    override suspend fun clear() {
        eventDao.deleteAll()
    }

    override suspend fun getProfile(pubkey: PublicKey): NDKEvent? {
        val dedupKey = "0:$pubkey"
        return eventDao.getByDedupKey(dedupKey)?.toNDKEvent()
    }

    override suspend fun getContacts(pubkey: PublicKey): NDKEvent? {
        val dedupKey = "3:$pubkey"
        return eventDao.getByDedupKey(dedupKey)?.toNDKEvent()
    }

    override suspend fun getRelayList(pubkey: PublicKey): NDKEvent? {
        val dedupKey = "10002:$pubkey"
        return eventDao.getByDedupKey(dedupKey)?.toNDKEvent()
    }

    /**
     * Check if event matches tag filters from NDKFilter.
     *
     * NDKFilter.tags maps tag names to required values.
     * An event matches if it has at least one tag with the name
     * where the first value is in the required set.
     */
    private fun matchesTagFilters(event: NDKEvent, filter: NDKFilter): Boolean {
        val tagFilters = filter.tags ?: return true

        for ((tagName, requiredValues) in tagFilters) {
            val eventTags = event.tagsWithName(tagName)
            val hasMatch = eventTags.any { tag ->
                val tagValue = tag.values.firstOrNull()
                tagValue != null && tagValue in requiredValues
            }
            if (!hasMatch) return false
        }

        return true
    }

    /**
     * Get the total number of cached events.
     */
    suspend fun eventCount(): Int = eventDao.count()

    /**
     * Check if an event is cached.
     */
    suspend fun hasEvent(id: EventId): Boolean = eventDao.exists(id)

    /**
     * Close the database connection.
     * Should be called when the cache is no longer needed.
     */
    fun close() {
        database.close()
    }
}
