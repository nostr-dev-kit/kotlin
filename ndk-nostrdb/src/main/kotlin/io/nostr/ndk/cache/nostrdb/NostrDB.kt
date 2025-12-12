package io.nostr.ndk.cache.nostrdb

import android.util.Log
import java.io.Closeable

/**
 * NostrDB wrapper providing JNI access to the nostrdb C library.
 *
 * NostrDB is an embedded LMDB-based database optimized for Nostr events.
 * It provides fast event storage, retrieval, and filtering with automatic
 * deduplication and replacement logic for NIP-01 event types.
 *
 * Usage:
 * ```kotlin
 * val ndb = NostrDB.open("/path/to/db")
 * ndb.use { db ->
 *     db.processEvent(jsonEvent)
 *     db.query(filter) { noteKey ->
 *         val content = db.noteContent(noteKey)
 *     }
 * }
 * ```
 */
class NostrDB private constructor(
    @PublishedApi internal val ndbPtr: Long,
    private val dbPath: String
) : Closeable {

    companion object {
        private const val TAG = "NostrDB"

        // Default map size: 1 GiB
        private const val DEFAULT_MAP_SIZE = 1L * 1024 * 1024 * 1024

        // Default ingester threads
        private const val DEFAULT_INGESTER_THREADS = 2

        init {
            System.loadLibrary("ndk_nostrdb")
        }

        /**
         * Opens or creates a NostrDB database at the specified path.
         *
         * @param dbPath Path to the database directory
         * @param mapSize Maximum database size in bytes (default 1 GiB)
         * @param ingesterThreads Number of threads for event ingestion (default 2)
         * @return NostrDB instance
         * @throws IllegalStateException if database initialization fails
         */
        fun open(
            dbPath: String,
            mapSize: Long = DEFAULT_MAP_SIZE,
            ingesterThreads: Int = DEFAULT_INGESTER_THREADS
        ): NostrDB {
            val ptr = nativeInit(dbPath, mapSize, ingesterThreads)
            if (ptr == 0L) {
                throw IllegalStateException("Failed to initialize NostrDB at $dbPath")
            }
            Log.i(TAG, "NostrDB opened at $dbPath")
            return NostrDB(ptr, dbPath)
        }

        // Native methods - internal for access from inline functions and NdbFilter
        // Note: @JvmName ensures stable JNI function names regardless of build variant
        @JvmStatic
        @JvmName("nativeInit")
        internal external fun nativeInit(dbPath: String, mapSize: Long, ingesterThreads: Int): Long

        @JvmStatic
        @JvmName("nativeDestroy")
        internal external fun nativeDestroy(ndbPtr: Long)

        @PublishedApi
        @JvmStatic
        @JvmName("nativeBeginQuery")
        internal external fun nativeBeginQuery(ndbPtr: Long): Long

        @PublishedApi
        @JvmStatic
        @JvmName("nativeEndQuery")
        internal external fun nativeEndQuery(txnPtr: Long)

        @JvmStatic
        @JvmName("nativeProcessEvent")
        internal external fun nativeProcessEvent(ndbPtr: Long, jsonEvent: String): Int

        @JvmStatic
        @JvmName("nativeGetNoteById")
        internal external fun nativeGetNoteById(ndbPtr: Long, txnPtr: Long, noteId: ByteArray): ByteArray?

        @JvmStatic
        @JvmName("nativeGetNoteKeyById")
        internal external fun nativeGetNoteKeyById(txnPtr: Long, noteId: ByteArray): Long

        @JvmStatic
        @JvmName("nativeGetProfileByPubkey")
        internal external fun nativeGetProfileByPubkey(ndbPtr: Long, txnPtr: Long, pubkey: ByteArray): String?

        @JvmStatic
        @JvmName("nativeFilterCreate")
        internal external fun nativeFilterCreate(): Long

        @JvmStatic
        @JvmName("nativeFilterDestroy")
        internal external fun nativeFilterDestroy(filterPtr: Long)

        @JvmStatic
        @JvmName("nativeFilterStartField")
        internal external fun nativeFilterStartField(filterPtr: Long, fieldType: Int): Int

        @JvmStatic
        @JvmName("nativeFilterAddIdElement")
        internal external fun nativeFilterAddIdElement(filterPtr: Long, id: ByteArray): Int

        @JvmStatic
        @JvmName("nativeFilterAddIntElement")
        internal external fun nativeFilterAddIntElement(filterPtr: Long, value: Long): Int

        @JvmStatic
        @JvmName("nativeFilterEndField")
        internal external fun nativeFilterEndField(filterPtr: Long)

        @JvmStatic
        @JvmName("nativeFilterEnd")
        internal external fun nativeFilterEnd(filterPtr: Long): Int

        @JvmStatic
        @JvmName("nativeQuery")
        internal external fun nativeQuery(txnPtr: Long, filterPtr: Long, limit: Int): LongArray?

        @JvmStatic
        @JvmName("nativeSubscribe")
        internal external fun nativeSubscribe(ndbPtr: Long, filterPtr: Long): Long

        @JvmStatic
        @JvmName("nativePollForNotes")
        internal external fun nativePollForNotes(ndbPtr: Long, subId: Long, maxNotes: Int): LongArray?

        @JvmStatic
        @JvmName("nativeUnsubscribe")
        internal external fun nativeUnsubscribe(ndbPtr: Long, subId: Long): Int

        @JvmStatic
        @JvmName("nativeNoteContent")
        internal external fun nativeNoteContent(ndbPtr: Long, txnPtr: Long, noteKey: Long): String?

        @JvmStatic
        @JvmName("nativeNoteId")
        internal external fun nativeNoteId(ndbPtr: Long, txnPtr: Long, noteKey: Long): ByteArray?

        @JvmStatic
        @JvmName("nativeNotePubkey")
        internal external fun nativeNotePubkey(ndbPtr: Long, txnPtr: Long, noteKey: Long): ByteArray?

        @JvmStatic
        @JvmName("nativeNoteCreatedAt")
        internal external fun nativeNoteCreatedAt(ndbPtr: Long, txnPtr: Long, noteKey: Long): Long

        @JvmStatic
        @JvmName("nativeNoteKind")
        internal external fun nativeNoteKind(ndbPtr: Long, txnPtr: Long, noteKey: Long): Int

        @JvmStatic
        @JvmName("nativeNoteTags")
        internal external fun nativeNoteTags(ndbPtr: Long, txnPtr: Long, noteKey: Long): Array<Array<String>>?

        @JvmStatic
        @JvmName("nativeNoteSig")
        internal external fun nativeNoteSig(ndbPtr: Long, txnPtr: Long, noteKey: Long): ByteArray?

        // Statistics native methods
        @JvmStatic
        @JvmName("nativeGetStats")
        internal external fun nativeGetStats(ndbPtr: Long): LongArray?

        @JvmStatic
        @JvmName("nativeGetDbName")
        internal external fun nativeGetDbName(dbIndex: Int): String?

        @JvmStatic
        @JvmName("nativeGetCommonKindName")
        internal external fun nativeGetCommonKindName(kindIndex: Int): String?

        @JvmStatic
        @JvmName("nativeGetDbCount")
        internal external fun nativeGetDbCount(): Int

        @JvmStatic
        @JvmName("nativeGetCommonKindCount")
        internal external fun nativeGetCommonKindCount(): Int
    }

    private var closed = false

    /**
     * Process a JSON event and store it in the database.
     *
     * @param jsonEvent The event in JSON format (NIP-01 EVENT array or raw event object)
     * @return 1 on success, 0 on failure
     */
    fun processEvent(jsonEvent: String): Int {
        checkNotClosed()
        return nativeProcessEvent(ndbPtr, jsonEvent)
    }

    /**
     * Execute a query within a transaction scope.
     *
     * @param block Lambda receiving a Transaction for queries
     */
    inline fun <T> withTransaction(block: (Transaction) -> T): T {
        checkNotClosed()
        val txnPtr = nativeBeginQuery(ndbPtr)
        if (txnPtr == 0L) {
            throw IllegalStateException("Failed to begin transaction")
        }
        return try {
            block(Transaction(ndbPtr, txnPtr))
        } finally {
            nativeEndQuery(txnPtr)
        }
    }

    /**
     * Get a note by its ID within a transaction.
     */
    fun getNoteKeyById(txnPtr: Long, noteId: ByteArray): Long {
        checkNotClosed()
        return nativeGetNoteKeyById(txnPtr, noteId)
    }

    /**
     * Get note content by key within a transaction.
     */
    fun noteContent(txnPtr: Long, noteKey: Long): String? {
        checkNotClosed()
        return nativeNoteContent(ndbPtr, txnPtr, noteKey)
    }

    /**
     * Get note ID bytes by key within a transaction.
     */
    fun noteId(txnPtr: Long, noteKey: Long): ByteArray? {
        checkNotClosed()
        return nativeNoteId(ndbPtr, txnPtr, noteKey)
    }

    /**
     * Get note pubkey bytes by key within a transaction.
     */
    fun notePubkey(txnPtr: Long, noteKey: Long): ByteArray? {
        checkNotClosed()
        return nativeNotePubkey(ndbPtr, txnPtr, noteKey)
    }

    /**
     * Get note created_at timestamp by key within a transaction.
     */
    fun noteCreatedAt(txnPtr: Long, noteKey: Long): Long {
        checkNotClosed()
        return nativeNoteCreatedAt(ndbPtr, txnPtr, noteKey)
    }

    /**
     * Get note kind by key within a transaction.
     */
    fun noteKind(txnPtr: Long, noteKey: Long): Int {
        checkNotClosed()
        return nativeNoteKind(ndbPtr, txnPtr, noteKey)
    }

    /**
     * Create a subscription for new events matching a filter.
     *
     * @param filter The filter to match events against
     * @return Subscription ID (0 on failure)
     */
    fun subscribe(filter: NdbFilter): Long {
        checkNotClosed()
        return nativeSubscribe(ndbPtr, filter.ptr)
    }

    /**
     * Poll for new notes on a subscription.
     *
     * @param subId Subscription ID
     * @param maxNotes Maximum number of notes to return
     * @return Array of note keys, or null if none
     */
    fun pollForNotes(subId: Long, maxNotes: Int = 100): LongArray? {
        checkNotClosed()
        return nativePollForNotes(ndbPtr, subId, maxNotes)
    }

    /**
     * Unsubscribe from a subscription.
     *
     * @param subId Subscription ID
     * @return 1 on success, 0 on failure
     */
    fun unsubscribe(subId: Long): Int {
        checkNotClosed()
        return nativeUnsubscribe(ndbPtr, subId)
    }

    /**
     * Get database statistics.
     *
     * @return NdbStats containing counts and sizes for all databases and event kinds,
     *         or null if statistics couldn't be retrieved
     */
    fun getStats(): NdbStats? {
        checkNotClosed()
        val rawStats = nativeGetStats(ndbPtr) ?: return null
        val dbCount = nativeGetDbCount()
        val kindCount = nativeGetCommonKindCount()

        return NdbStats.fromRawArray(
            rawStats = rawStats,
            dbCount = dbCount,
            kindCount = kindCount,
            getDbName = { nativeGetDbName(it) },
            getKindName = { nativeGetCommonKindName(it) }
        )
    }

    /**
     * Get the path to the database directory.
     */
    fun getDatabasePath(): String = dbPath

    override fun close() {
        if (!closed) {
            nativeDestroy(ndbPtr)
            closed = true
            Log.i(TAG, "NostrDB closed")
        }
    }

    @PublishedApi
    internal fun checkNotClosed() {
        if (closed) {
            throw IllegalStateException("NostrDB has been closed")
        }
    }

    /**
     * Read-only transaction for queries.
     */
    inner class Transaction(
        private val ndbPtr: Long,
        val ptr: Long
    ) {
        /**
         * Query for notes matching a filter.
         *
         * @param filter The filter to match
         * @param limit Maximum results to return
         * @return Array of note keys
         */
        fun query(filter: NdbFilter, limit: Int = 100): LongArray {
            return nativeQuery(ptr, filter.ptr, limit) ?: LongArray(0)
        }

        /**
         * Get note key by ID.
         */
        fun getNoteKeyById(noteId: ByteArray): Long {
            return nativeGetNoteKeyById(ptr, noteId)
        }

        /**
         * Get note content by key.
         */
        fun noteContent(noteKey: Long): String? {
            return nativeNoteContent(ndbPtr, ptr, noteKey)
        }

        /**
         * Get note ID by key.
         */
        fun noteId(noteKey: Long): ByteArray? {
            return nativeNoteId(ndbPtr, ptr, noteKey)
        }

        /**
         * Get note pubkey by key.
         */
        fun notePubkey(noteKey: Long): ByteArray? {
            return nativeNotePubkey(ndbPtr, ptr, noteKey)
        }

        /**
         * Get note created_at by key.
         */
        fun noteCreatedAt(noteKey: Long): Long {
            return nativeNoteCreatedAt(ndbPtr, ptr, noteKey)
        }

        /**
         * Get note kind by key.
         */
        fun noteKind(noteKey: Long): Int {
            return nativeNoteKind(ndbPtr, ptr, noteKey)
        }

        /**
         * Get note tags by key.
         * Returns list of tags, where each tag is a list of strings.
         */
        fun noteTags(noteKey: Long): List<List<String>> {
            val rawTags = nativeNoteTags(ndbPtr, ptr, noteKey) ?: return emptyList()
            return rawTags.map { it.toList() }
        }

        /**
         * Get note signature by key.
         * Returns 64-byte signature as hex string.
         */
        fun noteSig(noteKey: Long): String? {
            val sigBytes = nativeNoteSig(ndbPtr, ptr, noteKey) ?: return null
            return sigBytes.joinToString("") { "%02x".format(it) }
        }

        /**
         * Get profile JSON by pubkey.
         * Returns the profile data as a JSON string, or null if not found.
         *
         * This uses nostrdb's profile index for faster lookups.
         * The JSON contains: name, display_name, about, picture, banner, nip05, lud16, lud06, website
         */
        fun getProfileJson(pubkey: ByteArray): String? {
            return nativeGetProfileByPubkey(ndbPtr, ptr, pubkey)
        }
    }
}

