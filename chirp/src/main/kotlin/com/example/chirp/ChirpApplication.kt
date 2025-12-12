package com.example.chirp

import android.app.Application
import com.example.chirp.data.RelayPreferencesRepository
import dagger.hilt.android.HiltAndroidApp
import io.nostr.ndk.NDK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ChirpApplication : Application() {

    @Inject
    lateinit var ndk: NDK

    @Inject
    lateinit var relayPreferencesRepository: RelayPreferencesRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            // Initialize default relays if first launch
            relayPreferencesRepository.initializeDefaults()

            // Load relays from preferences and add to pool
            val connectedRelays = relayPreferencesRepository.connectedRelayUrls.first()
            connectedRelays.forEach { url ->
                ndk.pool.addRelay(url, connect = false)
            }

            // Connect to relays
            ndk.connect()

            // Restore any existing accounts
            ndk.restoreAccounts()
        }
    }
}
