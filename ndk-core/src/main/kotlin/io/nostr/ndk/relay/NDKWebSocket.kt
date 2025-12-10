package io.nostr.ndk.relay

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * WebSocket connection to a Nostr relay using OkHttp.
 *
 * This class handles the low-level WebSocket communication with a relay,
 * including connection management and message buffering via coroutine channels.
 *
 * @property url The WebSocket URL of the relay (e.g., "wss://relay.example.com")
 * @property okHttpClient The OkHttp client to use for the connection
 */
internal class NDKWebSocket(
    private val url: String,
    private val okHttpClient: OkHttpClient
) {
    /**
     * Channel-based message buffer for incoming messages.
     * Using UNLIMITED capacity prevents backpressure issues when messages arrive
     * faster than they can be processed.
     */
    private val incomingMessages = Channel<String>(Channel.UNLIMITED)

    /**
     * The underlying OkHttp WebSocket connection.
     * Null when disconnected.
     */
    private var webSocket: WebSocket? = null

    /**
     * Latency in milliseconds from the last message.
     * Extracted from response headers if available.
     */
    var latencyMs: Long? = null
        private set

    /**
     * Whether the connection supports compression.
     * Determined from the WebSocket handshake response.
     */
    var supportsCompression: Boolean = false
        private set

    /**
     * Connects to the relay's WebSocket endpoint.
     *
     * @throws IllegalStateException if already connected
     * @throws Exception if connection fails
     */
    suspend fun connect() {
        if (webSocket != null) {
            throw IllegalStateException("Already connected")
        }

        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(url)
                .build()

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    this@NDKWebSocket.webSocket = webSocket

                    // Extract latency if available
                    response.receivedResponseAtMillis.let { received ->
                        response.sentRequestAtMillis.let { sent ->
                            if (received > 0 && sent > 0) {
                                latencyMs = received - sent
                            }
                        }
                    }

                    // Check for compression support
                    supportsCompression = response.headers["Sec-WebSocket-Extensions"]
                        ?.contains("permessage-deflate") == true

                    continuation.resume(Unit)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    incomingMessages.trySend(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(t)
                    }
                    incomingMessages.close(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    incomingMessages.close()
                }
            }

            val ws = okHttpClient.newWebSocket(request, listener)

            continuation.invokeOnCancellation {
                ws.cancel()
                incomingMessages.close()
            }
        }
    }

    /**
     * Sends a message to the relay.
     *
     * @param message The message to send (typically JSON)
     * @return true if the message was enqueued, false if it failed
     * @throws IllegalStateException if not connected
     */
    suspend fun send(message: String): Boolean {
        val ws = webSocket ?: throw IllegalStateException("Not connected")
        return ws.send(message)
    }

    /**
     * Returns a Flow of incoming messages from the relay.
     *
     * Messages are buffered in an unlimited channel to prevent backpressure.
     * The flow completes when the WebSocket is closed.
     *
     * @return Flow of message strings
     */
    fun messages(): Flow<String> = incomingMessages.receiveAsFlow()

    /**
     * Disconnects from the relay.
     *
     * Sends a normal closure code (1000) and closes the channel.
     */
    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        incomingMessages.close()
    }

    /**
     * Whether the WebSocket is currently connected.
     */
    val isConnected: Boolean
        get() = webSocket != null
}
