package io.nostr.ndk.outbox

import io.nostr.ndk.NDK
import io.nostr.ndk.cache.InMemoryCacheAdapter
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for NDKOutboxTracker which manages relay lists for users.
 */
class NDKOutboxTrackerTest {

    private lateinit var cache: InMemoryCacheAdapter
    private lateinit var ndk: NDK
    private lateinit var tracker: NDKOutboxTracker

    @Before
    fun setup() {
        cache = InMemoryCacheAdapter()
        ndk = NDK(cacheAdapter = cache)
        tracker = NDKOutboxTracker(ndk)
    }

    @Test
    fun `getRelayList returns null for unknown pubkey`() = runTest {
        val result = tracker.getRelayList("unknown-pubkey")

        assertNull(result)
    }

    @Test
    fun `getRelayList returns cached relay list`() = runTest {
        // Store a relay list in cache
        val event = createRelayListEvent(
            pubkey = "user1",
            tags = listOf(
                listOf("r", "wss://relay1.com", "read"),
                listOf("r", "wss://relay2.com", "write")
            )
        )
        cache.store(event)

        val result = tracker.getRelayList("user1")

        assertNotNull(result)
        assertEquals("user1", result!!.pubkey)
        assertTrue(result.readRelays.contains("wss://relay1.com"))
        assertTrue(result.writeRelays.contains("wss://relay2.com"))
    }

    @Test
    fun `getWriteRelaysForPubkey returns write relays`() = runTest {
        val event = createRelayListEvent(
            pubkey = "user1",
            tags = listOf(
                listOf("r", "wss://write1.com", "write"),
                listOf("r", "wss://write2.com", "write"),
                listOf("r", "wss://read.com", "read")
            )
        )
        cache.store(event)

        val writeRelays = tracker.getWriteRelaysForPubkey("user1")

        assertEquals(2, writeRelays.size)
        assertTrue(writeRelays.contains("wss://write1.com"))
        assertTrue(writeRelays.contains("wss://write2.com"))
    }

    @Test
    fun `getReadRelaysForPubkey returns read relays`() = runTest {
        val event = createRelayListEvent(
            pubkey = "user1",
            tags = listOf(
                listOf("r", "wss://read1.com", "read"),
                listOf("r", "wss://read2.com", "read"),
                listOf("r", "wss://write.com", "write")
            )
        )
        cache.store(event)

        val readRelays = tracker.getReadRelaysForPubkey("user1")

        assertEquals(2, readRelays.size)
        assertTrue(readRelays.contains("wss://read1.com"))
        assertTrue(readRelays.contains("wss://read2.com"))
    }

    @Test
    fun `getWriteRelaysForPubkey returns empty set for unknown pubkey`() = runTest {
        val writeRelays = tracker.getWriteRelaysForPubkey("unknown")

        assertTrue(writeRelays.isEmpty())
    }

    @Test
    fun `getWriteRelaysForPubkeys returns combined write relays for multiple users`() = runTest {
        // User 1 relay list
        cache.store(createRelayListEvent(
            pubkey = "user1",
            tags = listOf(
                listOf("r", "wss://user1-write.com", "write")
            )
        ))

        // User 2 relay list
        cache.store(createRelayListEvent(
            pubkey = "user2",
            tags = listOf(
                listOf("r", "wss://user2-write.com", "write")
            )
        ))

        val writeRelays = tracker.getWriteRelaysForPubkeys(setOf("user1", "user2"))

        assertEquals(2, writeRelays.size)
        assertTrue(writeRelays.contains("wss://user1-write.com"))
        assertTrue(writeRelays.contains("wss://user2-write.com"))
    }

    @Test
    fun `getWriteRelaysForPubkeys deduplicates shared relays`() = runTest {
        // Both users use same relay
        cache.store(createRelayListEvent(
            pubkey = "user1",
            tags = listOf(listOf("r", "wss://shared.com", "write"))
        ))

        cache.store(createRelayListEvent(
            pubkey = "user2",
            tags = listOf(listOf("r", "wss://shared.com", "write"))
        ))

        val writeRelays = tracker.getWriteRelaysForPubkeys(setOf("user1", "user2"))

        assertEquals(1, writeRelays.size)
        assertTrue(writeRelays.contains("wss://shared.com"))
    }

