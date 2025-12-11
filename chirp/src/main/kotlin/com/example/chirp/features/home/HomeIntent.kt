package com.example.chirp.features.home

sealed interface HomeIntent {
    data object LoadFeed : HomeIntent
    data object RefreshFeed : HomeIntent
    data class SwitchTab(val tab: FeedTab) : HomeIntent
    data class ReactToNote(val eventId: String, val emoji: String) : HomeIntent
}
