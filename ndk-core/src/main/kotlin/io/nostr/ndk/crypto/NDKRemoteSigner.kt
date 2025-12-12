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
 * Supports three initialization methods:
 *
 * 1. Direct initialization with pubkey and relays (signer-initiated):
 * ```kotlin
 * val remoteSigner = NDKRemoteSigner(
 *     ndk = ndk,
 *     remotePubkey = "remote_signer_pubkey_hex",
 *     relayUrls = listOf("wss://relay.nsec.app")
 * )
 * remoteSigner.connect()
 * ```
 *
 * 2. From bunker:// URL (signer-initiated):
 * ```kotlin
 * val remoteSigner = NDKRemoteSigner.fromBunkerUrl(
 *     ndk = ndk,
 *     bunkerUrl = "bunker://pubkey?relay=wss://relay.example.com&secret=optional"
 * )
 * remoteSigner.connect()
 * ```
 *
 * 3. Client-initiated with nostrconnect:// URI:
 * ```kotlin
 * val (signer, connectUrl) = NDKRemoteSigner.awaitConnection(
 *     ndk = ndk,
 *     relayUrls = listOf("wss://relay.example.com"),
 *     appName = "MyApp"
 * )
 * // Display connectUrl.toUri() as QR code or deeplink
 * signer.connect() // Blocks until remote signer connects
 * ```
 *
 * @property ndk The NDK instance for relay communication
 * @property remotePubkey The remote signer's public key (null for client-initiated until connected)
 * @property relayUrls List of relay URLs to use for communication
 * @property localKeyPair Local keypair for encrypting requests (generated if not provided)
 * @property secret Optional secret for authentication with the remote signer
 * @property timeoutMs Timeout for waiting for responses (default: 30000ms)
 */
