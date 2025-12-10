package io.nostr.ndk.account

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.nips.KIND_CONTACT_LIST
import io.nostr.ndk.nips.followedPubkeys
import io.nostr.ndk.outbox.RelayList
import io.nostr.ndk.user.NDKUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents the current logged-in user.
 *
 * NDKCurrentUser extends NDKUser with:
 * - The ability to sign events
 * - Auto-synced session data (follows, mutes, relay list, blocked relays)
 *
 * Session data is automatically kept in sync when events are received.
 *
 * Usage:
 * ```kotlin
 * val currentUser = ndk.login(signer)
 *
 * // Sign events
 * val signed = currentUser.sign(unsignedEvent)
 *
 * // Access reactive session data
 * currentUser.follows.collect { follows -> ... }
 * currentUser.mutes.collect { muteList -> ... }
 * ```
 */
class NDKCurrentUser(
    val signer: NDKSigner,
    ndk: NDK
) : NDKUser(signer.pubkey, ndk) {

    // Timestamps for tracking latest event per kind
    private var contactListTimestamp: Long = 0
    private var muteListTimestamp: Long = 0
    private var relayListTimestamp: Long = 0
    private var blockedRelayListTimestamp: Long = 0

    // Session data flows
    private val _follows = MutableStateFlow<Set<PublicKey>>(emptySet())
    private val _mutes = MutableStateFlow(MuteList.empty(pubkey))
    private val _relayList = MutableStateFlow<RelayList?>(null)
    private val _blockedRelays = MutableStateFlow(BlockedRelayList.empty(pubkey))

    /**
     * The pubkeys this user follows (from kind 3 contact list).
     */
    val follows: StateFlow<Set<PublicKey>> = _follows.asStateFlow()

    /**
     * The user's mute list (from kind 10000).
     */
    val mutes: StateFlow<MuteList> = _mutes.asStateFlow()

    /**
     * The user's relay list (from kind 10002).
     */
    val relayList: StateFlow<RelayList?> = _relayList.asStateFlow()

    /**
     * The user's blocked relay list (from kind 10001).
     */
    val blockedRelays: StateFlow<BlockedRelayList> = _blockedRelays.asStateFlow()

    /**
     * Signs an event using this user's signer.
     */
    suspend fun sign(event: UnsignedEvent): NDKEvent {
        return signer.sign(event)
    }

    /**
     * Handles an incoming event and updates session data if relevant.
     *
     * @param event The event to process
     */
    fun handleEvent(event: NDKEvent) {
        // Only process events from this user
        if (event.pubkey != pubkey) return

        when (event.kind) {
            KIND_CONTACT_LIST -> handleContactList(event)
            KIND_SESSION_MUTE_LIST -> handleMuteList(event)
            KIND_SESSION_RELAY_LIST -> handleRelayList(event)
            KIND_SESSION_BLOCKED_RELAY_LIST -> handleBlockedRelayList(event)
        }
    }

    private fun handleContactList(event: NDKEvent) {
        if (event.createdAt <= contactListTimestamp) return
        contactListTimestamp = event.createdAt

        _follows.value = event.followedPubkeys.toSet()
    }

    private fun handleMuteList(event: NDKEvent) {
        if (event.createdAt <= muteListTimestamp) return
        muteListTimestamp = event.createdAt

        _mutes.value = MuteList.fromEvent(event)
    }

    private fun handleRelayList(event: NDKEvent) {
        if (event.createdAt <= relayListTimestamp) return
        relayListTimestamp = event.createdAt

        _relayList.value = RelayList.fromEvent(event)
    }

    private fun handleBlockedRelayList(event: NDKEvent) {
        if (event.createdAt <= blockedRelayListTimestamp) return
        blockedRelayListTimestamp = event.createdAt

        _blockedRelays.value = BlockedRelayList.fromEvent(event)
    }

    override fun toString(): String {
        return "NDKCurrentUser(pubkey=${pubkey.take(8)}...)"
    }
}
