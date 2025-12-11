package com.example.chirp.features.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThreadViewModel @Inject constructor(
    private val ndk: NDK,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val eventId: String = checkNotNull(savedStateHandle["eventId"])

    private val _state = MutableStateFlow(ThreadState())
    val state: StateFlow<ThreadState> = _state.asStateFlow()

    init {
        loadThread()
    }

    private fun loadThread() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                // Subscribe to the main event
                val mainEventFilter = NDKFilter(
                    ids = setOf(eventId)
                )

                val mainSubscription = ndk.subscribe(mainEventFilter)

                // Subscribe to replies (events that reference this event)
                val repliesFilter = NDKFilter(
                    kinds = setOf(1), // Text notes
                    tags = mapOf("e" to setOf(eventId))
                )

                val repliesSubscription = ndk.subscribe(repliesFilter)

                // Collect main event
                launch {
                    mainSubscription.events.collect { event ->
                        _state.update { it.copy(mainEvent = event, isLoading = false) }
                    }
                }

                // Collect replies
                launch {
                    repliesSubscription.events.collect { event ->
                        _state.update { currentState ->
                            val updatedReplies = (currentState.replies + event)
                                .distinctBy { it.id }
                                .sortedBy { it.createdAt }
                            currentState.copy(replies = updatedReplies)
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load thread"
                    )
                }
            }
        }
    }
}
