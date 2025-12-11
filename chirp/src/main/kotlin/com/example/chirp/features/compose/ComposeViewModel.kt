package com.example.chirp.features.compose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.builders.reply
import io.nostr.ndk.builders.textNote
import io.nostr.ndk.models.NDKFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComposeViewModel @Inject constructor(
    val ndk: NDK,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val replyToId: String? = savedStateHandle["replyTo"]

    private val _state = MutableStateFlow(ComposeState())
    val state: StateFlow<ComposeState> = _state.asStateFlow()

    init {
        replyToId?.let { loadReplyToEvent(it) }
    }

    private fun loadReplyToEvent(eventId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingReplyTo = true) }
            try {
                val filter = NDKFilter(ids = setOf(eventId))
                val subscription = ndk.subscribe(filter)

                launch {
                    subscription.events.collect { event ->
                        _state.update {
                            it.copy(
                                replyToEvent = event,
                                isLoadingReplyTo = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoadingReplyTo = false,
                        error = "Failed to load event: ${e.message}"
                    )
                }
            }
        }
    }

    fun onIntent(intent: ComposeIntent) {
        when (intent) {
            is ComposeIntent.UpdateContent -> updateContent(intent.content)
            is ComposeIntent.Post -> postNote()
            is ComposeIntent.Cancel -> Unit
        }
    }

    private fun updateContent(content: String) {
        _state.update { it.copy(content = content) }
    }

    private fun postNote() {
        val content = _state.value.content
        if (content.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isPosting = true, error = null) }
            try {
                val currentUser = ndk.currentUser.value
                if (currentUser == null) {
                    _state.update {
                        it.copy(
                            isPosting = false,
                            error = "No active user"
                        )
                    }
                    return@launch
                }

                val event = if (_state.value.replyToEvent != null) {
                    // Use reply builder for replies
                    _state.value.replyToEvent!!.reply()
                        .content(content)
                        .build(currentUser.signer)
                } else {
                    // Use textNote builder for new notes
                    ndk.textNote()
                        .content(content)
                        .build(currentUser.signer)
                }

                // Publish to all connected relays
                ndk.publish(event)

                _state.update {
                    it.copy(
                        isPosting = false,
                        posted = true,
                        content = ""
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isPosting = false,
                        error = e.message ?: "Failed to post"
                    )
                }
            }
        }
    }
}
