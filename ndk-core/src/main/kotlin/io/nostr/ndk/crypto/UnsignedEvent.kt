package io.nostr.ndk.crypto

import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.models.Timestamp

/**
 * Represents an unsigned Nostr event that needs to be signed.
 *
 * @property pubkey Public key of the event creator
 * @property createdAt Unix timestamp in seconds
 * @property kind Event kind
 * @property tags List of tags
 * @property content Event content
 */
data class UnsignedEvent(
    val pubkey: PublicKey,
    val createdAt: Timestamp,
    val kind: Int,
    val tags: List<NDKTag>,
    val content: String
)
