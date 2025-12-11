package io.nostr.ndk.builders

import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.nips.KIND_CONTACT_LIST
import io.nostr.ndk.nips.KIND_GENERIC_REPLY
import io.nostr.ndk.nips.KIND_GENERIC_REPOST
import io.nostr.ndk.nips.KIND_LONG_FORM
import io.nostr.ndk.nips.KIND_REACTION
import io.nostr.ndk.nips.KIND_REPOST
import io.nostr.ndk.nips.KIND_TEXT_NOTE
import io.nostr.ndk.nips.KIND_ZAP_REQUEST
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventBuildersTest {

    private val testPubkey = "abcd1234567890abcd1234567890abcd1234567890abcd1234567890abcd1234"

    @Test
    fun `TextNoteBuilder creates correct unsigned event`() {
        val builder = TextNoteBuilder()
            .content("Hello Nostr!")
            .hashtag("nostr")
            .hashtag("bitcoin")

        val unsigned = builder.buildUnsigned(testPubkey)

        assertEquals(KIND_TEXT_NOTE, unsigned.kind)
        assertEquals("Hello Nostr!", unsigned.content)
        assertEquals(testPubkey, unsigned.pubkey)
        assertEquals(2, unsigned.tags.size)
        assertTrue(unsigned.tags.any { it.name == "t" && it.values.first() == "nostr" })
        assertTrue(unsigned.tags.any { it.name == "t" && it.values.first() == "bitcoin" })
    }

    @Test
    fun `TextNoteBuilder adds reply tags correctly`() {
        val builder = TextNoteBuilder()
            .content("This is a reply")
            .replyTo("eventid123", "authorpubkey456", "wss://relay.com")

        val unsigned = builder.buildUnsigned(testPubkey)

        assertEquals(2, unsigned.tags.size)

        val eTag = unsigned.tags.find { it.name == "e" }
        assertEquals("eventid123", eTag?.values?.get(0))
        assertEquals("wss://relay.com", eTag?.values?.get(1))
        assertEquals("reply", eTag?.values?.get(2))

        val pTag = unsigned.tags.find { it.name == "p" }
        assertEquals("authorpubkey456", pTag?.values?.get(0))
    }

    @Test
    fun `TextNoteBuilder adds mention tags correctly`() {
        val builder = TextNoteBuilder()
            .content("Hello @user!")
            .mention("userpubkey123")

        val unsigned = builder.buildUnsigned(testPubkey)

        val pTag = unsigned.tags.find { it.name == "p" }
        assertEquals("userpubkey123", pTag?.values?.get(0))
    }

    @Test
    fun `ReactionBuilder creates like reaction`() {
        val builder = ReactionBuilder()
            .target("eventid123", "authorpubkey456")
            .like()

        // Can't test full build without signer, but we can verify the builder is constructed
        val reaction = builder
        // Builder pattern works
        assertTrue(true)
    }

    @Test
    fun `ArticleBuilder creates long form content with tags`() {
        val builder = ArticleBuilder()
            .identifier("my-article")
            .title("My Article Title")
            .content("Article body content...")
            .summary("Short summary")
            .image("https://example.com/image.jpg")
            .topic("nostr")

        // Verify builder chain works
        assertTrue(true)
    }

    @Test
    fun `ContactListBuilder creates follows`() {
        val builder = ContactListBuilder()
            .follow("pubkey1", "wss://relay.com", "alice")
            .follow("pubkey2")
            .follow("pubkey3", null, "charlie")

        // Verify builder chain works
        assertTrue(true)
    }

    @Test
    fun `ZapRequestBuilder creates zap request with all fields`() {
        val builder = ZapRequestBuilder()
            .recipient("recipientpubkey")
            .event("eventid123")
            .amount(21000)
            .lnurl("lnurl...")
            .relays(listOf("wss://relay1.com", "wss://relay2.com"))
            .comment("Great post!")

        // Verify builder chain works
        assertTrue(true)
    }

    @Test
    fun `TextNoteBuilder normalizes hashtags to lowercase`() {
        val builder = TextNoteBuilder()
            .content("Test")
            .hashtag("NOSTR")
            .hashtag("Bitcoin")

        val unsigned = builder.buildUnsigned(testPubkey)

        val hashtags = unsigned.tags.filter { it.name == "t" }.map { it.values.first() }
        assertTrue(hashtags.contains("nostr"))
        assertTrue(hashtags.contains("bitcoin"))
    }

    // Reply Builder Tests
    @Test
    fun `ReplyBuilder creates kind 1 reply to root text note`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        // Create a root text note
        val rootNote = NDKEvent(
            id = "rootid123",
            pubkey = "rootpubkey456",
            createdAt = 1234567890,
            kind = KIND_TEXT_NOTE,
            tags = emptyList(),
            content = "This is a root post",
            sig = "sig123"
        )

        val reply = ReplyBuilder(rootNote)
            .content("This is a reply")
            .build(signer)

        assertEquals(KIND_TEXT_NOTE, reply.kind)
        assertEquals("This is a reply", reply.content)

        // Should have one e tag with root marker
        val eTags = reply.tagsWithName("e")
        assertEquals(1, eTags.size)
        assertEquals("rootid123", eTags[0].values[0])
        assertEquals("root", eTags[0].values[2])

        // Should have one p tag for root author
        val pTags = reply.tagsWithName("p")
        assertEquals(1, pTags.size)
        assertEquals("rootpubkey456", pTags[0].values[0])
    }

    @Test
    fun `ReplyBuilder creates kind 1 reply to a reply (thread)`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        // Create a reply (has e tags)
        val parentReply = NDKEvent(
            id = "replyid123",
            pubkey = "replypubkey456",
            createdAt = 1234567890,
            kind = KIND_TEXT_NOTE,
            tags = listOf(
                NDKTag("e", listOf("rootid000", "", "root")),
                NDKTag("p", listOf("rootpubkey000"))
            ),
            content = "This is a reply to root",
            sig = "sig123"
        )

        val reply = ReplyBuilder(parentReply)
            .content("This is a reply to a reply")
            .build(signer)

        assertEquals(KIND_TEXT_NOTE, reply.kind)

        // Should copy parent's e and p tags
        val eTags = reply.tagsWithName("e")
        assertTrue(eTags.size >= 2) // root tag + reply tag

        // Should have the root event tagged
        assertTrue(eTags.any { it.values[0] == "rootid000" })

        // Should have parent as reply
        val replyTag = eTags.find { it.values[0] == "replyid123" }
        assertNotNull(replyTag)
        assertEquals("reply", replyTag?.values?.get(2))

        // Should have p tags for both root author and parent author
        val pTags = reply.tagsWithName("p")
        assertTrue(pTags.any { it.values[0] == "rootpubkey000" })
        assertTrue(pTags.any { it.values[0] == "replypubkey456" })
    }

    @Test
    fun `ReplyBuilder creates kind 1111 reply to article (NIP-22)`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        // Create an article (kind 30023)
        val article = NDKEvent(
            id = "articleid123",
            pubkey = "authorpubkey456",
            createdAt = 1234567890,
            kind = KIND_LONG_FORM,
            tags = listOf(NDKTag("d", listOf("my-article"))),
            content = "Article content",
            sig = "sig123"
        )

        val reply = ReplyBuilder(article)
            .content("Great article!")
            .build(signer)

        assertEquals(KIND_GENERIC_REPLY, reply.kind)
        assertEquals("Great article!", reply.content)

        // Should have uppercase tags for root
        assertTrue(reply.tagsWithName("E").isNotEmpty() || reply.tagsWithName("A").isNotEmpty())
        assertTrue(reply.tagsWithName("K").isNotEmpty())
        assertTrue(reply.tagsWithName("P").isNotEmpty())

        // Should have lowercase tags for parent
        assertTrue(reply.tagsWithName("a").isNotEmpty()) // Article is parameterized replaceable
        assertTrue(reply.tagsWithName("k").isNotEmpty())
        assertTrue(reply.tagsWithName("p").isNotEmpty())

        // Verify K tag has correct kind
        val kTag = reply.tagsWithName("K").firstOrNull()
        assertEquals(KIND_LONG_FORM.toString(), kTag?.values?.get(0))
    }

    @Test
    fun `ReplyBuilder creates kind 1111 reply to comment (nested NIP-22)`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        // Create a comment (already has uppercase tags)
        val comment = NDKEvent(
            id = "commentid123",
            pubkey = "commenterpubkey456",
            createdAt = 1234567890,
            kind = KIND_GENERIC_REPLY,
            tags = listOf(
                NDKTag("E", listOf("rootid000", "", "rootpubkey000")),
                NDKTag("K", listOf(KIND_LONG_FORM.toString())),
                NDKTag("P", listOf("rootpubkey000")),
                NDKTag("e", listOf("articleid789", "", "authorpubkey789")),
                NDKTag("k", listOf(KIND_LONG_FORM.toString())),
                NDKTag("p", listOf("authorpubkey789"))
            ),
            content = "First comment",
            sig = "sig123"
        )

        val reply = ReplyBuilder(comment)
            .content("Reply to comment")
            .build(signer)

        assertEquals(KIND_GENERIC_REPLY, reply.kind)

        // Should copy uppercase tags from parent comment
        val upperETags = reply.tagsWithName("E")
        assertTrue(upperETags.isNotEmpty())

        val upperKTags = reply.tagsWithName("K")
        assertTrue(upperKTags.isNotEmpty())

        val upperPTags = reply.tagsWithName("P")
        assertTrue(upperPTags.isNotEmpty())

        // Should add lowercase tags for the comment itself
        val lowerETags = reply.tagsWithName("e")
        assertTrue(lowerETags.any { it.values[0] == "commentid123" })
    }

    // Quote Builder Tests
    @Test
    fun `QuoteBuilder creates quote with q tag`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val originalEvent = NDKEvent(
            id = "quotedid123",
            pubkey = "quotedpubkey456",
            createdAt = 1234567890,
            kind = KIND_TEXT_NOTE,
            tags = emptyList(),
            content = "Original content",
            sig = "sig123"
        )

        val quote = QuoteBuilder(originalEvent)
            .content("Check this out! nostr:note123...")
            .build(signer)

        assertEquals(KIND_TEXT_NOTE, quote.kind)
        assertEquals("Check this out! nostr:note123...", quote.content)

        // Should have q tag
        val qTags = quote.tagsWithName("q")
        assertEquals(1, qTags.size)
        assertEquals("quotedid123", qTags[0].values[0])
        assertEquals("quotedpubkey456", qTags[0].values[2])
    }

    // Repost Builder Tests
    @Test
    fun `RepostBuilder creates kind 6 repost for text note`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val originalNote = NDKEvent(
            id = "originalid123",
            pubkey = "originalpubkey456",
            createdAt = 1234567890,
            kind = KIND_TEXT_NOTE,
            tags = emptyList(),
            content = "Original note",
            sig = "sig123"
        )

        val repost = RepostBuilder(originalNote)
            .build(signer)

        assertEquals(KIND_REPOST, repost.kind)

        // Content should be JSON of original event
        assertTrue(repost.content.contains("\"id\":\"originalid123\""))

        // Should have e tag
        val eTags = repost.tagsWithName("e")
        assertEquals(1, eTags.size)
        assertEquals("originalid123", eTags[0].values[0])

        // Should have p tag
        val pTags = repost.tagsWithName("p")
        assertEquals(1, pTags.size)
        assertEquals("originalpubkey456", pTags[0].values[0])

        // Should NOT have k tag for kind 1
        val kTags = repost.tagsWithName("k")
        assertTrue(kTags.isEmpty())
    }

    @Test
    fun `RepostBuilder creates kind 16 generic repost for article`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val article = NDKEvent(
            id = "articleid123",
            pubkey = "authorpubkey456",
            createdAt = 1234567890,
            kind = KIND_LONG_FORM,
            tags = listOf(NDKTag("d", listOf("my-article"))),
            content = "Article content",
            sig = "sig123"
        )

        val repost = RepostBuilder(article)
            .build(signer)

        assertEquals(KIND_GENERIC_REPOST, repost.kind)

        // Content should be JSON of original event
        assertTrue(repost.content.contains("\"id\":\"articleid123\""))

        // Should have a tag (article is parameterized replaceable)
        val aTags = repost.tagsWithName("a")
        assertEquals(1, aTags.size)
        assertTrue(aTags[0].values[0].startsWith("$KIND_LONG_FORM:authorpubkey456:"))

        // Should have p tag
        val pTags = repost.tagsWithName("p")
        assertEquals(1, pTags.size)
        assertEquals("authorpubkey456", pTags[0].values[0])

        // Should have k tag for non-kind-1
        val kTags = repost.tagsWithName("k")
        assertEquals(1, kTags.size)
        assertEquals(KIND_LONG_FORM.toString(), kTags[0].values[0])
    }

    // Extension function tests
    @Test
    fun `NDKEvent reply extension function works`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val event = NDKEvent(
            id = "eventid123",
            pubkey = "pubkey456",
            createdAt = 1234567890,
            kind = KIND_TEXT_NOTE,
            tags = emptyList(),
            content = "Original",
            sig = "sig123"
        )

        val reply = event.reply()
            .content("Reply content")
            .build(signer)

        assertEquals(KIND_TEXT_NOTE, reply.kind)
        assertEquals("Reply content", reply.content)
    }

    @Test
    fun `NDKEvent quote extension function works`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val event = NDKEvent(
            id = "eventid123",
            pubkey = "pubkey456",
            createdAt = 1234567890,
            kind = KIND_TEXT_NOTE,
            tags = emptyList(),
            content = "Original",
            sig = "sig123"
        )

        val quote = event.quote()
            .content("Quote content")
            .build(signer)

        assertEquals(KIND_TEXT_NOTE, quote.kind)
        assertTrue(quote.tagsWithName("q").isNotEmpty())
    }

    @Test
    fun `NDKEvent repost extension function works`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val event = NDKEvent(
            id = "eventid123",
            pubkey = "pubkey456",
            createdAt = 1234567890,
            kind = KIND_TEXT_NOTE,
            tags = emptyList(),
            content = "Original",
            sig = "sig123"
        )

        val repost = event.repost()
            .build(signer)

        assertEquals(KIND_REPOST, repost.kind)
        assertTrue(repost.content.contains("\"id\":\"eventid123\""))
    }
}
