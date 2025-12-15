package com.example.chirp.features.videos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chirp.models.RelayFilterMode
import com.example.chirp.models.RelayFilterState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.kinds.NDKVideo
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.nips.KIND_VIDEO
import io.nostr.ndk.subscription.NDKSubscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideoFeedViewModel @Inject constructor(
    val ndk: NDK
) : ViewModel() {

    private val _state = MutableStateFlow(VideoFeedState())
    val state: StateFlow<VideoFeedState> = _state.asStateFlow()

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

    fun setCurrentIndex(index: Int) {
        _state.update { it.copy(currentIndex = index) }
    }

    fun toggleMute() {
        _state.update { it.copy(isMuted = !it.isMuted) }
    }

    fun selectRelayFilter(mode: RelayFilterMode) {
        _relayFilterState.update { it.copy(mode = mode) }
        refreshFeed()
    }

    private fun loadFeed() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

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
                    kinds = setOf(KIND_VIDEO),
                    authors = authors,
                    limit = 50
                )

                subscription = when (relayFilterMode) {
                    is RelayFilterMode.AllRelays -> ndk.subscribe(filter)
                    is RelayFilterMode.SingleRelay -> ndk.subscribe(filter, relays = setOf(relayFilterMode.relay))
                }

                subscription?.events?.collect { event ->
                    val video = NDKVideo.from(event)
                    if (video != null && video.isValid) {
                        _state.update { state ->
                            val updated = (state.videos + video)
                                .distinctBy { it.id }
                                .sortedByDescending { it.publishedAt ?: it.createdAt }
                            state.copy(
                                videos = updated,
                                isLoading = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load videos"
                    )
                }
            }
        }
    }

    private fun refreshFeed() {
        _state.update { it.copy(videos = emptyList()) }
        loadFeed()
    }

    override fun onCleared() {
        subscription?.stop()
        super.onCleared()
    }
}
