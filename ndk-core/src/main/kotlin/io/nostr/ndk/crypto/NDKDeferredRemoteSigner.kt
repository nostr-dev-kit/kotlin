package io.nostr.ndk.crypto

import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey

/**
 * A deferred remote signer that holds configuration but hasn't been initialized with an NDK instance yet.
 *
 * This class is used during deserialization when the NDK instance isn't available.
 * It implements NDKSigner by delegating to an actual NDKRemoteSigner once initialized.
 *
 * Usage:
 * ```kotlin
 * val deferredSigner = NDKSigner.deserialize(bytes) as? NDKDeferredRemoteSigner
 * val actualSigner = deferredSigner?.initialize(ndk)
 * actualSigner?.connect()
 * ```
 *
 * @property remotePubkey The remote signer's public key
 * @property relayUrls List of relay URLs to use for communication
 * @property localKeyPair Local keypair for encrypting requests
 * @property secret Optional secret for authentication
 * @property timeoutMs Timeout for waiting for responses
 * @property userPubkey The user's public key (if known from previous connection)
 */
class NDKDeferredRemoteSigner internal constructor(
    private val remotePubkey: PublicKey,
    private val relayUrls: List<String>,
    private val localKeyPair: NDKKeyPair,
    private val secret: String?,
    private val timeoutMs: Long,
    private val userPubkey: PublicKey?
) : NDKSigner {

    private var actualSigner: NDKRemoteSigner? = null

    override val pubkey: PublicKey
        get() = userPubkey
            ?: actualSigner?.pubkey
            ?: throw IllegalStateException("Deferred signer not initialized. Call initialize(ndk) first.")

    /**
     * Initializes the deferred signer with an NDK instance.
     *
     * @param ndk The NDK instance for relay communication
     * @return The initialized NDKRemoteSigner
     */
    fun initialize(ndk: NDK): NDKRemoteSigner {
        if (actualSigner != null) {
            return actualSigner!!
        }

        val signer = NDKRemoteSigner(
            ndk = ndk,
            remotePubkey = remotePubkey,
            relayUrls = relayUrls,
            localKeyPair = localKeyPair,
            timeoutMs = timeoutMs
        )

        // If we already know the user's pubkey, we can set it directly
        // to avoid needing to call connect() again
        if (userPubkey != null) {
            // Use reflection or make _pubkey accessible via internal method
            // For now, we'll require connect() to be called
        }

        actualSigner = signer
        return signer
    }

    override suspend fun sign(event: UnsignedEvent): NDKEvent {
        val signer = actualSigner
            ?: throw IllegalStateException("Deferred signer not initialized. Call initialize(ndk) first.")
        return signer.sign(event)
    }

    override fun serialize(): ByteArray {
        // If we have an actual signer, use its serialization
        return actualSigner?.serialize() ?: run {
            // Otherwise, reconstruct the serialization format
            val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val data = mapOf(
                "type" to "NDKRemoteSigner",
                "data" to mapOf(
                    "remotePubkey" to remotePubkey,
                    "relayUrls" to relayUrls,
                    "localPrivateKeyHex" to (localKeyPair.privateKeyHex ?: ""),
                    "localPublicKeyHex" to localKeyPair.pubkeyHex,
                    "secret" to (secret ?: ""),
                    "timeoutMs" to timeoutMs,
                    "userPubkey" to (userPubkey ?: "")
                )
            )
            objectMapper.writeValueAsBytes(data)
        }
    }
}
