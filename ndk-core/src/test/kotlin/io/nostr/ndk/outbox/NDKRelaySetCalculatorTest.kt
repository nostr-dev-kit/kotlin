package io.nostr.ndk.outbox

import io.nostr.ndk.NDK
import io.nostr.ndk.cache.InMemoryCacheAdapter
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.relay.NDKRelay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for NDKRelaySetCalculator which determines optimal relays for subscriptions.
 */
class NDKRelaySetCalculatorTest {

    private lateinit var cache: InMemoryCacheAdapter
    private lateinit var ndk: NDK
    private lateinit var calculator: NDKRelaySetCalculator

    @Before
    fun setup() {
        cache = InMemoryCacheAdapter()
        ndk = NDK(cacheAdapter = cache)
        calculator = NDKRelaySetCalculator(ndk)
    }

    // ===========================================
    // Basic Behavior Tests
    // ===========================================

    @Test
    fun `returns connected relays when outbox model disabled`() = runTest {
        ndk.enableOutboxModel = false
        ndk.pool.addRelay("wss://relay1.com", connect = false)

        val filter = NDKFilter(authors = setOf("author1"))
        val relays = calculator.calculateRelaysForFilters(listOf(filter))

        // Should use connected relays regardless of authors
        assertTrue(relays.map { it.url }.contains("wss://relay1.com"))
    }

    @Test
    fun `returns connected relays when filter has no authors`() = runTest {
        ndk.pool.addRelay("wss://relay1.com", connect = false)
        ndk.pool.addRelay("wss://relay2.com", connect = false)

        val filter = NDKFilter(kinds = setOf(1), limit = 10) // No authors
        val relays = calculator.calculateRelaysForFilters(listOf(filter))

        assertEquals(2, relays.size)
    }

    // ===========================================
    // Cached Relay List Tests
    // ===========================================

    @Test
    fun `returns author write relays when cached`() = runTest {
        // Cache author's relay list
        cache.store(createRelayListEvent(
            pubkey = "author1",
            tags = listOf(
                listOf("r", "wss://author1-write.com", "write"),
                listOf("r", "wss://author1-read.com", "read")
            )
        ))

        val filter = NDKFilter(authors = setOf("author1"))
        val relays = calculator.calculateRelaysForFilters(listOf(filter))

        val urls = relays.map { it.url }
        assertTrue("Should include author's write relay", urls.contains("wss://author1-write.com"))
        assertFalse("Should NOT include author's read relay", urls.contains("wss://author1-read.com"))
    }

    @Test
    fun `combines write relays for multiple authors`() = runTest {
        cache.store(createRelayListEvent(
            pubkey = "alice",
            tags = listOf(listOf("r", "wss://alice-write.com", "write"))
        ))
        cache.store(createRelayListEvent(
            pubkey = "bob",
            tags = listOf(listOf("r", "wss://bob-write.com", "write"))
        ))

        val filter = NDKFilter(authors = setOf("alice", "bob"))
        val relays = calculator.calculateRelaysForFilters(listOf(filter))

        val urls = relays.map { it.url }
        assertTrue(urls.contains("wss://alice-write.com"))
        assertTrue(urls.contains("wss://bob-write.com"))
    }

    // ===========================================
    // Relay Goal Per Author Tests
    // ===========================================

    @Test
    fun `respects relayGoalPerAuthor setting`() = runTest {
        ndk.relayGoalPerAuthor = 2

        cache.store(createRelayListEvent(
            pubkey = "author1",
            tags = listOf(
                listOf("r", "wss://relay1.com", "write"),
                listOf("r", "wss://relay2.com", "write"),
                listOf("r", "wss://relay3.com", "write")
            )
        ))

        val filter = NDKFilter(authors = setOf("author1"))
        val relays = calculator.calculateRelaysForFilters(listOf(filter))

        // Should select at most relayGoalPerAuthor relays per author
        assertTrue(relays.size <= 2)
    }

