package com.example.chirp.features.settings.blossom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.blossom.NDKBlossom
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.NDKTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlossomSettingsViewModel @Inject constructor(
    private val ndk: NDK
) : ViewModel() {

    private val _state = MutableStateFlow(BlossomSettingsState())
    val state: StateFlow<BlossomSettingsState> = _state.asStateFlow()

    init {
        loadServerList()
    }

    private fun loadServerList() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val signer = ndk.currentUser.value?.signer
                if (signer == null) {
                    _state.update { it.copy(isLoading = false, error = "No active user") }
                    return@launch
                }

                val filter = NDKFilter(
                    kinds = setOf(NDKBlossom.KIND_BLOSSOM_SERVER_LIST),
                    authors = setOf(signer.pubkey)
                )

                val subscription = ndk.subscribe(filter)

                // Wait for first event or timeout
                val events = mutableListOf<io.nostr.ndk.models.NDKEvent>()
                kotlinx.coroutines.withTimeoutOrNull(5000) {
                    subscription.events.collect { event ->
                        events.add(event)
                        return@collect
                    }
                }
                subscription.stop()

                val servers = events.firstOrNull()?.tags
                    ?.filter { it.name == "server" || it.name == "r" }
                    ?.mapNotNull { it.values.firstOrNull() }
                    ?: emptyList()

                _state.update {
                    it.copy(
                        servers = servers,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load servers: ${e.message}"
                    )
                }
            }
        }
    }

    fun onNewServerUrlChanged(url: String) {
        _state.update { it.copy(newServerUrl = url) }
    }

    fun onAddServer() {
        val url = state.value.newServerUrl.trim()
        if (url.isBlank()) return

        // Normalize URL
        val normalizedUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url.trimEnd('/')
        } else {
            "https://${url.trimEnd('/')}"
        }

        if (state.value.servers.contains(normalizedUrl)) {
            _state.update { it.copy(error = "Server already in list") }
            return
        }

        _state.update {
            it.copy(
                servers = it.servers + normalizedUrl,
                newServerUrl = "",
                error = null
            )
        }
    }

    fun onRemoveServer(url: String) {
        _state.update {
            it.copy(servers = it.servers - url)
        }
    }

    fun onMoveServerUp(index: Int) {
        if (index <= 0) return
        _state.update {
            val servers = it.servers.toMutableList()
            val temp = servers[index]
            servers[index] = servers[index - 1]
            servers[index - 1] = temp
            it.copy(servers = servers)
        }
    }

    fun onMoveServerDown(index: Int) {
        if (index >= state.value.servers.size - 1) return
        _state.update {
            val servers = it.servers.toMutableList()
            val temp = servers[index]
            servers[index] = servers[index + 1]
            servers[index + 1] = temp
            it.copy(servers = servers)
        }
    }

    fun onSave() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }

            try {
                val signer = ndk.currentUser.value?.signer
                    ?: throw Exception("No active user")

                // Create kind 10063 event with server tags
                val tags = state.value.servers.map { url ->
                    NDKTag("server", listOf(url))
                }

                val unsigned = UnsignedEvent(
                    pubkey = signer.pubkey,
                    createdAt = System.currentTimeMillis() / 1000,
                    kind = NDKBlossom.KIND_BLOSSOM_SERVER_LIST,
                    tags = tags,
                    content = ""
                )

                val signedEvent = signer.sign(unsigned)
                ndk.publish(signedEvent)

                _state.update {
                    it.copy(isSaving = false)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = "Failed to save: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
