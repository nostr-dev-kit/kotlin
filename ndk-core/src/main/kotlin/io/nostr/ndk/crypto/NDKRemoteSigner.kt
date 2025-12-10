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
 * Supports two initialization methods:
 *
 * 1. Direct initialization with pubkey and relays:
 * ```kotlin
 * val remoteSigner = NDKRemoteSigner(
 *     ndk = ndk,
 *     remotePubkey = "remote_signer_pubkey_hex",
 *     relayUrls = listOf("wss://relay.nsec.app")
 * )
 * ```
 *
 * 2. From bunker:// URL:
 * ```kotlin
 * val remoteSigner = NDKRemoteSigner.fromBunkerUrl(
 *     ndk = ndk,
 *     bunkerUrl = "bunker://pubkey?relay=wss://relay.example.com&secret=optional"
 * )
 * ```
 *
 * After initialization, call connect() to establish the connection:
 * ```kotlin
 * remoteSigner.connect()
 * val event = remoteSigner.sign(unsignedEvent)
 * ```
 *
 * @property ndk The NDK instance for relay communication
 * @property remotePubkey The remote signer's public key
 * @property relayUrls List of relay URLs to use for communication
 * @property localKeyPair Local keypair for encrypting requests (generated if not provided)
 * @property secret Optional secret for authentication with the remote signer
 * @property timeoutMs Timeout for waiting for responses (default: 30000ms)
 */