    @Test
    fun `uses all available relays when author has fewer than goal`() = runTest {
        ndk.relayGoalPerAuthor = 3

        cache.store(createRelayListEvent(
            pubkey = "author1",
            tags = listOf(listOf("r", "wss://only-relay.com", "write"))
        ))

        val filter = NDKFilter(authors = setOf("author1"))
        val relays = calculator.calculateRelaysForFilters(listOf(filter))

        assertEquals(1, relays.size)
        assertEquals("wss://only-relay.com", relays.first().url)
    }

    // ===========================================
    // Connected Relay Preference Tests
    // ===========================================

    @Test
    fun `prefers already connected relays`() = runTest {
        ndk.relayGoalPerAuthor = 1

        // Add a relay to the pool (simulates it being connected)
        ndk.pool.addRelay("wss://relay2.com", connect = false)

        cache.store(createRelayListEvent(
            pubkey = "author1",
            tags = listOf(
                listOf("r", "wss://relay1.com", "write"),
                listOf("r", "wss://relay2.com", "write"), // This one is in pool
                listOf("r", "wss://relay3.com", "write")
            )
        ))

        val filter = NDKFilter(authors = setOf("author1"))
        val relays = calculator.calculateRelaysForFilters(listOf(filter))

        // Should prefer the already-available relay
        val urls = relays.map { it.url }
        assertTrue("Should include the pool relay", urls.contains("wss://relay2.com"))
    }

    // ===========================================
    // Fallback Tests
    // ===========================================

    @Test
    fun `falls back to connected relays when no author relays known`() = runTest {
        ndk.pool.addRelay("wss://fallback1.com", connect = false)
        ndk.pool.addRelay("wss://fallback2.com", connect = false)

        // No cached relay list for author
        val filter = NDKFilter(authors = setOf("unknown-author"))
        val relays = calculator.calculateRelaysForFilters(listOf(filter))

        // Should return pool relays as fallback
        assertTrue(relays.isNotEmpty())
    }

    // ===========================================
    // Multiple Filters Tests
    // ===========================================

    @Test
    fun `combines authors from multiple filters`() = runTest {
        cache.store(createRelayListEvent(
            pubkey = "alice",
            tags = listOf(listOf("r", "wss://alice.com", "write"))
        ))
        cache.store(createRelayListEvent(
            pubkey = "bob",
            tags = listOf(listOf("r", "wss://bob.com", "write"))
        ))

        val filters = listOf(
            NDKFilter(authors = setOf("alice")),
            NDKFilter(authors = setOf("bob"))
        )
        val relays = calculator.calculateRelaysForFilters(filters)

        val urls = relays.map { it.url }
        assertTrue(urls.contains("wss://alice.com"))
        assertTrue(urls.contains("wss://bob.com"))
    }

    // ===========================================
    // Temporary Relay Tests
    // ===========================================

    @Test
    fun `adds temporary relays for author relays not in pool`() = runTest {
        cache.store(createRelayListEvent(
            pubkey = "author1",
            tags = listOf(listOf("r", "wss://new-relay.com", "write"))
        ))

        val filter = NDKFilter(authors = setOf("author1"))
        val relays = calculator.calculateRelaysForFilters(listOf(filter))

        // Should add the new relay to pool
        assertEquals(1, relays.size)
        assertEquals("wss://new-relay.com", relays.first().url)

        // Verify it was added to pool
        assertNotNull(ndk.pool.getRelay("wss://new-relay.com"))
    }

    // ===========================================
    // Helper Functions
    // ===========================================

    private fun createRelayListEvent(
        pubkey: String,
        tags: List<List<String>>,
        createdAt: Long = System.currentTimeMillis() / 1000
    ): NDKEvent {
        return NDKEvent(
            id = "event-$pubkey-${System.nanoTime()}",
            pubkey = pubkey,
            createdAt = createdAt,
            kind = 10002,
            tags = tags.map { NDKTag(it.first(), it.drop(1)) },
            content = "",
            sig = "sig"
        )
    }
}
