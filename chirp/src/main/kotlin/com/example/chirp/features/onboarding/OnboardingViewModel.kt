package com.example.chirp.features.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.crypto.NDKRemoteSigner
import io.nostr.ndk.crypto.Nip55Signer
import io.nostr.ndk.utils.Nip19
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val ndk: NDK,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private var nostrConnectSigner: NDKRemoteSigner? = null
    private var nostrConnectJob: Job? = null

    init {
        // Check if user is already logged in
        viewModelScope.launch {
            ndk.currentUser.collect { currentUser ->
                if (currentUser != null) {
                    _state.update { it.copy(isLoggedIn = true) }
                }
            }
        }

        // Check if NIP-55 signer is available
        _state.update { it.copy(isNip55Available = Nip55Signer.isSignerInstalled(context)) }
    }

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.CreateAccount -> createAccount()
            is OnboardingIntent.LoginWithNsec -> loginWithNsec(intent.nsec)
            is OnboardingIntent.LoginWithBunker -> loginWithBunker(intent.bunkerUrl)
            OnboardingIntent.LoginWithNip55 -> startNip55Login()
            is OnboardingIntent.HandleNip55Result -> handleNip55Result(intent.resultCode, intent.data)
            OnboardingIntent.StartNostrConnect -> startNostrConnect()
            OnboardingIntent.CancelNostrConnect -> cancelNostrConnect()
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

    private fun loginWithBunker(bunkerUrl: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val remoteSigner = NDKRemoteSigner.fromBunkerUrl(ndk, bunkerUrl)
                remoteSigner.connect()
                ndk.login(remoteSigner)

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
                        error = e.message ?: "Failed to connect to bunker"
                    )
                }
            }
        }
    }

    private fun startNip55Login() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:")).apply {
            setPackage(Nip55Signer.DEFAULT_SIGNER_PACKAGE)
            putExtra("type", "get_public_key")
            putExtra("id", System.currentTimeMillis().toString())
        }
        _state.update { it.copy(isLoading = true, error = null, nip55Intent = intent) }
    }

    private fun handleNip55Result(resultCode: Int, data: Intent?) {
        _state.update { it.copy(nip55Intent = null) }

        if (resultCode != Activity.RESULT_OK || data == null) {
            _state.update { it.copy(isLoading = false, error = "Signing cancelled") }
            return
        }

        val pubkey = data.getStringExtra("pubkey")
        if (pubkey == null) {
            _state.update { it.copy(isLoading = false, error = "Failed to get public key from signer") }
            return
        }

        viewModelScope.launch {
            try {
                val signer = Nip55Signer(context)
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
                        error = e.message ?: "Failed to login with signer"
                    )
                }
            }
        }
    }

    private fun startNostrConnect() {
        // Use default relays from NDK pool or fallback
        val relayUrls = ndk.pool.availableRelays.value.map { it.url }.ifEmpty {
            listOf("wss://relay.nsec.app", "wss://relay.damus.io")
        }

        val (signer, connectUrl) = NDKRemoteSigner.awaitConnection(
            ndk = ndk,
            relayUrls = relayUrls,
            appName = "Chirp",
            timeoutMs = 120000L
        )

        nostrConnectSigner = signer
        _state.update {
            it.copy(
                nostrConnectUrl = connectUrl.toUri(),
                isWaitingForNostrConnect = true,
                error = null
            )
        }

        nostrConnectJob = viewModelScope.launch {
            try {
                signer.connect()
                ndk.login(signer)

                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        nostrConnectUrl = null,
                        isWaitingForNostrConnect = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to connect",
                        nostrConnectUrl = null,
                        isWaitingForNostrConnect = false
                    )
                }
            } finally {
                nostrConnectSigner = null
                nostrConnectJob = null
            }
        }
    }

    private fun cancelNostrConnect() {
        nostrConnectJob?.cancel()
        nostrConnectJob = null
        nostrConnectSigner?.close()
        nostrConnectSigner = null
        _state.update {
            it.copy(
                nostrConnectUrl = null,
                isWaitingForNostrConnect = false,
                error = null
            )
        }
    }
}
