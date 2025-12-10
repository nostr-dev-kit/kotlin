package io.nostr.ndk.outbox

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for NIP-65 relay list parsing.
 */
class RelayListTest {

    @Test
    fun `parse relay list with read and write markers`() {
        val event = createRelayListEvent(
            listOf(
                listOf("r", "wss://relay1.com", "read"),
                listOf("r", "wss://relay2.com", "write"),
                listOf("r", "wss://relay3.com") // No marker = read+write
            )
        )

        val relayList = RelayList.fromEvent(event)

        assertEquals(2, relayList.readRelays.size)
        assertEquals(2, relayList.writeRelays.size)

        assertTrue(relayList.readRelays.contains("wss://relay1.com"))
        assertTrue(relayList.readRelays.contains("wss://relay3.com"))

        assertTrue(relayList.writeRelays.contains("wss://relay2.com"))
        assertTrue(relayList.writeRelays.contains("wss://relay3.com"))
    }

    @Test
    fun `parse relay list with only read relays`() {
        val event = createRelayListEvent(
            listOf(
                listOf("r", "wss://read1.com", "read"),
                listOf("r", "wss://read2.com", "read")
            )
        )

        val relayList = RelayList.fromEvent(event)

        assertEquals(2, relayList.readRelays.size)
        assertEquals(0, relayList.writeRelays.size)

        assertTrue(relayList.readRelays.contains("wss://read1.com"))
        assertTrue(relayList.readRelays.contains("wss://read2.com"))
    }

    @Test
    fun `parse relay list with only write relays`() {
        val event = createRelayListEvent(
            listOf(
                listOf("r", "wss://write1.com", "write"),
                listOf("r", "wss://write2.com", "write")
            )
        )

        val relayList = RelayList.fromEvent(event)

        assertEquals(0, relayList.readRelays.size)
        assertEquals(2, relayList.writeRelays.size)

        assertTrue(relayList.writeRelays.contains("wss://write1.com"))
        assertTrue(relayList.writeRelays.contains("wss://write2.com"))
    }

    @Test
    fun `parse relay list with unmarked relays defaults to read and write`() {
        val event = createRelayListEvent(
            listOf(
                listOf("r", "wss://relay1.com"),
                listOf("r", "wss://relay2.com")
            )
        )

        val relayList = RelayList.fromEvent(event)

        assertEquals(2, relayList.readRelays.size)
        assertEquals(2, relayList.writeRelays.size)

        assertTrue(relayList.readRelays.contains("wss://relay1.com"))
        assertTrue(relayList.readRelays.contains("wss://relay2.com"))
        assertTrue(relayList.writeRelays.contains("wss://relay1.com"))
        assertTrue(relayList.writeRelays.contains("wss://relay2.com"))
    }

    @Test
    fun `parse empty relay list`() {
        val event = createRelayListEvent(emptyList())

        val relayList = RelayList.fromEvent(event)

        assertTrue(relayList.readRelays.isEmpty())
        assertTrue(relayList.writeRelays.isEmpty())
    }

    @Test
    fun `ignore non-r tags`() {
        val event = createRelayListEvent(
            listOf(
                listOf("r", "wss://relay1.com"),
                listOf("p", "somepubkey"),
                listOf("e", "someeventid")
            )
        )

        val relayList = RelayList.fromEvent(event)

        assertEquals(1, relayList.readRelays.size)
        assertEquals(1, relayList.writeRelays.size)
    }

    @Test
    fun `normalize relay URLs`() {
        val event = createRelayListEvent(
            listOf(
                listOf("r", "wss://relay1.com/"),  // Trailing slash
                listOf("r", "WSS://RELAY2.COM"),   // Uppercase
                listOf("r", "relay3.com")          // No scheme
            )
        )

        val relayList = RelayList.fromEvent(event)

        assertTrue(relayList.readRelays.contains("wss://relay1.com"))
        assertTrue(relayList.readRelays.contains("wss://relay2.com"))
        assertTrue(relayList.readRelays.contains("wss://relay3.com"))
    }

    @Test
    fun `pubkey from event`() {
        val event = createRelayListEvent(
            listOf(listOf("r", "wss://relay.com")),
            pubkey = "testpubkey123"
        )

        val relayList = RelayList.fromEvent(event)

        assertEquals("testpubkey123", relayList.pubkey)
    }

    @Test
    fun `createdAt from event`() {
        val event = createRelayListEvent(
            listOf(listOf("r", "wss://relay.com")),
            createdAt = 1234567890
        )

        val relayList = RelayList.fromEvent(event)

        assertEquals(1234567890L, relayList.createdAt)
    }

    @Test
    fun `get all relays combines read and write`() {
        val event = createRelayListEvent(
            listOf(
                listOf("r", "wss://read.com", "read"),
                listOf("r", "wss://write.com", "write"),
                listOf("r", "wss://both.com")
            )
        )

        val relayList = RelayList.fromEvent(event)
        val allRelays = relayList.allRelays

        assertEquals(3, allRelays.size)
        assertTrue(allRelays.contains("wss://read.com"))
        assertTrue(allRelays.contains("wss://write.com"))
        assertTrue(allRelays.contains("wss://both.com"))
    }

    @Test
    fun `isReadRelay returns correct value`() {
        val event = createRelayListEvent(
            listOf(
                listOf("r", "wss://read.com", "read"),
                listOf("r", "wss://write.com", "write")
            )
        )

        val relayList = RelayList.fromEvent(event)

        assertTrue(relayList.isReadRelay("wss://read.com"))
        assertFalse(relayList.isReadRelay("wss://write.com"))
        assertFalse(relayList.isReadRelay("wss://unknown.com"))
    }

    @Test
    fun `isWriteRelay returns correct value`() {
        val event = createRelayListEvent(
            listOf(
                listOf("r", "wss://read.com", "read"),
                listOf("r", "wss://write.com", "write")
            )
        )

        val relayList = RelayList.fromEvent(event)

        assertFalse(relayList.isWriteRelay("wss://read.com"))
        assertTrue(relayList.isWriteRelay("wss://write.com"))
        assertFalse(relayList.isWriteRelay("wss://unknown.com"))
    }

    // Helper function
    private fun createRelayListEvent(
        tags: List<List<String>>,
        pubkey: String = "testpubkey",
        createdAt: Long = System.currentTimeMillis() / 1000
    ): NDKEvent {
        return NDKEvent(
            id = "testeventid",
            pubkey = pubkey,
            createdAt = createdAt,
            kind = 10002, // NIP-65 relay list
            tags = tags.map { NDKTag(it.first(), it.drop(1)) },
            content = "",
            sig = "testsig"
        )
    }
}
