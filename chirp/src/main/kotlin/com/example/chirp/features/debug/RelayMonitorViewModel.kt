package com.example.chirp.features.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.relay.NDKRelay
import io.nostr.ndk.relay.NDKRelayState
import io.nostr.ndk.relay.NDKRelayStatisticsSnapshot
import io.nostr.ndk.relay.nip11.Nip11RelayInformation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RelayInfo(
    val url: String,
    val state: NDKRelayState,
    val stats: NDKRelayStatisticsSnapshot,
    val nip11: Nip11RelayInformation?,
    val isTemporary: Boolean = false
)

data class RelayMonitorState(
    val relays: List<RelayInfo> = emptyList(),
    val totalRelays: Int = 0,
    val connectedRelays: Int = 0,
    val isLoading: Boolean = true,
    val selectedRelay: RelayInfo? = null
)

@HiltViewModel
class RelayMonitorViewModel @Inject constructor(
    private val ndk: NDK
) : ViewModel() {

    private val _state = MutableStateFlow(RelayMonitorState())
    val state: StateFlow<RelayMonitorState> = _state.asStateFlow()

    init {
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                refreshRelays()
                delay(2500) // Smart auto-refresh at 2.5 seconds
            }
        }
    }

    private fun refreshRelays() {
        val allRelays = ndk.pool.availableRelays.value + ndk.outboxPool.availableRelays.value
        val connectedCount = ndk.pool.connectedRelays.value.size + ndk.outboxPool.connectedRelays.value.size

        val relayInfoList = allRelays.map { relay ->
            RelayInfo(
                url = relay.url,
                state = relay.state.value,
                stats = relay.getStatistics(),
                nip11 = relay.nip11Info,
                isTemporary = !relay.autoReconnect
            )
        }.sortedWith(
            compareByDescending<RelayInfo> { it.state.isConnected }
                .thenBy { it.url }
        )

        _state.update { current ->
            current.copy(
                relays = relayInfoList,
                totalRelays = allRelays.size,
                connectedRelays = connectedCount,
                isLoading = false,
                // Update selected relay if it exists
                selectedRelay = current.selectedRelay?.let { selected ->
                    relayInfoList.find { it.url == selected.url }
                }
            )
        }
    }

    fun selectRelay(relay: RelayInfo?) {
        _state.update { it.copy(selectedRelay = relay) }
    }

    fun reconnectRelay(url: String) {
        viewModelScope.launch {
            val relay = ndk.pool.getRelay(url) ?: ndk.outboxPool.getRelay(url)
            relay?.connect()
        }
    }

    fun reconnectAll() {
        ndk.reconnect(ignoreDelay = true)
    }

    fun fetchNip11(url: String) {
        viewModelScope.launch {
            val relay = ndk.pool.getRelay(url) ?: ndk.outboxPool.getRelay(url)
            relay?.fetchNip11Info()
            refreshRelays()
        }
    }
}

private val NDKRelayState.isConnected: Boolean
    get() = this == NDKRelayState.CONNECTED || this == NDKRelayState.AUTHENTICATED