class NDKRemoteSigner private constructor(
    private val ndk: NDK,
    private var remotePubkey: PublicKey?,
    private val relayUrls: List<String>,
    private val localKeyPair: NDKKeyPair,
    private val secret: String?,
    private val timeoutMs: Long,
    private val isClientInitiated: Boolean = false
) : NDKSigner {

    /**
     * Primary constructor for direct initialization (signer-initiated).
     */
    constructor(
        ndk: NDK,
        remotePubkey: PublicKey,
        relayUrls: List<String>,
        localKeyPair: NDKKeyPair = NDKKeyPair.generate(),
        timeoutMs: Long = 30000L
    ) : this(ndk, remotePubkey, relayUrls, localKeyPair, null, timeoutMs, false)

    companion object {
        private const val KIND_NOSTR_CONNECT = 24133
        private val objectMapper: ObjectMapper = jacksonObjectMapper()

        init {
            // Register deserializer that creates a deferred signer
            // The signer requires an NDK instance which isn't available during deserialization
            NDKSigner.registerDeserializer("NDKRemoteSigner") { data ->
                val remotePubkey = data["remotePubkey"] as? String ?: return@registerDeserializer null
                val relayUrls = (data["relayUrls"] as? List<*>)?.mapNotNull { it as? String }
                    ?: return@registerDeserializer null

                val localPrivateKeyHex = data["localPrivateKeyHex"] as? String
                val localPublicKeyHex = data["localPublicKeyHex"] as? String
                    ?: return@registerDeserializer null

                val localKeyPair = if (!localPrivateKeyHex.isNullOrEmpty()) {
                    NDKKeyPair.fromPrivateKey(localPrivateKeyHex)
                } else {
                    NDKKeyPair.fromPublicKey(localPublicKeyHex)
                }

                val secret = (data["secret"] as? String)?.takeIf { it.isNotEmpty() }
                val timeoutMs = (data["timeoutMs"] as? Number)?.toLong() ?: 30000L
                val userPubkey = data["userPubkey"] as? String

                // Return a deferred signer that will be initialized with NDK later
                NDKDeferredRemoteSigner(
                    remotePubkey = remotePubkey,
                    relayUrls = relayUrls,
                    localKeyPair = localKeyPair,
                    secret = secret,
                    timeoutMs = timeoutMs,
                    userPubkey = userPubkey
                )
            }
        }

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
                timeoutMs = timeoutMs,
                isClientInitiated = false
            )
        }

        /**
         * Creates a client-initiated connection setup.
         *
         * Returns a signer and a NostrConnectUrl that should be displayed to the user
         * (as a QR code or deeplink). When the remote signer scans/opens the URL,
         * they will connect to this client.
         *
         * Usage:
         * ```kotlin
         * val (signer, connectUrl) = NDKRemoteSigner.awaitConnection(
         *     ndk = ndk,
         *     relayUrls = listOf("wss://relay.example.com"),
         *     appName = "MyApp"
         * )
         * // Display connectUrl.toUri() as QR code
         * // Or launch as deeplink: Intent(Intent.ACTION_VIEW, Uri.parse(connectUrl.toUri()))
         * signer.connect() // This will wait for the remote signer to connect
         * ```
         *
         * @param ndk The NDK instance for relay communication
         * @param relayUrls Relay URLs where this client listens for the connection
         * @param appName Optional name of this application
         * @param appUrl Optional URL of this application
         * @param appImage Optional icon URL of this application
         * @param permissions Optional list of permissions to request
         * @param timeoutMs Timeout for waiting for connection (default: 60000ms)
         * @return Pair of NDKRemoteSigner and NostrConnectUrl
         */
        fun awaitConnection(
            ndk: NDK,
            relayUrls: List<String>,
            appName: String? = null,
            appUrl: String? = null,
            appImage: String? = null,
            permissions: List<String>? = null,
            timeoutMs: Long = 60000L
        ): Pair<NDKRemoteSigner, NostrConnectUrl> {
            val localKeyPair = NDKKeyPair.generate()
            val secret = NostrConnectUrl.generateSecret()

            val connectUrl = NostrConnectUrl(
                clientPubkey = localKeyPair.pubkeyHex,
                relays = relayUrls,
                secret = secret,
                permissions = permissions,
                name = appName,
                url = appUrl,
                image = appImage
            )

            val signer = NDKRemoteSigner(
                ndk = ndk,
                remotePubkey = null,
                relayUrls = relayUrls,
                localKeyPair = localKeyPair,
                secret = secret,
                timeoutMs = timeoutMs,
                isClientInitiated = true
            )

            return signer to connectUrl
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<NIP46Response>>()
    private var responseSubscription: io.nostr.ndk.subscription.NDKSubscription? = null
    private var pendingConnectionResponse: CompletableDeferred<Pair<PublicKey, NIP46Response>>? = null

    private var _pubkey: PublicKey? = null
    override val pubkey: PublicKey
        get() = _pubkey ?: throw IllegalStateException("Remote signer not connected. Call connect() first.")

    private var isConnected = false

    /**
     * Establishes connection with the remote signer and retrieves its public key.
     *
     * For signer-initiated (bunker://) connections:
     * 1. Ensures relays are connected
     * 2. Subscribes to response events from the remote signer
     * 3. Sends a connect request if secret is provided
     * 4. Requests the user's public key
     *
     * For client-initiated (nostrconnect://) connections:
     * 1. Ensures relays are connected
     * 2. Subscribes to incoming events tagged with our pubkey
     * 3. Waits for a connect response from any remote signer
     * 4. Validates the secret and extracts the remote signer pubkey
     * 5. Requests the user's public key
     *
     * @throws IllegalStateException if connection fails or times out
     */
    suspend fun connect() {
        if (isConnected) return

        ensureRelaysConnected()

        if (isClientInitiated) {
            // Client-initiated: wait for incoming connection
            awaitIncomingConnection()
        } else {
            // Signer-initiated: we already know the remote pubkey
            subscribeToResponses()

            // If we have a secret, send connect request first
            val signerPubkey = remotePubkey
            if (secret != null && signerPubkey != null) {
                sendRequest("connect", listOf(signerPubkey, secret))
            }
        }

        // Get the actual user's public key
        val response = sendRequest("get_public_key", emptyList())
        _pubkey = response.result as? String
            ?: throw IllegalStateException("Failed to get public key from remote signer")

        isConnected = true
    }

    /**
     * Waits for an incoming connection from a remote signer (client-initiated flow).
     * Subscribes to all kind 24133 events tagged with our pubkey and waits for
     * a connect response with a valid secret.
     */
    private suspend fun awaitIncomingConnection() {
        val filter = NDKFilter(
            kinds = setOf(KIND_NOSTR_CONNECT),
            tags = mapOf("p" to setOf(localKeyPair.pubkeyHex))
        )

        val subscription = ndk.subscribe(filter)
        responseSubscription = subscription

        val deferred = CompletableDeferred<Pair<PublicKey, NIP46Response>>()
        pendingConnectionResponse = deferred

        scope.launch {
            subscription.events
                .filter { it.kind == KIND_NOSTR_CONNECT }
                .collect { event ->
                    handleIncomingConnectionEvent(event)
                }
        }

        try {
            withTimeout(timeoutMs) {
                val (signerPubkey, response) = deferred.await()

                // Validate secret
                val responseSecret = (response.result as? String)
                if (secret != null && responseSecret != secret) {
                    throw IllegalStateException("Invalid secret in connect response")
                }

                // Set the remote pubkey now that we know it
                remotePubkey = signerPubkey

                // Re-subscribe with author filter for better performance
                responseSubscription?.stop()
                subscribeToResponses()
            }
        } finally {
            pendingConnectionResponse = null
        }
    }

    /**
     * Handles incoming events during client-initiated connection setup.
     * Looks for connect responses and validates the secret.
     */
    private fun handleIncomingConnectionEvent(event: NDKEvent) {
        try {
            val privateKey = localKeyPair.privateKey
                ?: throw IllegalStateException("Local keypair missing private key")

            val decryptedJson = Nip44.decrypt(
                encryptedPayload = event.content,
                recipientPrivateKey = privateKey,
                senderPublicKey = event.pubkey
            )

            val response = objectMapper.readValue<NIP46Response>(decryptedJson)

            // Check if this is a connect response
            if (response.id == "connect" || response.result != null) {
                pendingConnectionResponse?.complete(event.pubkey to response)
            }
        } catch (e: Exception) {
            // Ignore malformed responses
        }
    }

    override fun serialize(): ByteArray {
        val data = mapOf(
            "type" to "NDKRemoteSigner",
            "data" to mapOf(
                "remotePubkey" to remotePubkey,
                "relayUrls" to relayUrls,
                "localPrivateKeyHex" to (localKeyPair.privateKeyHex ?: ""),
                "localPublicKeyHex" to localKeyPair.pubkeyHex,
                "secret" to (secret ?: ""),
                "timeoutMs" to timeoutMs,
                "userPubkey" to (_pubkey ?: "")
            )
        )
        return objectMapper.writeValueAsBytes(data)
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
        val signerPubkey = remotePubkey
            ?: throw IllegalStateException("Remote pubkey not set. Cannot send request.")

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
                recipientPublicKey = signerPubkey
            )

            val requestEvent = NDKPrivateKeySigner(localKeyPair).sign(
                UnsignedEvent(
                    pubkey = localKeyPair.pubkeyHex,
                    createdAt = System.currentTimeMillis() / 1000,
                    kind = KIND_NOSTR_CONNECT,
                    tags = listOf(NDKTag("p", listOf(signerPubkey))),
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
        val signerPubkey = remotePubkey
            ?: throw IllegalStateException("Remote pubkey not set. Cannot subscribe to responses.")

        val filter = NDKFilter(
            kinds = setOf(KIND_NOSTR_CONNECT),
            authors = setOf(signerPubkey),
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
            val signerPubkey = remotePubkey ?: return
            val privateKey = localKeyPair.privateKey
                ?: throw IllegalStateException("Local keypair missing private key")

            val decryptedJson = Nip44.decrypt(
                encryptedPayload = event.content,
                recipientPrivateKey = privateKey,
                senderPublicKey = signerPubkey
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
