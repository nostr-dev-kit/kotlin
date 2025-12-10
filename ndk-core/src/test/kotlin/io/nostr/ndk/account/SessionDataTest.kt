package io.nostr.ndk.account

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import org.junit.Assert.*
import org.junit.Test

class SessionDataTest {

    @Test
    fun `MuteList parses pubkeys from kind 10000 event`() {
        val event = NDKEvent(
            id = "abc",
            pubkey = "user123",
            createdAt = 1000L,
            kind = 10000,
            tags = listOf(
                NDKTag("p", listOf("muted1")),
                NDKTag("p", listOf("muted2")),
            ),
            content = "",
            sig = "sig"
        )

        val muteList = MuteList.fromEvent(event)

        assertEquals(2, muteList.pubkeys.size)
        assertTrue(muteList.pubkeys.contains("muted1"))
        assertTrue(muteList.pubkeys.contains("muted2"))
        assertTrue(muteList.isMuted("muted1"))
        assertFalse(muteList.isMuted("notmuted"))
    }

    @Test
    fun `MuteList parses words from kind 10000 event`() {
        val event = NDKEvent(
            id = "abc",
            pubkey = "user123",
            createdAt = 1000L,
            kind = 10000,
            tags = listOf(
                NDKTag("word", listOf("spam")),
                NDKTag("word", listOf("nsfw")),
            ),
            content = "",
            sig = "sig"
        )

        val muteList = MuteList.fromEvent(event)

        assertEquals(2, muteList.words.size)
        assertTrue(muteList.words.contains("spam"))
        assertTrue(muteList.words.contains("nsfw"))
    }

    @Test
    fun `MuteList parses event IDs from kind 10000 event`() {
        val event = NDKEvent(
            id = "abc",
            pubkey = "user123",
            createdAt = 1000L,
            kind = 10000,
            tags = listOf(
                NDKTag("e", listOf("event1")),
                NDKTag("e", listOf("event2")),
            ),
            content = "",
            sig = "sig"
        )

        val muteList = MuteList.fromEvent(event)

        assertEquals(2, muteList.eventIds.size)
        assertTrue(muteList.eventIds.contains("event1"))
    }

    @Test
    fun `BlockedRelayList parses relay URLs from kind 10001 event`() {
        val event = NDKEvent(
            id = "abc",
            pubkey = "user123",
            createdAt = 1000L,
            kind = 10001,
            tags = listOf(
                NDKTag("relay", listOf("wss://bad-relay.com")),
                NDKTag("relay", listOf("wss://another-bad.com")),
            ),
            content = "",
            sig = "sig"
        )

        val blockedRelays = BlockedRelayList.fromEvent(event)

        assertEquals(2, blockedRelays.relays.size)
        assertTrue(blockedRelays.isBlocked("wss://bad-relay.com"))
        assertFalse(blockedRelays.isBlocked("wss://good-relay.com"))
    }

    @Test
    fun `empty MuteList for missing event`() {
        val muteList = MuteList.empty("user123")

        assertTrue(muteList.pubkeys.isEmpty())
        assertTrue(muteList.words.isEmpty())
        assertTrue(muteList.eventIds.isEmpty())
    }
}
