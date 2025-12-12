package com.example.chirp.features.settings.relay

import io.nostr.ndk.relay.NDKRelay

/**
 * UI state for relay settings screen.
 */
data class RelaySettingsState(
    /** List of all relays */
    val relays: List<NDKRelay> = emptyList(),

    /** Whether data is loading */
    val isLoading: Boolean = false,

    /** Error message if any */
    val error: String? = null,

    /** Whether the add relay dialog is shown */
    val showAddDialog: Boolean = false
)
