package com.example.chirp.features.relaysets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chirp.data.RelaySetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the relay set editor screen.
 *
 * Handles creating and editing relay sets (NIP-51 kind 30002).
 */
@HiltViewModel
class RelaySetEditorViewModel @Inject constructor(
    private val relaySetRepository: RelaySetRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(RelaySetEditorState())
    val state: StateFlow<RelaySetEditorState> = _state.asStateFlow()

    init {
        // If editing existing relay set, load it
        val identifier = savedStateHandle.get<String>("identifier")
        if (identifier != null) {
            loadRelaySet(identifier)
        }
    }

    private fun loadRelaySet(identifier: String) {
        _state.update { it.copy(isLoading = true, isEditMode = true) }
        // Note: Loading existing relay sets would require fetching from NDK
        // For now, we'll just set edit mode - actual loading can be added later
        _state.update { it.copy(identifier = identifier, isLoading = false) }
    }

    fun updateIdentifier(value: String) {
        _state.update { it.copy(identifier = value) }
    }

    fun updateTitle(value: String) {
        _state.update { it.copy(title = value) }
    }

    fun updateDescription(value: String) {
        _state.update { it.copy(description = value) }
    }

    fun updateImage(value: String) {
        _state.update { it.copy(image = value) }
    }

    fun addRelay(url: String) {
        val normalizedUrl = normalizeUrl(url)

        if (!isValidRelayUrl(normalizedUrl)) {
            _state.update { it.copy(error = "Invalid relay URL") }
            return
        }

        val currentRelays = _state.value.relays
        if (currentRelays.contains(normalizedUrl)) {
            _state.update { it.copy(error = "Relay already added") }
            return
        }

        _state.update {
            it.copy(
                relays = currentRelays + normalizedUrl,
                showAddRelayDialog = false
            )
        }
    }

    fun removeRelay(url: String) {
        _state.update { it.copy(relays = it.relays - url) }
    }

    fun showAddRelayDialog() {
        _state.update { it.copy(showAddRelayDialog = true) }
    }

    fun hideAddRelayDialog() {
        _state.update { it.copy(showAddRelayDialog = false) }
    }

    fun save(onSuccess: () -> Unit) {
        val currentState = _state.value

        if (currentState.identifier.isBlank()) {
            _state.update { it.copy(error = "Identifier is required") }
            return
        }

        if (currentState.title.length > 100) {
            _state.update { it.copy(error = "Title must be 100 characters or less") }
            return
        }

        _state.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val result = relaySetRepository.publishRelaySet(
                identifier = currentState.identifier,
                title = currentState.title.takeIf { it.isNotBlank() },
                description = currentState.description.takeIf { it.isNotBlank() },
                image = currentState.image.takeIf { it.isNotBlank() },
                relays = currentState.relays
            )

            result.fold(
                onSuccess = {
                    _state.update { it.copy(isSaving = false) }
                    onSuccess()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            error = "Failed to save: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun delete(onSuccess: () -> Unit) {
        val identifier = _state.value.identifier
        if (identifier.isBlank()) return

        _state.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val result = relaySetRepository.deleteRelaySet(identifier)

            result.fold(
                onSuccess = {
                    _state.update { it.copy(isSaving = false) }
                    onSuccess()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            error = "Failed to delete: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("wss://") && !normalized.startsWith("ws://")) {
            normalized = "wss://$normalized"
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }
        return normalized
    }

    private fun isValidRelayUrl(url: String): Boolean {
        return url.startsWith("wss://") || url.startsWith("ws://")
    }
}
