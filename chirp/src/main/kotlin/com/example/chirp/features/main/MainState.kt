package com.example.chirp.features.main

import com.example.chirp.models.ContentType

data class MainState(
    val currentUserPubkey: String? = null,
    val isLoading: Boolean = false,
    val selectedContent: ContentType = ContentType.TextNotes
)
