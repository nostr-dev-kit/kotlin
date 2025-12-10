package io.nostr.ndk.nips

import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey

/**
 * NIP-01 extension properties for text notes (kind 1).
 *
 * Text notes are the basic kind of event for short text content,
 * similar to tweets or posts.
 */

/**
 * Kind constant for text notes.
 */
const val KIND_TEXT_NOTE = 1

/**
 * Returns true if this event is a text note (kind 1).
 */
val NDKEvent.isTextNote: Boolean
    get() = kind == KIND_TEXT_NOTE

/**
 * Gets the root event ID from e tags with "root" marker (NIP-10).
 * Falls back to the first e tag if no marker is present.
 */
val NDKEvent.rootEventId: EventId?
    get() {
        // Look for explicit root marker first
        val rootTag = tagsWithName("e").find { tag ->
            tag.values.getOrNull(2) == "root"
        }
        if (rootTag != null) {
            return rootTag.values.firstOrNull()
        }

        // Fallback: first e tag is root (deprecated NIP-10 positional)
        return tagsWithName("e").firstOrNull()?.values?.firstOrNull()
    }

/**
 * Gets the reply-to event ID from e tags with "reply" marker (NIP-10).
 * Falls back to the last e tag if no marker is present.
 */
val NDKEvent.replyToEventId: EventId?
    get() {
        // Look for explicit reply marker first
        val replyTag = tagsWithName("e").find { tag ->
            tag.values.getOrNull(2) == "reply"
        }
        if (replyTag != null) {
            return replyTag.values.firstOrNull()
        }

        // Fallback: last e tag is reply (deprecated NIP-10 positional)
        val eTags = tagsWithName("e")
        return if (eTags.size > 1) eTags.lastOrNull()?.values?.firstOrNull() else null
    }

/**
 * Gets all mentioned event IDs from e tags with "mention" marker.
 */
val NDKEvent.mentionedEventIds: List<EventId>
    get() = tagsWithName("e")
        .filter { it.values.getOrNull(2) == "mention" }
        .mapNotNull { it.values.firstOrNull() }

/**
 * Gets all mentioned pubkeys from p tags.
 */
val NDKEvent.mentionedPubkeys: List<PublicKey>
    get() = referencedPubkeys()

/**
 * Gets all hashtags from t tags.
 */
val NDKEvent.hashtags: List<String>
    get() = tagsWithName("t").mapNotNull { it.values.firstOrNull() }
