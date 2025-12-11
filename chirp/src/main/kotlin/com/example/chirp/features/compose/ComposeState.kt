package com.example.chirp.features.compose

data class ComposeState(
    val content: String = "",
    val isPosting: Boolean = false,
    val posted: Boolean = false,
    val error: String? = null,
    val replyToEventId: String? = null
)
