package com.example.chirp.features.profile

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.user.NDKUser

data class ProfileState(
    val user: NDKUser? = null,
    val notes: List<NDKEvent> = emptyList(),
    val articles: List<NDKEvent> = emptyList(),
    val images: List<NDKEvent> = emptyList(),
    val videos: List<NDKEvent> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val hasArticles: Boolean get() = articles.isNotEmpty()
    val hasImages: Boolean get() = images.isNotEmpty()
    val hasVideos: Boolean get() = videos.isNotEmpty()

    val featuredArticles: List<NDKEvent> get() {
        val oneYearAgo = System.currentTimeMillis() / 1000 - (365 * 24 * 60 * 60)
        return articles.filter { it.createdAt >= oneYearAgo }.take(5)
    }
}
