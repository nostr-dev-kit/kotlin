package io.nostr.ndk.outbox

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.models.Timestamp

/**
 * Represents a user's relay list per NIP-65.
 *
 * NIP-65 defines kind 10002 events that specify a user's preferred relays
 * for reading and writing. Relays can be marked as:
 * - "read" - User reads from this relay (their inbox)
 * - "write" - User writes to this relay (their outbox)
 * - No marker - Both read and write
 *
 * @property pubkey The public key of the user who owns this relay list
 * @property createdAt When this relay list was published
 * @property readRelays Set of relay URLs where this user reads from
 * @property writeRelays Set of relay URLs where this user writes to
 */
data class RelayList(
    val pubkey: PublicKey,
    val createdAt: Timestamp,
    val readRelays: Set<String>,
    val writeRelays: Set<String>
) {
    /**
     * All relays in this list (union of read and write).
     */
    val allRelays: Set<String>
        get() = readRelays + writeRelays

    /**
     * Check if a relay is in the read list.
     */
    fun isReadRelay(url: String): Boolean = readRelays.contains(normalizeUrl(url))

    /**
     * Check if a relay is in the write list.
     */
    fun isWriteRelay(url: String): Boolean = writeRelays.contains(normalizeUrl(url))

    companion object {
        /**
         * Parses a NIP-65 relay list event (kind 10002) into a RelayList.
         *
         * @param event The kind 10002 event to parse
         * @return A RelayList with the parsed relay preferences
         */
        fun fromEvent(event: NDKEvent): RelayList {
            require(event.kind == 10002) { "Expected kind 10002 event, got kind ${event.kind}" }

            val readRelays = mutableSetOf<String>()
            val writeRelays = mutableSetOf<String>()

            event.tags.forEach { tag ->
                if (tag.name == "r" && tag.values.isNotEmpty()) {
                    val url = normalizeUrl(tag.values[0])
                    val marker = tag.values.getOrNull(1)

                    when (marker) {
                        "read" -> readRelays.add(url)
                        "write" -> writeRelays.add(url)
                        else -> {
                            // No marker = both read and write
                            readRelays.add(url)
                            writeRelays.add(url)
                        }
                    }
                }
            }

            return RelayList(
                pubkey = event.pubkey,
                createdAt = event.createdAt,
                readRelays = readRelays,
                writeRelays = writeRelays
            )
        }

        /**
         * Normalizes a relay URL:
         * - Adds wss:// if no scheme
         * - Lowercases
         * - Removes trailing slash
         */
        private fun normalizeUrl(url: String): String {
            var normalized = url.lowercase()

            // Add wss:// if no scheme
            if (!normalized.startsWith("wss://") && !normalized.startsWith("ws://")) {
                normalized = "wss://$normalized"
            }

            // Remove trailing slash
            if (normalized.endsWith("/")) {
                normalized = normalized.dropLast(1)
            }

            return normalized
        }
    }
}
