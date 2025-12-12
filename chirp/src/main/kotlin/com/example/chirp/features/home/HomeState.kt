package com.example.chirp.features.home

import io.nostr.ndk.models.NDKEvent

data class HomeState(
    val notes: List<NDKEvent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTab: FeedTab = FeedTab.NOTES
)

enum class FeedTab {
    NOTES,
    IMAGES,
    VIDEOS
}
