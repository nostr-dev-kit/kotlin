package io.nostr.ndk.models

import org.junit.Test
import org.junit.Assert.*

class NDKEventTest {

    @Test
    fun `event ID calculation matches NIP-01 spec`() {
        // Given: A test event with known values
        val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        val createdAt = 1731505809L
        val kind = 1
        val tags = listOf(
            NDKTag("t", listOf("nostr"))
        )
        val content = "Hello Nostr!"

        val event = NDKEvent(
            id = "",  // Will be calculated
            pubkey = pubkey,
            createdAt = createdAt,
            kind = kind,
            tags = tags,
            content = content,
            sig = null
        )

        // When: Calculate the event ID
        val calculatedId = event.calculateId()

        // Then: ID should be valid 64-character hex string
        assertEquals(64, calculatedId.length)
        assertTrue(calculatedId.matches(Regex("[0-9a-f]{64}")))

        // The calculated ID should match when recalculated
        val eventWithId = event.copy(id = calculatedId)
        assertTrue(eventWithId.isIdValid())
    }

    @Test
    fun `replaceable event deduplication key includes kind and pubkey`() {
        // Given: A replaceable event (kind 0)
        val pubkey = "abc123"
        val event = NDKEvent(
            id = "event1",
            pubkey = pubkey,
            createdAt = 1234567890,
            kind = 0,
            tags = emptyList(),
            content = "{}",
            sig = null
        )

        // When: Get deduplication key
        val dedupKey = event.deduplicationKey()

        // Then: Should include kind and pubkey
        assertEquals("0:abc123", dedupKey)
    }

    @Test
    fun `parameterized replaceable event deduplication key includes d tag`() {
        // Given: A parameterized replaceable event (kind 30000)
        val pubkey = "xyz789"
        val dTag = "my-article"
        val event = NDKEvent(
            id = "event2",
            pubkey = pubkey,
            createdAt = 1234567890,
            kind = 30000,
            tags = listOf(NDKTag("d", listOf(dTag))),
            content = "Article content",
            sig = null
        )

        // When: Get deduplication key
        val dedupKey = event.deduplicationKey()

        // Then: Should include kind, pubkey, and d tag
        assertEquals("30000:xyz789:my-article", dedupKey)
    }

    @Test
    fun `regular event deduplication key is event ID`() {
        // Given: A regular event (kind 1)
        val eventId = "unique123"
        val event = NDKEvent(
            id = eventId,
            pubkey = "pubkey",
            createdAt = 1234567890,
            kind = 1,
            tags = emptyList(),
            content = "Regular note",
            sig = null
        )

        // When: Get deduplication key
        val dedupKey = event.deduplicationKey()

        // Then: Should be the event ID
        assertEquals(eventId, dedupKey)
    }

    @Test
    fun `event classification - ephemeral`() {
        // Given: Event with kind in ephemeral range (20000-29999)
        val event = NDKEvent(
            id = "id",
            pubkey = "pubkey",
            createdAt = 1234567890,
            kind = 20001,
            tags = emptyList(),
            content = "",
            sig = null
        )

        // Then: Should be classified as ephemeral
        assertTrue(event.isEphemeral)
        assertFalse(event.isReplaceable)
        assertFalse(event.isParameterizedReplaceable)
    }

    @Test
    fun `event classification - replaceable`() {
        // Given: Event with replaceable kind (0, 3, or 10000-19999)
        val kinds = listOf(0, 3, 10000, 15000, 19999)

        kinds.forEach { kind ->
            val event = NDKEvent(
                id = "id",
                pubkey = "pubkey",
                createdAt = 1234567890,
                kind = kind,
                tags = emptyList(),
                content = "",
                sig = null
            )

            // Then: Should be classified as replaceable
            assertTrue("Kind $kind should be replaceable", event.isReplaceable)
            assertFalse(event.isEphemeral)
            assertFalse(event.isParameterizedReplaceable)
        }
    }

    @Test
    fun `event classification - parameterized replaceable`() {
        // Given: Event with kind in parameterized replaceable range (30000-39999)
        val event = NDKEvent(
            id = "id",
            pubkey = "pubkey",
            createdAt = 1234567890,
            kind = 30001,
            tags = emptyList(),
            content = "",
            sig = null
        )

        // Then: Should be classified as parameterized replaceable
        assertTrue(event.isParameterizedReplaceable)
        assertFalse(event.isEphemeral)
        assertFalse(event.isReplaceable)
    }

    @Test
    fun `tagsWithName returns all tags with specified name`() {
        // Given: Event with multiple tags
        val event = NDKEvent(
            id = "id",
            pubkey = "pubkey",
            createdAt = 1234567890,
            kind = 1,
            tags = listOf(
                NDKTag("e", listOf("event1")),
                NDKTag("p", listOf("pubkey1")),
                NDKTag("e", listOf("event2")),
                NDKTag("t", listOf("nostr"))
            ),
            content = "",
            sig = null
        )

        // When: Get tags with name "e"
        val eTags = event.tagsWithName("e")

        // Then: Should return both e tags
        assertEquals(2, eTags.size)
        assertEquals("event1", eTags[0].values[0])
        assertEquals("event2", eTags[1].values[0])
    }

