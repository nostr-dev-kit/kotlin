package com.example.chirp.features.search

import io.nostr.ndk.models.NDKEvent

data class SearchState(
    val query: String = "",
    val results: List<NDKEvent> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null
)
