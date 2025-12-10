package io.nostr.ndk.cache

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for NDKCacheAdapter interface and implementations.
 * These tests verify that cache adapters correctly store, query, and manage events.
 */
class NDKCacheAdapterTest {

    private lateinit var cache: NDKCacheAdapter

    @Before
    fun setup() {
        cache = InMemoryCacheAdapter()
    }

    // Helper to create test events
    private fun createTestEvent(
        id: String,
        kind: Int = 1,
        pubkey: String = "pubkey123",
        content: String = "test content",
        createdAt: Long = System.currentTimeMillis() / 1000,
        tags: List<List<String>> = emptyList()
    ): NDKEvent {
        return NDKEvent(
            id = id,
            pubkey = pubkey,
            createdAt = createdAt,
            kind = kind,
            tags = tags.map { io.nostr.ndk.models.NDKTag(it.first(), it.drop(1)) },
            content = content,
            sig = "sig123"
        )
    }

    // ============ Store Tests ============

    @Test
    fun `store saves event and can be retrieved`() = runTest {
        val event = createTestEvent("event1")

        cache.store(event)

        val filter = NDKFilter(ids = setOf("event1"))
        val results = cache.query(filter).toList()

        assertEquals(1, results.size)
        assertEquals(event, results[0])
    }

    @Test
    fun `store multiple events`() = runTest {
        val event1 = createTestEvent("event1")
        val event2 = createTestEvent("event2")
        val event3 = createTestEvent("event3")

        cache.store(event1)
        cache.store(event2)
        cache.store(event3)

        val filter = NDKFilter(ids = setOf("event1", "event2", "event3"))
        val results = cache.query(filter).toList()

        assertEquals(3, results.size)
    }

    @Test
    fun `store replaces replaceable event (same kind and pubkey)`() = runTest {
        // Kind 0 (profile) is replaceable
        val oldEvent = createTestEvent("old", kind = 0, pubkey = "user1", createdAt = 1000)
        val newEvent = createTestEvent("new", kind = 0, pubkey = "user1", createdAt = 2000)

        cache.store(oldEvent)
        cache.store(newEvent)

        val filter = NDKFilter(kinds = setOf(0), authors = setOf("user1"))
        val results = cache.query(filter).toList()

        assertEquals(1, results.size)
        assertEquals("new", results[0].id)
    }

    @Test
    fun `store replaces parameterized replaceable event (same kind, pubkey, d-tag)`() = runTest {
        // Kind 30000+ is parameterized replaceable
        val oldEvent = createTestEvent(
            id = "old",
            kind = 30023, // Article
            pubkey = "user1",
            createdAt = 1000,
            tags = listOf(listOf("d", "my-article"))
        )
        val newEvent = createTestEvent(
            id = "new",
            kind = 30023,
            pubkey = "user1",
            createdAt = 2000,
            tags = listOf(listOf("d", "my-article"))
        )

        cache.store(oldEvent)
        cache.store(newEvent)

        val filter = NDKFilter(kinds = setOf(30023), authors = setOf("user1"))
        val results = cache.query(filter).toList()

        assertEquals(1, results.size)
        assertEquals("new", results[0].id)
    }

    @Test
    fun `store does not cache ephemeral events`() = runTest {
        // Kind 20000-29999 are ephemeral
        val ephemeralEvent = createTestEvent("ephemeral", kind = 20001)

        cache.store(ephemeralEvent)

        val filter = NDKFilter(ids = setOf("ephemeral"))
        val results = cache.query(filter).toList()

        assertEquals(0, results.size)
    }

    @Test
    fun `store ignores older replaceable event`() = runTest {
        val newEvent = createTestEvent("new", kind = 0, pubkey = "user1", createdAt = 2000)
        val oldEvent = createTestEvent("old", kind = 0, pubkey = "user1", createdAt = 1000)

        cache.store(newEvent)
        cache.store(oldEvent) // This should be ignored

        val filter = NDKFilter(kinds = setOf(0), authors = setOf("user1"))
        val results = cache.query(filter).toList()

        assertEquals(1, results.size)
        assertEquals("new", results[0].id)
    }

    // ============ Query Tests ============

    @Test
    fun `query by ids`() = runTest {
        cache.store(createTestEvent("a"))
        cache.store(createTestEvent("b"))
        cache.store(createTestEvent("c"))

        val results = cache.query(NDKFilter(ids = setOf("a", "c"))).toList()

        assertEquals(2, results.size)
        assertTrue(results.any { it.id == "a" })
        assertTrue(results.any { it.id == "c" })
    }

    @Test
    fun `query by authors`() = runTest {
        cache.store(createTestEvent("e1", pubkey = "alice"))
        cache.store(createTestEvent("e2", pubkey = "bob"))
        cache.store(createTestEvent("e3", pubkey = "alice"))

        val results = cache.query(NDKFilter(authors = setOf("alice"))).toList()

        assertEquals(2, results.size)
        assertTrue(results.all { it.pubkey == "alice" })
    }

    @Test
    fun `query by kinds`() = runTest {
        cache.store(createTestEvent("e1", kind = 1))
        cache.store(createTestEvent("e2", kind = 3))
        cache.store(createTestEvent("e3", kind = 1))

        val results = cache.query(NDKFilter(kinds = setOf(1))).toList()

        assertEquals(2, results.size)
        assertTrue(results.all { it.kind == 1 })
    }

