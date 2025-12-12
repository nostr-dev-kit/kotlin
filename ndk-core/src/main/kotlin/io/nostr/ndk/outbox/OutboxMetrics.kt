package io.nostr.ndk.outbox

import io.nostr.ndk.models.PublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Aggregated metrics for the outbox model.
 * Thread-safe counters that can be queried on demand via [snapshot].
 *
 * Example usage:
 * ```kotlin
 * val stats = ndk.outboxMetrics.snapshot()
 * println("Cache hit rate: ${stats.cacheHitRate * 100}%")
 * println("Top relays: ${stats.topRelays(5)}")
 * ```
 */
class OutboxMetrics {

    // Cache metrics
    private val _cacheHits = AtomicLong(0)
    private val _cacheMisses = AtomicLong(0)

    // Fetch metrics
    private val _fetchesStarted = AtomicLong(0)
    private val _fetchesSucceeded = AtomicLong(0)
    private val _fetchesTimedOut = AtomicLong(0)
    private val _fetchesNoRelays = AtomicLong(0)
    private val _totalFetchDurationMs = AtomicLong(0)

    // Subscription metrics
    private val _subscriptionsCalculated = AtomicLong(0)
    private val _dynamicRelaysAdded = AtomicLong(0)
    private val _totalAuthorsQueried = AtomicLong(0)
    private val _totalAuthorsCovered = AtomicLong(0)

    // Relay distribution tracking
    private val relayUsageCounts = ConcurrentHashMap<String, AtomicLong>()

    // Known relay lists (pubkeys we have cached)
    private val knownPubkeys = ConcurrentHashMap.newKeySet<PublicKey>()

    // ==========================================
    // Recording methods (called internally)
    // ==========================================

    internal fun recordCacheHit(pubkey: PublicKey) {
        _cacheHits.incrementAndGet()
        knownPubkeys.add(pubkey)
    }

    internal fun recordCacheMiss() {
        _cacheMisses.incrementAndGet()
    }

    internal fun recordFetchStarted() {
        _fetchesStarted.incrementAndGet()
    }

    internal fun recordFetchSuccess(durationMs: Long) {
        _fetchesSucceeded.incrementAndGet()
        _totalFetchDurationMs.addAndGet(durationMs)
    }

    internal fun recordFetchTimeout() {
        _fetchesTimedOut.incrementAndGet()
    }

    internal fun recordFetchNoRelays() {
        _fetchesNoRelays.incrementAndGet()
    }

    internal fun recordSubscriptionCalculated(authorCount: Int, coveredCount: Int) {
        _subscriptionsCalculated.incrementAndGet()
        _totalAuthorsQueried.addAndGet(authorCount.toLong())
        _totalAuthorsCovered.addAndGet(coveredCount.toLong())
    }

    internal fun recordRelayUsed(relayUrl: String) {
        relayUsageCounts.computeIfAbsent(relayUrl) { AtomicLong(0) }.incrementAndGet()
    }

    internal fun recordDynamicRelayAdded() {
        _dynamicRelaysAdded.incrementAndGet()
    }

    internal fun recordRelayListKnown(pubkey: PublicKey) {
        knownPubkeys.add(pubkey)
    }

    // ==========================================
    // Public API
    // ==========================================

    /**
     * Returns an immutable snapshot of current metrics.
     */
    fun snapshot(): OutboxMetricsSnapshot = OutboxMetricsSnapshot(
        cacheHits = _cacheHits.get(),
        cacheMisses = _cacheMisses.get(),
        fetchesStarted = _fetchesStarted.get(),
        fetchesSucceeded = _fetchesSucceeded.get(),
        fetchesTimedOut = _fetchesTimedOut.get(),
        fetchesNoRelays = _fetchesNoRelays.get(),
        avgFetchDurationMs = if (_fetchesSucceeded.get() > 0)
            _totalFetchDurationMs.get() / _fetchesSucceeded.get() else 0,
        subscriptionsCalculated = _subscriptionsCalculated.get(),
        dynamicRelaysAdded = _dynamicRelaysAdded.get(),
        totalAuthorsQueried = _totalAuthorsQueried.get(),
        totalAuthorsCovered = _totalAuthorsCovered.get(),
        knownRelayListCount = knownPubkeys.size,
        relayUsageDistribution = relayUsageCounts.mapValues { it.value.get() }
    )

    /**
     * Resets all metrics to zero.
     */
    fun reset() {
        _cacheHits.set(0)
        _cacheMisses.set(0)
        _fetchesStarted.set(0)
        _fetchesSucceeded.set(0)
        _fetchesTimedOut.set(0)
        _fetchesNoRelays.set(0)
        _totalFetchDurationMs.set(0)
        _subscriptionsCalculated.set(0)
        _dynamicRelaysAdded.set(0)
        _totalAuthorsQueried.set(0)
        _totalAuthorsCovered.set(0)
        relayUsageCounts.clear()
        knownPubkeys.clear()
    }
}

/**
 * Immutable snapshot of outbox metrics at a point in time.
 */
data class OutboxMetricsSnapshot(
    val cacheHits: Long,
    val cacheMisses: Long,
    val fetchesStarted: Long,
    val fetchesSucceeded: Long,
    val fetchesTimedOut: Long,
    val fetchesNoRelays: Long,
    val avgFetchDurationMs: Long,
    val subscriptionsCalculated: Long,
    val dynamicRelaysAdded: Long,
    val totalAuthorsQueried: Long,
    val totalAuthorsCovered: Long,
    val knownRelayListCount: Int,
    val relayUsageDistribution: Map<String, Long>
) {
    /**
     * Cache hit rate as a ratio (0.0 to 1.0).
     */
    val cacheHitRate: Float
        get() {
            val total = cacheHits + cacheMisses
            return if (total > 0) cacheHits.toFloat() / total else 0f
        }

    /**
     * Author coverage rate as a ratio (0.0 to 1.0).
     * How many queried authors had cached relay lists.
     */
    val authorCoverageRate: Float
        get() = if (totalAuthorsQueried > 0)
            totalAuthorsCovered.toFloat() / totalAuthorsQueried else 0f

    /**
     * Fetch success rate as a ratio (0.0 to 1.0).
     */
    val fetchSuccessRate: Float
        get() = if (fetchesStarted > 0)
            fetchesSucceeded.toFloat() / fetchesStarted else 0f

    /**
     * Returns the top N most used relays.
     */
    fun topRelays(n: Int = 10): List<Pair<String, Long>> {
        return relayUsageDistribution.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key to it.value }
    }

    override fun toString(): String {
        return """
            OutboxMetrics:
              Cache: $cacheHits hits, $cacheMisses misses (${(cacheHitRate * 100).toInt()}% hit rate)
              Fetches: $fetchesSucceeded succeeded, $fetchesTimedOut timed out, $fetchesNoRelays no relays
              Avg fetch: ${avgFetchDurationMs}ms
              Subscriptions: $subscriptionsCalculated calculated, $dynamicRelaysAdded dynamic relays added
              Authors: $totalAuthorsCovered/$totalAuthorsQueried covered (${(authorCoverageRate * 100).toInt()}%)
              Known relay lists: $knownRelayListCount
        """.trimIndent()
    }
}
