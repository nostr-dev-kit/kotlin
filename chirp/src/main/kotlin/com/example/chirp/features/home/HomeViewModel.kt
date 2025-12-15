package com.example.chirp.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chirp.models.RelayFilterMode
import com.example.chirp.models.RelayFilterState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.subscription.NDKSubscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    val ndk: NDK
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _relayFilterState = MutableStateFlow(RelayFilterState())
    val relayFilterState: StateFlow<RelayFilterState> = _relayFilterState.asStateFlow()

    private var subscription: NDKSubscription? = null

    init {
        loadFeed()

        // Reload feed when activeFollows changes (e.g., user follows someone new)
        viewModelScope.launch {
            ndk.activeFollows.collect { follows ->
                if (follows.isNotEmpty() && subscription != null) {
                    refreshFeed()
                }
            }
        }
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.LoadFeed -> loadFeed()
            HomeIntent.RefreshFeed -> refreshFeed()
        }
    }

    fun selectRelayFilter(mode: RelayFilterMode) {
        _relayFilterState.update { it.copy(mode = mode) }
        refreshFeed()
    }

    private fun loadFeed() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                subscription?.stop()

                val relayFilterMode = _relayFilterState.value.mode

                // When exploring a specific relay, show all content (no author filter)
                // Otherwise, filter by followed authors
                val authors = when (relayFilterMode) {
                    is RelayFilterMode.SingleRelay -> null
                    is RelayFilterMode.AllRelays -> ndk.activeFollows.value.ifEmpty { null }
                }

                val filter = NDKFilter(
                    kinds = setOf(1),
                    authors = authors,
                    limit = 100
                )

                subscription = when (relayFilterMode) {
                    is RelayFilterMode.AllRelays -> ndk.subscribe(filter)
                    is RelayFilterMode.SingleRelay -> ndk.subscribe(filter, relays = setOf(relayFilterMode.relay))
                }

                subscription?.events?.collect { event ->
                    _state.update { state ->
                        state.copy(
                            notes = (state.notes + event).distinctBy { it.id }.sortedByDescending { it.createdAt },
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load feed"
                    )
                }
            }
        }
    }

    private fun refreshFeed() {
        _state.update { it.copy(notes = emptyList()) }
        loadFeed()
    }

    override fun onCleared() {
        subscription?.stop()
        super.onCleared()
    }
}