    @Test
    fun `query with since filter`() = runTest {
        cache.store(createTestEvent("old", createdAt = 1000))
        cache.store(createTestEvent("new", createdAt = 3000))

        val results = cache.query(NDKFilter(since = 2000)).toList()

        assertEquals(1, results.size)
        assertEquals("new", results[0].id)
    }

    @Test
    fun `query with until filter`() = runTest {
        cache.store(createTestEvent("old", createdAt = 1000))
        cache.store(createTestEvent("new", createdAt = 3000))

        val results = cache.query(NDKFilter(until = 2000)).toList()

        assertEquals(1, results.size)
        assertEquals("old", results[0].id)
    }

    @Test
    fun `query with limit`() = runTest {
        cache.store(createTestEvent("e1", createdAt = 1000))
        cache.store(createTestEvent("e2", createdAt = 2000))
        cache.store(createTestEvent("e3", createdAt = 3000))

        val results = cache.query(NDKFilter(limit = 2)).toList()

        assertEquals(2, results.size)
        // Should return newest first
        assertEquals("e3", results[0].id)
        assertEquals("e2", results[1].id)
    }

    @Test
    fun `query by tag`() = runTest {
        cache.store(createTestEvent("e1", tags = listOf(listOf("p", "user1"))))
        cache.store(createTestEvent("e2", tags = listOf(listOf("p", "user2"))))
        cache.store(createTestEvent("e3", tags = listOf(listOf("e", "event1"))))

        val results = cache.query(NDKFilter(tags = mapOf("p" to setOf("user1")))).toList()

        assertEquals(1, results.size)
        assertEquals("e1", results[0].id)
    }

    @Test
    fun `query with combined filters uses AND logic`() = runTest {
        cache.store(createTestEvent("e1", kind = 1, pubkey = "alice"))
        cache.store(createTestEvent("e2", kind = 1, pubkey = "bob"))
        cache.store(createTestEvent("e3", kind = 3, pubkey = "alice"))

        val results = cache.query(NDKFilter(
            kinds = setOf(1),
            authors = setOf("alice")
        )).toList()

        assertEquals(1, results.size)
        assertEquals("e1", results[0].id)
    }

    @Test
    fun `query returns empty list when no matches`() = runTest {
        cache.store(createTestEvent("e1"))

        val results = cache.query(NDKFilter(ids = setOf("nonexistent"))).toList()

        assertTrue(results.isEmpty())
    }

    @Test
    fun `query results ordered by createdAt descending`() = runTest {
        cache.store(createTestEvent("e2", createdAt = 2000))
        cache.store(createTestEvent("e1", createdAt = 1000))
        cache.store(createTestEvent("e3", createdAt = 3000))

        val results = cache.query(NDKFilter()).toList()

        assertEquals("e3", results[0].id)
        assertEquals("e2", results[1].id)
        assertEquals("e1", results[2].id)
    }

    // ============ Delete Tests ============

    @Test
    fun `delete removes event by id`() = runTest {
        cache.store(createTestEvent("e1"))
        cache.store(createTestEvent("e2"))

        cache.delete("e1")

        val results = cache.query(NDKFilter()).toList()
        assertEquals(1, results.size)
        assertEquals("e2", results[0].id)
    }

    @Test
    fun `delete nonexistent event does not throw`() = runTest {
        cache.delete("nonexistent")
        // Should not throw
    }

    // ============ Clear Tests ============

    @Test
    fun `clear removes all events`() = runTest {
        cache.store(createTestEvent("e1"))
        cache.store(createTestEvent("e2"))
        cache.store(createTestEvent("e3"))

        cache.clear()

        val results = cache.query(NDKFilter()).toList()
        assertTrue(results.isEmpty())
    }

    // ============ Get Event Tests ============

    @Test
    fun `getEvent returns event by id`() = runTest {
        val event = createTestEvent("target")
        cache.store(event)

        val result = cache.getEvent("target")

        assertEquals(event, result)
    }

    @Test
    fun `getEvent returns null for nonexistent id`() = runTest {
        val result = cache.getEvent("nonexistent")

        assertNull(result)
    }

    // ============ Profile-specific Tests ============

    @Test
    fun `getProfile returns kind 0 event for pubkey`() = runTest {
        val profile = createTestEvent("profile1", kind = 0, pubkey = "user1", content = """{"name":"Alice"}""")
        cache.store(profile)

        val result = cache.getProfile("user1")

        assertEquals(profile, result)
    }

    @Test
    fun `getProfile returns null when no profile exists`() = runTest {
        val result = cache.getProfile("unknown")

        assertNull(result)
    }

    // ============ Contacts-specific Tests ============

    @Test
    fun `getContacts returns kind 3 event for pubkey`() = runTest {
        val contacts = createTestEvent("contacts1", kind = 3, pubkey = "user1")
        cache.store(contacts)

        val result = cache.getContacts("user1")

        assertEquals(contacts, result)
    }

    // ============ Relay List Tests ============

    @Test
    fun `getRelayList returns kind 10002 event for pubkey`() = runTest {
        val relayList = createTestEvent("relay1", kind = 10002, pubkey = "user1")
        cache.store(relayList)

        val result = cache.getRelayList("user1")

        assertEquals(relayList, result)
    }
}
