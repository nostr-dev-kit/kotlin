package com.example.chirp.features.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.outbox.OutboxMetricsEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    val ndk: NDK
) : ViewModel() {

    private val _state = MutableStateFlow(DebugState())
    val state: StateFlow<DebugState> = _state.asStateFlow()

    private var eventCollectorJob: Job? = null
    private var autoRefreshJob: Job? = null

    private val maxEvents = 100

    init {
        startEventCollection()
        startAutoRefresh()
        refreshMetrics()
    }

    fun onIntent(intent: DebugIntent) {
        when (intent) {
            DebugIntent.RefreshMetrics -> refreshMetrics()
            DebugIntent.ClearEvents -> clearEvents()
            DebugIntent.ResetMetrics -> resetMetrics()
            is DebugIntent.ToggleAutoRefresh -> toggleAutoRefresh(intent.enabled)
        }
    }

    private fun startEventCollection() {
        eventCollectorJob = viewModelScope.launch {
            ndk.outboxEvents.collect { event ->
                _state.update { state ->
                    val newEvents = (listOf(event) + state.recentEvents).take(maxEvents)
                    state.copy(recentEvents = newEvents)
                }
            }
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                if (_state.value.isAutoRefresh) {
                    refreshMetrics()
                }
                delay(1000)
            }
        }
    }

    private fun refreshMetrics() {
        val snapshot = ndk.outboxMetrics.snapshot()
        val poolStatus = PoolStatus(
            mainPoolConnected = ndk.pool.connectedRelays.value.size,
            mainPoolTotal = ndk.pool.availableRelays.value.size,
            outboxPoolConnected = ndk.outboxPool.connectedRelays.value.size,
            outboxPoolTotal = ndk.outboxPool.availableRelays.value.size
        )
        _state.update { it.copy(metricsSnapshot = snapshot, poolStatus = poolStatus) }
    }

    private fun clearEvents() {
        _state.update { it.copy(recentEvents = emptyList()) }
    }

    private fun resetMetrics() {
        ndk.outboxMetrics.reset()
        refreshMetrics()
    }

    private fun toggleAutoRefresh(enabled: Boolean) {
        _state.update { it.copy(isAutoRefresh = enabled) }
    }

    override fun onCleared() {
        eventCollectorJob?.cancel()
        autoRefreshJob?.cancel()
        super.onCleared()
    }
}
