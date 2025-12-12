package com.example.chirp.features.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.relay.AggregatedRelayStatistics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RelayValidationInfo(
    val url: String,
    val validatedEvents: Long,
    val nonValidatedEvents: Long,
    val validationRate: Float,
    val activeSubscriptions: Long,
    val uniqueMessageRate: Float
)

data class SubscriptionsState(
    val aggregatedStats: AggregatedRelayStatistics? = null,
    val relayValidation: List<RelayValidationInfo> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val ndk: NDK
) : ViewModel() {

    private val _state = MutableStateFlow(SubscriptionsState())
    val state: StateFlow<SubscriptionsState> = _state.asStateFlow()

    init {
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                refreshStats()
                delay(2500)
            }
        }
    }

    private fun refreshStats() {
        val aggregated = ndk.pool.getAggregatedStatistics()

        val allRelays = ndk.pool.availableRelays.value + ndk.outboxPool.availableRelays.value
        val validationInfo = allRelays.map { relay ->
            val stats = relay.getStatistics()
            RelayValidationInfo(
                url = relay.url,
                validatedEvents = stats.validatedEvents,
                nonValidatedEvents = stats.nonValidatedEvents,
                validationRate = stats.validationRate,
                activeSubscriptions = stats.activeSubscriptions,
                uniqueMessageRate = stats.uniqueMessageRate
            )
        }.filter { it.validatedEvents + it.nonValidatedEvents > 0 }
            .sortedByDescending { it.validatedEvents + it.nonValidatedEvents }

        _state.update {
            it.copy(
                aggregatedStats = aggregated,
                relayValidation = validationInfo,
                isLoading = false
            )
        }
    }
}
