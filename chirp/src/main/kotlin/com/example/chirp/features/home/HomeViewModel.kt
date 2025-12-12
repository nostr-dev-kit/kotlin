package com.example.chirp.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chirp.data.RelaySetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.relay.NDKRelay
import io.nostr.ndk.subscription.NDKSubscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    val ndk: NDK,
    private val relaySetRepository: RelaySetRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _relayFilterState = MutableStateFlow(RelayFilterState())
    val relayFilterState: StateFlow<RelayFilterState> = _relayFilterState.asStateFlow()

    private var subscription: NDKSubscription? = null

    init {
        loadFeed()
        loadRelaySets()
    }

    private fun loadRelaySets() {
        viewModelScope.launch {
            // Get user's own relay sets
            ndk.currentUser.value?.pubkey?.let { userPubkey ->
                relaySetRepository.fetchOwnRelaySets(userPubkey).collect { ownSets ->
                    // Get relay sets from follows
                    val follows = ndk.currentUser.value?.contacts()?.map { it.pubkey } ?: emptyList()
                    if (follows.isNotEmpty()) {
                        relaySetRepository.fetchRelaySetsFromFollows(follows).collect { followSets ->
                            _relayFilterState.update {
                                it.copy(availableRelaySets = ownSets + followSets)
                            }
                        }
                    } else {
                        _relayFilterState.update {
                            it.copy(availableRelaySets = ownSets)
                        }
                    }
                }
            }
        }
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
                    FeedTab.FOLLOWING -> {
                        val contacts = ndk.currentUser.value?.contacts()?.map { it.pubkey }?.toSet() ?: emptySet()
                        if (contacts.isEmpty()) {
                            // If no contacts, show global feed
                            NDKFilter(
                                kinds = setOf(1),
                                limit = 100
                            )
                        } else {
                            NDKFilter(
                                kinds = setOf(1),
                                authors = contacts,
                                limit = 100
                            )
                        }
                    }
                    FeedTab.GLOBAL -> NDKFilter(
                        kinds = setOf(1),
                        limit = 100
                    )
                    FeedTab.NOTIFICATIONS -> {
                        val userPubkey = ndk.currentUser.value?.pubkey ?: ""
                        NDKFilter(
                            kinds = setOf(1),
                            tags = mapOf("p" to setOf(userPubkey)),
                            limit = 100
                        )
                    }
                }

                // Create subscription based on relay filter mode
                val relayFilterMode = _relayFilterState.value.mode

                // When using a relay set filter, ensure those relays are in the pool
                if (relayFilterMode is RelayFilterMode.RelaySetFilter) {
                    relayFilterMode.relaySet.relays.forEach { url ->
                        if (ndk.pool.getRelay(url) == null) {
                            ndk.pool.addRelay(url, connect = true)
                        }
                    }
                }

                subscription = ndk.subscribe(filter)

                subscription?.events?.collect { event ->
                    // Filter events by relay source if using relay set filter
                    val shouldInclude = when (relayFilterMode) {
                        is RelayFilterMode.AllRelays -> true
                        is RelayFilterMode.RelaySetFilter -> {
                            // Check if event came from any of the relay set's relays
                            // Note: This requires the event to have relay metadata
                            // For now, we'll include all events as NDK doesn't expose relay source
                            true
                        }
                    }

                    if (shouldInclude) {
                        _state.update { state ->
                            state.copy(
                                notes = (state.notes + event).distinctBy { it.id }.sortedByDescending { it.createdAt },
                                isLoading = false
                            )
                        }
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
