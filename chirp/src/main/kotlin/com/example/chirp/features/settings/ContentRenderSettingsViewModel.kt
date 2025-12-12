package com.example.chirp.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chirp.data.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.nostr.ndk.compose.content.ContentRendererSettings
import javax.inject.Inject

/**
 * ViewModel for managing content renderer settings.
 */
@HiltViewModel
class ContentRenderSettingsViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    val settings: StateFlow<ContentRendererSettings> = appSettingsRepository.contentRendererSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ContentRendererSettings()
        )

    /**
     * Updates settings.
     */
    fun updateSettings(newSettings: ContentRendererSettings) {
        viewModelScope.launch {
            appSettingsRepository.updateContentRendererSettings(newSettings)
        }
    }

    /**
     * Resets settings to defaults.
     */
    fun resetToDefaults() {
        updateSettings(ContentRendererSettings())
    }
}
