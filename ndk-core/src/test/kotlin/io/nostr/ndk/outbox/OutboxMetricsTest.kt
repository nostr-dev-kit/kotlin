package io.nostr.ndk.outbox

import io.nostr.ndk.NDK
import io.nostr.ndk.cache.InMemoryCacheAdapter
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for OutboxMetrics and OutboxMetricsEvent observability system.
 */
class OutboxMetricsTest {

    private lateinit var metrics: OutboxMetrics

    @Before
    fun setup() {
        metrics = OutboxMetrics()
    }

    // ===========================================
    // Cache Metrics Tests
    // ===========================================

    @Test
    fun `cache hits are recorded correctly`() {
        metrics.recordCacheHit("pubkey1")
        metrics.recordCacheHit("pubkey2")
        metrics.recordCacheHit("pubkey1") // Same pubkey, should still count

        val snapshot = metrics.snapshot()

        assertEquals(3, snapshot.cacheHits)
    }

    @Test
    fun `cache misses are recorded correctly`() {
        metrics.recordCacheMiss()
        metrics.recordCacheMiss()

        val snapshot = metrics.snapshot()

        assertEquals(2, snapshot.cacheMisses)
    }

    @Test
    fun `cache hit rate is calculated correctly`() {
        metrics.recordCacheHit("p1")
        metrics.recordCacheHit("p2")
        metrics.recordCacheHit("p3")
        metrics.recordCacheMiss()

        val snapshot = metrics.snapshot()

        // 3 hits, 1 miss = 75% hit rate
        assertEquals(0.75f, snapshot.cacheHitRate, 0.001f)
    }

    @Test
    fun `cache hit rate is zero when no lookups`() {
        val snapshot = metrics.snapshot()

        assertEquals(0f, snapshot.cacheHitRate, 0.001f)
    }

    // ===========================================
    // Fetch Metrics Tests
    // ===========================================

    @Test
    fun `fetch metrics are recorded correctly`() {
        metrics.recordFetchStarted()
        metrics.recordFetchStarted()
        metrics.recordFetchSuccess(100)
        metrics.recordFetchTimeout()

        val snapshot = metrics.snapshot()

        assertEquals(2, snapshot.fetchesStarted)
        assertEquals(1, snapshot.fetchesSucceeded)
        assertEquals(1, snapshot.fetchesTimedOut)
    }

    @Test
    fun `fetch no relays is recorded`() {
        metrics.recordFetchNoRelays()
        metrics.recordFetchNoRelays()

        val snapshot = metrics.snapshot()

        assertEquals(2, snapshot.fetchesNoRelays)
    }

    @Test
    fun `average fetch duration is calculated correctly`() {
        metrics.recordFetchSuccess(100)
        metrics.recordFetchSuccess(200)
        metrics.recordFetchSuccess(300)

        val snapshot = metrics.snapshot()

        // Average of 100, 200, 300 = 200
        assertEquals(200, snapshot.avgFetchDurationMs)
    }

    @Test
    fun `fetch success rate is calculated correctly`() {
        metrics.recordFetchStarted()
        metrics.recordFetchStarted()
        metrics.recordFetchStarted()
        metrics.recordFetchStarted()
        metrics.recordFetchSuccess(100)
        metrics.recordFetchSuccess(100)
        metrics.recordFetchSuccess(100)
        metrics.recordFetchTimeout()

        val snapshot = metrics.snapshot()

        // 3 succeeded out of 4 started = 75%
        assertEquals(0.75f, snapshot.fetchSuccessRate, 0.001f)
    }

    // ===========================================
    // Subscription Metrics Tests
    // ===========================================

    @Test
    fun `subscription calculation metrics are recorded`() {
        metrics.recordSubscriptionCalculated(authorCount = 5, coveredCount = 3)
        metrics.recordSubscriptionCalculated(authorCount = 10, coveredCount = 8)

        val snapshot = metrics.snapshot()

        assertEquals(2, snapshot.subscriptionsCalculated)
        assertEquals(15, snapshot.totalAuthorsQueried) // 5 + 10
        assertEquals(11, snapshot.totalAuthorsCovered) // 3 + 8
    }

