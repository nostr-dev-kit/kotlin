package io.nostr.ndk.test

import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.nips.*
import java.security.SecureRandom
import java.util.UUID

/**
 * Generator for creating test events with realistic data.
 *
 * EventGenerator provides factory methods for creating various types
 * of Nostr events for testing purposes. Events can be unsigned or signed
 * with deterministic test keys.
 *
 * Usage:
 * ```kotlin
 * val generator = EventGenerator()
 *
 * // Generate a text note
 * val note = generator.textNote("Hello world!")
 *
 * // Generate a profile
 * val profile = generator.profile(name = "Alice", nip05 = "alice@example.com")
 *
 * // Generate a signed event
 * val signedNote = generator.signedTextNote("Test content")
 *
 * // Generate a batch of events
 * val feed = generator.feed(count = 50)
 * ```
 */
class EventGenerator(
    private val defaultPubkey: String = generateTestPubkey(),
    private val signer: NDKPrivateKeySigner? = null
) {
    companion object {
        private val random = SecureRandom()

        /**
         * Generates a deterministic test pubkey.
         */
        fun generateTestPubkey(seed: Int = 0): String {
            val bytes = ByteArray(32)
            random.setSeed(seed.toLong())
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        /**
         * Generates a random event ID.
         */
        fun generateEventId(): String {
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        /**
         * Creates an EventGenerator with a signer for signed events.
         */
        fun withSigner(): EventGenerator {
            val keyPair = NDKKeyPair.generate()
            val signer = NDKPrivateKeySigner(keyPair)
            return EventGenerator(keyPair.pubkeyHex, signer)
        }
    }

    private var eventCounter = 0
    private var timestampBase = System.currentTimeMillis() / 1000

    /**
     * Creates a text note (kind 1) event.
     */
    fun textNote(
        content: String = "Test note ${eventCounter++}",
        pubkey: String = defaultPubkey,
        tags: List<NDKTag> = emptyList(),
        createdAt: Long = timestampBase--
    ): NDKEvent {
        return NDKEvent(
            id = generateEventId(),
            pubkey = pubkey,
            createdAt = createdAt,
            kind = KIND_TEXT_NOTE,
            tags = tags,
            content = content,
            sig = null
        )
    }

    /**
     * Creates a reply to another event.
     */
    fun reply(
        content: String = "Reply ${eventCounter++}",
        replyTo: NDKEvent,
        pubkey: String = defaultPubkey,
        createdAt: Long = timestampBase--
    ): NDKEvent {
        val tags = mutableListOf<NDKTag>()

        // Add root tag if replying to root, or use existing root
        val existingRoot = replyTo.tagsWithName("e").find { it.values.getOrNull(2) == "root" }
        if (existingRoot != null) {
            tags.add(existingRoot)
            tags.add(NDKTag("e", listOf(replyTo.id, "", "reply")))
        } else {
            tags.add(NDKTag("e", listOf(replyTo.id, "", "root")))
        }

        // Add p tag for the author being replied to
        tags.add(NDKTag("p", listOf(replyTo.pubkey)))

        return NDKEvent(
            id = generateEventId(),
            pubkey = pubkey,
            createdAt = createdAt,
            kind = KIND_TEXT_NOTE,
            tags = tags,
            content = content,
            sig = null
        )
    }

    /**
     * Creates a metadata/profile event (kind 0).
     */
    fun profile(
        name: String? = "TestUser${eventCounter++}",
        displayName: String? = null,
        about: String? = "Test profile",
        picture: String? = null,
        nip05: String? = null,
        lud16: String? = null,
        pubkey: String = defaultPubkey,
        createdAt: Long = timestampBase--
    ): NDKEvent {
        val contentMap = buildMap {
            name?.let { put("name", it) }
            displayName?.let { put("display_name", it) }
            about?.let { put("about", it) }
            picture?.let { put("picture", it) }
            nip05?.let { put("nip05", it) }
            lud16?.let { put("lud16", it) }
        }

        val content = contentMap.entries.joinToString(",", "{", "}") { (k, v) ->
            "\"$k\":\"$v\""
        }

        return NDKEvent(
            id = generateEventId(),
            pubkey = pubkey,
            createdAt = createdAt,
            kind = 0,
            tags = emptyList(),
            content = content,
            sig = null
        )
    }

    /**
     * Creates a contact list event (kind 3).
     */
    fun contactList(
        follows: List<String>,
        pubkey: String = defaultPubkey,
        createdAt: Long = timestampBase--
    ): NDKEvent {
        val tags = follows.map { followPubkey ->
            NDKTag("p", listOf(followPubkey))
        }

        return NDKEvent(
            id = generateEventId(),
            pubkey = pubkey,
            createdAt = createdAt,
            kind = KIND_CONTACT_LIST,
            tags = tags,
            content = "{}",
            sig = null
        )
    }

    /**
     * Creates a reaction event (kind 7).
     */
    fun reaction(
        target: NDKEvent,
        content: String = "+",
        pubkey: String = defaultPubkey,
        createdAt: Long = timestampBase--
    ): NDKEvent {
        val tags = listOf(
            NDKTag("e", listOf(target.id)),
            NDKTag("p", listOf(target.pubkey)),
            NDKTag("k", listOf(target.kind.toString()))
        )

        return NDKEvent(
            id = generateEventId(),
            pubkey = pubkey,
            createdAt = createdAt,
            kind = KIND_REACTION,
            tags = tags,
            content = content,
            sig = null
        )
    }

    /**
     * Creates a long-form article event (kind 30023).
     */
    fun article(
        title: String = "Test Article ${eventCounter++}",
        content: String = "Article content...",
        identifier: String = "article-${UUID.randomUUID()}",
        summary: String? = null,
        image: String? = null,
        topics: List<String> = emptyList(),
        pubkey: String = defaultPubkey,
        createdAt: Long = timestampBase--
    ): NDKEvent {
        val tags = mutableListOf(
            NDKTag("d", listOf(identifier)),
            NDKTag("title", listOf(title))
        )
        summary?.let { tags.add(NDKTag("summary", listOf(it))) }
        image?.let { tags.add(NDKTag("image", listOf(it))) }
        topics.forEach { tags.add(NDKTag("t", listOf(it))) }

        return NDKEvent(
            id = generateEventId(),
            pubkey = pubkey,
            createdAt = createdAt,
            kind = KIND_LONG_FORM,
            tags = tags,
            content = content,
            sig = null
        )
    }

    /**
     * Creates a zap receipt event (kind 9735).
     */
    fun zapReceipt(
        recipient: String,
        sender: String = defaultPubkey,
        amountMillisats: Long = 21000,
        zappedEvent: NDKEvent? = null,
        createdAt: Long = timestampBase--
    ): NDKEvent {
        val tags = mutableListOf(
            NDKTag("p", listOf(recipient)),
            NDKTag("bolt11", listOf("lnbc${amountMillisats}n1test"))
        )
        zappedEvent?.let {
            tags.add(NDKTag("e", listOf(it.id)))
        }

        // Create a mock zap request
        val zapRequest = NDKEvent(
            id = generateEventId(),
            pubkey = sender,
            createdAt = createdAt - 1,
            kind = KIND_ZAP_REQUEST,
            tags = listOf(
                NDKTag("p", listOf(recipient)),
                NDKTag("amount", listOf(amountMillisats.toString()))
            ),
            content = "",
            sig = null
        )
        tags.add(NDKTag("description", listOf(zapRequest.toJson())))

        return NDKEvent(
            id = generateEventId(),
            pubkey = "zap-provider-pubkey",
            createdAt = createdAt,
            kind = KIND_ZAP_RECEIPT,
            tags = tags,
            content = "",
            sig = null
        )
    }

    /**
     * Creates a relay list event (kind 10002).
     */
    fun relayList(
        relays: Map<String, String>, // url to read/write
        pubkey: String = defaultPubkey,
        createdAt: Long = timestampBase--
    ): NDKEvent {
        val tags = relays.map { (url, marker) ->
            if (marker.isBlank()) {
                NDKTag("r", listOf(url))
            } else {
                NDKTag("r", listOf(url, marker))
            }
        }

        return NDKEvent(
            id = generateEventId(),
            pubkey = pubkey,
            createdAt = createdAt,
            kind = 10002,
            tags = tags,
            content = "",
            sig = null
        )
    }

    /**
     * Creates a mute list event (kind 10000).
     */
    fun muteList(
        mutedPubkeys: List<String> = emptyList(),
        mutedEventIds: List<String> = emptyList(),
        mutedWords: List<String> = emptyList(),
        pubkey: String = defaultPubkey,
        createdAt: Long = timestampBase--
    ): NDKEvent {
        val tags = mutableListOf<NDKTag>()
        mutedPubkeys.forEach { tags.add(NDKTag("p", listOf(it))) }
        mutedEventIds.forEach { tags.add(NDKTag("e", listOf(it))) }
        mutedWords.forEach { tags.add(NDKTag("word", listOf(it))) }

        return NDKEvent(
            id = generateEventId(),
            pubkey = pubkey,
            createdAt = createdAt,
            kind = KIND_MUTE_LIST,
            tags = tags,
            content = "",
            sig = null
        )
    }

    /**
     * Generates a feed of text notes.
     */
    fun feed(
        count: Int = 20,
        authors: List<String>? = null,
        includeReplies: Boolean = false
    ): List<NDKEvent> {
        val pubkeys = authors ?: (0 until 5).map { generateTestPubkey(it) }
        val events = mutableListOf<NDKEvent>()

        repeat(count) { i ->
            val author = pubkeys[i % pubkeys.size]
            val note = textNote(
                content = "Test note #$i from ${author.take(8)}",
                pubkey = author
            )
            events.add(note)

            if (includeReplies && i > 0 && random.nextBoolean()) {
                val replyTarget = events[random.nextInt(events.size)]
                events.add(reply("Reply to note", replyTarget, author))
            }
        }

        return events.sortedByDescending { it.createdAt }
    }

    /**
     * Generates a thread of replies.
     */
    fun thread(
        rootContent: String = "Thread root",
        replyCount: Int = 5,
        authors: List<String>? = null
    ): List<NDKEvent> {
        val pubkeys = authors ?: (0 until 3).map { generateTestPubkey(it) }
        val events = mutableListOf<NDKEvent>()

        val root = textNote(content = rootContent, pubkey = pubkeys[0])
        events.add(root)

        var lastEvent = root
        repeat(replyCount) { i ->
            val author = pubkeys[(i + 1) % pubkeys.size]
            val replyEvent = reply("Reply $i", lastEvent, author)
            events.add(replyEvent)
            lastEvent = replyEvent
        }

        return events
    }

    /**
     * Generates multiple user profiles.
     */
    fun profiles(count: Int = 10): List<NDKEvent> {
        return (0 until count).map { i ->
            profile(
                name = "User$i",
                displayName = "Test User $i",
                about = "Bio for user $i",
                pubkey = generateTestPubkey(i)
            )
        }
    }

    /**
     * Signs an event if a signer is available.
     */
    suspend fun sign(event: NDKEvent): NDKEvent {
        val s = signer ?: throw IllegalStateException("No signer configured")
        val unsigned = UnsignedEvent(
            pubkey = event.pubkey,
            createdAt = event.createdAt,
            kind = event.kind,
            tags = event.tags,
            content = event.content
        )
        return s.sign(unsigned)
    }

    /**
     * Creates and signs a text note.
     */
    suspend fun signedTextNote(content: String): NDKEvent {
        return sign(textNote(content))
    }
}
