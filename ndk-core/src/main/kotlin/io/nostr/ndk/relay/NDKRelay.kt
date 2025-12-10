package io.nostr.ndk.relay

import android.util.Log
import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.Timestamp
import io.nostr.ndk.relay.messages.ClientMessage
import io.nostr.ndk.relay.messages.RelayMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private const val TAG = "NDKRelay"

/**
 * Represents a connection to a Nostr relay with state management and reconnection logic.
 *
 * @param url The WebSocket URL of the relay (e.g., "wss://relay.damus.io")
 * @param ndk The parent NDK instance
 */
class NDKRelay(
    val url: String,
    private val ndk: NDK?
) {
    private val _state = MutableStateFlow(NDKRelayState.DISCONNECTED)

    /**
     * The current connection state of the relay.
     */
    val state: StateFlow<NDKRelayState> = _state.asStateFlow()

    private var _connectionAttempts = 0

    /**
     * The number of connection attempts made.
     */
    val connectionAttempts: Int
        get() = _connectionAttempts

    private var _lastConnectedAt: Timestamp? = null

    /**
     * The timestamp when the relay was last successfully connected.
     */
    val lastConnectedAt: Timestamp?
        get() = _lastConnectedAt

    /**
     * Count of events that have been validated.
     */
    var validatedEventCount: Long = 0

    /**
     * Count of events that have not been validated.
     */
    var nonValidatedEventCount: Long = 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: NDKWebSocket? = null

    /**
     * Connects to the relay.
     */
    suspend fun connect() {
        if (_state.value == NDKRelayState.CONNECTED || _state.value == NDKRelayState.CONNECTING) {
            Log.d(TAG, "[$url] Already connected or connecting, skipping")
            return
        }

        _state.value = NDKRelayState.CONNECTING
        _connectionAttempts++
        Log.d(TAG, "[$url] Connecting... (attempt $_connectionAttempts)")

        try {
            val ws = NDKWebSocket(url, okHttpClient)
            ws.connect()
            webSocket = ws
            _state.value = NDKRelayState.CONNECTED
            _lastConnectedAt = System.currentTimeMillis() / 1000
            Log.d(TAG, "[$url] Connected successfully")

            // Start listening for messages
            scope.launch {
                ws.messages().collect { message ->
                    handleMessage(message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$url] Connection failed: ${e.message}")
            _state.value = NDKRelayState.DISCONNECTED
            throw e
        }
    }

    /**
     * Disconnects from the relay.
     */
    suspend fun disconnect() {
        webSocket?.disconnect()
        webSocket = null
        _state.value = NDKRelayState.DISCONNECTED
    }

    /**
     * Subscribes to events matching the given filters.
     *
     * @param subId The subscription ID
     * @param filters The filters to apply
     */
    internal fun subscribe(subId: String, filters: List<NDKFilter>) {
        val ws = webSocket
        if (ws == null) {
            Log.w(TAG, "[$url] Cannot subscribe - no WebSocket connection")
            return
        }
        val reqMessage = ClientMessage.Req(subId, filters)
        val json = reqMessage.toJson()
        Log.d(TAG, "[$url] Sending REQ: $json")
        scope.launch {
            try {
                ws.send(json)
                Log.d(TAG, "[$url] REQ sent successfully")
            } catch (e: Exception) {
                Log.e(TAG, "[$url] Failed to send REQ: ${e.message}")
            }
        }
    }

    /**
     * Unsubscribes from a subscription.
     *
     * @param subId The subscription ID to close
     */
    internal fun unsubscribe(subId: String) {
        val ws = webSocket ?: return
        val closeMessage = ClientMessage.Close(subId)
        scope.launch {
            try {
                ws.send(closeMessage.toJson())
            } catch (e: Exception) {
                // Log error but don't crash
            }
        }
    }

    /**
     * Publishes an event to the relay.
     *
     * @param event The event to publish
     * @return Result indicating success or failure
     */
    internal suspend fun publish(event: NDKEvent): Result<Unit> {
        val ws = webSocket ?: return Result.failure(IllegalStateException("Not connected"))
        val eventMessage = ClientMessage.Event(event)
        return try {
            ws.send(eventMessage.toJson())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Authenticates with the relay using NIP-42.
     *
     * @param challenge The authentication challenge from the relay
     */
    internal suspend fun authenticate(challenge: String) {
        _state.value = NDKRelayState.AUTHENTICATING
        // NIP-42 authentication - requires a signed event
        // For now, just mark as connected after auth required
    }

    /**
     * Handles incoming messages from the relay.
     */
    private fun handleMessage(json: String) {
        try {
            val message = RelayMessage.parse(json)
            when (message) {
                is RelayMessage.Event -> {
                    validatedEventCount++
                    Log.d(TAG, "[$url] EVENT received: kind=${message.event.kind}, id=${message.event.id.take(8)}...")
                    ndk?.subscriptionManager?.dispatchEvent(message.event, this, message.subscriptionId)
                }
                is RelayMessage.Eose -> {
                    Log.d(TAG, "[$url] EOSE received for subscription ${message.subscriptionId}")
                    ndk?.subscriptionManager?.dispatchEose(this, message.subscriptionId)
                }
                is RelayMessage.Ok -> {
                    Log.d(TAG, "[$url] OK received: ${message.eventId.take(8)}... success=${message.success}")
                }
                is RelayMessage.Notice -> {
                    Log.d(TAG, "[$url] NOTICE: ${message.message}")
                }
                is RelayMessage.Auth -> {
                    Log.d(TAG, "[$url] AUTH required")
                    _state.value = NDKRelayState.AUTH_REQUIRED
                }
                is RelayMessage.Closed -> {
                    Log.d(TAG, "[$url] CLOSED: ${message.subscriptionId} - ${message.message}")
                }
                is RelayMessage.Count -> {
                    Log.d(TAG, "[$url] COUNT: ${message.subscriptionId} = ${message.count}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$url] Failed to parse message: ${e.message}, json=${json.take(100)}...")
            nonValidatedEventCount++
        }
    }
}
