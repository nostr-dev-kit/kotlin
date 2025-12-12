package com.example.chirp.features.onboarding

import android.content.Intent

data class OnboardingState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
    val generatedNsec: String? = null,
    val isNip55Available: Boolean = false,
    val nip55Intent: Intent? = null,
    val nostrConnectUrl: String? = null,
    val isWaitingForNostrConnect: Boolean = false
)
