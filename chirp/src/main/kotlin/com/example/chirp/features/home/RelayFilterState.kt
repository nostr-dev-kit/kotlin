package com.example.chirp.features.home

import io.nostr.ndk.nips.RelaySet

/**
 * Represents the relay filter mode for content browsing.
 */
sealed class RelayFilterMode {
    /**
     * No filter - use all app relays (default mode).
     */
    data object AllRelays : RelayFilterMode()

    /**
     * Filter content by a specific relay set.
     */
    data class RelaySetFilter(val relaySet: RelaySet) : RelayFilterMode()
}

/**
 * State for relay filtering in the feed.
 */
data class RelayFilterState(
    val mode: RelayFilterMode = RelayFilterMode.AllRelays,
    val availableRelaySets: List<RelaySet> = emptyList()
)
