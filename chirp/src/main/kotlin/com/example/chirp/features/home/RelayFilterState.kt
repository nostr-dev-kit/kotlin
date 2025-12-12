package com.example.chirp.features.home

import io.nostr.ndk.relay.NDKRelay

/**
 * Represents the relay filter mode for content browsing.
 */
sealed class RelayFilterMode {
    /**
     * No filter - use all app relays (default mode).
     */
    data object AllRelays : RelayFilterMode()

    /**
     * Filter content to a single relay.
     */
    data class SingleRelay(val relay: NDKRelay) : RelayFilterMode()
}

/**
 * State for relay filtering in the feed.
 */
data class RelayFilterState(
    val mode: RelayFilterMode = RelayFilterMode.AllRelays
)
