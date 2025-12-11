package com.example.chirp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.nostr.ndk.NDK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ChirpApplication : Application() {

    @Inject
    lateinit var ndk: NDK

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            // Connect to relays
            ndk.connect()

            // Restore any existing accounts
            ndk.restoreAccounts()
        }
    }
}
