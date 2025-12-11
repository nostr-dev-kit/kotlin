package com.example.chirp.features.main

import com.example.chirp.models.ContentType
import io.nostr.ndk.models.NDKUser

data class MainState(
    val currentUser: NDKUser? = null,
    val isLoading: Boolean = false,
    val selectedContent: ContentType = ContentType.TextNotes
)
