package io.nostr.ndk.nips

import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent

/**
 * NIP-10: Reply Threading
 *
 * Defines how e tags are used to indicate replies and mentions.
 * Supports both the newer marker-based approach and the deprecated positional approach.
 *
 * Markers:
 * - "root": the root event of the thread
 * - "reply": the event being directly replied to
 * - "mention": an event being referenced but not replied to
 */

/**
 * Parsed thread information from an event's e tags.
 */
data class ThreadInfo(
    /**
     * The root event ID of the thread, or null if this is a root event.
     */
    val root: EventId?,

    /**
     * The event ID being directly replied to, or null if not a reply.
     */
    val replyTo: EventId?,

    /**
     * List of mentioned event IDs (not direct replies).
     */
    val mentions: List<EventId>
) {
    /**
     * Returns true if this event is a root (not replying to anything).
     */
    val isRoot: Boolean get() = root == null && replyTo == null

    /**
     * Returns true if this event is a reply to another event.
     */
    val isReply: Boolean get() = replyTo != null || root != null
}

/**
 * Parses thread information from the event's e tags.
 *
 * Supports both marker-based (preferred) and positional (deprecated) approaches:
 *
 * Marker-based (NIP-10 recommended):
 * - ["e", "<event-id>", "<relay-url>", "root"] - root of thread
 * - ["e", "<event-id>", "<relay-url>", "reply"] - direct reply target
 * - ["e", "<event-id>", "<relay-url>", "mention"] - mentioned event
 *
 * Positional (deprecated):
 * - First e tag is root
 * - Last e tag is reply (if different from first)
 * - Middle e tags are mentions
 */
val NDKEvent.threadInfo: ThreadInfo
    get() {
        val eTags = tagsWithName("e")
        if (eTags.isEmpty()) {
            return ThreadInfo(root = null, replyTo = null, mentions = emptyList())
        }

        // Check for marker-based approach first
        val rootTag = eTags.find { it.values.getOrNull(2) == "root" }
        val replyTag = eTags.find { it.values.getOrNull(2) == "reply" }
        val mentionTags = eTags.filter { it.values.getOrNull(2) == "mention" }

        // If any markers are present, use marker-based parsing
        if (rootTag != null || replyTag != null || mentionTags.isNotEmpty()) {
            return ThreadInfo(
                root = rootTag?.values?.firstOrNull(),
                replyTo = replyTag?.values?.firstOrNull(),
                mentions = mentionTags.mapNotNull { it.values.firstOrNull() }
            )
        }

        // Fallback to positional (deprecated) approach
        return when (eTags.size) {
            1 -> {
                // Single e tag: could be root or reply, treat as reply-to-root
                val eventId = eTags[0].values.firstOrNull()
                ThreadInfo(
                    root = eventId,
                    replyTo = eventId,
                    mentions = emptyList()
                )
            }
            2 -> {
                // Two e tags: first is root, second is reply
                ThreadInfo(
                    root = eTags[0].values.firstOrNull(),
                    replyTo = eTags[1].values.firstOrNull(),
                    mentions = emptyList()
                )
            }
            else -> {
                // Multiple e tags: first is root, last is reply, middle are mentions
                ThreadInfo(
                    root = eTags.first().values.firstOrNull(),
                    replyTo = eTags.last().values.firstOrNull(),
                    mentions = eTags.drop(1).dropLast(1).mapNotNull { it.values.firstOrNull() }
                )
            }
        }
    }

/**
 * Returns the relay URL hint for an e tag, if present.
 */
fun NDKEvent.getReplyRelayHint(eventId: EventId): String? {
    return tagsWithName("e")
        .find { it.values.firstOrNull() == eventId }
        ?.values?.getOrNull(1)
        ?.takeIf { it.isNotBlank() && it.startsWith("wss://") }
}
