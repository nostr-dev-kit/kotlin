package com.example.chirp.features.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chirp.models.ContentType
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val ndk: NDK
) : ViewModel() {

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            ndk.currentUser.collect { user ->
                // Update current user pubkey when available
                _state.update { state ->
                    state.copy(currentUserPubkey = user?.let { "logged_in" })
                }
            }
        }
    }

    fun selectContentType(contentType: ContentType) {
        _state.update { it.copy(selectedContent = contentType) }
    }
}
