package io.nostr.ndk.cache.nostrdb

import android.content.Context
import io.nostr.ndk.cache.NDKCacheAdapter
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.PublicKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * NostrDB-backed implementation of NDKCacheAdapter.
 *
 * Uses the high-performance nostrdb embedded database (LMDB-based) for
 * persistent event storage with automatic deduplication and replacement
 * logic for NIP-01 event types.
 *
 * Features:
 * - Memory-mapped storage for fast reads
 * - Automatic handling of replaceable events (kinds 0, 3, 10000-19999)
 * - Automatic handling of parameterized replaceable events (kinds 30000-39999)
 * - Never stores ephemeral events (kinds 20000-29999)
 * - Thread-safe with LMDB transactions
 *
 * Usage:
 * ```kotlin
 * val cache = NostrDBCacheAdapter.create(context)
 * val ndk = NDK(
 *     explicitRelayUrls = setOf("wss://relay.damus.io"),
 *     cacheAdapter = cache
 * )
 * ```
 */
class NostrDBCacheAdapter private constructor(
    private val ndb: NostrDB
) : NDKCacheAdapter {

    companion object {
        private const val DB_NAME = "nostrdb"

        /**
         * Create a NostrDBCacheAdapter using the app's data directory.
         *
         * @param context Android context for accessing data directory
         * @param mapSize Maximum database size in bytes (default 512 MiB)
         * @return NostrDBCacheAdapter instance
         */
        fun create(
            context: Context,
            mapSize: Long = 512L * 1024 * 1024
        ): NostrDBCacheAdapter {
            val dbDir = File(context.filesDir, DB_NAME)
            if (!dbDir.exists()) {
                dbDir.mkdirs()
            }
            val ndb = NostrDB.open(dbDir.absolutePath, mapSize)
            return NostrDBCacheAdapter(ndb)
        }

        /**
         * Create a NostrDBCacheAdapter with a custom database path.
         *
         * @param dbPath Path to database directory
         * @param mapSize Maximum database size in bytes
         * @return NostrDBCacheAdapter instance
         */
        fun create(
            dbPath: String,
            mapSize: Long = 512L * 1024 * 1024
        ): NostrDBCacheAdapter {
            val dbDir = File(dbPath)
            if (!dbDir.exists()) {
                dbDir.mkdirs()
            }
            val ndb = NostrDB.open(dbPath, mapSize)
            return NostrDBCacheAdapter(ndb)
        }
    }

    override suspend fun store(event: NDKEvent) {
        // Don't cache ephemeral events
        if (event.isEphemeral) return

        withContext(Dispatchers.IO) {
            // Convert event to JSON and process
            val json = event.toJson()
            ndb.processEvent(json)
        }
    }

    override fun query(filter: NDKFilter): Flow<NDKEvent> = flow {
        ndb.withTransaction { txn ->
            val ndbFilter = filter.toNdbFilter()
            ndbFilter.use { f ->
                val noteKeys = txn.query(f, filter.limit ?: 100)

                for (noteKey in noteKeys) {
                    val event = noteKeyToEvent(txn, noteKey)
                    if (event != null) {
                        emit(event)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getEvent(id: EventId): NDKEvent? {
        return withContext(Dispatchers.IO) {
            ndb.withTransaction { txn ->
                val idBytes = id.hexToBytes()
                val noteKey = txn.getNoteKeyById(idBytes)
                if (noteKey == 0L) return@withTransaction null
                noteKeyToEvent(txn, noteKey)
            }
        }
    }

    override suspend fun delete(id: EventId) {
        // nostrdb doesn't support deletion - LMDB is append-only
        // Events are only removed when replaced by newer versions
    }

    override suspend fun clear() {
        // nostrdb doesn't support clearing - would need to delete and recreate database
    }

    override suspend fun getProfile(pubkey: PublicKey): NDKEvent? {
        return withContext(Dispatchers.IO) {
            ndb.withTransaction { txn ->
                val filter = NdbFilter()
                    .authors(listOf(pubkey.hexToBytes()))
                    .kinds(listOf(0))
                    .limit(1)
                    .build()

                filter.use { f ->
                    val noteKeys = txn.query(f, 1)
                    if (noteKeys.isEmpty()) return@withTransaction null
                    noteKeyToEvent(txn, noteKeys[0])
                }
            }
        }
    }

    override suspend fun getContacts(pubkey: PublicKey): NDKEvent? {
        return withContext(Dispatchers.IO) {
            ndb.withTransaction { txn ->
                val filter = NdbFilter()
                    .authors(listOf(pubkey.hexToBytes()))
                    .kinds(listOf(3))
                    .limit(1)
                    .build()

                filter.use { f ->
                    val noteKeys = txn.query(f, 1)
                    if (noteKeys.isEmpty()) return@withTransaction null
                    noteKeyToEvent(txn, noteKeys[0])
                }
            }
        }
    }

    override suspend fun getRelayList(pubkey: PublicKey): NDKEvent? {
        return withContext(Dispatchers.IO) {
            ndb.withTransaction { txn ->
                val filter = NdbFilter()
                    .authors(listOf(pubkey.hexToBytes()))
                    .kinds(listOf(10002))
                    .limit(1)
                    .build()

                filter.use { f ->
                    val noteKeys = txn.query(f, 1)
                    if (noteKeys.isEmpty()) return@withTransaction null
                    noteKeyToEvent(txn, noteKeys[0])
                }
            }
        }
    }

    /**
     * Close the database. Should be called when done.
     */
    fun close() {
        ndb.close()
    }

    /**
     * Convert a note key to an NDKEvent.
     */
    private fun noteKeyToEvent(txn: NostrDB.Transaction, noteKey: Long): NDKEvent? {
        val id = txn.noteId(noteKey)?.toHex() ?: return null
        val pubkey = txn.notePubkey(noteKey)?.toHex() ?: return null
        val createdAt = txn.noteCreatedAt(noteKey)
        val kind = txn.noteKind(noteKey)
        val content = txn.noteContent(noteKey) ?: ""
        val rawTags = txn.noteTags(noteKey)
        val sig = txn.noteSig(noteKey)

        // Convert raw tags (List<List<String>>) to NDKTag objects
        val tags = rawTags.mapNotNull { tagElements ->
            val name = tagElements.firstOrNull()
            if (name.isNullOrEmpty()) null
            else NDKTag(
                name = name,
                values = tagElements.drop(1).filterNotNull()
            )
        }

        return NDKEvent(
            id = id,
            pubkey = pubkey,
            createdAt = createdAt,
            kind = kind,
            tags = tags,
            content = content,
            sig = sig
        )
    }

    /**
     * Convert NDKFilter to NdbFilter.
     */
    private fun NDKFilter.toNdbFilter(): NdbFilter {
        val filter = NdbFilter()

        ids?.let { ids ->
            if (ids.isNotEmpty()) {
                filter.ids(ids.map { it.hexToBytes() })
            }
        }

        authors?.let { authors ->
            if (authors.isNotEmpty()) {
                filter.authors(authors.map { it.hexToBytes() })
            }
        }

        kinds?.let { kinds ->
            if (kinds.isNotEmpty()) {
                filter.kinds(kinds.toList())
            }
        }

        since?.let { since ->
            filter.since(since)
        }

        until?.let { until ->
            filter.until(until)
        }

        limit?.let { limit ->
            filter.limit(limit)
        }

        return filter.build()
    }
}

// Extension functions for hex conversion
private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Hex string must have even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

private fun ByteArray.toHex(): String {
    return joinToString("") { "%02x".format(it) }
}
