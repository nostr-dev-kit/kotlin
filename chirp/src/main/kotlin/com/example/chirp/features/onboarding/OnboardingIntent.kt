package com.example.chirp.features.onboarding

sealed interface OnboardingIntent {
    data object CreateAccount : OnboardingIntent
    data class LoginWithNsec(val nsec: String) : OnboardingIntent
}
