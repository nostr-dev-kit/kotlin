package com.example.chirp.features.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.utils.Nip19
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val ndk: NDK
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.CreateAccount -> createAccount()
            is OnboardingIntent.LoginWithNsec -> loginWithNsec(intent.nsec)
        }
    }

    private fun createAccount() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val keyPair = NDKKeyPair.generate()
                val signer = NDKPrivateKeySigner(keyPair)

                ndk.login(signer)

                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        generatedNsec = keyPair.nsec
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to create account"
                    )
                }
            }
        }
    }

    private fun loginWithNsec(nsec: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val decoded = Nip19.decode(nsec)
                require(decoded is Nip19.Decoded.Nsec) { "Invalid nsec format" }

                val keyPair = NDKKeyPair.fromPrivateKey(decoded.privateKey)
                val signer = NDKPrivateKeySigner(keyPair)

                ndk.login(signer)

                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Invalid nsec key"
                    )
                }
            }
        }
    }
}
