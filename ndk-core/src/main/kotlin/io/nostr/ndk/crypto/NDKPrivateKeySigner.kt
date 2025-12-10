package io.nostr.ndk.crypto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fr.acinq.secp256k1.Secp256k1
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey
import java.security.MessageDigest

/**
 * Signs Nostr events using a secp256k1 private key.
 *
 * @property keyPair The keypair containing the private key for signing
 * @throws IllegalArgumentException if the keypair doesn't have a private key
 */
class NDKPrivateKeySigner(val keyPair: NDKKeyPair) : NDKSigner {

    init {
        require(keyPair.privateKey != null) {
            "Cannot create NDKPrivateKeySigner with read-only keypair (no private key)"
        }
    }

    override val pubkey: PublicKey
        get() = keyPair.pubkeyHex

    override suspend fun sign(event: UnsignedEvent): NDKEvent {
        val privateKey = keyPair.privateKey
            ?: throw IllegalStateException("Cannot sign without private key")

        // Calculate event ID
        val eventId = calculateEventId(event)

        // Sign the event ID with Schnorr signature
        val signature = signSchnorr(privateKey, eventId)

        // Return signed event
        return NDKEvent(
            id = eventId,
            pubkey = event.pubkey,
            createdAt = event.createdAt,
            kind = event.kind,
            tags = event.tags,
            content = event.content,
            sig = signature
        )
    }

    /**
     * Calculates the event ID per NIP-01 specification.
     */
    private fun calculateEventId(event: UnsignedEvent): String {
        // Serialize event per NIP-01: [0, pubkey, created_at, kind, tags, content]
        val serialized = serializeForId(event)

        // Calculate SHA256 hash
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(serialized.toByteArray(Charsets.UTF_8))

        // Convert to hex string
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Serializes event for ID calculation per NIP-01.
     */
    private fun serializeForId(event: UnsignedEvent): String {
        val array = listOf(
            0,
            event.pubkey,
            event.createdAt,
            event.kind,
            event.tags.map { listOf(it.name) + it.values },
            event.content
        )
        return objectMapper.writeValueAsString(array)
    }

    /**
     * Signs a message using Schnorr signature with x-only public key.
     *
     * Note: Nostr uses BIP-340 Schnorr signatures which expect x-only public keys (32 bytes).
     * The signSchnorr method in secp256k1-kmp handles this internally.
     */
    private fun signSchnorr(privateKey: ByteArray, messageHex: String): String {
        val messageBytes = messageHex.hexToBytes()
        // auxrand32 is set to null for deterministic signing (recommended for Nostr)
        val signatureBytes = secp256k1.signSchnorr(messageBytes, privateKey, null)
        return signatureBytes.toHex()
    }

    companion object {
        private val secp256k1 = Secp256k1.get()
        private val objectMapper: ObjectMapper = jacksonObjectMapper()
    }
}

/**
 * Converts a byte array to a hex string.
 */
private fun ByteArray.toHex(): String {
    return joinToString("") { "%02x".format(it) }
}

/**
 * Converts a hex string to a byte array.
 */
private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Hex string must have even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
