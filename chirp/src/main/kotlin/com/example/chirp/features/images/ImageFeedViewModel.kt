package com.example.chirp.features.images

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chirp.models.RelayFilterMode
import com.example.chirp.models.RelayFilterState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.kinds.NDKImage
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.nips.KIND_IMAGE
import io.nostr.ndk.subscription.NDKSubscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImageFeedViewModel @Inject constructor(
    val ndk: NDK
) : ViewModel() {

    private val _state = MutableStateFlow(ImageFeedState())
    val state: StateFlow<ImageFeedState> = _state.asStateFlow()

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

    fun getGalleryById(galleryId: String): NDKImage? {
        return _state.value.galleries.find { it.id == galleryId }
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
                    kinds = setOf(KIND_IMAGE),
                    authors = authors,
                    limit = 100
                )

                subscription = when (relayFilterMode) {
                    is RelayFilterMode.AllRelays -> ndk.subscribe(filter)
                    is RelayFilterMode.SingleRelay -> ndk.subscribe(filter, relays = setOf(relayFilterMode.relay))
                }

                subscription?.events?.collect { event ->
                    val image = NDKImage.from(event)
                    if (image != null && image.isValid) {
                        _state.update { state ->
                            val updated = (state.galleries + image)
                                .distinctBy { it.id }
                                .sortedByDescending { it.createdAt }
                            state.copy(
                                galleries = updated,
                                isLoading = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load galleries"
                    )
                }
            }
        }
    }

    private fun refreshFeed() {
        _state.update { it.copy(galleries = emptyList()) }
        loadFeed()
    }

    override fun onCleared() {
        subscription?.stop()
        super.onCleared()
    }
}
