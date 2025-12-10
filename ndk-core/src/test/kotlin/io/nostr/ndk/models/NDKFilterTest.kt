package io.nostr.ndk.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NDKFilterTest {

    @Test
    fun `empty filter matches all events`() {
        val filter = NDKFilter()
        val event = createTestEvent(kind = 1)

        assertTrue(filter.matches(event))
    }

    @Test
    fun `filter matches event with matching kind`() {
        val filter = NDKFilter(kinds = setOf(1, 2))
        val event = createTestEvent(kind = 1)

        assertTrue(filter.matches(event))
    }

    @Test
    fun `filter rejects event with non-matching kind`() {
        val filter = NDKFilter(kinds = setOf(1, 2))
        val event = createTestEvent(kind = 3)

        assertFalse(filter.matches(event))
    }

    @Test
    fun `filter matches event with matching author`() {
        val filter = NDKFilter(authors = setOf("author1", "author2"))
        val event = createTestEvent(pubkey = "author1")

        assertTrue(filter.matches(event))
    }

    @Test
    fun `filter rejects event with non-matching author`() {
        val filter = NDKFilter(authors = setOf("author1", "author2"))
        val event = createTestEvent(pubkey = "author3")

        assertFalse(filter.matches(event))
    }

    @Test
    fun `filter matches event with matching id`() {
        val filter = NDKFilter(ids = setOf("id1", "id2"))
        val event = createTestEvent(id = "id1")

        assertTrue(filter.matches(event))
    }

    @Test
    fun `filter rejects event with non-matching id`() {
        val filter = NDKFilter(ids = setOf("id1", "id2"))
        val event = createTestEvent(id = "id3")

        assertFalse(filter.matches(event))
    }

    @Test
    fun `filter matches event within since range`() {
        val filter = NDKFilter(since = 1000L)
        val event = createTestEvent(createdAt = 2000L)

        assertTrue(filter.matches(event))
    }

    @Test
    fun `filter rejects event before since`() {
        val filter = NDKFilter(since = 2000L)
        val event = createTestEvent(createdAt = 1000L)

        assertFalse(filter.matches(event))
    }

    @Test
    fun `filter matches event within until range`() {
        val filter = NDKFilter(until = 2000L)
        val event = createTestEvent(createdAt = 1000L)

        assertTrue(filter.matches(event))
    }

    @Test
    fun `filter rejects event after until`() {
        val filter = NDKFilter(until = 1000L)
        val event = createTestEvent(createdAt = 2000L)

        assertFalse(filter.matches(event))
    }

    @Test
    fun `filter matches event within since and until range`() {
        val filter = NDKFilter(since = 1000L, until = 3000L)
        val event = createTestEvent(createdAt = 2000L)

        assertTrue(filter.matches(event))
    }

    @Test
    fun `filter matches event with matching e tag`() {
        val filter = NDKFilter(tags = mapOf("e" to setOf("event123")))
        val event = createTestEvent(tags = listOf(NDKTag.event("event123")))

        assertTrue(filter.matches(event))
    }

    @Test
    fun `filter rejects event without matching e tag`() {
        val filter = NDKFilter(tags = mapOf("e" to setOf("event123")))
        val event = createTestEvent(tags = listOf(NDKTag.event("event456")))

        assertFalse(filter.matches(event))
    }

    @Test
    fun `filter matches event with matching p tag`() {
        val filter = NDKFilter(tags = mapOf("p" to setOf("pubkey123")))
        val event = createTestEvent(tags = listOf(NDKTag.pubkey("pubkey123")))

        assertTrue(filter.matches(event))
    }

    @Test
    fun `filter matches event with matching t tag`() {
        val filter = NDKFilter(tags = mapOf("t" to setOf("nostr")))
        val event = createTestEvent(tags = listOf(NDKTag.hashtag("nostr")))

        assertTrue(filter.matches(event))
    }

    @Test
    fun `filter matches event with multiple matching tags`() {
        val filter = NDKFilter(
            tags = mapOf(
                "e" to setOf("event123"),
                "p" to setOf("pubkey456")
            )
        )
        val event = createTestEvent(
            tags = listOf(
                NDKTag.event("event123"),
                NDKTag.pubkey("pubkey456")
            )
        )

        assertTrue(filter.matches(event))
    }

    @Test
    fun `filter rejects event missing one required tag`() {
        val filter = NDKFilter(
            tags = mapOf(
                "e" to setOf("event123"),
                "p" to setOf("pubkey456")
            )
        )
        val event = createTestEvent(
            tags = listOf(
                NDKTag.event("event123")
                // Missing p tag
            )
        )

        assertFalse(filter.matches(event))
    }

    @Test
    fun `filter with limit does not affect matching`() {
        val filter = NDKFilter(kinds = setOf(1), limit = 10)
        val event = createTestEvent(kind = 1)

        assertTrue(filter.matches(event))
    }

    @Test
    fun `filter with search does not affect matching`() {
        val filter = NDKFilter(kinds = setOf(1), search = "hello")
        val event = createTestEvent(kind = 1)

        // search is relay-side only, doesn't affect client-side matching
        assertTrue(filter.matches(event))
    }

    @Test
    fun `filter combines all criteria with AND logic`() {
        val filter = NDKFilter(
            kinds = setOf(1),
            authors = setOf("author1"),
            since = 1000L,
            until = 3000L
        )

        val matchingEvent = createTestEvent(
            kind = 1,
            pubkey = "author1",
            createdAt = 2000L
        )
        assertTrue(filter.matches(matchingEvent))

        val wrongKind = createTestEvent(
            kind = 2,
            pubkey = "author1",
            createdAt = 2000L
        )
        assertFalse(filter.matches(wrongKind))

        val wrongAuthor = createTestEvent(
            kind = 1,
            pubkey = "author2",
            createdAt = 2000L
        )
        assertFalse(filter.matches(wrongAuthor))

        val tooEarly = createTestEvent(
            kind = 1,
            pubkey = "author1",
            createdAt = 500L
        )
        assertFalse(filter.matches(tooEarly))

        val tooLate = createTestEvent(
            kind = 1,
            pubkey = "author1",
            createdAt = 4000L
        )
        assertFalse(filter.matches(tooLate))
    }

    @Test
    fun `toJson produces valid NIP-01 filter JSON`() {
        val filter = NDKFilter(
            ids = setOf("id1", "id2"),
            authors = setOf("author1"),
            kinds = setOf(1, 2),
            since = 1000L,
            until = 2000L,
            limit = 10,
            tags = mapOf(
                "e" to setOf("event1", "event2"),
                "p" to setOf("pubkey1")
            )
        )

        val json = filter.toJson()

        // Should be valid JSON
        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))

        // Should contain all fields
        assertTrue(json.contains("\"ids\""))
        assertTrue(json.contains("\"authors\""))
        assertTrue(json.contains("\"kinds\""))
        assertTrue(json.contains("\"since\""))
        assertTrue(json.contains("\"until\""))
        assertTrue(json.contains("\"limit\""))
        assertTrue(json.contains("\"#e\""))
        assertTrue(json.contains("\"#p\""))
    }

    @Test
    fun `toJson omits null fields`() {
        val filter = NDKFilter(kinds = setOf(1))

        val json = filter.toJson()

        // Should not contain null fields
        assertFalse(json.contains("\"ids\""))
        assertFalse(json.contains("\"authors\""))
        assertFalse(json.contains("\"since\""))
        assertFalse(json.contains("\"until\""))
        assertFalse(json.contains("\"limit\""))
        assertFalse(json.contains("\"search\""))
    }

    @Test
    fun `fromJson parses filter JSON correctly`() {
        val json = """
            {
                "ids": ["id1", "id2"],
                "authors": ["author1"],
                "kinds": [1, 2],
                "since": 1000,
                "until": 2000,
                "limit": 10,
                "#e": ["event1", "event2"],
                "#p": ["pubkey1"]
            }
        """.trimIndent()

        val filter = NDKFilter.fromJson(json)

        assertEquals(setOf("id1", "id2"), filter.ids)
        assertEquals(setOf("author1"), filter.authors)
        assertEquals(setOf(1, 2), filter.kinds)
        assertEquals(1000L, filter.since)
        assertEquals(2000L, filter.until)
        assertEquals(10, filter.limit)
        assertEquals(setOf("event1", "event2"), filter.tags["e"])
        assertEquals(setOf("pubkey1"), filter.tags["p"])
    }

    @Test
    fun `fromJson handles missing fields`() {
        val json = """{"kinds": [1]}"""

        val filter = NDKFilter.fromJson(json)

        assertEquals(setOf(1), filter.kinds)
        assertEquals(null, filter.ids)
        assertEquals(null, filter.authors)
        assertEquals(null, filter.since)
        assertEquals(null, filter.until)
        assertEquals(null, filter.limit)
        assertEquals(emptyMap<String, Set<String>>(), filter.tags)
    }

    @Test
    fun `fingerprint produces deterministic string`() {
        val filter1 = NDKFilter(
            kinds = setOf(1, 2),
            authors = setOf("author1", "author2"),
            tags = mapOf("e" to setOf("event1"))
        )

        val filter2 = NDKFilter(
            kinds = setOf(1, 2),
            authors = setOf("author1", "author2"),
            tags = mapOf("e" to setOf("event1"))
        )

        assertEquals(filter1.fingerprint(), filter2.fingerprint())
    }

    @Test
    fun `fingerprint is order-independent for sets`() {
        val filter1 = NDKFilter(
            kinds = setOf(1, 2, 3),
            authors = setOf("a", "b", "c")
        )

        val filter2 = NDKFilter(
            kinds = setOf(3, 1, 2),
            authors = setOf("c", "a", "b")
        )

        assertEquals(filter1.fingerprint(), filter2.fingerprint())
    }

    @Test
    fun `fingerprint excludes temporal constraints`() {
        val filter1 = NDKFilter(
            kinds = setOf(1),
            since = 1000L,
            until = 2000L,
            limit = 10
        )

        val filter2 = NDKFilter(
            kinds = setOf(1),
            since = 3000L,
            until = 4000L,
            limit = 20
        )

        // Fingerprints should match because temporal constraints are excluded
        assertEquals(filter1.fingerprint(), filter2.fingerprint())
    }

    @Test
    fun `fingerprint distinguishes different filters`() {
        val filter1 = NDKFilter(kinds = setOf(1))
        val filter2 = NDKFilter(kinds = setOf(2))

        assertNotEquals(filter1.fingerprint(), filter2.fingerprint())
    }

    @Test
    fun `withoutTemporalConstraints removes since, until, and limit`() {
        val filter = NDKFilter(
            kinds = setOf(1),
            authors = setOf("author1"),
            since = 1000L,
            until = 2000L,
            limit = 10
        )

        val unconstrained = filter.withoutTemporalConstraints()

        assertEquals(setOf(1), unconstrained.kinds)
        assertEquals(setOf("author1"), unconstrained.authors)
        assertEquals(null, unconstrained.since)
        assertEquals(null, unconstrained.until)
        assertEquals(null, unconstrained.limit)
    }

    @Test
    fun `withoutTemporalConstraints preserves other fields`() {
        val filter = NDKFilter(
            ids = setOf("id1"),
            authors = setOf("author1"),
            kinds = setOf(1, 2),
            since = 1000L,
            until = 2000L,
            limit = 10,
            search = "hello",
            tags = mapOf("e" to setOf("event1"))
        )

        val unconstrained = filter.withoutTemporalConstraints()

        assertEquals(filter.ids, unconstrained.ids)
        assertEquals(filter.authors, unconstrained.authors)
        assertEquals(filter.kinds, unconstrained.kinds)
        assertEquals(filter.search, unconstrained.search)
        assertEquals(filter.tags, unconstrained.tags)
    }

    // Helper function to create test events
    private fun createTestEvent(
        id: String = "test_id",
        pubkey: String = "test_pubkey",
        createdAt: Long = 1234567890L,
        kind: Int = 1,
        tags: List<NDKTag> = emptyList(),
        content: String = "test content",
        sig: String? = "test_sig"
    ): NDKEvent {
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
}
