package io.nostr.ndk.crypto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.PublicKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * NIP-46 Nostr Connect remote signer implementation.
 *
 * This signer delegates signing operations to a remote signer application
 * over the Nostr network using encrypted kind 24133 events.
 *
 * Usage:
 * ```kotlin
 * val remoteSigner = NDKRemoteSigner(
 *     ndk = ndk,
 *     remotePubkey = "remote_signer_pubkey_hex",
 *     relayUrls = listOf("wss://relay.nsec.app")
 * )
 *
 * remoteSigner.connect()
 * val event = remoteSigner.sign(unsignedEvent)
 * ```
 *
 * @property ndk The NDK instance for relay communication
 * @property remotePubkey The remote signer's public key
 * @property relayUrls List of relay URLs to use for communication
 * @property localKeyPair Local keypair for encrypting requests (generated if not provided)
 * @property timeoutMs Timeout for waiting for responses (default: 30000ms)
 */
class NDKRemoteSigner(
    private val ndk: NDK,
    private val remotePubkey: PublicKey,
    private val relayUrls: List<String>,
    private val localKeyPair: NDKKeyPair = NDKKeyPair.generate(),
    private val timeoutMs: Long = 30000L
) : NDKSigner {

    companion object {
        private const val KIND_NOSTR_CONNECT = 24133
        private val objectMapper: ObjectMapper = jacksonObjectMapper()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<NIP46Response>>()

    private var _pubkey: PublicKey? = null
    override val pubkey: PublicKey
        get() = _pubkey ?: throw IllegalStateException("Remote signer not connected. Call connect() first.")

    private var isConnected = false

    /**
     * Establishes connection with the remote signer and retrieves its public key.
     *
     * This method:
     * 1. Ensures relays are connected
     * 2. Subscribes to response events from the remote signer
     * 3. Sends a get_public_key request to retrieve the remote signer's key
     *
     * @throws IllegalStateException if connection fails or times out
     */
    suspend fun connect() {
        if (isConnected) return

        ensureRelaysConnected()
        subscribeToResponses()

        val response = sendRequest("get_public_key", emptyList())
        _pubkey = response.result as? String
            ?: throw IllegalStateException("Failed to get public key from remote signer")

        isConnected = true
    }

    /**
     * Signs an unsigned event by delegating to the remote signer.
     *
     * @param event The unsigned event to sign
     * @return The signed NDKEvent
     * @throws IllegalStateException if not connected or signing fails
     */
    override suspend fun sign(event: UnsignedEvent): NDKEvent {
        if (!isConnected) {
            throw IllegalStateException("Remote signer not connected. Call connect() first.")
        }

        val eventJson = serializeUnsignedEvent(event)
        val response = sendRequest("sign_event", listOf(eventJson))

        val signedEventJson = response.result as? String
            ?: throw IllegalStateException("Invalid response from remote signer: missing result")

        return NDKEvent.fromJson(signedEventJson)
    }

    /**
     * Sends an encrypted request to the remote signer and waits for response.
     *
     * @param method The NIP-46 method name (e.g., "sign_event", "get_public_key")
     * @param params The method parameters
     * @return The parsed response
     * @throws TimeoutCancellationException if response times out
     * @throws IllegalStateException if response contains an error
     */
    private suspend fun sendRequest(method: String, params: List<Any>): NIP46Response {
        val requestId = generateRequestId()
        val request = NIP46Request(id = requestId, method = method, params = params)

        val deferred = CompletableDeferred<NIP46Response>()
        pendingRequests[requestId] = deferred

        try {
            val requestJson = objectMapper.writeValueAsString(request)
            val encryptedContent = Nip44.encrypt(
                plaintext = requestJson,
                senderPrivateKey = localKeyPair.privateKey
                    ?: throw IllegalStateException("Local keypair missing private key"),
                recipientPublicKey = remotePubkey
            )

            val requestEvent = NDKPrivateKeySigner(localKeyPair).sign(
                UnsignedEvent(
                    pubkey = localKeyPair.pubkeyHex,
                    createdAt = System.currentTimeMillis() / 1000,
                    kind = KIND_NOSTR_CONNECT,
                    tags = listOf(NDKTag("p", listOf(remotePubkey))),
                    content = encryptedContent
                )
            )

            publishToRelays(requestEvent)

            return withTimeout(timeoutMs) {
                val response = deferred.await()
                if (response.error != null) {
                    throw IllegalStateException("Remote signer error: ${response.error}")
                }
                response
            }
        } finally {
            pendingRequests.remove(requestId)
        }
    }

    /**
     * Subscribes to kind 24133 events from the remote signer.
     */
    private fun subscribeToResponses() {
        val filter = NDKFilter(
            kinds = setOf(KIND_NOSTR_CONNECT),
            authors = setOf(remotePubkey),
            tags = mapOf("p" to setOf(localKeyPair.pubkeyHex))
        )

        val subscription = ndk.subscribe(filter)

        scope.launch {
            subscription.events
                .filter { it.kind == KIND_NOSTR_CONNECT }
                .collect { event ->
                    handleResponseEvent(event)
                }
        }
    }

    /**
     * Handles an incoming response event from the remote signer.
     */
    private fun handleResponseEvent(event: NDKEvent) {
        try {
            val privateKey = localKeyPair.privateKey
                ?: throw IllegalStateException("Local keypair missing private key")

            val decryptedJson = Nip44.decrypt(
                encryptedPayload = event.content,
                recipientPrivateKey = privateKey,
                senderPublicKey = remotePubkey
            )

            val response = objectMapper.readValue<NIP46Response>(decryptedJson)
            pendingRequests[response.id]?.complete(response)
        } catch (e: Exception) {
            // Ignore malformed responses
        }
    }

    /**
     * Ensures the specified relays are connected.
     */
    private suspend fun ensureRelaysConnected() {
        relayUrls.forEach { url ->
            ndk.pool.addRelay(url, connect = true)
        }
        ndk.pool.connect(timeoutMs = 5000)
    }

    /**
     * Publishes an event to all configured relays.
     */
    private suspend fun publishToRelays(event: NDKEvent) {
        relayUrls.forEach { url ->
            val relay = ndk.pool.getRelay(url)
            relay?.publish(event)
        }
    }

    /**
     * Serializes an unsigned event to JSON format expected by NIP-46.
     */
    private fun serializeUnsignedEvent(event: UnsignedEvent): String {
        val map = mapOf(
            "pubkey" to event.pubkey,
            "created_at" to event.createdAt,
            "kind" to event.kind,
            "tags" to event.tags.map { listOf(it.name) + it.values },
            "content" to event.content
        )
        return objectMapper.writeValueAsString(map)
    }

    /**
     * Generates a random request ID.
     */
    private fun generateRequestId(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Closes the remote signer and releases resources.
     */
    fun close() {
        scope.cancel()
        pendingRequests.clear()
        isConnected = false
    }
}

/**
 * NIP-46 request message.
 */
private data class NIP46Request(
    val id: String,
    val method: String,
    val params: List<Any>
)

/**
 * NIP-46 response message.
 */
private data class NIP46Response(
    val id: String,
    val result: Any?,
    val error: String?
)
