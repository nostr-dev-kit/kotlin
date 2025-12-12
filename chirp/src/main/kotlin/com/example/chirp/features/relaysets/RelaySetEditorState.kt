package com.example.chirp.features.relaysets

/**
 * UI state for the relay set editor screen.
 */
data class RelaySetEditorState(
    val identifier: String = "",
    val title: String = "",
    val description: String = "",
    val image: String = "",
    val relays: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val showAddRelayDialog: Boolean = false,
    val isEditMode: Boolean = false
)
