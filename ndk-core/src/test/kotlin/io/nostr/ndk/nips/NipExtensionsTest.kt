package io.nostr.ndk.nips

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NipExtensionsTest {

    // ========== NIP-01 Text Note Tests ==========

    @Test
    fun `isTextNote returns true for kind 1`() {
        val event = createEvent(kind = 1)
        assertTrue(event.isTextNote)
    }

    @Test
    fun `isTextNote returns false for other kinds`() {
        val event = createEvent(kind = 7)
        assertFalse(event.isTextNote)
    }

    @Test
    fun `rootEventId extracts root marker`() {
        val event = createEvent(
            kind = 1,
            tags = listOf(
                NDKTag("e", listOf("event1", "wss://relay.com", "root")),
                NDKTag("e", listOf("event2", "wss://relay.com", "reply"))
            )
        )
        assertEquals("event1", event.rootEventId)
    }

    @Test
    fun `rootEventId falls back to first e tag when no marker`() {
        val event = createEvent(
            kind = 1,
            tags = listOf(
                NDKTag("e", listOf("event1", "wss://relay.com")),
                NDKTag("e", listOf("event2", "wss://relay.com"))
            )
        )
        assertEquals("event1", event.rootEventId)
    }

    @Test
    fun `replyToEventId extracts reply marker`() {
        val event = createEvent(
            kind = 1,
            tags = listOf(
                NDKTag("e", listOf("event1", "wss://relay.com", "root")),
                NDKTag("e", listOf("event2", "wss://relay.com", "reply"))
            )
        )
        assertEquals("event2", event.replyToEventId)
    }

    @Test
    fun `hashtags extracts t tags`() {
        val event = createEvent(
            kind = 1,
            tags = listOf(
                NDKTag("t", listOf("nostr")),
                NDKTag("t", listOf("bitcoin"))
            )
        )
        assertEquals(listOf("nostr", "bitcoin"), event.hashtags)
    }

    // ========== NIP-02 Contact List Tests ==========

    @Test
    fun `isContactList returns true for kind 3`() {
        val event = createEvent(kind = 3)
        assertTrue(event.isContactList)
    }

    @Test
    fun `contacts parses p tags correctly`() {
        val event = createEvent(
            kind = 3,
            tags = listOf(
                NDKTag("p", listOf("pubkey1", "wss://relay.com", "alice")),
                NDKTag("p", listOf("pubkey2")),
                NDKTag("p", listOf("pubkey3", "", "bob"))
            )
        )

        val contacts = event.contacts
        assertEquals(3, contacts.size)

        assertEquals("pubkey1", contacts[0].pubkey)
        assertEquals("wss://relay.com", contacts[0].relayUrl)
        assertEquals("alice", contacts[0].petname)

        assertEquals("pubkey2", contacts[1].pubkey)
        assertNull(contacts[1].relayUrl)
        assertNull(contacts[1].petname)

        assertEquals("pubkey3", contacts[2].pubkey)
        assertNull(contacts[2].relayUrl)
        assertEquals("bob", contacts[2].petname)
    }

    @Test
    fun `isFollowing checks if pubkey is in contact list`() {
        val event = createEvent(
            kind = 3,
            tags = listOf(
                NDKTag("p", listOf("pubkey1")),
                NDKTag("p", listOf("pubkey2"))
            )
        )

        assertTrue(event.isFollowing("pubkey1"))
        assertTrue(event.isFollowing("pubkey2"))
        assertFalse(event.isFollowing("pubkey3"))
    }

    // ========== NIP-10 Thread Info Tests ==========

    @Test
    fun `threadInfo parses marker-based tags`() {
        val event = createEvent(
            kind = 1,
            tags = listOf(
                NDKTag("e", listOf("root-event", "", "root")),
                NDKTag("e", listOf("reply-event", "", "reply")),
                NDKTag("e", listOf("mention1", "", "mention")),
                NDKTag("e", listOf("mention2", "", "mention"))
            )
        )

        val info = event.threadInfo
        assertEquals("root-event", info.root)
        assertEquals("reply-event", info.replyTo)
        assertEquals(listOf("mention1", "mention2"), info.mentions)
        assertTrue(info.isReply)
        assertFalse(info.isRoot)
    }

    @Test
    fun `threadInfo uses positional fallback`() {
        val event = createEvent(
            kind = 1,
            tags = listOf(
                NDKTag("e", listOf("event1")),
                NDKTag("e", listOf("event2")),
                NDKTag("e", listOf("event3"))
            )
        )

        val info = event.threadInfo
        assertEquals("event1", info.root)
        assertEquals("event3", info.replyTo)
        assertEquals(listOf("event2"), info.mentions)
    }

    @Test
    fun `threadInfo returns isRoot true for no e tags`() {
        val event = createEvent(kind = 1, tags = emptyList())
        val info = event.threadInfo
        assertTrue(info.isRoot)
        assertFalse(info.isReply)
        assertNull(info.root)
        assertNull(info.replyTo)
    }

    // ========== NIP-23 Long Form Tests ==========

    @Test
    fun `isLongFormContent returns true for kind 30023`() {
        val event = createEvent(kind = 30023)
        assertTrue(event.isLongFormContent)
    }

    @Test
    fun `articleTitle extracts title tag`() {
        val event = createEvent(
            kind = 30023,
            tags = listOf(
                NDKTag("d", listOf("article-id")),
                NDKTag("title", listOf("My Article Title"))
            )
        )
        assertEquals("My Article Title", event.articleTitle)
    }

    @Test
    fun `articlePublishedAt falls back to createdAt`() {
        val event = createEvent(kind = 30023, createdAt = 1234567890)
        assertEquals(1234567890L, event.articlePublishedAt)
    }

    @Test
    fun `articlePublishedAt uses published_at tag`() {
        val event = createEvent(
            kind = 30023,
            createdAt = 2000000000,
            tags = listOf(NDKTag("published_at", listOf("1234567890")))
        )
        assertEquals(1234567890L, event.articlePublishedAt)
    }

    // ========== NIP-25 Reaction Tests ==========

    @Test
    fun `isReaction returns true for kind 7`() {
        val event = createEvent(kind = 7)
        assertTrue(event.isReaction)
    }

    @Test
    fun `isLike returns true for plus sign`() {
        val event = createEvent(kind = 7, content = "+")
        assertTrue(event.isLike)
    }

    @Test
    fun `isLike returns true for thumbs up emoji`() {
        val event = createEvent(kind = 7, content = "\uD83D\uDC4D")
        assertTrue(event.isLike)
    }

    @Test
    fun `isDislike returns true for minus sign`() {
        val event = createEvent(kind = 7, content = "-")
        assertTrue(event.isDislike)
    }

    @Test
    fun `isCustomReaction returns true for custom emoji`() {
        val event = createEvent(kind = 7, content = "🔥")
        assertTrue(event.isCustomReaction)
        assertFalse(event.isLike)
        assertFalse(event.isDislike)
    }

    @Test
    fun `reactionTargetEventId extracts last e tag`() {
        val event = createEvent(
            kind = 7,
            content = "+",
            tags = listOf(
                NDKTag("e", listOf("event1")),
                NDKTag("e", listOf("event2"))
            )
        )
        assertEquals("event2", event.reactionTargetEventId)
    }

    // ========== NIP-51 List Tests ==========

    @Test
    fun `isMuteList returns true for kind 10000`() {
        val event = createEvent(kind = 10000)
        assertTrue(event.isMuteList)
        assertTrue(event.isList)
    }

    @Test
    fun `isPeopleList returns true for kind 30000`() {
        val event = createEvent(kind = 30000)
        assertTrue(event.isPeopleList)
    }

    @Test
    fun `listItems parses all item types`() {
        val event = createEvent(
            kind = 10000,
            tags = listOf(
                NDKTag("p", listOf("pubkey1", "wss://relay.com")),
                NDKTag("e", listOf("eventid1")),
                NDKTag("t", listOf("hashtag1")),
                NDKTag("word", listOf("spam"))
            )
        )

        val items = event.listItems
        assertEquals(4, items.size)

        assertTrue(items[0] is ListItem.Pubkey)
        assertEquals("pubkey1", (items[0] as ListItem.Pubkey).pubkey)

        assertTrue(items[1] is ListItem.Event)
        assertEquals("eventid1", (items[1] as ListItem.Event).eventId)

        assertTrue(items[2] is ListItem.Hashtag)
        assertEquals("hashtag1", (items[2] as ListItem.Hashtag).tag)

        assertTrue(items[3] is ListItem.Word)
        assertEquals("spam", (items[3] as ListItem.Word).word)
    }

    @Test
    fun `listContainsPubkey checks list membership`() {
        val event = createEvent(
            kind = 10000,
            tags = listOf(
                NDKTag("p", listOf("pubkey1")),
                NDKTag("p", listOf("pubkey2"))
            )
        )

        assertTrue(event.listContainsPubkey("pubkey1"))
        assertFalse(event.listContainsPubkey("pubkey3"))
    }

    // ========== NIP-57 Zap Tests ==========

    @Test
    fun `isZapReceipt returns true for kind 9735`() {
        val event = createEvent(kind = 9735)
        assertTrue(event.isZapReceipt)
    }

    @Test
    fun `isZapRequest returns true for kind 9734`() {
        val event = createEvent(kind = 9734)
        assertTrue(event.isZapRequest)
    }

    @Test
    fun `zapBolt11 extracts bolt11 tag`() {
        val event = createEvent(
            kind = 9735,
            tags = listOf(NDKTag("bolt11", listOf("lnbc1000...")))
        )
        assertEquals("lnbc1000...", event.zapBolt11)
    }

    @Test
    fun `parseBolt11Amount parses nano amount`() {
        // 1000n = 1000 millisatoshis (nano = 1 millisat)
        assertEquals(1000L, parseBolt11Amount("lnbc1000n1pjtest"))
    }

    @Test
    fun `parseBolt11Amount parses micro amount`() {
        // 1u = 1000 millisats (micro = 1000 millisats = 1 satoshi)
        assertEquals(1000L, parseBolt11Amount("lnbc1u1pjtest"))
    }

    @Test
    fun `parseBolt11Amount parses milli amount`() {
        // 1m = 1,000,000 millisats = 1000 satoshis
        assertEquals(1_000_000L, parseBolt11Amount("lnbc1m1pjtest"))
    }

    @Test
    fun `parseBolt11Amount returns null for invalid invoice`() {
        assertNull(parseBolt11Amount("invalid"))
        assertNull(parseBolt11Amount(""))
    }

    // ========== NIP-05 Tests ==========

    @Test
    fun `Nip05Verifier parseIdentifier splits correctly`() {
        val verifier = Nip05Verifier()

        val result = verifier.parseIdentifier("user@domain.com")
        assertEquals(Pair("user", "domain.com"), result)
    }

    @Test
    fun `Nip05Verifier parseIdentifier handles underscore`() {
        val verifier = Nip05Verifier()

        val result = verifier.parseIdentifier("_@domain.com")
        assertEquals(Pair("_", "domain.com"), result)
    }

    @Test
    fun `Nip05Verifier parseIdentifier normalizes to lowercase`() {
        val verifier = Nip05Verifier()

        val result = verifier.parseIdentifier("USER@Domain.Com")
        assertEquals(Pair("user", "domain.com"), result)
    }

    @Test
    fun `Nip05Verifier parseIdentifier returns null for invalid`() {
        val verifier = Nip05Verifier()

        assertNull(verifier.parseIdentifier("invalid"))
        assertNull(verifier.parseIdentifier("no-at-sign"))
        assertNull(verifier.parseIdentifier("@nodomain"))
        assertNull(verifier.parseIdentifier("user@"))
    }

    // Helper function to create test events
    private fun createEvent(
        kind: Int,
        content: String = "",
        tags: List<NDKTag> = emptyList(),
        createdAt: Long = System.currentTimeMillis() / 1000
    ): NDKEvent {
        return NDKEvent(
            id = "test-event-id",
            pubkey = "test-pubkey",
            createdAt = createdAt,
            kind = kind,
            tags = tags,
            content = content,
            sig = null
        )
    }
}
