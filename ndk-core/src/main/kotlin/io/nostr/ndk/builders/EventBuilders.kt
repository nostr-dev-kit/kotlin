package io.nostr.ndk.builders

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.nips.KIND_CONTACT_LIST
import io.nostr.ndk.nips.KIND_GENERIC_REPLY
import io.nostr.ndk.nips.KIND_GENERIC_REPOST
import io.nostr.ndk.nips.KIND_LONG_FORM
import io.nostr.ndk.nips.KIND_REACTION
import io.nostr.ndk.nips.KIND_REPOST
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

/**
 * Builder for creating reply events following NIP-10 conventions.
 *
 * This builder handles both:
 * - Standard kind 1 replies (for replying to kind 1 events)
 * - NIP-22 generic replies (kind 1111, for replying to any other kind)
 *
 * The builder automatically:
 * - Sets the correct event kind based on the parent event
 * - Creates proper root/reply tag markers
 * - Copies necessary tags from parent events
 * - Handles thread structure correctly
 *
 * Usage:
 * ```kotlin
 * // Reply to a text note (kind 1)
 * val reply = ReplyBuilder(parentEvent)
 *     .content("Great point!")
 *     .build(signer)
 *
 * // Reply to a long-form article (kind 30023)
 * val comment = ReplyBuilder(article)
 *     .content("Interesting article!")
 *     .build(signer)
 * ```
 */
class ReplyBuilder(private val replyTo: NDKEvent) {
    private var content: String = ""
    private val tags = mutableListOf<NDKTag>()

    fun content(content: String) = apply { this.content = content }

    fun tag(name: String, vararg values: String) = apply {
        tags.add(NDKTag(name, values.toList()))
    }

    suspend fun build(signer: NDKSigner): NDKEvent {
        val finalKind: Int
        val finalTags = mutableListOf<NDKTag>()

        if (replyTo.kind == KIND_TEXT_NOTE) {
            // Standard kind 1 reply following NIP-10
            finalKind = KIND_TEXT_NOTE

            val hasETags = replyTo.tagsWithName("e").isNotEmpty()

            if (hasETags) {
                // Parent has e-tags, so it's part of a thread
                // Copy all existing e and p tags from parent
                finalTags.addAll(replyTo.tagsWithName("e"))
                finalTags.addAll(replyTo.tagsWithName("p"))

                // Add the parent event as "reply"
                finalTags.add(NDKTag("e", listOf(replyTo.id, "", "reply")))

                // Add parent author if not already present
                if (!finalTags.any { it.name == "p" && it.values.firstOrNull() == replyTo.pubkey }) {
                    finalTags.add(NDKTag("p", listOf(replyTo.pubkey)))
                }
            } else {
                // Parent is a root event, mark it as such
                finalTags.add(NDKTag("e", listOf(replyTo.id, "", "root")))
                finalTags.add(NDKTag("p", listOf(replyTo.pubkey)))
            }
        } else {
            // NIP-22 generic reply (kind 1111) for non-kind-1 events
            finalKind = KIND_GENERIC_REPLY

            // Check if parent already has uppercase root tags (A, E, I, K, P)
            val hasUppercaseTags = replyTo.tags.any {
                it.name in listOf("A", "E", "I", "K", "P")
            }

            if (hasUppercaseTags) {
                // Parent is itself a comment, copy its uppercase tags
                finalTags.addAll(replyTo.tags.filter { it.name in listOf("A", "E", "I", "K", "P") })
            } else {
                // Parent is a root event, create uppercase tags
                if (replyTo.isParameterizedReplaceable) {
                    // Use 'A' tag for replaceable events
                    val dTag = replyTo.tagValue("d") ?: ""
                    val coordinate = "${replyTo.kind}:${replyTo.pubkey}:$dTag"
                    finalTags.add(NDKTag("A", listOf(coordinate, "")))
                } else {
                    // Use 'E' tag for regular events
                    finalTags.add(NDKTag("E", listOf(replyTo.id, "", replyTo.pubkey)))
                }

                // Add uppercase K and P tags for root
                finalTags.add(NDKTag("K", listOf(replyTo.kind.toString())))
                finalTags.add(NDKTag("P", listOf(replyTo.pubkey)))
            }

            // Add lowercase tags for the direct parent
            if (replyTo.isParameterizedReplaceable) {
                val dTag = replyTo.tagValue("d") ?: ""
                val coordinate = "${replyTo.kind}:${replyTo.pubkey}:$dTag"
                finalTags.add(NDKTag("a", listOf(coordinate, "")))
            } else {
                finalTags.add(NDKTag("e", listOf(replyTo.id, "", replyTo.pubkey)))
            }

            // Add lowercase k and p tags for parent
            finalTags.add(NDKTag("k", listOf(replyTo.kind.toString())))
            finalTags.add(NDKTag("p", listOf(replyTo.pubkey)))

            // Carry over all p tags from parent (excluding parent author which we already added)
            replyTo.tagsWithName("p").forEach { pTag ->
                val taggedPubkey = pTag.values.firstOrNull()
                if (taggedPubkey != null && taggedPubkey != replyTo.pubkey) {
                    if (!finalTags.any { it.name == "p" && it.values.firstOrNull() == taggedPubkey }) {
                        finalTags.add(pTag)
                    }
                }
            }
        }

        // Add any custom tags provided by the caller
        finalTags.addAll(tags)

        val unsigned = UnsignedEvent(
            pubkey = signer.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = finalKind,
            tags = finalTags,
            content = content
        )
        return signer.sign(unsigned)
    }
}

