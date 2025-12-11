package io.nostr.ndk.nips

import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.PublicKey

/**
 * NIP-17: Private Direct Messages
 *
 * https://github.com/nostr-protocol/nips/blob/master/17.md
 *
 * Defines private direct messages using gift wrap (NIP-59) for metadata protection.
 * Private messages are unsigned (kind 14) events that are sealed and gift-wrapped before publishing.
 */

/**
 * Kind constant for private messages (unsigned, inside seal).
 */
const val KIND_PRIVATE_MESSAGE = 14

/**
 * Kind constant for private file messages.
 */
const val KIND_PRIVATE_FILE_MESSAGE = 15

/**
 * Kind constant for DM relay list (user's preferred DM relays).
 */
const val KIND_DM_RELAY_LIST = 10050

/**
 * Returns true if this event is a private message (kind 14).
 */
val NDKEvent.isPrivateMessage: Boolean
    get() = kind == KIND_PRIVATE_MESSAGE

/**
 * Returns true if this event is a private file message (kind 15).
 */
val NDKEvent.isPrivateFileMessage: Boolean
    get() = kind == KIND_PRIVATE_FILE_MESSAGE

/**
 * Returns all recipient public keys from p tags.
 */
val NDKEvent.dmRecipients: List<PublicKey>
    get() = tagsWithName("p").mapNotNull { it.values.firstOrNull() }

/**
 * Returns the subject of the private message from the subject tag.
 */
val NDKEvent.dmSubject: String?
    get() = tagValue("subject")

/**
 * Returns the event ID being replied to from e tag with "reply" marker or first e tag.
 */
val NDKEvent.dmReplyTo: EventId?
    get() {
        val eTags = tagsWithName("e")
        if (eTags.isEmpty()) return null

        // Look for e tag with "reply" marker
        val replyTag = eTags.find { it.values.getOrNull(2) == "reply" }
        if (replyTag != null) {
            return replyTag.values.firstOrNull()
        }

        // Fallback to first e tag
        return eTags.firstOrNull()?.values?.firstOrNull()
    }

/**
 * Creates an unsigned private message (kind 14) to be sealed and gift-wrapped.
 *
 * This creates the inner event that will be wrapped using NIP-59 gift wrap.
 * The event is unsigned (a "rumor") and will be encrypted to the recipient.
 *
 * @param recipientPubkey The recipient's public key
 * @param content The message content
 * @param subject Optional subject line for the message
 * @param replyTo Optional event ID being replied to
 * @param replyToPubkey Optional public key of the author being replied to
 * @return An unsigned event (kind 14) ready to be sealed and gift-wrapped
 */
fun createPrivateMessage(
    recipientPubkey: String,
    content: String,
    subject: String? = null,
    replyTo: EventId? = null,
    replyToPubkey: String? = null
): UnsignedEvent {
    val tags = buildList {
        // Add recipient p tag
        add(NDKTag.pubkey(recipientPubkey))

        // Add reply author p tag if provided
        if (replyToPubkey != null) {
            add(NDKTag.pubkey(replyToPubkey))
        }

        // Add subject tag if provided
        if (subject != null) {
            add(NDKTag("subject", listOf(subject)))
        }

        // Add e tag for reply if provided
        if (replyTo != null) {
            add(NDKTag.event(replyTo, relay = "", marker = "reply"))
        }
    }

    // Note: pubkey and createdAt will be set by the caller before signing
    // For now, we use empty values as this is an unsigned event
    return UnsignedEvent(
        pubkey = "", // Will be set by the signer
        createdAt = System.currentTimeMillis() / 1000,
        kind = KIND_PRIVATE_MESSAGE,
        tags = tags,
        content = content
    )
}

/**
 * Create a gift-wrapped private message ready to publish.
 *
 * Flow: unsigned kind 14 → seal (kind 13) → gift wrap (kind 1059)
 *
 * Per NIP-17, the unsigned message is sealed and gift-wrapped using NIP-59.
 *
 * @param message The unsigned kind 14 private message (from createPrivateMessage)
 * @param signer The sender's signer (must be NDKPrivateKeySigner for encryption)
 * @param recipientPubkey The recipient's public key
 * @return The gift-wrapped event (kind 1059) ready to publish
 */
suspend fun wrapPrivateMessage(
    message: UnsignedEvent,
    signer: NDKPrivateKeySigner,
    recipientPubkey: String
): NDKEvent {
    require(message.kind == KIND_PRIVATE_MESSAGE) {
        "Can only wrap kind 14 private messages, got kind ${message.kind}"
    }

    // Convert the unsigned event to a rumor (NDKEvent with no signature)
    // Set the pubkey to the sender's pubkey
    val rumor = NDKEvent(
        id = "",
        pubkey = signer.pubkey,
        createdAt = message.createdAt,
        kind = message.kind,
        tags = message.tags,
        content = message.content,
        sig = null
    )

    // Use NIP-59 gift wrap flow: seal then gift wrap
    return rumor.seal(signer, recipientPubkey)
        .giftWrap(recipientPubkey)
}