    @Test
    fun `author coverage rate is calculated correctly`() {
        metrics.recordSubscriptionCalculated(authorCount = 10, coveredCount = 7)

        val snapshot = metrics.snapshot()

        assertEquals(0.70f, snapshot.authorCoverageRate, 0.001f)
    }

    @Test
    fun `dynamic relay additions are recorded`() {
        metrics.recordDynamicRelayAdded()
        metrics.recordDynamicRelayAdded()
        metrics.recordDynamicRelayAdded()

        val snapshot = metrics.snapshot()

        assertEquals(3, snapshot.dynamicRelaysAdded)
    }

    // ===========================================
    // Relay Usage Tests
    // ===========================================

    @Test
    fun `relay usage is tracked`() {
        metrics.recordRelayUsed("wss://relay1.com")
        metrics.recordRelayUsed("wss://relay1.com")
        metrics.recordRelayUsed("wss://relay2.com")

        val snapshot = metrics.snapshot()

        assertEquals(2L, snapshot.relayUsageDistribution["wss://relay1.com"])
        assertEquals(1L, snapshot.relayUsageDistribution["wss://relay2.com"])
    }

    @Test
    fun `top relays returns sorted by usage`() {
        metrics.recordRelayUsed("wss://relay-low.com")
        metrics.recordRelayUsed("wss://relay-high.com")
        metrics.recordRelayUsed("wss://relay-high.com")
        metrics.recordRelayUsed("wss://relay-high.com")
        metrics.recordRelayUsed("wss://relay-medium.com")
        metrics.recordRelayUsed("wss://relay-medium.com")

        val snapshot = metrics.snapshot()
        val topRelays = snapshot.topRelays(2)

        assertEquals(2, topRelays.size)
        assertEquals("wss://relay-high.com", topRelays[0].first)
        assertEquals(3L, topRelays[0].second)
        assertEquals("wss://relay-medium.com", topRelays[1].first)
        assertEquals(2L, topRelays[1].second)
    }

    // ===========================================
    // Known Relay List Count Tests
    // ===========================================

    @Test
    fun `known relay lists are tracked via cache hits`() {
        metrics.recordCacheHit("pubkey1")
        metrics.recordCacheHit("pubkey2")
        metrics.recordCacheHit("pubkey1") // Duplicate, shouldn't increase count

        val snapshot = metrics.snapshot()

        assertEquals(2, snapshot.knownRelayListCount)
    }

    @Test
    fun `known relay lists tracked via recordRelayListKnown`() {
        metrics.recordRelayListKnown("pubkey1")
        metrics.recordRelayListKnown("pubkey2")
        metrics.recordRelayListKnown("pubkey3")

        val snapshot = metrics.snapshot()

        assertEquals(3, snapshot.knownRelayListCount)
    }

    // ===========================================
    // Reset Tests
    // ===========================================

    @Test
    fun `reset clears all metrics`() {
        // Record various metrics
        metrics.recordCacheHit("p1")
        metrics.recordCacheMiss()
        metrics.recordFetchStarted()
        metrics.recordFetchSuccess(100)
        metrics.recordSubscriptionCalculated(10, 5)
        metrics.recordRelayUsed("wss://relay.com")
        metrics.recordDynamicRelayAdded()

        // Reset
        metrics.reset()

        val snapshot = metrics.snapshot()

        assertEquals(0, snapshot.cacheHits)
        assertEquals(0, snapshot.cacheMisses)
        assertEquals(0, snapshot.fetchesStarted)
        assertEquals(0, snapshot.fetchesSucceeded)
        assertEquals(0, snapshot.subscriptionsCalculated)
        assertTrue(snapshot.relayUsageDistribution.isEmpty())
        assertEquals(0, snapshot.knownRelayListCount)
    }

    // ===========================================
    // Snapshot toString Tests
    // ===========================================