    @Test
    fun `tagValue returns first value of first matching tag`() {
        // Given: Event with multiple tags
        val event = NDKEvent(
            id = "id",
            pubkey = "pubkey",
            createdAt = 1234567890,
            kind = 30001,
            tags = listOf(
                NDKTag("d", listOf("identifier")),
                NDKTag("title", listOf("My Article")),
                NDKTag("d", listOf("another-id"))  // Should not be returned
            ),
            content = "",
            sig = null
        )

        // When: Get tag value
        val dValue = event.tagValue("d")
        val titleValue = event.tagValue("title")
        val nonExistent = event.tagValue("missing")

        // Then: Should return first match or null
        assertEquals("identifier", dValue)
        assertEquals("My Article", titleValue)
        assertNull(nonExistent)
    }

    @Test
    fun `referencedEventIds extracts all event references`() {
        // Given: Event with e tags
        val event = NDKEvent(
            id = "id",
            pubkey = "pubkey",
            createdAt = 1234567890,
            kind = 1,
            tags = listOf(
                NDKTag("e", listOf("event1")),
                NDKTag("p", listOf("pubkey1")),
                NDKTag("e", listOf("event2", "relay-url"))
            ),
            content = "",
            sig = null
        )

        // When: Get referenced event IDs
        val eventIds = event.referencedEventIds()

        // Then: Should extract all e tag values
        assertEquals(2, eventIds.size)
        assertTrue(eventIds.contains("event1"))
        assertTrue(eventIds.contains("event2"))
    }

    @Test
    fun `referencedPubkeys extracts all pubkey references`() {
        // Given: Event with p tags
        val event = NDKEvent(
            id = "id",
            pubkey = "pubkey",
            createdAt = 1234567890,
            kind = 1,
            tags = listOf(
                NDKTag("p", listOf("pubkey1")),
                NDKTag("e", listOf("event1")),
                NDKTag("p", listOf("pubkey2", "relay-url", "petname"))
            ),
            content = "",
            sig = null
        )

        // When: Get referenced pubkeys
        val pubkeys = event.referencedPubkeys()

        // Then: Should extract all p tag values
        assertEquals(2, pubkeys.size)
        assertTrue(pubkeys.contains("pubkey1"))
        assertTrue(pubkeys.contains("pubkey2"))
    }

    @Test
    fun `JSON serialization round trip`() {
        // Given: A complete event
        val originalEvent = NDKEvent(
            id = "abc123",
            pubkey = "pubkey123",
            createdAt = 1234567890,
            kind = 1,
            tags = listOf(
                NDKTag("e", listOf("event1")),
                NDKTag("p", listOf("pubkey1"))
            ),
            content = "Hello World",
            sig = "signature123"
        )

        // When: Serialize and deserialize
        val json = originalEvent.toJson()
        val deserializedEvent = NDKEvent.fromJson(json)

        // Then: Should match original
        assertEquals(originalEvent.id, deserializedEvent.id)
        assertEquals(originalEvent.pubkey, deserializedEvent.pubkey)
        assertEquals(originalEvent.createdAt, deserializedEvent.createdAt)
        assertEquals(originalEvent.kind, deserializedEvent.kind)
        assertEquals(originalEvent.content, deserializedEvent.content)
        assertEquals(originalEvent.sig, deserializedEvent.sig)
        assertEquals(originalEvent.tags.size, deserializedEvent.tags.size)
    }

    @Test
    fun `NDKTag factory methods create correct tags`() {
        // When: Create tags using factory methods
        val eventTag = NDKTag.event("event123", "wss://relay.example.com", "reply")
        val pubkeyTag = NDKTag.pubkey("pubkey123", "wss://relay.example.com", "alice")
        val referenceTag = NDKTag.reference("https://example.com")
        val hashtagTag = NDKTag.hashtag("bitcoin")

        // Then: Should have correct structure
        assertEquals("e", eventTag.name)
        assertEquals("event123", eventTag.values[0])
        assertEquals("wss://relay.example.com", eventTag.values[1])
        assertEquals("reply", eventTag.values[2])

        assertEquals("p", pubkeyTag.name)
        assertEquals("pubkey123", pubkeyTag.values[0])

        assertEquals("r", referenceTag.name)
        assertEquals("https://example.com", referenceTag.values[0])

        assertEquals("t", hashtagTag.name)
        assertEquals("bitcoin", hashtagTag.values[0])
    }

    @Test
    fun `NDKTag get operator returns value at index`() {
        // Given: A tag with multiple values
        val tag = NDKTag("e", listOf("value0", "value1", "value2"))

        // When/Then: Access by index
        assertEquals("value0", tag[0])
        assertEquals("value1", tag[1])
        assertEquals("value2", tag[2])
        assertNull(tag[3])  // Out of bounds
        assertNull(tag[-1]) // Negative index
    }
}
