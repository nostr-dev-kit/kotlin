package io.nostr.ndk.builders

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.nips.KIND_CONTACT_LIST
import io.nostr.ndk.nips.KIND_LONG_FORM
import io.nostr.ndk.nips.KIND_REACTION
import io.nostr.ndk.nips.KIND_TEXT_NOTE
import io.nostr.ndk.nips.KIND_ZAP_REQUEST

/**
 * Builder for creating text note events (kind 1).
 *
 * Usage:
 * ```kotlin
 * val note = TextNoteBuilder()
 *     .content("Hello Nostr!")
 *     .mention(somePubkey)
 *     .hashtag("nostr")
 *     .replyTo(parentEventId, parentPubkey)
 *     .build(signer)
 *
 * ndk.publish(note)
 * ```
 */
class TextNoteBuilder {
    private var content: String = ""
    private val tags = mutableListOf<NDKTag>()

    fun content(content: String) = apply { this.content = content }

    fun mention(pubkey: PublicKey) = apply {
        tags.add(NDKTag("p", listOf(pubkey)))
    }

    fun hashtag(tag: String) = apply {
        tags.add(NDKTag("t", listOf(tag.lowercase().trim())))
    }

    fun replyTo(eventId: String, authorPubkey: PublicKey, relayHint: String? = null) = apply {
        val eTagValues = mutableListOf(eventId)
        relayHint?.let { eTagValues.add(it) }
        eTagValues.add("reply")
        tags.add(NDKTag("e", eTagValues))
        tags.add(NDKTag("p", listOf(authorPubkey)))
    }

    fun rootEvent(eventId: String, relayHint: String? = null) = apply {
        val eTagValues = mutableListOf(eventId)
        relayHint?.let { eTagValues.add(it) }
        eTagValues.add("root")
        tags.add(NDKTag("e", eTagValues))
    }

    fun mentionEvent(eventId: String, relayHint: String? = null) = apply {
        val eTagValues = mutableListOf(eventId)
        relayHint?.let { eTagValues.add(it) }
        eTagValues.add("mention")
        tags.add(NDKTag("e", eTagValues))
    }

    fun tag(name: String, vararg values: String) = apply {
        tags.add(NDKTag(name, values.toList()))
    }

    suspend fun build(signer: NDKSigner): NDKEvent {
        val unsigned = UnsignedEvent(
            pubkey = signer.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_TEXT_NOTE,
            tags = tags,
            content = content
        )
        return signer.sign(unsigned)
    }

    fun buildUnsigned(pubkey: PublicKey): UnsignedEvent {
        return UnsignedEvent(
            pubkey = pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_TEXT_NOTE,
            tags = tags,
            content = content
        )
    }
}

/**
 * Builder for creating reaction events (kind 7).
 *
 * Usage:
 * ```kotlin
 * val like = ReactionBuilder()
 *     .target(eventId, authorPubkey)
 *     .like()
 *     .build(signer)
 * ```
 */
class ReactionBuilder {
    private var content: String = "+"
    private val tags = mutableListOf<NDKTag>()

    fun target(eventId: String, authorPubkey: PublicKey, eventKind: Int? = null) = apply {
        tags.add(NDKTag("e", listOf(eventId)))
        tags.add(NDKTag("p", listOf(authorPubkey)))
        eventKind?.let { tags.add(NDKTag("k", listOf(it.toString()))) }
    }

    fun like() = apply { content = "+" }

    fun dislike() = apply { content = "-" }

    fun emoji(emoji: String) = apply { content = emoji }

    suspend fun build(signer: NDKSigner): NDKEvent {
        val unsigned = UnsignedEvent(
            pubkey = signer.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_REACTION,
            tags = tags,
            content = content
        )
        return signer.sign(unsigned)
    }
}

/**
 * Builder for creating long-form content events (kind 30023).
 *
 * Usage:
 * ```kotlin
 * val article = ArticleBuilder()
 *     .identifier("my-article")
 *     .title("My Article Title")
 *     .content("Article content in markdown...")
 *     .summary("Short summary")
 *     .image("https://example.com/cover.jpg")
 *     .topic("nostr")
 *     .build(signer)
 * ```
 */
