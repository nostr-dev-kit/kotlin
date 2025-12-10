package io.nostr.ndk.relay.messages

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.NDKTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientMessageTest {

    @Test
    fun `serialize REQ message with single filter`() {
        val filter = NDKFilter(kinds = setOf(1), limit = 10)
        val message = ClientMessage.Req("sub1", listOf(filter))

        val json = message.toJson()

        assertTrue(json.startsWith("""["REQ","sub1","""))
        assertTrue(json.contains(""""kinds":[1]"""))
        assertTrue(json.contains(""""limit":10"""))
    }

    @Test
    fun `serialize REQ message with multiple filters`() {
        val filter1 = NDKFilter(kinds = setOf(1))
        val filter2 = NDKFilter(kinds = setOf(3))
        val message = ClientMessage.Req("sub1", listOf(filter1, filter2))

        val json = message.toJson()

        assertTrue(json.startsWith("""["REQ","sub1","""))
        assertTrue(json.contains(""""kinds":[1]"""))
        assertTrue(json.contains(""""kinds":[3]"""))
    }

    @Test
    fun `serialize REQ message with author filter`() {
        val filter = NDKFilter(authors = setOf("pubkey1", "pubkey2"))
        val message = ClientMessage.Req("sub1", listOf(filter))

        val json = message.toJson()

        assertTrue(json.contains(""""authors":"""))
        assertTrue(json.contains(""""pubkey1""""))
        assertTrue(json.contains(""""pubkey2""""))
    }

    @Test
    fun `serialize REQ message with tag filter`() {
        val filter = NDKFilter(
            kinds = setOf(1),
            tags = mapOf("e" to setOf("event1"), "p" to setOf("pubkey1"))
        )
        val message = ClientMessage.Req("sub1", listOf(filter))

        val json = message.toJson()

        assertTrue(json.contains(""""#e":"""))
        assertTrue(json.contains(""""event1""""))
        assertTrue(json.contains(""""#p":"""))
        assertTrue(json.contains(""""pubkey1""""))
    }

    @Test
    fun `serialize REQ message with temporal constraints`() {
        val filter = NDKFilter(
            kinds = setOf(1),
            since = 1234567890L,
            until = 1234567900L
        )
        val message = ClientMessage.Req("sub1", listOf(filter))

        val json = message.toJson()

        assertTrue(json.contains(""""since":1234567890"""))
        assertTrue(json.contains(""""until":1234567900"""))
    }

    @Test
    fun `serialize CLOSE message`() {
        val message = ClientMessage.Close("sub1")

        val json = message.toJson()

        assertEquals("""["CLOSE","sub1"]""", json)
    }

    @Test
    fun `serialize EVENT message`() {
        val event = NDKEvent(
            id = "abc123",
            pubkey = "def456",
            createdAt = 1234567890L,
            kind = 1,
            tags = emptyList(),
            content = "Hello",
            sig = "sig123"
        )
        val message = ClientMessage.Event(event)

        val json = message.toJson()

        assertTrue(json.startsWith("""["EVENT","""))
        assertTrue(json.contains(""""id":"abc123""""))
        assertTrue(json.contains(""""pubkey":"def456""""))
        assertTrue(json.contains(""""created_at":1234567890"""))
        assertTrue(json.contains(""""kind":1"""))
        assertTrue(json.contains(""""content":"Hello""""))
        assertTrue(json.contains(""""sig":"sig123""""))
    }

    @Test
    fun `serialize EVENT message with tags`() {
        val event = NDKEvent(
            id = "abc",
            pubkey = "def",
            createdAt = 1234567890L,
            kind = 1,
            tags = listOf(
                NDKTag("e", listOf("event1")),
                NDKTag("p", listOf("pubkey1", "relay1"))
            ),
            content = "Reply",
            sig = "sig"
        )
        val message = ClientMessage.Event(event)

        val json = message.toJson()

        assertTrue(json.contains(""""tags":[["e","event1"],["p","pubkey1","relay1"]]"""))
    }

    @Test
    fun `serialize AUTH message`() {
        val event = NDKEvent(
            id = "auth123",
            pubkey = "pubkey123",
            createdAt = 1234567890L,
            kind = 22242,
            tags = listOf(
                NDKTag("relay", listOf("wss://relay.example.com")),
                NDKTag("challenge", listOf("challenge123"))
            ),
            content = "",
            sig = "authsig123"
        )
        val message = ClientMessage.Auth(event)

        val json = message.toJson()

        assertTrue(json.startsWith("""["AUTH","""))
        assertTrue(json.contains(""""kind":22242"""))
        assertTrue(json.contains(""""relay""""))
        assertTrue(json.contains(""""challenge""""))
    }

    @Test
    fun `REQ message with empty filters list`() {
        val message = ClientMessage.Req("sub1", emptyList())

        val json = message.toJson()

        assertEquals("""["REQ","sub1"]""", json)
    }

    @Test
    fun `REQ message serialization is deterministic`() {
        val filter = NDKFilter(kinds = setOf(1, 3), limit = 10)
        val message = ClientMessage.Req("sub1", listOf(filter))

        val json1 = message.toJson()
        val json2 = message.toJson()

        assertEquals(json1, json2)
    }

    @Test
    fun `EVENT message with unsigned event has null sig`() {
        val event = NDKEvent(
            id = "abc",
            pubkey = "def",
            createdAt = 1234567890L,
            kind = 1,
            tags = emptyList(),
            content = "Hello",
            sig = null
        )
        val message = ClientMessage.Event(event)

        val json = message.toJson()

        assertTrue(json.contains(""""sig":null"""))
    }
}
