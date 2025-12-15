package io.nostr.ndk.relay

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.logging.NDKLogging
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.Timestamp
import io.nostr.ndk.relay.messages.ClientMessage
import io.nostr.ndk.relay.messages.RelayMessage
import io.nostr.ndk.relay.nip11.Nip11Cache
import io.nostr.ndk.relay.nip11.Nip11RelayInformation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "NDKRelay"

/**
 * Represents a connection to a Nostr relay with state management and reconnection logic.
 *
 * Implements automatic reconnection with exponential backoff following Amethyst's patterns:
 * - Exponential backoff: 1s → 2s → 4s → ... → 60s max
 * - Automatic subscription restoration after reconnect
 * - Flapping detection to avoid hammering failing relays
 * - Thread-safe connection management with atomic mutex
 *
 * @param url The WebSocket URL of the relay (e.g., "wss://relay.damus.io")
 * @param ndk The parent NDK instance
 */
class NDKRelay(
    val url: String,
    private val ndk: NDK?,
    private val okHttpClient: OkHttpClient
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

    // NEW: Statistics tracking
    private val statistics = NDKRelayStatistics()

    // NEW: NIP-11 information (lazy-loaded via cache)
    private var _nip11Info: Nip11RelayInformation? = null

    /**
     * Get relay statistics snapshot.
     */
    fun getStatistics(): NDKRelayStatisticsSnapshot = statistics.snapshot()

    /**
     * Get NIP-11 relay information.
     * Returns cached value if available, null otherwise.
     * Use [fetchNip11Info] to actively fetch.
     */
    val nip11Info: Nip11RelayInformation?
        get() = _nip11Info

    /**
     * Fetch NIP-11 relay information and cache it.
     * Uses the pool's cache if relay is part of a pool.
     */
    suspend fun fetchNip11Info(): Result<Nip11RelayInformation> {
        val cache = Nip11Cache()
        val result = cache.get(url)
        result.onSuccess { _nip11Info = it }
        return result
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Reconnection state
    private val reconnectionStrategy = NDKReconnectionStrategy()
    private var reconnectAttempt = 0
    private var lastConnectAttemptTime = 0L
    private val connectingMutex = AtomicBoolean(false)
    private var reconnectJob: Job? = null
    private var messageListenerJob: Job? = null

    /**
     * Whether automatic reconnection is enabled.
     * Set to false to disable reconnection (e.g., for temporary relays).
     */
    var autoReconnect = true

    // Track active subscriptions for restoration after reconnect
    private val activeSubscriptions = ConcurrentHashMap<String, List<NDKFilter>>()

    private var webSocket: NDKWebSocket? = null

    /**
     * Connects to the relay.
     *
     * Uses atomic mutex to prevent multiple simultaneous connection attempts.
     * On successful connection, resets the backoff delay and restores subscriptions.
     */
    suspend fun connect() {
        // Prevent concurrent connection attempts
        if (!connectingMutex.compareAndSet(false, true)) {
            NDKLogging.d(TAG, "[$url] Connection already in progress, skipping")
            return
        }

        try {
            if (_state.value == NDKRelayState.CONNECTED) {
                NDKLogging.d(TAG, "[$url] Already connected, skipping")
                return
            }

            _state.value = NDKRelayState.CONNECTING
            _connectionAttempts++
            lastConnectAttemptTime = System.currentTimeMillis()
            NDKLogging.d(TAG, "[$url] Connecting... (attempt $_connectionAttempts, reconnect attempt $reconnectAttempt)")

            // Track connection attempt
            statistics.recordConnectionAttempt()

            val ws = NDKWebSocket(url, okHttpClient)
            ws.connect()
            webSocket = ws
            _state.value = NDKRelayState.CONNECTED
            _lastConnectedAt = System.currentTimeMillis() / 1000

            // Track successful connection
            statistics.recordSuccessfulConnection(_lastConnectedAt!! * 1000)

            // Reset backoff on successful connection
            reconnectAttempt = 0
            NDKLogging.d(TAG, "[$url] Connected successfully")

            // Start listening for messages
            messageListenerJob = scope.launch {
                try {
                    ws.messages().collect { message ->
                        handleMessage(message)
                    }
                } catch (e: Exception) {
                    NDKLogging.e(TAG, "[$url] Message listener error: ${e.message}")
                    handleDisconnection(e)
                }
            }

            // Restore subscriptions after reconnect
            restoreSubscriptions()

        } catch (e: Exception) {
            NDKLogging.e(TAG, "[$url] Connection failed: ${e.message}")
            _state.value = NDKRelayState.DISCONNECTED
            scheduleReconnect()
        } finally {
            connectingMutex.set(false)
        }
    }

    /**
     * Disconnects from the relay.
     *
     * @param permanent If true, disables auto-reconnect and clears subscriptions.
     *                  If false (default), allows reconnection and preserves subscriptions.
     */
    suspend fun disconnect(permanent: Boolean = false) {
        NDKLogging.d(TAG, "[$url] Disconnecting (permanent=$permanent)")

        // Cancel any pending reconnect
        reconnectJob?.cancel()
        reconnectJob = null

        // Cancel message listener
        messageListenerJob?.cancel()
        messageListenerJob = null

        if (permanent) {
            autoReconnect = false
            activeSubscriptions.clear()
            reconnectAttempt = 0
        }

        // Track disconnection
        statistics.recordDisconnection()

        webSocket?.disconnect()
        webSocket = null
        _state.value = NDKRelayState.DISCONNECTED
    }

    /**
     * Handles unexpected disconnection by scheduling a reconnect.
     */
    private fun handleDisconnection(error: Exception? = null) {
        NDKLogging.w(TAG, "[$url] Disconnected unexpectedly: ${error?.message}")

        webSocket = null
        _state.value = NDKRelayState.DISCONNECTED

        // Check for flapping (connected for less than 1 second)
        val connectionDuration = System.currentTimeMillis() - ((_lastConnectedAt ?: 0) * 1000)
        if (reconnectionStrategy.isFlapping(connectionDuration)) {
            NDKLogging.w(TAG, "[$url] Connection flapping detected, increasing backoff")
            reconnectAttempt++ // Extra penalty for flapping
        }

        scheduleReconnect()
    }

    /**
     * Schedules a reconnection attempt with exponential backoff.
     */
    private fun scheduleReconnect() {
        if (!autoReconnect) {
            NDKLogging.d(TAG, "[$url] Auto-reconnect disabled, not scheduling reconnect")
            return
        }

        if (reconnectAttempt >= NDKReconnectionStrategy.MAX_ATTEMPTS) {
            NDKLogging.w(TAG, "[$url] Max reconnection attempts reached, giving up")
            _state.value = NDKRelayState.DISCONNECTED
            return
        }

        val delayMs = reconnectionStrategy.nextDelay(reconnectAttempt)
        reconnectAttempt++

        NDKLogging.d(TAG, "[$url] Scheduling reconnect in ${delayMs}ms (attempt $reconnectAttempt)")
        _state.value = NDKRelayState.RECONNECTING

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            try {
                connect()
            } catch (e: Exception) {
                NDKLogging.e(TAG, "[$url] Reconnect attempt failed: ${e.message}")
            }
        }
    }

    /**
     * Attempts to reconnect immediately if not already connecting.
     * Respects the backoff delay unless [ignoreDelay] is true.
     *
     * @param ignoreDelay If true, bypasses the backoff delay
     */
    fun reconnectIfDisconnected(ignoreDelay: Boolean = false) {
        if (_state.value == NDKRelayState.CONNECTED ||
            _state.value == NDKRelayState.CONNECTING ||
            _state.value == NDKRelayState.RECONNECTING) {
            return
        }

        val timeSinceLastAttempt = System.currentTimeMillis() - lastConnectAttemptTime
        val requiredDelay = reconnectionStrategy.nextDelay(reconnectAttempt)

        if (ignoreDelay || timeSinceLastAttempt >= requiredDelay) {
            scope.launch { connect() }
        }
    }

    /**
     * Restores all tracked subscriptions after a reconnect.
     */
    private fun restoreSubscriptions() {
        if (activeSubscriptions.isEmpty()) {
            NDKLogging.d(TAG, "[$url] No subscriptions to restore")
            return
        }

        NDKLogging.d(TAG, "[$url] Restoring ${activeSubscriptions.size} subscriptions")
        activeSubscriptions.forEach { (subId, filters) ->
            sendSubscription(subId, filters)
        }
    }

    /**
     * Subscribes to events matching the given filters.
     * Tracks the subscription for automatic restoration after reconnect.
     *
     * @param subId The subscription ID
     * @param filters The filters to apply
     */
    internal fun subscribe(subId: String, filters: List<NDKFilter>) {
        // Track for restoration after reconnect
        activeSubscriptions[subId] = filters

        // Track subscription
        statistics.recordSubscriptionAdded()

        if (webSocket == null) {
            NDKLogging.w(TAG, "[$url] Cannot subscribe yet - no WebSocket connection, will send on connect")
            return
        }

        sendSubscription(subId, filters)
    }

    /**
     * Sends the subscription REQ message to the relay.
     */
    private fun sendSubscription(subId: String, filters: List<NDKFilter>) {
        val ws = webSocket ?: return

        val reqMessage = ClientMessage.Req(subId, filters)
        val json = reqMessage.toJson()
        NDKLogging.d(TAG, "[$url] Sending REQ: $json")

        scope.launch {
            try {
                ws.send(json)
                NDKLogging.d(TAG, "[$url] REQ sent successfully")
            } catch (e: Exception) {
                NDKLogging.e(TAG, "[$url] Failed to send REQ: ${e.message}")
            }
        }
    }

    /**
     * Unsubscribes from a subscription.
     * Removes from tracking so it won't be restored after reconnect.
     *
     * @param subId The subscription ID to close
     */
    internal fun unsubscribe(subId: String) {
        // Remove from tracking
        activeSubscriptions.remove(subId)

        // Track subscription removal
        statistics.recordSubscriptionRemoved()

        val ws = webSocket ?: return
        val closeMessage = ClientMessage.Close(subId)

        scope.launch {
            try {
                ws.send(closeMessage.toJson())
            } catch (e: Exception) {
                NDKLogging.w(TAG, "[$url] Failed to send CLOSE: ${e.message}")
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
        val json = eventMessage.toJson()

        // Track sent message bytes
        statistics.recordMessageSent(json.length)

        return try {
            ws.send(json)
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
        val signer = ndk?.signer
        if (signer == null) {
            NDKLogging.w(TAG, "[$url] Cannot authenticate - no signer configured")
            return
        }

        _state.value = NDKRelayState.AUTHENTICATING
        NDKLogging.d(TAG, "[$url] Authenticating with challenge: ${challenge.take(16)}...")

        // Track auth attempt
        statistics.recordAuthAttempt()

        try {
            // Create NIP-42 AUTH event (kind 22242)
            val unsignedEvent = UnsignedEvent(
                pubkey = signer.pubkey,
                createdAt = System.currentTimeMillis() / 1000,
                kind = 22242,
                tags = listOf(
                    NDKTag("relay", listOf(url)),
                    NDKTag("challenge", listOf(challenge))
                ),
                content = ""
            )

            // Sign the event
            val signedEvent = signer.sign(unsignedEvent)
            NDKLogging.d(TAG, "[$url] Auth event signed: ${signedEvent.id.take(8)}...")

            // Send AUTH message
            val ws = webSocket
            if (ws == null) {
                NDKLogging.e(TAG, "[$url] Cannot send AUTH - no WebSocket connection")
                _state.value = NDKRelayState.AUTH_REQUIRED
                return
            }

            val authMessage = ClientMessage.Auth(signedEvent)
            ws.send(authMessage.toJson())
            NDKLogging.d(TAG, "[$url] AUTH message sent")

            // State will be updated to AUTHENTICATED when we receive OK response
            // For now, consider ourselves authenticated after sending
            _state.value = NDKRelayState.AUTHENTICATED

            // Track auth success
            statistics.recordAuthSuccess()

        } catch (e: Exception) {
            NDKLogging.e(TAG, "[$url] Authentication failed: ${e.message}")
            _state.value = NDKRelayState.AUTH_REQUIRED
        }
    }

    /**
     * Handles incoming messages from the relay.
     */
    private fun handleMessage(json: String) {
        // Track received message bytes
        statistics.recordMessageReceived(json.length)

        try {
            val message = RelayMessage.parse(json)
            when (message) {
                is RelayMessage.Event -> {
                    validatedEventCount++

                    // Track validated event
                    statistics.recordValidatedEvent()

                    NDKLogging.d(TAG, "[$url] EVENT received: kind=${message.event.kind}, id=${message.event.id.take(8)}...")
                    ndk?.subscriptionManager?.dispatchEvent(message.event, this, message.subscriptionId)
                }
                is RelayMessage.Eose -> {
                    NDKLogging.d(TAG, "[$url] EOSE received for subscription ${message.subscriptionId}")
                    ndk?.subscriptionManager?.dispatchEose(this, message.subscriptionId)
                }
                is RelayMessage.Ok -> {
                    NDKLogging.d(TAG, "[$url] OK received: ${message.eventId.take(8)}... success=${message.success}")
                }
                is RelayMessage.Notice -> {
                    NDKLogging.d(TAG, "[$url] NOTICE: ${message.message}")
                }
                is RelayMessage.Auth -> {
                    NDKLogging.d(TAG, "[$url] AUTH required: ${message.challenge.take(16)}...")
                    _state.value = NDKRelayState.AUTH_REQUIRED

                    // Automatically attempt authentication if signer is available
                    if (ndk?.signer != null) {
                        scope.launch {
                            authenticate(message.challenge)
                        }
                    } else {
                        NDKLogging.w(TAG, "[$url] Cannot auto-authenticate - no signer configured")
                    }
                }
                is RelayMessage.Closed -> {
                    NDKLogging.d(TAG, "[$url] CLOSED: ${message.subscriptionId} - ${message.message}")
                }
                is RelayMessage.Count -> {
                    NDKLogging.d(TAG, "[$url] COUNT: ${message.subscriptionId} = ${message.count}")
                }
            }
        } catch (e: Exception) {
            NDKLogging.e(TAG, "[$url] Failed to parse message: ${e.message}, json=${json.take(100)}...")
            nonValidatedEventCount++

            // Track non-validated event
            statistics.recordNonValidatedEvent()
        }
    }

    /**
     * Cleans up all resources associated with this relay.
     * Call this when the relay is being permanently removed.
     */
    fun close() {
        NDKLogging.d(TAG, "[$url] Closing relay")
        autoReconnect = false
        reconnectJob?.cancel()
        messageListenerJob?.cancel()
        webSocket?.disconnect()
        webSocket = null
        activeSubscriptions.clear()
        scope.cancel()
    }
}
