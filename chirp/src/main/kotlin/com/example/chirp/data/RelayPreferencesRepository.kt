package com.example.chirp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.relayPreferencesDataStore by preferencesDataStore(name = "relay_preferences")

/**
 * Repository for persisting relay URLs and connection preferences using DataStore.
 *
 * Stores:
 * - All configured relay URLs
 * - Which relays should auto-connect on startup
 */
@Singleton
class RelayPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val RELAY_URLS_KEY = stringSetPreferencesKey("relay_urls")
    private val CONNECTED_RELAYS_KEY = stringSetPreferencesKey("connected_relays")

    companion object {
        /**
         * Default relays configured on first launch.
         */
        val DEFAULT_RELAYS = setOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.nostr.band"
        )
    }

    /**
     * Flow of all relay URLs (both connected and disconnected).
     */
    val relayUrls: Flow<Set<String>> = context.relayPreferencesDataStore.data.map { prefs ->
        prefs[RELAY_URLS_KEY] ?: DEFAULT_RELAYS
    }

    /**
     * Flow of relay URLs that should be connected on startup.
     */
    val connectedRelayUrls: Flow<Set<String>> = context.relayPreferencesDataStore.data.map { prefs ->
        prefs[CONNECTED_RELAYS_KEY] ?: DEFAULT_RELAYS
    }

    /**
     * Adds a relay URL to the list.
     */
    suspend fun addRelay(url: String) {
        context.relayPreferencesDataStore.edit { prefs ->
            val current = prefs[RELAY_URLS_KEY] ?: DEFAULT_RELAYS
            prefs[RELAY_URLS_KEY] = current + url
        }
    }

    /**
     * Removes a relay URL from the list.
     * Also removes it from connected relays.
     */
    suspend fun removeRelay(url: String) {
        context.relayPreferencesDataStore.edit { prefs ->
            val currentUrls = prefs[RELAY_URLS_KEY] ?: DEFAULT_RELAYS
            val currentConnected = prefs[CONNECTED_RELAYS_KEY] ?: DEFAULT_RELAYS

            prefs[RELAY_URLS_KEY] = currentUrls - url
            prefs[CONNECTED_RELAYS_KEY] = currentConnected - url
        }
    }

    /**
     * Sets whether a relay should be connected.
     *
     * @param url The relay URL
     * @param connected Whether the relay should auto-connect
     */
    suspend fun setRelayConnected(url: String, connected: Boolean) {
        context.relayPreferencesDataStore.edit { prefs ->
            val current = prefs[CONNECTED_RELAYS_KEY] ?: DEFAULT_RELAYS
            prefs[CONNECTED_RELAYS_KEY] = if (connected) {
                current + url
            } else {
                current - url
            }
        }
    }

    /**
     * Initializes with default relays if no relays are configured.
     * Should be called on app startup.
     */
    suspend fun initializeDefaults() {
        context.relayPreferencesDataStore.edit { prefs ->
            if (prefs[RELAY_URLS_KEY] == null) {
                prefs[RELAY_URLS_KEY] = DEFAULT_RELAYS
                prefs[CONNECTED_RELAYS_KEY] = DEFAULT_RELAYS
            }
        }
    }
}
