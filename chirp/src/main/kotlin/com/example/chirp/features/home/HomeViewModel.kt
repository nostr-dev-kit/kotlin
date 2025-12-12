package com.example.chirp.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.LoadFeed -> loadFeed()
            HomeIntent.RefreshFeed -> refreshFeed()
            is HomeIntent.SwitchTab -> switchTab(intent.tab)
            is HomeIntent.ReactToNote -> reactToNote(intent.eventId, intent.emoji)
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

                val filter = when (_state.value.selectedTab) {
                    FeedTab.NOTES -> NDKFilter(
                        kinds = setOf(1),
                        limit = 100
                    )
                    FeedTab.IMAGES -> NDKFilter(
                        kinds = setOf(20),
                        limit = 100
                    )
                    FeedTab.VIDEOS -> NDKFilter(
                        kinds = setOf(22),
                        limit = 100
                    )
                }

                // Create subscription based on relay filter mode
                val relayFilterMode = _relayFilterState.value.mode

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

    private fun switchTab(tab: FeedTab) {
        _state.update { it.copy(selectedTab = tab, notes = emptyList()) }
        loadFeed()
    }

    private fun reactToNote(eventId: String, emoji: String) {
        // TODO: Implement reaction publishing once we understand the NDK publish API better
        viewModelScope.launch {
            _state.update { it.copy(error = "Reactions not yet implemented") }
        }
    }

    override fun onCleared() {
        subscription?.stop()
        super.onCleared()
    }
}
