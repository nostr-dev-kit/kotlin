package io.nostr.ndk.user

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.nips.Contact
import io.nostr.ndk.nips.KIND_CONTACT_LIST
import io.nostr.ndk.nips.contacts
import io.nostr.ndk.nips.followedPubkeys
import io.nostr.ndk.subscription.NDKSubscription
import io.nostr.ndk.utils.Nip19
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Represents a Nostr user and provides convenient access to user data.
 *
 * NDKUser provides a high-level API for working with user profiles,
 * contact lists, and user-specific queries.
 *
 * Usage:
 * ```kotlin
 * val user = ndk.user("npub1...")
 * user.fetchProfile()
 *
 * // Reactive profile access
 * user.profile.collect { profile ->
 *     println("Name: ${profile?.name}")
 * }
 *
 * // Get user's notes
 * val notes = user.notes()
 * notes.events.collect { note ->
 *     println(note.content)
 * }
 * ```
 */
open class NDKUser(
    val pubkey: PublicKey,
    protected val ndk: NDK
) {
    private val objectMapper = jacksonObjectMapper()

    private val _profile = MutableStateFlow<UserProfile?>(null)

    /**
     * The user's profile (kind 0 metadata), as a StateFlow.
     * Automatically updated when fetchProfile() is called.
     */
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _contactList = MutableStateFlow<NDKEvent?>(null)

    /**
     * The user's contact list event (kind 3).
     */
    val contactListEvent: StateFlow<NDKEvent?> = _contactList.asStateFlow()

    /**
     * Returns the npub (bech32-encoded) representation of the user's pubkey.
     */
    val npub: String by lazy {
        Nip19.encodeNpub(pubkey)
    }

    /**
     * Fetches the user's profile (kind 0) from relays.
     *
     * @return Subscription that will emit profile events
     */
    fun fetchProfile(): NDKSubscription {
        val filter = NDKFilter(
            kinds = setOf(0),
            authors = setOf(pubkey),
            limit = 1
        )

        val subscription = ndk.subscribe(filter)

        // Update internal state when profile is received
        subscription.events.map { event ->
            if (event.kind == 0) {
                parseProfile(event)?.let { _profile.value = it }
            }
        }

        return subscription
    }

    /**
     * Fetches the user's contact list (kind 3) from relays.
     *
     * @return Subscription that will emit contact list events
     */
    fun fetchContactList(): NDKSubscription {
        val filter = NDKFilter(
            kinds = setOf(KIND_CONTACT_LIST),
            authors = setOf(pubkey),
            limit = 1
        )

        val subscription = ndk.subscribe(filter)

        subscription.events.map { event ->
            if (event.kind == KIND_CONTACT_LIST) {
                _contactList.value = event
            }
        }

        return subscription
    }

    /**
     * Returns the user's followed pubkeys from the contact list.
     */
    fun follows(): List<PublicKey> {
        return _contactList.value?.followedPubkeys ?: emptyList()
    }

    /**
     * Returns the user's contacts with metadata.
     */
    fun contacts(): List<Contact> {
        return _contactList.value?.contacts ?: emptyList()
    }

    /**
     * Creates a subscription for the user's text notes (kind 1).
     *
     * @param limit Maximum number of notes to fetch
     * @param since Only fetch notes after this timestamp
     * @param until Only fetch notes before this timestamp
     */
    fun notes(
        limit: Int? = 50,
        since: Long? = null,
        until: Long? = null
    ): NDKSubscription {
        val filter = NDKFilter(
            kinds = setOf(1),
            authors = setOf(pubkey),
            limit = limit,
            since = since,
            until = until
        )
        return ndk.subscribe(filter)
    }

    /**
     * Creates a subscription for reactions to the user's events.
     *
     * @param limit Maximum number of reactions to fetch
     */
    fun reactions(limit: Int? = 100): NDKSubscription {
        val filter = NDKFilter(
            kinds = setOf(7),
            tags = mapOf("p" to setOf(pubkey)),
            limit = limit
        )
        return ndk.subscribe(filter)
    }

    /**
     * Creates a subscription for zaps received by the user.
     *
     * @param limit Maximum number of zaps to fetch
     */
    fun zaps(limit: Int? = 50): NDKSubscription {
        val filter = NDKFilter(
            kinds = setOf(9735),
            tags = mapOf("p" to setOf(pubkey)),
            limit = limit
        )
        return ndk.subscribe(filter)
    }

    /**
     * Creates a subscription for the user's followers (people who follow this user).
     *
     * @param limit Maximum number of contact lists to check
     */
    fun followers(limit: Int? = 500): NDKSubscription {
        val filter = NDKFilter(
            kinds = setOf(KIND_CONTACT_LIST),
            tags = mapOf("p" to setOf(pubkey)),
            limit = limit
        )
        return ndk.subscribe(filter)
    }

    /**
     * Parses a kind 0 metadata event into a UserProfile.
     */
    private fun parseProfile(event: NDKEvent): UserProfile? {
        return try {
            val json: Map<String, Any?> = objectMapper.readValue(event.content)
            UserProfile(
                pubkey = event.pubkey,
                name = json["name"] as? String,
                displayName = json["display_name"] as? String,
                about = json["about"] as? String,
                picture = json["picture"] as? String,
                banner = json["banner"] as? String,
                nip05 = json["nip05"] as? String,
                lud16 = json["lud16"] as? String,
                lud06 = json["lud06"] as? String,
                website = json["website"] as? String,
                createdAt = event.createdAt
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NDKUser) return false
        return pubkey == other.pubkey
    }

    override fun hashCode(): Int {
        return pubkey.hashCode()
    }

    override fun toString(): String {
        return "NDKUser(pubkey=${pubkey.take(8)}...)"
    }
}

/**
 * Parsed user profile from kind 0 metadata event.
 */
data class UserProfile(
    val pubkey: PublicKey,
    val name: String?,
    val displayName: String?,
    val about: String?,
    val picture: String?,
    val banner: String?,
    val nip05: String?,
    val lud16: String?,
    val lud06: String?,
    val website: String?,
    val createdAt: Long
) {
    /**
     * Returns the best available display name.
     * Priority: displayName > name > shortened pubkey
     */
    val bestName: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: "${pubkey.take(8)}..."

    /**
     * Returns the Lightning address (prefers lud16 over lud06).
     */
    val lightningAddress: String?
        get() = lud16?.takeIf { it.isNotBlank() } ?: lud06?.takeIf { it.isNotBlank() }
}

/**
 * Extension function to create an NDKUser from a pubkey.
 */
fun NDK.user(pubkey: PublicKey): NDKUser {
    return NDKUser(pubkey, this)
}

/**
 * Extension function to create an NDKUser from an npub.
 */
fun NDK.userFromNpub(npub: String): NDKUser? {
    return when (val decoded = Nip19.decode(npub)) {
        is Nip19.Decoded.Npub -> NDKUser(decoded.pubkey, this)
        else -> null
    }
}
