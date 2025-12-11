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
                _state.update { it.copy(currentUser = user) }
            }
        }
    }

    fun selectContentType(contentType: ContentType) {
        _state.update { it.copy(selectedContent = contentType) }
    }
}
