package com.example.chirp.features.images

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.kinds.NDKImage
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.nips.KIND_IMAGE
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

    init {
        subscribeToImageGalleries()
    }

    fun getGalleryById(galleryId: String): NDKImage? {
        return _state.value.galleries.find { it.id == galleryId }
    }

    private fun subscribeToImageGalleries() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                val filter = NDKFilter(
                    kinds = setOf(KIND_IMAGE),
                    limit = 100
                )

                val subscription = ndk.subscribe(filter)

                subscription.events.collect { event ->
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
}
