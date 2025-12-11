package com.example.chirp.features.thread

import io.nostr.ndk.models.NDKEvent

data class ThreadState(
    val mainEvent: NDKEvent? = null,
    val replies: List<NDKEvent> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
