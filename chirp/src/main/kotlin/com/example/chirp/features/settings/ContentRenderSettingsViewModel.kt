package com.example.chirp.features.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.nostr.ndk.compose.content.ContentRendererSettings
import javax.inject.Inject

/**
 * ViewModel for managing content renderer settings.
 */
@HiltViewModel
class ContentRenderSettingsViewModel @Inject constructor() : ViewModel() {

    private val _settings = MutableStateFlow(ContentRendererSettings())
    val settings: StateFlow<ContentRendererSettings> = _settings.asStateFlow()

    /**
     * Updates settings.
     */
    fun updateSettings(newSettings: ContentRendererSettings) {
        _settings.value = newSettings
    }

    /**
     * Resets settings to defaults.
     */
    fun resetToDefaults() {
        updateSettings(ContentRendererSettings())
    }
}
