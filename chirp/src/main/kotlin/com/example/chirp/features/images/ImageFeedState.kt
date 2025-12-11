package com.example.chirp.features.images

import io.nostr.ndk.kinds.NDKImage

data class ImageFeedState(
    val galleries: List<NDKImage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
