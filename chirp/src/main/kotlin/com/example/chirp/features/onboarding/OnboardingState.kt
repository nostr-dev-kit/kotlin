package com.example.chirp.features.onboarding

data class OnboardingState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
    val generatedNsec: String? = null
)