/**
 * NostrDB filter builder for queries.
 */
class NdbFilter : Closeable {
    internal val ptr: Long

    init {
        ptr = NostrDB.nativeFilterCreate()
        if (ptr == 0L) {
            throw IllegalStateException("Failed to create filter")
        }
    }

    /**
     * Add authors (pubkeys) to filter.
     */
    fun authors(pubkeys: List<ByteArray>): NdbFilter {
        NostrDB.nativeFilterStartField(ptr, NdbFilterField.AUTHORS.value)
        pubkeys.forEach { pk ->
            NostrDB.nativeFilterAddIdElement(ptr, pk)
        }
        NostrDB.nativeFilterEndField(ptr)
        return this
    }

    /**
     * Add event IDs to filter.
     */
    fun ids(ids: List<ByteArray>): NdbFilter {
        NostrDB.nativeFilterStartField(ptr, NdbFilterField.IDS.value)
        ids.forEach { id ->
            NostrDB.nativeFilterAddIdElement(ptr, id)
        }
        NostrDB.nativeFilterEndField(ptr)
        return this
    }

    /**
     * Add kinds to filter.
     */
    fun kinds(kinds: List<Int>): NdbFilter {
        NostrDB.nativeFilterStartField(ptr, NdbFilterField.KINDS.value)
        kinds.forEach { kind ->
            NostrDB.nativeFilterAddIntElement(ptr, kind.toLong())
        }
        NostrDB.nativeFilterEndField(ptr)
        return this
    }

