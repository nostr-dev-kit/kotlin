package io.nostr.ndk.relay.nip11

import io.nostr.ndk.logging.NDKLogging
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "Nip11Cache"

/**
 * LRU cache for NIP-11 relay information with TTL support.
 *
 * Features:
 * - Automatic expiration based on TTL
 * - In-flight request deduplication (prevents duplicate concurrent requests)
 * - Thread-safe operations using ConcurrentHashMap
 * - LRU eviction when cache is full
 *
 * @param maxSize Maximum number of entries to cache (default: 1000)
 * @param ttlMs Time-to-live in milliseconds (default: 1 hour)
 * @param fetcher NIP-11 fetcher instance
 */
class Nip11Cache(
    private val maxSize: Int = 1000,
    private val ttlMs: Long = 3600_000L, // 1 hour default
    private val fetcher: Nip11Fetcher = Nip11Fetcher()
) {
    private data class CacheEntry(
        val info: Nip11RelayInformation,
        val timestamp: Long
    ) {
        fun isExpired(ttlMs: Long): Boolean {
            return System.currentTimeMillis() - timestamp > ttlMs
        }
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val inFlightRequests = ConcurrentHashMap<String, CompletableDeferred<Result<Nip11RelayInformation>>>()

    // Track access order for LRU
    private val accessOrder = ConcurrentHashMap<String, Long>()
    private var accessCounter = 0L

    /**
     * Get relay information, fetching if not cached or expired.
     *
     * @param relayUrl The relay URL (wss://...)
     * @return Result containing relay information or error
     */
    suspend fun get(relayUrl: String): Result<Nip11RelayInformation> {
        // Check cache first
        val cached = cache[relayUrl]
        if (cached != null && !cached.isExpired(ttlMs)) {
            NDKLogging.d(TAG, "Cache hit for $relayUrl")
            recordAccess(relayUrl)
            return Result.success(cached.info)
        }

        // Remove expired entry
        if (cached != null) {
            cache.remove(relayUrl)
            NDKLogging.d(TAG, "Removed expired cache entry for $relayUrl")
        }

        // Check for in-flight request
        val existingRequest = inFlightRequests[relayUrl]
        if (existingRequest != null) {
            NDKLogging.d(TAG, "Waiting for in-flight request for $relayUrl")
            return existingRequest.await()
        }

        // Create new in-flight request
        val deferred = CompletableDeferred<Result<Nip11RelayInformation>>()
        inFlightRequests[relayUrl] = deferred

        try {
            // Fetch from relay
            val result = fetcher.fetch(relayUrl)

            // Cache successful results
            result.onSuccess { info ->
                // Enforce LRU eviction if cache is full
                if (cache.size >= maxSize) {
                    evictLRU()
                }

                cache[relayUrl] = CacheEntry(info, System.currentTimeMillis())
                recordAccess(relayUrl)
                NDKLogging.d(TAG, "Cached NIP-11 info for $relayUrl")
            }

            deferred.complete(result)
            return result

        } catch (e: Exception) {
            val error = Result.failure<Nip11RelayInformation>(e)
            deferred.complete(error)
            return error
        } finally {
            inFlightRequests.remove(relayUrl)
        }
    }

    /**
     * Get from cache only, without fetching.
     *
     * @param relayUrl The relay URL
     * @return Cached info or null if not in cache or expired
     */
    fun getCached(relayUrl: String): Nip11RelayInformation? {
        val cached = cache[relayUrl]
        if (cached != null && !cached.isExpired(ttlMs)) {
            recordAccess(relayUrl)
            return cached.info
        }
        return null
    }

    /**
     * Invalidate a specific cache entry.
     */
    fun invalidate(relayUrl: String) {
        cache.remove(relayUrl)
        accessOrder.remove(relayUrl)
        NDKLogging.d(TAG, "Invalidated cache for $relayUrl")
    }

    /**
     * Clear all cache entries.
     */
    fun clear() {
        cache.clear()
        accessOrder.clear()
        NDKLogging.d(TAG, "Cleared all cache entries")
    }

    /**
     * Get current cache size.
     */
    fun size(): Int = cache.size

    /**
     * Record access to an entry for LRU tracking.
     */
    private fun recordAccess(relayUrl: String) {
        accessOrder[relayUrl] = accessCounter++
    }

    /**
     * Evict the least recently used entry.
     */
    private fun evictLRU() {
        // Find least recently used entry
        val lruEntry = accessOrder.entries.minByOrNull { it.value }
        if (lruEntry != null) {
            cache.remove(lruEntry.key)
            accessOrder.remove(lruEntry.key)
            NDKLogging.d(TAG, "Evicted LRU entry: ${lruEntry.key}")
        }
    }
}
