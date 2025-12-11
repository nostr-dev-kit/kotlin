package com.example.chirp.features.compose

import io.nostr.ndk.models.NDKEvent

data class ComposeState(
    val content: String = "",
    val isPosting: Boolean = false,
    val posted: Boolean = false,
    val error: String? = null,
    val replyToEvent: NDKEvent? = null,
    val isLoadingReplyTo: Boolean = false
)
