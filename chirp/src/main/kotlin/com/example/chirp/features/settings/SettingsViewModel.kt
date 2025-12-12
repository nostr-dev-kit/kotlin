package com.example.chirp.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val ndk: NDK
) : ViewModel() {

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            ndk.logout()
            onComplete()
        }
    }
}
