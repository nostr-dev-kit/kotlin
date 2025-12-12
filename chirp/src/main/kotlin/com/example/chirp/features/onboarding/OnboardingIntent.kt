package com.example.chirp.features.onboarding

import android.content.Intent

sealed interface OnboardingIntent {
    data object CreateAccount : OnboardingIntent
    data class LoginWithNsec(val nsec: String) : OnboardingIntent
    data class LoginWithBunker(val bunkerUrl: String) : OnboardingIntent
    data object LoginWithNip55 : OnboardingIntent
    data class HandleNip55Result(val resultCode: Int, val data: Intent?) : OnboardingIntent
    data object StartNostrConnect : OnboardingIntent
    data object CancelNostrConnect : OnboardingIntent
}
