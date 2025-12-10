package io.nostr.ndk.models

/**
 * Type-safe representation of a Nostr event tag.
 *
 * Tags are name-value pairs where the first element is the tag name
 * and subsequent elements are the tag values.
 *
 * Example: ["e", "event_id", "relay_url", "marker"]
 */
data class NDKTag(
    val name: String,
    val values: List<String>
) {
    /**
     * Access tag values by index. Returns null if index is out of bounds.
     */
    operator fun get(index: Int): String? = values.getOrNull(index)

    companion object {
        /**
         * Creates an event reference tag (e tag).
         *
         * @param id The event ID being referenced
         * @param relay Optional relay URL where the event can be found
         * @param marker Optional marker (e.g., "reply", "root", "mention")
         */
        fun event(id: EventId, relay: String? = null, marker: String? = null): NDKTag {
            val values = mutableListOf(id)
            if (relay != null) {
                values.add(relay)
                if (marker != null) {
                    values.add(marker)
                }
            }
            return NDKTag("e", values)
        }

        /**
         * Creates a pubkey reference tag (p tag).
         *
         * @param pubkey The public key being referenced
         * @param relay Optional relay URL where the user can be found
         * @param petname Optional pet name for the user
         */
        fun pubkey(pubkey: PublicKey, relay: String? = null, petname: String? = null): NDKTag {
            val values = mutableListOf(pubkey)
            if (relay != null) {
                values.add(relay)
                if (petname != null) {
                    values.add(petname)
                }
            }
            return NDKTag("p", values)
        }

        /**
         * Creates a reference tag (r tag) for URLs.
         *
         * @param ref The URL being referenced
         */
        fun reference(ref: String): NDKTag {
            return NDKTag("r", listOf(ref))
        }

        /**
         * Creates a hashtag tag (t tag).
         *
         * @param tag The hashtag (without the # symbol)
         */
        fun hashtag(tag: String): NDKTag {
            return NDKTag("t", listOf(tag))
        }

        /**
         * Creates a delegation tag (delegation tag).
         *
         * @param pubkey The delegator's public key
         * @param conditions The delegation conditions
         * @param sig The delegation signature
         */
        fun delegation(pubkey: PublicKey, conditions: String, sig: Signature): NDKTag {
            return NDKTag("delegation", listOf(pubkey, conditions, sig))
        }
    }
}