    /**
     * Set since timestamp.
     */
    fun since(timestamp: Long): NdbFilter {
        NostrDB.nativeFilterStartField(ptr, NdbFilterField.SINCE.value)
        NostrDB.nativeFilterAddIntElement(ptr, timestamp)
        NostrDB.nativeFilterEndField(ptr)
        return this
    }

    /**
     * Set until timestamp.
     */
    fun until(timestamp: Long): NdbFilter {
        NostrDB.nativeFilterStartField(ptr, NdbFilterField.UNTIL.value)
        NostrDB.nativeFilterAddIntElement(ptr, timestamp)
        NostrDB.nativeFilterEndField(ptr)
        return this
    }

    /**
     * Set limit.
     */
    fun limit(limit: Int): NdbFilter {
        NostrDB.nativeFilterStartField(ptr, NdbFilterField.LIMIT.value)
        NostrDB.nativeFilterAddIntElement(ptr, limit.toLong())
        NostrDB.nativeFilterEndField(ptr)
        return this
    }

    /**
     * Finalize the filter. Must be called before querying.
     */
    fun build(): NdbFilter {
        NostrDB.nativeFilterEnd(ptr)
        return this
    }

    override fun close() {
        NostrDB.nativeFilterDestroy(ptr)
    }
}

/**
 * Filter field types matching nostrdb's enum ndb_filter_fieldtype.
 * Values must match the C enum which starts at 1, not 0.
 */
enum class NdbFilterField(val value: Int) {
    IDS(1),
    AUTHORS(2),
    KINDS(3),
    TAGS(4),
    SINCE(5),
    UNTIL(6),
    LIMIT(7)
}
