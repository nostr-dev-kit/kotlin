package com.example.chirp.features.videos

import io.nostr.ndk.kinds.NDKVideo

data class VideoFeedState(
    val videos: List<NDKVideo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentIndex: Int = 0,
    val isMuted: Boolean = true  // Start muted (auto-play requirement)
)