    @Test
    fun `trackRelayList stores event and updates tracker`() = runTest {
        val event = createRelayListEvent(
            pubkey = "newuser",
            tags = listOf(listOf("r", "wss://new-relay.com"))
        )

        tracker.trackRelayList(event)

        val relayList = tracker.getRelayList("newuser")
        assertNotNull(relayList)
        assertTrue(relayList!!.readRelays.contains("wss://new-relay.com"))
    }

    @Test
    fun `getRelaysToQueryForPubkey returns write relays (outbox model)`() = runTest {
        // In outbox model, to find events FROM a user, query their WRITE relays
        cache.store(createRelayListEvent(
            pubkey = "alice",
            tags = listOf(
                listOf("r", "wss://alice-outbox.com", "write"),
                listOf("r", "wss://alice-inbox.com", "read")
            )
        ))

        val relaysToQuery = tracker.getRelaysToQueryForPubkey("alice")

        assertEquals(1, relaysToQuery.size)
        assertTrue(relaysToQuery.contains("wss://alice-outbox.com"))
    }

    @Test
    fun `getRelaysToPublishForPubkey returns read relays (inbox model)`() = runTest {
        // To publish TO a user's timeline, publish to their READ relays (their inbox)
        cache.store(createRelayListEvent(
            pubkey = "bob",
            tags = listOf(
                listOf("r", "wss://bob-inbox.com", "read"),
                listOf("r", "wss://bob-outbox.com", "write")
            )
        ))

        val relaysToPublish = tracker.getRelaysToPublishForPubkey("bob")

        assertEquals(1, relaysToPublish.size)
        assertTrue(relaysToPublish.contains("wss://bob-inbox.com"))
    }

    // ===========================================
    // fetchRelayList Tests
    // ===========================================

    @Test
    fun `fetchRelayList returns cached relay list immediately`() = runTest {
        // Cache a relay list
        cache.store(createRelayListEvent(
            pubkey = "cached-user",
            tags = listOf(listOf("r", "wss://cached-relay.com", "write"))
        ))

        val result = tracker.fetchRelayList("cached-user")

        assertNotNull(result)
        assertEquals("cached-user", result!!.pubkey)
        assertTrue(result.writeRelays.contains("wss://cached-relay.com"))
    }

    @Test
    fun `fetchRelayList returns null for unknown pubkey with no relays`() = runTest {
        // No cache, no relays to query
        val result = tracker.fetchRelayList("unknown-pubkey")

        // Should return null since we can't find the relay list
        assertNull(result)
    }

    @Test
    fun `trackRelayList emits relay list update event`() = runTest {
        val receivedUpdates = mutableListOf<Pair<String, RelayList>>()

        // Collect updates in background
        val job = launch {
            tracker.onRelayListDiscovered.collect { update ->
                receivedUpdates.add(update)
            }
        }

        // Give time for collector to start
        yield()

        val event = createRelayListEvent(
            pubkey = "new-user",
            tags = listOf(listOf("r", "wss://new-relay.com", "write"))
        )
        tracker.trackRelayList(event)

        // Give time for emission
        yield()

        job.cancel()

        assertEquals(1, receivedUpdates.size)
        assertEquals("new-user", receivedUpdates[0].first)
        assertTrue(receivedUpdates[0].second.writeRelays.contains("wss://new-relay.com"))
    }

    // Helper
    private fun createRelayListEvent(
        pubkey: String,
        tags: List<List<String>>,
        createdAt: Long = System.currentTimeMillis() / 1000
    ): NDKEvent {
        return NDKEvent(
            id = "event-$pubkey",
            pubkey = pubkey,
            createdAt = createdAt,
            kind = 10002,
            tags = tags.map { NDKTag(it.first(), it.drop(1)) },
            content = "",
            sig = "sig"
        )
    }
}
