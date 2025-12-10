package io.nostr.ndk.cache.room

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.NDKTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCacheAdapterTest {

    private lateinit var database: NDKDatabase
    private lateinit var cacheAdapter: RoomCacheAdapter

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = NDKDatabase.createInMemory(context)
        cacheAdapter = RoomCacheAdapter(database)
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createTestEvent(
        id: String = "abc123",
        pubkey: String = "pubkey123",
        kind: Int = 1,
        createdAt: Long = System.currentTimeMillis() / 1000,
        content: String = "Hello, world!",
        tags: List<NDKTag> = emptyList(),
        sig: String? = "sig123"
    ) = NDKEvent(
        id = id,
        pubkey = pubkey,
        kind = kind,
        createdAt = createdAt,
        content = content,
        tags = tags,
        sig = sig
    )

    @Test
    fun store_and_retrieve_event_by_id() = runBlocking {
        val event = createTestEvent(id = "event1")
        cacheAdapter.store(event)

        val retrieved = cacheAdapter.getEvent("event1")
        assertNotNull(retrieved)
        assertEquals("event1", retrieved?.id)
        assertEquals("Hello, world!", retrieved?.content)
    }

    @Test
    fun store_does_not_store_ephemeral_events() = runBlocking {
        val ephemeralEvent = createTestEvent(id = "ephemeral1", kind = 20001)
        cacheAdapter.store(ephemeralEvent)

        val retrieved = cacheAdapter.getEvent("ephemeral1")
        assertNull(retrieved)
    }

    @Test
    fun store_replaces_older_replaceable_event() = runBlocking {
        val oldProfile = createTestEvent(
            id = "profile1",
            kind = 0,
            createdAt = 1000,
            content = "Old profile"
        )
        val newProfile = createTestEvent(
            id = "profile2",
            kind = 0,
            createdAt = 2000,
            content = "New profile"
        )

        cacheAdapter.store(oldProfile)
        cacheAdapter.store(newProfile)

        val profile = cacheAdapter.getProfile(oldProfile.pubkey)
        assertNotNull(profile)
        assertEquals("New profile", profile?.content)
        assertEquals("profile2", profile?.id)
    }

    @Test
    fun store_keeps_newer_replaceable_event() = runBlocking {
        val newProfile = createTestEvent(
            id = "profile1",
            kind = 0,
            createdAt = 2000,
            content = "Newer profile"
        )
        val oldProfile = createTestEvent(
            id = "profile2",
            kind = 0,
            createdAt = 1000,
            content = "Older profile"
        )

        cacheAdapter.store(newProfile)
        cacheAdapter.store(oldProfile) // Should be ignored

        val profile = cacheAdapter.getProfile(newProfile.pubkey)
        assertNotNull(profile)
        assertEquals("Newer profile", profile?.content)
        assertEquals("profile1", profile?.id)
    }

    @Test
    fun getProfile_returns_kind0_event() = runBlocking {
        val profile = createTestEvent(
            id = "profile1",
            kind = 0,
            content = """{"name":"Test User"}"""
        )
        cacheAdapter.store(profile)

        val retrieved = cacheAdapter.getProfile(profile.pubkey)
        assertNotNull(retrieved)
        assertEquals(0, retrieved?.kind)
    }

    @Test
    fun getContacts_returns_kind3_event() = runBlocking {
        val contacts = createTestEvent(
            id = "contacts1",
            kind = 3,
            tags = listOf(
                NDKTag("p", listOf("friend1")),
                NDKTag("p", listOf("friend2"))
            )
        )
        cacheAdapter.store(contacts)

        val retrieved = cacheAdapter.getContacts(contacts.pubkey)
        assertNotNull(retrieved)
        assertEquals(3, retrieved?.kind)
        assertEquals(2, retrieved?.tags?.size)
    }

    @Test
    fun getRelayList_returns_kind10002_event() = runBlocking {
        val relayList = createTestEvent(
            id = "relaylist1",
            kind = 10002,
            tags = listOf(
                NDKTag("r", listOf("wss://relay.damus.io", "read")),
                NDKTag("r", listOf("wss://nos.lol", "write"))
            )
        )
        cacheAdapter.store(relayList)

        val retrieved = cacheAdapter.getRelayList(relayList.pubkey)
        assertNotNull(retrieved)
        assertEquals(10002, retrieved?.kind)
    }

    @Test
    fun query_by_kind_returns_matching_events() = runBlocking {
        val textNote1 = createTestEvent(id = "note1", kind = 1, content = "Note 1")
        val textNote2 = createTestEvent(id = "note2", kind = 1, content = "Note 2")
        val reaction = createTestEvent(id = "reaction1", kind = 7, content = "+")

        cacheAdapter.store(textNote1)
        cacheAdapter.store(textNote2)
        cacheAdapter.store(reaction)

        val filter = NDKFilter(kinds = setOf(1))
        val results = cacheAdapter.query(filter).toList()

        assertEquals(2, results.size)
        assertTrue(results.all { it.kind == 1 })
    }

    @Test
    fun query_by_author_returns_matching_events() = runBlocking {
        val event1 = createTestEvent(id = "e1", pubkey = "author1")
        val event2 = createTestEvent(id = "e2", pubkey = "author1")
        val event3 = createTestEvent(id = "e3", pubkey = "author2")

        cacheAdapter.store(event1)
        cacheAdapter.store(event2)
        cacheAdapter.store(event3)

        val filter = NDKFilter(authors = setOf("author1"))
        val results = cacheAdapter.query(filter).toList()

        assertEquals(2, results.size)
        assertTrue(results.all { it.pubkey == "author1" })
    }

    @Test
    fun query_by_ids_returns_matching_events() = runBlocking {
        val event1 = createTestEvent(id = "id1")
        val event2 = createTestEvent(id = "id2")
        val event3 = createTestEvent(id = "id3")

        cacheAdapter.store(event1)
        cacheAdapter.store(event2)
        cacheAdapter.store(event3)

        val filter = NDKFilter(ids = setOf("id1", "id3"))
        val results = cacheAdapter.query(filter).toList()

        assertEquals(2, results.size)
        assertTrue(results.map { it.id }.containsAll(listOf("id1", "id3")))
    }

    @Test
    fun query_with_since_filter() = runBlocking {
        val oldEvent = createTestEvent(id = "old", createdAt = 1000)
        val newEvent = createTestEvent(id = "new", createdAt = 2000)

        cacheAdapter.store(oldEvent)
        cacheAdapter.store(newEvent)

        val filter = NDKFilter(since = 1500)
        val results = cacheAdapter.query(filter).toList()

        assertEquals(1, results.size)
        assertEquals("new", results.first().id)
    }

    @Test
    fun query_with_until_filter() = runBlocking {
        val oldEvent = createTestEvent(id = "old", createdAt = 1000)
        val newEvent = createTestEvent(id = "new", createdAt = 2000)

        cacheAdapter.store(oldEvent)
        cacheAdapter.store(newEvent)

        val filter = NDKFilter(until = 1500)
        val results = cacheAdapter.query(filter).toList()

        assertEquals(1, results.size)
        assertEquals("old", results.first().id)
    }

    @Test
    fun query_with_tag_filter() = runBlocking {
        val event1 = createTestEvent(
            id = "e1",
            tags = listOf(NDKTag("p", listOf("target1")))
        )
        val event2 = createTestEvent(
            id = "e2",
            tags = listOf(NDKTag("p", listOf("target2")))
        )

        cacheAdapter.store(event1)
        cacheAdapter.store(event2)

        val filter = NDKFilter(tags = mapOf("p" to setOf("target1")))
        val results = cacheAdapter.query(filter).toList()

        assertEquals(1, results.size)
        assertEquals("e1", results.first().id)
    }

    @Test
    fun delete_removes_event() = runBlocking {
        val event = createTestEvent(id = "todelete")
        cacheAdapter.store(event)

        assertNotNull(cacheAdapter.getEvent("todelete"))

        cacheAdapter.delete("todelete")

        assertNull(cacheAdapter.getEvent("todelete"))
    }

    @Test
    fun clear_removes_all_events() = runBlocking {
        cacheAdapter.store(createTestEvent(id = "e1"))
        cacheAdapter.store(createTestEvent(id = "e2"))
        cacheAdapter.store(createTestEvent(id = "e3"))

        assertTrue(cacheAdapter.eventCount() >= 3)

        cacheAdapter.clear()

        assertEquals(0, cacheAdapter.eventCount())
    }

    @Test
    fun parameterized_replaceable_events_use_d_tag() = runBlocking {
        val article1 = createTestEvent(
            id = "article1",
            kind = 30023,
            createdAt = 1000,
            content = "Version 1",
            tags = listOf(NDKTag("d", listOf("my-article")))
        )
        val article2 = createTestEvent(
            id = "article2",
            kind = 30023,
            createdAt = 2000,
            content = "Version 2",
            tags = listOf(NDKTag("d", listOf("my-article")))
        )

        cacheAdapter.store(article1)
        cacheAdapter.store(article2)

        // Should only have the newer version
        val filter = NDKFilter(kinds = setOf(30023))
        val results = cacheAdapter.query(filter).toList()

        assertEquals(1, results.size)
        assertEquals("Version 2", results.first().content)
    }
}