    @Test
    fun `snapshot toString contains key metrics`() {
        metrics.recordCacheHit("p1")
        metrics.recordCacheHit("p2")
        metrics.recordCacheMiss()
        metrics.recordFetchSuccess(150)

        val snapshot = metrics.snapshot()
        val str = snapshot.toString()

        assertTrue(str.contains("2 hits"))
        assertTrue(str.contains("1 miss"))
        assertTrue(str.contains("66%")) // 2/3 = 66%
    }

    // ===========================================
    // Integration Tests with NDK
    // ===========================================

    @Test
    fun `cache hit emits event through NDK`() = runTest {
        val cache = InMemoryCacheAdapter()
        val ndk = NDK(cacheAdapter = cache)

        // Store a relay list
        val event = NDKEvent(
            id = "event-1",
            pubkey = "user1",
            createdAt = System.currentTimeMillis() / 1000,
            kind = 10002,
            tags = listOf(NDKTag("r", listOf("wss://relay.com", "write"))),
            content = "",
            sig = "sig"
        )
        cache.store(event)

        // Collect events
        var cacheHitEvent: OutboxMetricsEvent? = null
        val job = launch {
            cacheHitEvent = withTimeoutOrNull(1000) {
                ndk.outboxEvents.first { it is OutboxMetricsEvent.RelayListCacheHit }
            }
        }

        yield()

        // Query relay list - should trigger cache hit
        ndk.outboxTracker.getRelayList("user1")

        yield()
        job.cancel()

        assertTrue(cacheHitEvent is OutboxMetricsEvent.RelayListCacheHit)
        assertEquals("user1", (cacheHitEvent as OutboxMetricsEvent.RelayListCacheHit).pubkey)

        // Verify counter was also incremented
        assertEquals(1, ndk.outboxMetrics.snapshot().cacheHits)

        ndk.close()
    }

    @Test
    fun `cache miss emits event through NDK`() = runTest {
        val cache = InMemoryCacheAdapter()
        val ndk = NDK(cacheAdapter = cache)

        // Collect events
        var cacheMissEvent: OutboxMetricsEvent? = null
        val job = launch {
            cacheMissEvent = withTimeoutOrNull(1000) {
                ndk.outboxEvents.first { it is OutboxMetricsEvent.RelayListCacheMiss }
            }
        }

        yield()

        // Query non-existent relay list - should trigger cache miss
        ndk.outboxTracker.getRelayList("unknown-user")

        yield()
        job.cancel()

        assertTrue(cacheMissEvent is OutboxMetricsEvent.RelayListCacheMiss)
        assertEquals("unknown-user", (cacheMissEvent as OutboxMetricsEvent.RelayListCacheMiss).pubkey)

        // Verify counter was also incremented
        assertEquals(1, ndk.outboxMetrics.snapshot().cacheMisses)

        ndk.close()
    }

    @Test
    fun `trackRelayList emits RelayListTracked event`() = runTest {
        val cache = InMemoryCacheAdapter()
        val ndk = NDK(cacheAdapter = cache)

        // Collect events
        var trackedEvent: OutboxMetricsEvent? = null
        val job = launch {
            trackedEvent = withTimeoutOrNull(1000) {
                ndk.outboxEvents.first { it is OutboxMetricsEvent.RelayListTracked }
            }
        }

        yield()

        // Track a relay list
        val event = NDKEvent(
            id = "event-2",
            pubkey = "tracked-user",
            createdAt = System.currentTimeMillis() / 1000,
            kind = 10002,
            tags = listOf(
                NDKTag("r", listOf("wss://read.com", "read")),
                NDKTag("r", listOf("wss://write1.com", "write")),
                NDKTag("r", listOf("wss://write2.com", "write"))
            ),
            content = "",
            sig = "sig"
        )
        ndk.outboxTracker.trackRelayList(event)

        yield()
        job.cancel()

        assertTrue(trackedEvent is OutboxMetricsEvent.RelayListTracked)
        val tracked = trackedEvent as OutboxMetricsEvent.RelayListTracked
        assertEquals("tracked-user", tracked.pubkey)
        assertEquals(1, tracked.readRelayCount)
        assertEquals(2, tracked.writeRelayCount)

        ndk.close()
    }
}
