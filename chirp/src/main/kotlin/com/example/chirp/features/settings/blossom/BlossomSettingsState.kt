package com.example.chirp.features.settings.blossom

data class BlossomSettingsState(
    val servers: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val newServerUrl: String = ""
)
