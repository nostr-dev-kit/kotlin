package io.nostr.ndk.account

import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.models.Timestamp

/**
 * Kind constants for session data events.
 *
 * Note: KIND_BLOCKED_RELAY_LIST uses 10001, which overlaps with NIP-51's PIN_LIST.
 * This appears to be non-standard usage. In NIP-51, kind 10001 is for pin lists.
 * The session management uses it for blocked relays as per the plan specification.
 */
const val KIND_SESSION_MUTE_LIST = 10000
const val KIND_SESSION_BLOCKED_RELAY_LIST = 10001
const val KIND_SESSION_RELAY_LIST = 10002

/**
 * Represents a user's mute list (kind 10000).
 */
data class MuteList(
    val pubkey: PublicKey,
    val createdAt: Timestamp,
    val pubkeys: Set<PublicKey>,
    val eventIds: Set<EventId>,
    val words: Set<String>,
    val hashtags: Set<String>
) {
    /**
     * Checks if a pubkey is muted.
     */
    fun isMuted(pubkey: PublicKey): Boolean = pubkeys.contains(pubkey)

    /**
     * Checks if an event ID is muted.
     */
    fun isMutedEvent(eventId: EventId): Boolean = eventIds.contains(eventId)

    /**
     * Checks if content contains a muted word.
     */
    fun containsMutedWord(content: String): Boolean {
        val lowerContent = content.lowercase()
        return words.any { lowerContent.contains(it.lowercase()) }
    }

    companion object {
        fun fromEvent(event: NDKEvent): MuteList {
            require(event.kind == KIND_SESSION_MUTE_LIST) { "Expected kind $KIND_SESSION_MUTE_LIST, got ${event.kind}" }

            val pubkeys = mutableSetOf<PublicKey>()
            val eventIds = mutableSetOf<EventId>()
            val words = mutableSetOf<String>()
            val hashtags = mutableSetOf<String>()

            event.tags.forEach { tag ->
                when (tag.name) {
                    "p" -> tag.values.firstOrNull()?.let { pubkeys.add(it) }
                    "e" -> tag.values.firstOrNull()?.let { eventIds.add(it) }
                    "word" -> tag.values.firstOrNull()?.let { words.add(it) }
                    "t" -> tag.values.firstOrNull()?.let { hashtags.add(it) }
                }
            }

            return MuteList(
                pubkey = event.pubkey,
                createdAt = event.createdAt,
                pubkeys = pubkeys,
                eventIds = eventIds,
                words = words,
                hashtags = hashtags
            )
        }

        fun empty(pubkey: PublicKey): MuteList = MuteList(
            pubkey = pubkey,
            createdAt = 0L,
            pubkeys = emptySet(),
            eventIds = emptySet(),
            words = emptySet(),
            hashtags = emptySet()
        )
    }
}

/**
 * Represents a user's blocked relay list (kind 10001).
 */
data class BlockedRelayList(
    val pubkey: PublicKey,
    val createdAt: Timestamp,
    val relays: Set<String>
) {
    /**
     * Checks if a relay URL is blocked.
     */
    fun isBlocked(url: String): Boolean = relays.contains(normalizeUrl(url))

    companion object {
        fun fromEvent(event: NDKEvent): BlockedRelayList {
            require(event.kind == KIND_SESSION_BLOCKED_RELAY_LIST) { "Expected kind $KIND_SESSION_BLOCKED_RELAY_LIST, got ${event.kind}" }

            val relays = mutableSetOf<String>()

            event.tags.forEach { tag ->
                if (tag.name == "relay") {
                    tag.values.firstOrNull()?.let { relays.add(normalizeUrl(it)) }
                }
            }

            return BlockedRelayList(
                pubkey = event.pubkey,
                createdAt = event.createdAt,
                relays = relays
            )
        }

        fun empty(pubkey: PublicKey): BlockedRelayList = BlockedRelayList(
            pubkey = pubkey,
            createdAt = 0L,
            relays = emptySet()
        )

        private fun normalizeUrl(url: String): String {
            var normalized = url.lowercase()
            if (!normalized.startsWith("wss://") && !normalized.startsWith("ws://")) {
                normalized = "wss://$normalized"
            }
            if (normalized.endsWith("/")) {
                normalized = normalized.dropLast(1)
            }
            return normalized
        }
    }
}
