package io.nostr.ndk.cache.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Nostr events.
 *
 * Provides optimized queries for common Nostr access patterns:
 * - Single event lookup by ID
 * - Kind-based filtering with pagination
 * - Replaceable event lookups (profile, contacts, relay list)
 * - Time-range queries
 * - Author filtering
 */
@Dao
interface EventDao {

    /**
     * Insert or replace an event.
     * Uses REPLACE strategy to handle dedup key conflicts for replaceable events.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity)

    /**
     * Insert multiple events.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    /**
     * Get an event by ID.
     */
    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): EventEntity?

    /**
     * Get an event by deduplication key.
     * Used for replaceable event lookups.
     */
    @Query("SELECT * FROM events WHERE dedupKey = :dedupKey LIMIT 1")
    suspend fun getByDedupKey(dedupKey: String): EventEntity?

    /**
     * Delete an event by ID.
     */
    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Clear all events.
     */
    @Query("DELETE FROM events")
    suspend fun deleteAll()

    /**
     * Get events by kind, ordered by createdAt descending.
     */
    @Query("SELECT * FROM events WHERE kind = :kind ORDER BY createdAt DESC LIMIT :limit")
    fun getByKind(kind: Int, limit: Int = 100): Flow<List<EventEntity>>

    /**
     * Get events by multiple kinds, ordered by createdAt descending.
     */
    @Query("SELECT * FROM events WHERE kind IN (:kinds) ORDER BY createdAt DESC LIMIT :limit")
    fun getByKinds(kinds: List<Int>, limit: Int = 100): Flow<List<EventEntity>>

    /**
     * Get events by author, ordered by createdAt descending.
     */
    @Query("SELECT * FROM events WHERE pubkey = :pubkey ORDER BY createdAt DESC LIMIT :limit")
    fun getByAuthor(pubkey: String, limit: Int = 100): Flow<List<EventEntity>>

    /**
     * Get events by author and kind, ordered by createdAt descending.
     */
    @Query("SELECT * FROM events WHERE pubkey = :pubkey AND kind = :kind ORDER BY createdAt DESC LIMIT :limit")
    fun getByAuthorAndKind(pubkey: String, kind: Int, limit: Int = 100): Flow<List<EventEntity>>

    /**
     * Get events created after a timestamp, ordered by createdAt descending.
     */
    @Query("SELECT * FROM events WHERE createdAt >= :since ORDER BY createdAt DESC LIMIT :limit")
    fun getSince(since: Long, limit: Int = 100): Flow<List<EventEntity>>

    /**
     * Get events created before a timestamp, ordered by createdAt descending.
     */
    @Query("SELECT * FROM events WHERE createdAt <= :until ORDER BY createdAt DESC LIMIT :limit")
    fun getUntil(until: Long, limit: Int = 100): Flow<List<EventEntity>>

    /**
     * Get events in a time range, ordered by createdAt descending.
     */
    @Query("SELECT * FROM events WHERE createdAt >= :since AND createdAt <= :until ORDER BY createdAt DESC LIMIT :limit")
    fun getInRange(since: Long, until: Long, limit: Int = 100): Flow<List<EventEntity>>

    /**
     * Get events by IDs.
     */
    @Query("SELECT * FROM events WHERE id IN (:ids) ORDER BY createdAt DESC")
    fun getByIds(ids: List<String>): Flow<List<EventEntity>>

    /**
     * Query with all filter parameters.
     * This is a flexible query that handles multiple filter conditions.
     *
     * Note: Tag-based queries are handled separately since Room doesn't support
     * JSON queries efficiently. Filter by tags in the RoomCacheAdapter.
     */
    @Query("""
        SELECT * FROM events
        WHERE (:kindsEmpty = 1 OR kind IN (:kinds))
        AND (:authorsEmpty = 1 OR pubkey IN (:authors))
        AND (:idsEmpty = 1 OR id IN (:ids))
        AND (:since IS NULL OR createdAt >= :since)
        AND (:until IS NULL OR createdAt <= :until)
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    fun query(
        kinds: List<Int>,
        kindsEmpty: Int,
        authors: List<String>,
        authorsEmpty: Int,
        ids: List<String>,
        idsEmpty: Int,
        since: Long?,
        until: Long?,
        limit: Int
    ): Flow<List<EventEntity>>

    /**
     * Get total event count.
     */
    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int

    /**
     * Check if an event exists by ID.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM events WHERE id = :id)")
    suspend fun exists(id: String): Boolean
}
