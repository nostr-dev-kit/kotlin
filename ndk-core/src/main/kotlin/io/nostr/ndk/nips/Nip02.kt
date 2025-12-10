package io.nostr.ndk.nips

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey

/**
 * NIP-02: Contact List (kind 3)
 *
 * Contact lists store a user's follows as p tags. Each p tag can include:
 * - pubkey: the followed user's public key
 * - relay: optional recommended relay URL for that user
 * - petname: optional local name for the user
 */

/**
 * Kind constant for contact list events.
 */
const val KIND_CONTACT_LIST = 3

/**
 * Returns true if this event is a contact list (kind 3).
 */
val NDKEvent.isContactList: Boolean
    get() = kind == KIND_CONTACT_LIST

/**
 * Represents a contact (follow) from a contact list.
 */
data class Contact(
    val pubkey: PublicKey,
    val relayUrl: String?,
    val petname: String?
)

/**
 * Gets all contacts from a contact list event.
 * Returns empty list if not a contact list.
 */
val NDKEvent.contacts: List<Contact>
    get() {
        if (!isContactList) return emptyList()

        return tagsWithName("p").map { tag ->
            Contact(
                pubkey = tag.values.getOrElse(0) { "" },
                relayUrl = tag.values.getOrNull(1)?.takeIf { it.isNotBlank() },
                petname = tag.values.getOrNull(2)?.takeIf { it.isNotBlank() }
            )
        }
    }

/**
 * Gets all followed pubkeys from a contact list.
 * Returns empty list if not a contact list.
 */
val NDKEvent.followedPubkeys: List<PublicKey>
    get() {
        if (!isContactList) return emptyList()
        return referencedPubkeys()
    }

/**
 * Checks if a pubkey is followed in this contact list.
 */
fun NDKEvent.isFollowing(pubkey: PublicKey): Boolean {
    return followedPubkeys.contains(pubkey)
}
