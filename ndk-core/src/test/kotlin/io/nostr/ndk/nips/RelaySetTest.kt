package io.nostr.ndk.nips

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for NIP-51 relay set parsing (kind 30002).
 */
class RelaySetTest {

    @Test
    fun `parse relay set with all fields`() {
        val event = createRelaySetEvent(
            identifier = "tech-relays",
            title = "Tech Relays",
            description = "Relays focused on technology content",
            image = "https://example.com/tech.png",
            relays = listOf(
                "wss://relay1.com",
                "wss://relay2.com",
                "wss://relay3.com"
            )
        )

        val relaySet = RelaySet.fromEvent(event)

        assertEquals("tech-relays", relaySet.identifier)
        assertEquals("Tech Relays", relaySet.title)
        assertEquals("Relays focused on technology content", relaySet.description)
        assertEquals("https://example.com/tech.png", relaySet.image)
        assertEquals(3, relaySet.relays.size)
        assertTrue(relaySet.relays.contains("wss://relay1.com"))
        assertTrue(relaySet.relays.contains("wss://relay2.com"))
        assertTrue(relaySet.relays.contains("wss://relay3.com"))
    }

    @Test
    fun `parse relay set with minimal fields`() {
        val event = createRelaySetEvent(
            identifier = "minimal",
            relays = listOf("wss://relay.com")
        )

        val relaySet = RelaySet.fromEvent(event)

        assertEquals("minimal", relaySet.identifier)
        assertNull(relaySet.title)
        assertNull(relaySet.description)
        assertNull(relaySet.image)
        assertEquals(1, relaySet.relays.size)
        assertEquals("wss://relay.com", relaySet.relays.first())
    }

    @Test
    fun `parse relay set with empty identifier`() {
        val event = createRelaySetEvent(
            identifier = "",
            relays = listOf("wss://relay.com")
        )

        val relaySet = RelaySet.fromEvent(event)

        assertEquals("", relaySet.identifier)
    }

    @Test
    fun `parse relay set with no relays`() {
        val event = createRelaySetEvent(
            identifier = "empty",
            relays = emptyList()
        )

        val relaySet = RelaySet.fromEvent(event)

        assertTrue(relaySet.relays.isEmpty())
    }

    @Test
    fun `parse relay set with multiple relays`() {
        val relays = (1..10).map { "wss://relay$it.com" }
        val event = createRelaySetEvent(
            identifier = "many-relays",
            relays = relays
        )

        val relaySet = RelaySet.fromEvent(event)

        assertEquals(10, relaySet.relays.size)
        relays.forEach { relay ->
            assertTrue(relaySet.relays.contains(relay))
        }
    }

    @Test
    fun `pubkey from event`() {
        val event = createRelaySetEvent(
            identifier = "test",
            relays = listOf("wss://relay.com"),
            pubkey = "testpubkey123"
        )

        val relaySet = RelaySet.fromEvent(event)

        assertEquals("testpubkey123", relaySet.author)
    }

    @Test
    fun `createdAt from event`() {
        val event = createRelaySetEvent(
            identifier = "test",
            relays = listOf("wss://relay.com"),
            createdAt = 1234567890
        )

        val relaySet = RelaySet.fromEvent(event)

        assertEquals(1234567890L, relaySet.createdAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on non-relay-set event`() {
        val event = NDKEvent(
            id = "testeventid",
            pubkey = "testpubkey",
            createdAt = System.currentTimeMillis() / 1000,
            kind = 1, // Wrong kind
            tags = emptyList(),
            content = "",
            sig = "testsig"
        )

        RelaySet.fromEvent(event)
    }

    @Test
    fun `ignore non-relay tags`() {
        val tags = listOf(
            listOf("d", "test"),
            listOf("relay", "wss://relay1.com"),
            listOf("p", "somepubkey"),
            listOf("e", "someeventid"),
            listOf("relay", "wss://relay2.com")
        )

        val event = createRelaySetEventWithTags(tags)
        val relaySet = RelaySet.fromEvent(event)

        assertEquals(2, relaySet.relays.size)
        assertTrue(relaySet.relays.contains("wss://relay1.com"))
        assertTrue(relaySet.relays.contains("wss://relay2.com"))
    }

    // Helper functions
    private fun createRelaySetEvent(
        identifier: String,
        title: String? = null,
        description: String? = null,
        image: String? = null,
        relays: List<String>,
        pubkey: String = "testpubkey",
        createdAt: Long = System.currentTimeMillis() / 1000
    ): NDKEvent {
        val tags = mutableListOf<List<String>>()

        tags.add(listOf("d", identifier))
        title?.let { tags.add(listOf("title", it)) }
        description?.let { tags.add(listOf("description", it)) }
        image?.let { tags.add(listOf("image", it)) }
        relays.forEach { tags.add(listOf("relay", it)) }

        return NDKEvent(
            id = "testeventid",
            pubkey = pubkey,
            createdAt = createdAt,
            kind = KIND_RELAY_SET,
            tags = tags.map { NDKTag(it.first(), it.drop(1)) },
            content = "",
            sig = "testsig"
        )
    }

    private fun createRelaySetEventWithTags(
        tags: List<List<String>>,
        pubkey: String = "testpubkey",
        createdAt: Long = System.currentTimeMillis() / 1000
    ): NDKEvent {
        return NDKEvent(
            id = "testeventid",
            pubkey = pubkey,
            createdAt = createdAt,
            kind = KIND_RELAY_SET,
            tags = tags.map { NDKTag(it.first(), it.drop(1)) },
            content = "",
            sig = "testsig"
        )
    }
}