class ArticleBuilder {
    private var content: String = ""
    private val tags = mutableListOf<NDKTag>()
    private var identifier: String = ""

    fun identifier(id: String) = apply {
        identifier = id
    }

    fun title(title: String) = apply {
        tags.add(NDKTag("title", listOf(title)))
    }

    fun content(content: String) = apply {
        this.content = content
    }

    fun summary(summary: String) = apply {
        tags.add(NDKTag("summary", listOf(summary)))
    }

    fun image(url: String) = apply {
        tags.add(NDKTag("image", listOf(url)))
    }

    fun publishedAt(timestamp: Long) = apply {
        tags.add(NDKTag("published_at", listOf(timestamp.toString())))
    }

    fun topic(topic: String) = apply {
        tags.add(NDKTag("t", listOf(topic.lowercase().trim())))
    }

    suspend fun build(signer: NDKSigner): NDKEvent {
        // Ensure d tag is set
        if (identifier.isNotBlank()) {
            tags.add(0, NDKTag("d", listOf(identifier)))
        }

        val unsigned = UnsignedEvent(
            pubkey = signer.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_LONG_FORM,
            tags = tags,
            content = content
        )
        return signer.sign(unsigned)
    }
}

/**
 * Builder for creating contact list events (kind 3).
 *
 * Usage:
 * ```kotlin
 * val contacts = ContactListBuilder()
 *     .follow(pubkey1, "wss://relay.com", "alice")
 *     .follow(pubkey2)
 *     .build(signer)
 * ```
 */
class ContactListBuilder {
    private val tags = mutableListOf<NDKTag>()
    private var content: String = "{}" // Optional relay list JSON

    fun follow(pubkey: PublicKey, relayUrl: String? = null, petname: String? = null) = apply {
        val values = mutableListOf(pubkey)
        relayUrl?.let { values.add(it) } ?: values.add("")
        petname?.let { values.add(it) }
        tags.add(NDKTag("p", values))
    }

    fun relayListJson(json: String) = apply {
        content = json
    }

    suspend fun build(signer: NDKSigner): NDKEvent {
        val unsigned = UnsignedEvent(
            pubkey = signer.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_CONTACT_LIST,
            tags = tags,
            content = content
        )
        return signer.sign(unsigned)
    }
}

/**
 * Builder for creating zap request events (kind 9734).
 *
 * Usage:
 * ```kotlin
 * val zapRequest = ZapRequestBuilder()
 *     .recipient(recipientPubkey)
 *     .amount(21000) // millisats
 *     .lnurl("lnurl...")
 *     .relays(listOf("wss://relay1.com", "wss://relay2.com"))
 *     .event(eventId) // optional: zap a specific event
 *     .build(signer)
 * ```
 */
class ZapRequestBuilder {
    private var content: String = ""
    private val tags = mutableListOf<NDKTag>()

    fun recipient(pubkey: PublicKey) = apply {
        tags.add(NDKTag("p", listOf(pubkey)))
    }

    fun event(eventId: String) = apply {
        tags.add(NDKTag("e", listOf(eventId)))
    }

    fun amount(millisats: Long) = apply {
        tags.add(NDKTag("amount", listOf(millisats.toString())))
    }

    fun lnurl(lnurl: String) = apply {
        tags.add(NDKTag("lnurl", listOf(lnurl)))
    }

    fun relays(urls: List<String>) = apply {
        tags.add(NDKTag("relays", urls))
    }

    fun comment(comment: String) = apply {
        content = comment
    }

    suspend fun build(signer: NDKSigner): NDKEvent {
        val unsigned = UnsignedEvent(
            pubkey = signer.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_ZAP_REQUEST,
            tags = tags,
            content = content
        )
        return signer.sign(unsigned)
    }
}

/**
 * Extension function for convenient text note creation.
 */
fun NDK.textNote() = TextNoteBuilder()

/**
 * Extension function for convenient reaction creation.
 */
fun NDK.reaction() = ReactionBuilder()

/**
 * Extension function for convenient article creation.
 */
fun NDK.article() = ArticleBuilder()

/**
 * Extension function for convenient contact list creation.
 */
fun NDK.contactList() = ContactListBuilder()

/**
 * Extension function for convenient zap request creation.
 */
fun NDK.zapRequest() = ZapRequestBuilder()
