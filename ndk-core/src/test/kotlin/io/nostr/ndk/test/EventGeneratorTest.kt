package io.nostr.ndk.test

import io.nostr.ndk.nips.KIND_CONTACT_LIST
import io.nostr.ndk.nips.KIND_LONG_FORM
import io.nostr.ndk.nips.KIND_MUTE_LIST
import io.nostr.ndk.nips.KIND_REACTION
import io.nostr.ndk.nips.KIND_TEXT_NOTE
import io.nostr.ndk.nips.KIND_ZAP_RECEIPT
import org.junit.Assert.*
import org.junit.Test

class EventGeneratorTest {

    @Test
    fun `generateTestPubkey creates valid 64 char hex`() {
        val pubkey = EventGenerator.generateTestPubkey()
        assertEquals(64, pubkey.length)
        assertTrue(pubkey.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `generateEventId creates valid 64 char hex`() {
        val eventId = EventGenerator.generateEventId()
        assertEquals(64, eventId.length)
        assertTrue(eventId.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `textNote creates kind 1 event`() {
        val generator = EventGenerator()
        val note = generator.textNote("Hello world!")

        assertEquals(KIND_TEXT_NOTE, note.kind)
        assertEquals("Hello world!", note.content)
        assertEquals(64, note.id.length)
        assertEquals(64, note.pubkey.length)
        assertNull(note.sig)
    }

    @Test
    fun `textNote with custom tags`() {
        val generator = EventGenerator()
        val tags = listOf(tag("t", "test"), tag("e", "eventid"))
        val note = generator.textNote("Test", tags = tags)

        assertEquals(2, note.tags.size)
        assertEquals("t", note.tags[0].name)
        assertEquals("test", note.tags[0].values.first())
    }

    @Test
    fun `reply creates proper threading tags`() {
        val generator = EventGenerator()
        val root = generator.textNote("Root post")
        val reply = generator.reply("Reply content", root)

        assertEquals(KIND_TEXT_NOTE, reply.kind)
        assertTrue(reply.tagsWithName("e").any { it.values.contains("root") })
        assertTrue(reply.tagsWithName("p").any { it.values.first() == root.pubkey })
    }

    @Test
    fun `profile creates kind 0 with JSON content`() {
        val generator = EventGenerator()
        val profile = generator.profile(
            name = "Alice",
            about = "Test bio",
            nip05 = "alice@example.com"
        )

        assertEquals(0, profile.kind)
        assertTrue(profile.content.contains("\"name\":\"Alice\""))
        assertTrue(profile.content.contains("\"about\":\"Test bio\""))
        assertTrue(profile.content.contains("\"nip05\":\"alice@example.com\""))
    }

    @Test
    fun `contactList creates kind 3 with p tags`() {
        val generator = EventGenerator()
        val follows = listOf("pubkey1", "pubkey2", "pubkey3")
        val contactList = generator.contactList(follows)

        assertEquals(KIND_CONTACT_LIST, contactList.kind)
        assertEquals(3, contactList.tagsWithName("p").size)
    }

    @Test
    fun `reaction creates kind 7 with target references`() {
        val generator = EventGenerator()
        val note = generator.textNote("Target post")
        val reaction = generator.reaction(note, "+")

        assertEquals(KIND_REACTION, reaction.kind)
        assertEquals("+", reaction.content)
        assertTrue(reaction.tagsWithName("e").any { it.values.first() == note.id })
        assertTrue(reaction.tagsWithName("p").any { it.values.first() == note.pubkey })
        assertTrue(reaction.tagsWithName("k").any { it.values.first() == "1" })
    }

    @Test
    fun `article creates kind 30023 with proper tags`() {
        val generator = EventGenerator()
        val article = generator.article(
            title = "Test Title",
            content = "Article body",
            identifier = "test-article",
            summary = "A test summary",
            topics = listOf("nostr", "testing")
        )

        assertEquals(KIND_LONG_FORM, article.kind)
        assertEquals("Article body", article.content)
        assertTrue(article.tagsWithName("d").any { it.values.first() == "test-article" })
        assertTrue(article.tagsWithName("title").any { it.values.first() == "Test Title" })
        assertTrue(article.tagsWithName("summary").any { it.values.first() == "A test summary" })
        assertEquals(2, article.tagsWithName("t").size)
    }

    @Test
    fun `zapReceipt creates kind 9735`() {
        val generator = EventGenerator()
        val recipient = EventGenerator.generateTestPubkey(1)
        val receipt = generator.zapReceipt(recipient, amountMillisats = 21000)

        assertEquals(KIND_ZAP_RECEIPT, receipt.kind)
        assertTrue(receipt.tagsWithName("p").any { it.values.first() == recipient })
        assertTrue(receipt.tagsWithName("bolt11").isNotEmpty())
        assertTrue(receipt.tagsWithName("description").isNotEmpty())
    }

    @Test
    fun `relayList creates kind 10002 with r tags`() {
        val generator = EventGenerator()
        val relays = mapOf(
            "wss://relay1.com" to "read",
            "wss://relay2.com" to "write",
            "wss://relay3.com" to ""
        )
        val list = generator.relayList(relays)

        assertEquals(10002, list.kind)
        assertEquals(3, list.tagsWithName("r").size)
    }

    @Test
    fun `muteList creates kind 10000 with proper tags`() {
        val generator = EventGenerator()
        val muteList = generator.muteList(
            mutedPubkeys = listOf("pk1", "pk2"),
            mutedEventIds = listOf("eid1"),
            mutedWords = listOf("spam")
        )

        assertEquals(KIND_MUTE_LIST, muteList.kind)
        assertEquals(2, muteList.tagsWithName("p").size)
        assertEquals(1, muteList.tagsWithName("e").size)
        assertEquals(1, muteList.tagsWithName("word").size)
    }

    @Test
    fun `feed generates multiple text notes`() {
        val generator = EventGenerator()
        val feed = generator.feed(count = 10)

        assertEquals(10, feed.size)
        assertTrue(feed.all { it.kind == KIND_TEXT_NOTE })
        // Verify sorted by createdAt descending
        val sorted = feed.sortedByDescending { it.createdAt }
        assertEquals(sorted, feed)
    }

    @Test
    fun `thread creates root and replies`() {
        val generator = EventGenerator()
        val thread = generator.thread(rootContent = "Thread root", replyCount = 5)

        assertEquals(6, thread.size) // 1 root + 5 replies
        assertEquals("Thread root", thread[0].content)
        // Check replies have proper structure
        for (i in 1 until thread.size) {
            assertTrue(thread[i].tagsWithName("e").isNotEmpty())
        }
    }

    @Test
    fun `profiles generates multiple metadata events`() {
        val generator = EventGenerator()
        val profiles = generator.profiles(count = 5)

        assertEquals(5, profiles.size)
        assertTrue(profiles.all { it.kind == 0 })
        // All should have different pubkeys
        val uniquePubkeys = profiles.map { it.pubkey }.toSet()
        assertEquals(5, uniquePubkeys.size)
    }

    @Test
    fun `events have decreasing timestamps`() {
        val generator = EventGenerator()
        val note1 = generator.textNote("First")
        val note2 = generator.textNote("Second")
        val note3 = generator.textNote("Third")

        // Each subsequent event should have earlier timestamp
        assertTrue(note1.createdAt > note2.createdAt)
        assertTrue(note2.createdAt > note3.createdAt)
    }
}
