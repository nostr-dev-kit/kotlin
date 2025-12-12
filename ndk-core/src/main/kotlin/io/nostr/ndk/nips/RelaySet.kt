package io.nostr.ndk.nips

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.models.Timestamp

/**
 * Represents a relay set per NIP-51 (kind 30002).
 *
 * Relay sets are collections of relays that can be used to filter content
 * browsing to specific relay sources.
 *
 * @property identifier The d tag value - unique identifier for this relay set
 * @property title Optional display name for the relay set
 * @property description Optional description of the relay set
 * @property image Optional image/icon URL for the relay set
 * @property relays List of relay URLs in this set
 * @property author Public key of the user who created this relay set
 * @property createdAt Timestamp when this relay set was created
 */
data class RelaySet(
    val identifier: String,
    val title: String?,
    val description: String?,
    val image: String?,
    val relays: List<String>,
    val author: PublicKey,
    val createdAt: Timestamp
) {
    companion object {
        /**
         * Parses a NIP-51 relay set event (kind 30002) into a RelaySet.
         *
         * @param event The kind 30002 event to parse
         * @return A RelaySet with the parsed data
         * @throws IllegalArgumentException if event is not kind 30002
         */
        fun fromEvent(event: NDKEvent): RelaySet {
            require(event.kind == KIND_RELAY_SET) {
                "Expected kind $KIND_RELAY_SET event, got kind ${event.kind}"
            }

            val identifier = event.tagValue("d") ?: ""
            val title = event.tagValue("title")
            val description = event.tagValue("description")
            val image = event.tagValue("image")
            val relays = event.tagsWithName("relay")
                .mapNotNull { it.values.getOrNull(0) }

            return RelaySet(
                identifier = identifier,
                title = title,
                description = description,
                image = image,
                relays = relays,
                author = event.pubkey,
                createdAt = event.createdAt
            )
        }
    }
}
