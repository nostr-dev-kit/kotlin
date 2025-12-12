package com.example.chirp.features.settings.relay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chirp.data.RelayPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.relay.NDKRelayState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for relay settings screen.
 *
 * Manages:
 * - Loading relays from DataStore
 * - Adding/removing relays
 * - Connecting/disconnecting relays
 * - Persisting connection preferences
 */
@HiltViewModel
class RelaySettingsViewModel @Inject constructor(
    private val ndk: NDK,
    private val relayPreferencesRepository: RelayPreferencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RelaySettingsState(isLoading = true))
    val state: StateFlow<RelaySettingsState> = _state.asStateFlow()

    init {
        initializeRelays()
        observePoolChanges()
    }

    /**
     * Initializes relays from DataStore on first launch.
     */
    private fun initializeRelays() {
        viewModelScope.launch {
            // Initialize default relays if needed
            relayPreferencesRepository.initializeDefaults()

            // Load relay URLs and sync with pool
            relayPreferencesRepository.relayUrls.collect { urls ->
                syncRelaysWithPool(urls)
            }
        }
    }

    /**
     * Syncs DataStore relay URLs with NDK pool.
     */
    private suspend fun syncRelaysWithPool(urls: Set<String>) {
        // Add missing relays to pool
        urls.forEach { url ->
            if (ndk.pool.getRelay(url) == null) {
                ndk.pool.addRelay(url, connect = false)
            }
        }

        // Auto-connect to relays marked as connected in preferences
        val connectedUrls = relayPreferencesRepository.connectedRelayUrls.first()
        connectedUrls.forEach { url ->
            ndk.pool.getRelay(url)?.let { relay ->
                if (relay.state.value == NDKRelayState.DISCONNECTED) {
                    relay.connect()
                }
            }
        }

        updateRelaysList()
    }

    /**
     * Observes NDK pool changes and updates UI state.
     */
    private fun observePoolChanges() {
        viewModelScope.launch {
            ndk.pool.availableRelays.collect {
                updateRelaysList()
            }
        }
    }

    /**
     * Updates the relays list in state from NDK pool.
     */
    private fun updateRelaysList() {
        val relays = ndk.pool.availableRelays.value.toList()
            .sortedBy { it.url }

        _state.update { it.copy(relays = relays, isLoading = false) }
    }

    /**
     * Adds a new relay.
     */
    fun addRelay(url: String) {
        viewModelScope.launch {
            try {
                val normalizedUrl = normalizeUrl(url)

                // Add to DataStore
                relayPreferencesRepository.addRelay(normalizedUrl)

                // Add to pool and connect
                ndk.pool.addRelay(normalizedUrl, connect = true)

                // Mark as connected in preferences
                relayPreferencesRepository.setRelayConnected(normalizedUrl, true)

                hideAddDialog()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to add relay: ${e.message}") }
            }
        }
    }

    /**
     * Removes a relay.
     */
    fun removeRelay(url: String) {
        viewModelScope.launch {
            try {
                // Remove from DataStore
                relayPreferencesRepository.removeRelay(url)

                // Remove from pool (this disconnects and cleans up)
                ndk.pool.removeRelay(url)

                updateRelaysList()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to remove relay: ${e.message}") }
            }
        }
    }

    /**
     * Toggles relay connection.
     */
    fun toggleRelayConnection(url: String, shouldConnect: Boolean) {
        viewModelScope.launch {
            try {
                val relay = ndk.pool.getRelay(url) ?: return@launch

                if (shouldConnect) {
                    relay.connect()
                    relayPreferencesRepository.setRelayConnected(url, true)
                } else {
                    relay.disconnect()
                    relayPreferencesRepository.setRelayConnected(url, false)
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to toggle connection: ${e.message}") }
            }
        }
    }

    /**
     * Shows the add relay dialog.
     */
    fun showAddDialog() {
        _state.update { it.copy(showAddDialog = true) }
    }

    /**
     * Hides the add relay dialog.
     */
    fun hideAddDialog() {
        _state.update { it.copy(showAddDialog = false) }
    }

    /**
     * Clears the error message.
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Normalizes a relay URL.
     * - Adds wss:// prefix if missing
     * - Removes trailing slash
     */
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
}
