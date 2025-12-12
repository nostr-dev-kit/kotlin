package com.example.chirp.features.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.user.user
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    val ndk: NDK,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val pubkey: String = checkNotNull(savedStateHandle["pubkey"])

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val user = ndk.user(pubkey)
                _state.update { it.copy(user = user) }

                // Fetch user profile
                user.fetchProfile()

                // Subscribe to user's notes (kind:1)
                val notesFilter = NDKFilter(
                    authors = setOf(pubkey),
                    kinds = setOf(1),
                    limit = 50
                )

                // Subscribe to user's articles (kind:30023)
                val articlesFilter = NDKFilter(
                    authors = setOf(pubkey),
                    kinds = setOf(30023),
                    limit = 50
                )

                // Subscribe to user's images (kind:20)
                val imagesFilter = NDKFilter(
                    authors = setOf(pubkey),
                    kinds = setOf(20),
                    limit = 50
                )

                // Subscribe to user's videos (kind:22)
                val videosFilter = NDKFilter(
                    authors = setOf(pubkey),
                    kinds = setOf(22),
                    limit = 50
                )

                val notesSubscription = ndk.subscribe(notesFilter)
                val articlesSubscription = ndk.subscribe(articlesFilter)
                val imagesSubscription = ndk.subscribe(imagesFilter)
                val videosSubscription = ndk.subscribe(videosFilter)

                // Collect notes
                launch {
                    notesSubscription.events.collect { event ->
                        _state.update { currentState ->
                            val updatedNotes = (currentState.notes + event)
                                .distinctBy { it.id }
                                .sortedByDescending { it.createdAt }
                            currentState.copy(notes = updatedNotes, isLoading = false)
                        }
                    }
                }

                // Collect articles
                launch {
                    articlesSubscription.events.collect { event ->
                        _state.update { currentState ->
                            val updatedArticles = (currentState.articles + event)
                                .distinctBy { it.id }
                                .sortedByDescending { it.createdAt }
                            currentState.copy(articles = updatedArticles)
                        }
                    }
                }

                // Collect images
                launch {
                    imagesSubscription.events.collect { event ->
                        _state.update { currentState ->
                            val updatedImages = (currentState.images + event)
                                .distinctBy { it.id }
                                .sortedByDescending { it.createdAt }
                            currentState.copy(images = updatedImages)
                        }
                    }
                }

                // Collect videos
                launch {
                    videosSubscription.events.collect { event ->
                        _state.update { currentState ->
                            val updatedVideos = (currentState.videos + event)
                                .distinctBy { it.id }
                                .sortedByDescending { it.createdAt }
                            currentState.copy(videos = updatedVideos)
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load profile"
                    )
                }
            }
        }
    }
}
