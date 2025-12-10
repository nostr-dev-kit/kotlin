package io.nostr.ndk.nips

import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey

/**
 * NIP-25: Reactions (kind 7)
 *
 * Reactions allow users to react to events with emoji or simple +/- indicators.
 *
 * Tags:
 * - e: the event being reacted to
 * - p: the author of the event being reacted to
 * - k: the kind of the event being reacted to (optional)
 *
 * Content:
 * - "+" for like
 * - "-" for dislike
 * - Custom emoji or text
 */

/**
 * Kind constant for reactions.
 */
const val KIND_REACTION = 7

/**
 * Returns true if this event is a reaction (kind 7).
 */
val NDKEvent.isReaction: Boolean
    get() = kind == KIND_REACTION

/**
 * Gets the event ID being reacted to (last e tag per NIP-25).
 */
val NDKEvent.reactionTargetEventId: EventId?
    get() = tagsWithName("e").lastOrNull()?.values?.firstOrNull()

/**
 * Gets the author of the event being reacted to (last p tag).
 */
val NDKEvent.reactionTargetAuthor: PublicKey?
    get() = tagsWithName("p").lastOrNull()?.values?.firstOrNull()

/**
 * Gets the kind of the event being reacted to (k tag).
 */
val NDKEvent.reactionTargetKind: Int?
    get() = tagValue("k")?.toIntOrNull()

/**
 * Gets the reaction content (emoji, +, -, or custom text).
 */
val NDKEvent.reactionContent: String
    get() = content

/**
 * Returns true if this is a "like" reaction (+).
 */
val NDKEvent.isLike: Boolean
    get() = isReaction && (content == "+" || content == "\uD83D\uDC4D" || content == "❤️")

/**
 * Returns true if this is a "dislike" reaction (-).
 */
val NDKEvent.isDislike: Boolean
    get() = isReaction && content == "-"

/**
 * Returns true if this reaction uses a custom emoji (not + or -).
 */
val NDKEvent.isCustomReaction: Boolean
    get() = isReaction && content != "+" && content != "-"