class NDKRemoteSigner private constructor(
    private val ndk: NDK,
    private val remotePubkey: PublicKey,
    private val relayUrls: List<String>,
    private val localKeyPair: NDKKeyPair,
    private val secret: String?,
    private val timeoutMs: Long
) : NDKSigner {

    /**
     * Primary constructor for direct initialization.
     */
    constructor(
        ndk: NDK,
        remotePubkey: PublicKey,
        relayUrls: List<String>,
        localKeyPair: NDKKeyPair = NDKKeyPair.generate(),
        timeoutMs: Long = 30000L
    ) : this(ndk, remotePubkey, relayUrls, localKeyPair, null, timeoutMs)

    companion object {
        private const val KIND_NOSTR_CONNECT = 24133
        private val objectMapper: ObjectMapper = jacksonObjectMapper()

        /**
         * Creates an NDKRemoteSigner from a bunker:// URL.
         *
         * URL format: bunker://<remote-signer-pubkey>?relay=<wss://relay>&secret=<optional-secret>
         *
         * @param ndk The NDK instance for relay communication
         * @param bunkerUrl The bunker URL (e.g., "bunker://abc123...?relay=wss://relay.example.com")
         * @param localKeyPair Local keypair for encrypting requests (generated if not provided)
         * @param timeoutMs Timeout for waiting for responses (default: 30000ms)
         * @return NDKRemoteSigner configured from the bunker URL
         * @throws IllegalArgumentException if the URL is invalid
         */
        fun fromBunkerUrl(
            ndk: NDK,
            bunkerUrl: String,
            localKeyPair: NDKKeyPair = NDKKeyPair.generate(),
            timeoutMs: Long = 30000L
        ): NDKRemoteSigner {
            val parsed = BunkerUrl.parse(bunkerUrl)
            return NDKRemoteSigner(
                ndk = ndk,
                remotePubkey = parsed.pubkey,
                relayUrls = parsed.relays,
                localKeyPair = localKeyPair,
                secret = parsed.secret,
                timeoutMs = timeoutMs
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<NIP46Response>>()
    private var responseSubscription: io.nostr.ndk.subscription.NDKSubscription? = null

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
     * 3. Sends a connect request if secret is provided, or get_public_key otherwise
     *
     * @throws IllegalStateException if connection fails or times out
     */
    suspend fun connect() {
        if (isConnected) return

        ensureRelaysConnected()
        subscribeToResponses()

        // If we have a secret, send connect request first
        if (secret != null) {
            val connectParams = listOf(remotePubkey, secret)
            sendRequest("connect", connectParams)
        }

        // Get the actual user's public key
        val response = sendRequest("get_public_key", emptyList())
        _pubkey = response.result as? String
            ?: throw IllegalStateException("Failed to get public key from remote signer")

        isConnected = true
    }

    override fun serialize(): ByteArray {
        throw UnsupportedOperationException(
            "NDKRemoteSigner cannot be serialized - it requires NDK instance. " +
            "Store the bunker URL or remote pubkey and relay URLs, then recreate the signer on app restart."
        )
    }

    /**
     * Signs an unsigned event by delegating to the remote signer.
     *
     * @param event The unsigned event to sign
     * @return The signed NDKEvent
     * @throws IllegalStateException if not connected or signing fails
     */
    override suspend fun sign(event: UnsignedEvent): NDKEvent {
        requireConnected()

        val eventJson = serializeUnsignedEvent(event)
        val response = sendRequest("sign_event", listOf(eventJson))

        val signedEventJson = response.result as? String
            ?: throw IllegalStateException("Invalid response from remote signer: missing result")

        return NDKEvent.fromJson(signedEventJson)
    }

    /**
     * Encrypts a message for a third party using NIP-44 encryption via the remote signer.
     *
     * This delegates the encryption to the remote signer, which has access to the
     * user's private key. The signer performs ECDH with the recipient's public key
     * and encrypts the plaintext.
     *
     * @param thirdPartyPubkey The recipient's public key (hex)
     * @param plaintext The message to encrypt
     * @return The encrypted ciphertext (base64)
     * @throws IllegalStateException if not connected or encryption fails
     */
    suspend fun nip44Encrypt(thirdPartyPubkey: PublicKey, plaintext: String): String {
        requireConnected()

        val response = sendRequest("nip44_encrypt", listOf(thirdPartyPubkey, plaintext))
        return response.result as? String
            ?: throw IllegalStateException("Invalid response from remote signer: missing result")
    }

    /**
     * Decrypts a NIP-44 encrypted message from a third party via the remote signer.
     *
     * This delegates the decryption to the remote signer, which has access to the
     * user's private key.
     *
     * @param thirdPartyPubkey The sender's public key (hex)
     * @param ciphertext The encrypted message (base64)
     * @return The decrypted plaintext
     * @throws IllegalStateException if not connected or decryption fails
     */
    suspend fun nip44Decrypt(thirdPartyPubkey: PublicKey, ciphertext: String): String {
        requireConnected()

        val response = sendRequest("nip44_decrypt", listOf(thirdPartyPubkey, ciphertext))
        return response.result as? String
            ?: throw IllegalStateException("Invalid response from remote signer: missing result")
    }

    /**
     * Encrypts a message for a third party using NIP-04 encryption via the remote signer.
     *
     * NIP-04 is the legacy encryption scheme. Prefer nip44Encrypt for new implementations.
     *
     * @param thirdPartyPubkey The recipient's public key (hex)
     * @param plaintext The message to encrypt
     * @return The encrypted ciphertext
     * @throws IllegalStateException if not connected or encryption fails
     */
    suspend fun nip04Encrypt(thirdPartyPubkey: PublicKey, plaintext: String): String {
        requireConnected()

        val response = sendRequest("nip04_encrypt", listOf(thirdPartyPubkey, plaintext))
        return response.result as? String
            ?: throw IllegalStateException("Invalid response from remote signer: missing result")
    }

    /**
     * Decrypts a NIP-04 encrypted message from a third party via the remote signer.
     *
     * NIP-04 is the legacy encryption scheme. Prefer nip44Decrypt for new implementations.
     *
     * @param thirdPartyPubkey The sender's public key (hex)
     * @param ciphertext The encrypted message
     * @return The decrypted plaintext
     * @throws IllegalStateException if not connected or decryption fails
     */
    suspend fun nip04Decrypt(thirdPartyPubkey: PublicKey, ciphertext: String): String {
        requireConnected()

        val response = sendRequest("nip04_decrypt", listOf(thirdPartyPubkey, ciphertext))
        return response.result as? String
            ?: throw IllegalStateException("Invalid response from remote signer: missing result")
    }

    /**
     * Sends a ping to the remote signer to check connection health.
     *
     * @return true if the remote signer responds successfully
     * @throws IllegalStateException if not connected
     */
    suspend fun ping(): Boolean {
        requireConnected()

        return try {
            val response = sendRequest("ping", emptyList())
            response.result == "pong"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns the local public key used for communication with the remote signer.
     * This is different from the user's pubkey, which is obtained from the remote signer.
     */
    val localPubkey: PublicKey
        get() = localKeyPair.pubkeyHex

    /**
     * Returns whether the signer is currently connected.
     */
    val connected: Boolean
        get() = isConnected

    private fun requireConnected() {
        if (!isConnected) {
            throw IllegalStateException("Remote signer not connected. Call connect() first.")
        }
    }

    /**
     * Sends an encrypted request to the remote signer and waits for response.
     *
     * @param method The NIP-46 method name (e.g., "sign_event", "get_public_key")
     * @param params The method parameters
     * @return The parsed response
     * @throws kotlinx.coroutines.TimeoutCancellationException if response times out
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
        responseSubscription = subscription

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
        responseSubscription?.stop()
        responseSubscription = null
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
