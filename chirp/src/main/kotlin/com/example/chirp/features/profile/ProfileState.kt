package com.example.chirp.features.profile

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.user.NDKUser

data class ProfileState(
    val user: NDKUser? = null,
    val notes: List<NDKEvent> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
