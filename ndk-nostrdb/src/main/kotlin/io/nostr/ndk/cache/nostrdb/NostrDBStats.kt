package io.nostr.ndk.cache.nostrdb

/**
 * Statistics counts for a database or kind.
 */
data class NdbStatCounts(
    val count: Long,
    val keySize: Long,
    val valueSize: Long
) {
    val totalSize: Long get() = keySize + valueSize
}

/**
 * Statistics for a specific database index.
 */
data class NdbDatabaseStats(
    val name: String,
    val counts: NdbStatCounts
)

/**
 * Statistics for a specific event kind.
 */
data class NdbKindStats(
    val name: String,
    val counts: NdbStatCounts
)

/**
 * Complete NostrDB statistics snapshot.
 */
data class NdbStats(
    val databases: List<NdbDatabaseStats>,
    val commonKinds: List<NdbKindStats>,
    val otherKinds: NdbStatCounts
) {
    /**
     * Total number of events across all kinds.
     */
    val totalEvents: Long
        get() = commonKinds.sumOf { it.counts.count } + otherKinds.count

    /**
     * Total storage size across all databases.
     */
    val totalStorageSize: Long
        get() = databases.sumOf { it.counts.totalSize }

    /**
     * Get database stats by name.
     */
    fun database(name: String): NdbDatabaseStats? =
        databases.find { it.name.equals(name, ignoreCase = true) }

    /**
     * Get kind stats by name.
     */
    fun kind(name: String): NdbKindStats? =
        commonKinds.find { it.name.equals(name, ignoreCase = true) }

    companion object {
        /**
         * Parse raw stats array from JNI into NdbStats.
         * Array format: [db0.count, db0.keySize, db0.valueSize, db1.count, ...]
         */
        internal fun fromRawArray(
            rawStats: LongArray,
            dbCount: Int,
            kindCount: Int,
            getDbName: (Int) -> String?,
            getKindName: (Int) -> String?
        ): NdbStats {
            var idx = 0

            // Parse database stats
            val databases = (0 until dbCount).map { i ->
                val count = rawStats[idx++]
                val keySize = rawStats[idx++]
                val valueSize = rawStats[idx++]
                NdbDatabaseStats(
                    name = getDbName(i) ?: "DB $i",
                    counts = NdbStatCounts(count, keySize, valueSize)
                )
            }

            // Parse common kind stats
            val commonKinds = (0 until kindCount).map { i ->
                val count = rawStats[idx++]
                val keySize = rawStats[idx++]
                val valueSize = rawStats[idx++]
                NdbKindStats(
                    name = getKindName(i) ?: "Kind $i",
                    counts = NdbStatCounts(count, keySize, valueSize)
                )
            }

            // Parse other kinds stats
            val otherKinds = NdbStatCounts(
                count = rawStats[idx++],
                keySize = rawStats[idx++],
                valueSize = rawStats[idx]
            )

            return NdbStats(databases, commonKinds, otherKinds)
        }
    }
}
