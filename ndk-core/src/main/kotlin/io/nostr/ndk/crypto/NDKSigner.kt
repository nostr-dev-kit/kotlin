package io.nostr.ndk.crypto

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey

/**
 * Interface for signing Nostr events.
 *
 * Implementations include:
 * - NDKPrivateKeySigner: Signs with secp256k1 private key
 * - NDKRemoteSigner: Remote signing via Nostr Connect (NIP-46)
 * - NDKAmberSigner: Signs using Android Amber app (NIP-55)
 */
interface NDKSigner {
    /**
     * The public key associated with this signer.
     */
    val pubkey: PublicKey

    /**
     * Signs an unsigned event and returns a signed NDKEvent.
     *
     * @param event The unsigned event to sign
     * @return The signed NDKEvent with id and signature
     * @throws IllegalStateException if signing fails
     */
    suspend fun sign(event: UnsignedEvent): NDKEvent

    /**
     * Serializes this signer to a byte array for storage.
     *
     * @return Serialized signer data
     */
    fun serialize(): ByteArray

    companion object {
        private val objectMapper = jacksonObjectMapper()

        /**
         * Registered signer deserializers by type name.
         */
        private val deserializers = mutableMapOf<String, (Map<String, Any?>) -> NDKSigner?>()

        /**
         * Registers a deserializer for a signer type.
         */
        fun registerDeserializer(type: String, deserializer: (Map<String, Any?>) -> NDKSigner?) {
            deserializers[type] = deserializer
        }

        /**
         * Deserializes a signer from a byte array.
         *
         * @param data The serialized signer data
         * @return The deserialized signer, or null if deserialization fails
         */
        fun deserialize(data: ByteArray): NDKSigner? {
            return try {
                val json: Map<String, Any?> = objectMapper.readValue(data)
                val type = json["type"] as? String ?: return null
                val signerData = json["data"] as? Map<String, Any?> ?: return null

                deserializers[type]?.invoke(signerData)
            } catch (e: Exception) {
                null
            }
        }
    }
}