/**
 * Builder for creating quote events following NIP-18 and NIP-10.
 *
 * A quote is when you want to reference another event while adding your own commentary.
 * Uses a "q" tag to reference the quoted event.
 *
 * Usage:
 * ```kotlin
 * val quote = QuoteBuilder(originalEvent)
 *     .content("This is a great take! nostr:${originalEvent.nevent()}")
 *     .build(signer)
 * ```
 */
class QuoteBuilder(private val quotedEvent: NDKEvent) {
    private var content: String = ""
    private val tags = mutableListOf<NDKTag>()

    fun content(content: String) = apply { this.content = content }

    fun tag(name: String, vararg values: String) = apply {
        tags.add(NDKTag(name, values.toList()))
    }

    suspend fun build(signer: NDKSigner): NDKEvent {
        val finalTags = mutableListOf<NDKTag>()

        // Add q tag for the quoted event (NIP-18)
        // Format: ["q", <event-id>, <relay-url>, <pubkey>]
        finalTags.add(NDKTag("q", listOf(quotedEvent.id, "", quotedEvent.pubkey)))

        // Add any custom tags
        finalTags.addAll(tags)

        val unsigned = UnsignedEvent(
            pubkey = signer.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_TEXT_NOTE, // Quotes are always kind 1
            tags = finalTags,
            content = content
        )
        return signer.sign(unsigned)
    }
}

/**
 * Builder for creating repost events following NIP-18.
 *
 * Reposts are used to share another user's event with your followers.
 * - For kind 1 events, uses kind 6 (repost)
 * - For other event kinds, uses kind 16 (generic repost)
 *
 * The repost event contains:
 * - The original event JSON in the content (for non-protected events)
 * - An "e" or "a" tag referencing the original event
 * - A "k" tag indicating the kind of the original event (for non-kind-1)
 * - A "p" tag referencing the original author
 *
 * Usage:
 * ```kotlin
 * val repost = RepostBuilder(originalEvent)
 *     .build(signer)
 * ```
 */
class RepostBuilder(private val originalEvent: NDKEvent) {
    private val tags = mutableListOf<NDKTag>()

    fun tag(name: String, vararg values: String) = apply {
        tags.add(NDKTag(name, values.toList()))
    }

    suspend fun build(signer: NDKSigner): NDKEvent {
        val finalTags = mutableListOf<NDKTag>()

        // Determine the repost kind
        val repostKind = if (originalEvent.kind == KIND_TEXT_NOTE) {
            KIND_REPOST
        } else {
            KIND_GENERIC_REPOST
        }

        // Add reference to the original event
        if (originalEvent.isParameterizedReplaceable) {
            // Use 'a' tag for replaceable events
            val dTag = originalEvent.tagValue("d") ?: ""
            val coordinate = "${originalEvent.kind}:${originalEvent.pubkey}:$dTag"
            finalTags.add(NDKTag("a", listOf(coordinate, "")))
        } else {
            // Use 'e' tag for regular events
            finalTags.add(NDKTag("e", listOf(originalEvent.id, "")))
        }

        // Add p tag for the original author
        finalTags.add(NDKTag("p", listOf(originalEvent.pubkey)))

        // Add k tag for non-kind-1 events
        if (originalEvent.kind != KIND_TEXT_NOTE) {
            finalTags.add(NDKTag("k", listOf(originalEvent.kind.toString())))
        }

        // Add any custom tags
        finalTags.addAll(tags)

        // Content is the JSON of the original event (if not protected)
        val content = originalEvent.toJson()

        val unsigned = UnsignedEvent(
            pubkey = signer.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = repostKind,
            tags = finalTags,
            content = content
        )
        return signer.sign(unsigned)
    }
}

/**
 * Extension function to create a reply to this event.
 */
fun NDKEvent.reply() = ReplyBuilder(this)

/**
 * Extension function to create a quote of this event.
 */
fun NDKEvent.quote() = QuoteBuilder(this)

/**
 * Extension function to create a repost of this event.
 */
fun NDKEvent.repost() = RepostBuilder(this)
