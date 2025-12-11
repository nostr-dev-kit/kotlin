package com.example.chirp.features.home

import io.nostr.ndk.models.NDKEvent

data class HomeState(
    val notes: List<NDKEvent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTab: FeedTab = FeedTab.GLOBAL
)

enum class FeedTab {
    FOLLOWING,
    GLOBAL,
    NOTIFICATIONS
}
