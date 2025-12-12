package com.example.chirp.features.videos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.kinds.NDKVideo
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.nips.KIND_VIDEO
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

    init {
        subscribeToVideos()
    }

    fun setCurrentIndex(index: Int) {
        _state.update { it.copy(currentIndex = index) }
    }

    fun toggleMute() {
        _state.update { it.copy(isMuted = !it.isMuted) }
    }

    private fun subscribeToVideos() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                val filter = NDKFilter(
                    kinds = setOf(KIND_VIDEO),
                    limit = 50  // Start with 50 videos
                )

                val subscription = ndk.subscribe(filter)

                subscription.events.collect { event ->
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
}
