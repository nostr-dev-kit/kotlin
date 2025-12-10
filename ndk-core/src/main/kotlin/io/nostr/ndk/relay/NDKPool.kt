package io.nostr.ndk.relay

import io.nostr.ndk.NDK
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages a pool of relay connections with observable state and lifecycle events.
 *
 * The pool handles:
 * - Adding and removing relays
 * - Bulk connect/disconnect operations
 * - Tracking connected vs available relays
 * - Temporary relays with auto-removal after idle timeout
 * - URL normalization (wss:// prefix, trailing slash removal)
 *
 * @param ndk The parent NDK instance
 */
class NDKPool(private val ndk: NDK) {
    private val relays = ConcurrentHashMap<String, NDKRelay>()
    private val temporaryRelayJobs = ConcurrentHashMap<String, Job>()
    private val relayStateJobs = ConcurrentHashMap<String, Job>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _availableRelays = MutableStateFlow<Set<NDKRelay>>(emptySet())
    /**
     * All relays in the pool, regardless of connection status.
     */
    val availableRelays: StateFlow<Set<NDKRelay>> = _availableRelays.asStateFlow()

    private val _connectedRelays = MutableStateFlow<Set<NDKRelay>>(emptySet())
    /**
     * Relays that are currently connected.
     */
    val connectedRelays: StateFlow<Set<NDKRelay>> = _connectedRelays.asStateFlow()

    private val _events = MutableSharedFlow<PoolEvent>(replay = 0, extraBufferCapacity = 64)
    /**
     * Flow of pool events (connect, disconnect, auth, removal, etc.).
     */
    val events: SharedFlow<PoolEvent> = _events.asSharedFlow()

    /**
     * Adds a relay to the pool.
     *
     * @param url The relay URL (will be normalized)
     * @param connect Whether to immediately connect to the relay
     * @return The relay instance (existing or newly created)
     */
    fun addRelay(url: String, connect: Boolean = true): NDKRelay {
        val normalizedUrl = normalizeUrl(url)

        val relay = relays.getOrPut(normalizedUrl) {
            val newRelay = NDKRelay(normalizedUrl, ndk)
            startMonitoringRelay(newRelay)
            newRelay
        }

        updateAvailableRelays()

        if (connect) {
            scope.launch {
                relay.connect()
            }
        }

        return relay
    }

    /**
     * Starts monitoring a relay's state changes.
     */
    private fun startMonitoringRelay(relay: NDKRelay) {
        val job = scope.launch {
            var previousState: NDKRelayState? = null
            relay.state.collect { state ->
                when (state) {
                    NDKRelayState.CONNECTED, NDKRelayState.AUTHENTICATED -> {
                        updateConnectedRelays()
                        if (previousState != NDKRelayState.CONNECTED && previousState != NDKRelayState.AUTHENTICATED) {
                            _events.emit(PoolEvent.RelayConnected(relay))
                        }
                    }
                    NDKRelayState.DISCONNECTED -> {
                        updateConnectedRelays()
                        if (previousState != null && previousState != NDKRelayState.DISCONNECTED) {
                            _events.emit(PoolEvent.RelayDisconnected(relay, null))
                        }
                    }
                    NDKRelayState.AUTH_REQUIRED -> {
                        // Handle auth required event if needed
                    }
                    else -> {
                        updateConnectedRelays()
                    }
                }
                previousState = state
            }
        }
        relayStateJobs[relay.url] = job
    }

    /**
     * Removes a relay from the pool.
     *
     * @param url The relay URL (will be normalized)
     */
    fun removeRelay(url: String) {
        val normalizedUrl = normalizeUrl(url)

        relays.remove(normalizedUrl)?.let { relay ->
            // Cancel monitoring job
            relayStateJobs.remove(normalizedUrl)?.cancel()

            // Cancel temporary relay job if exists
            temporaryRelayJobs.remove(normalizedUrl)?.cancel()

            // Close the relay (cleans up all resources including reconnection jobs)
            relay.close()

            scope.launch {
                _events.emit(PoolEvent.RelayRemoved(normalizedUrl))
            }

            updateAvailableRelays()
            updateConnectedRelays()
        }
    }

    /**
     * Triggers reconnection for all disconnected relays.
     *
     * @param ignoreDelay If true, bypasses backoff delays
     */
    fun reconnectAll(ignoreDelay: Boolean = false) {
        relays.values.forEach { relay ->
            relay.reconnectIfDisconnected(ignoreDelay)
        }
    }

    /**
     * Gets a relay by URL.
     *
     * @param url The relay URL (will be normalized)
     * @return The relay instance or null if not found
     */
    fun getRelay(url: String): NDKRelay? {
        val normalizedUrl = normalizeUrl(url)
        return relays[normalizedUrl]
    }

    /**
     * Connects to all relays in the pool and waits for at least one to connect.
     *
     * @param timeoutMs Maximum time to wait for connections
     */
    suspend fun connect(timeoutMs: Long = 5000) {
        // Start connections for any relays not already connecting
        relays.values.forEach { relay ->
            scope.launch {
                try {
                    relay.connect()
                } catch (e: Exception) {
                    // Log but don't fail
                }
            }
        }

        // Wait for at least one relay to connect (with timeout)
        withTimeoutOrNull(timeoutMs) {
            connectedRelays.first { it.isNotEmpty() }
        }

        // Ensure connected relays is up to date
        updateConnectedRelays()
    }

    /**
     * Disconnects all relays in the pool.
     */
    suspend fun disconnect() {
        coroutineScope {
            relays.values.forEach { relay ->
                launch {
                    try {
                        relay.disconnect()
                    } catch (e: Exception) {
                        // Log but don't fail the entire operation
                    }
                }
            }
        }
    }

    /**
     * Adds a temporary relay that will be auto-removed after the specified idle timeout.
     * Temporary relays do not auto-reconnect if disconnected.
     *
     * @param url The relay URL (will be normalized)
     * @param idleTimeoutMs Time in milliseconds before auto-removal
     * @return The relay instance
     */
    fun addTemporaryRelay(url: String, idleTimeoutMs: Long = 30_000): NDKRelay {
        val relay = addRelay(url, connect = true)
        val normalizedUrl = normalizeUrl(url)

        // Disable auto-reconnect for temporary relays
        relay.autoReconnect = false

        // Cancel existing timeout job if any
        temporaryRelayJobs[normalizedUrl]?.cancel()

        // Schedule removal after idle timeout
        val job = scope.launch {
            delay(idleTimeoutMs)
            removeRelay(normalizedUrl)
        }

        temporaryRelayJobs[normalizedUrl] = job

        return relay
    }

    /**
     * Normalizes a relay URL by:
     * - Adding wss:// prefix if missing
     * - Removing trailing slash
     *
     * @param url The URL to normalize
     * @return The normalized URL
     */
    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()

        // Add wss:// prefix if missing
        if (!normalized.startsWith("wss://") && !normalized.startsWith("ws://")) {
            normalized = "wss://$normalized"
        }

        // Remove trailing slash
        if (normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }

        return normalized
    }

    /**
     * Updates the availableRelays state flow.
     */
    private fun updateAvailableRelays() {
        _availableRelays.value = relays.values.toSet()
    }

    /**
     * Updates the connectedRelays state flow.
     */
    private fun updateConnectedRelays() {
        val connected = relays.values.filter { relay ->
            relay.state.value == NDKRelayState.CONNECTED ||
            relay.state.value == NDKRelayState.AUTHENTICATED
        }.toSet()
        _connectedRelays.value = connected
    }

    /**
     * Cleans up resources when the pool is no longer needed.
     * Closes all relays and cancels all pending jobs.
     */
    fun close() {
        // Cancel all monitoring jobs
        relayStateJobs.values.forEach { it.cancel() }
        relayStateJobs.clear()

        // Cancel all temporary relay jobs
        temporaryRelayJobs.values.forEach { it.cancel() }
        temporaryRelayJobs.clear()

        // Close all relays (this stops reconnection attempts and cleans up resources)
        relays.values.forEach { it.close() }
        relays.clear()

        // Update state
        _availableRelays.value = emptySet()
        _connectedRelays.value = emptySet()

        // Cancel the pool scope
        scope.cancel()
    }
}
