package io.nostr.ndk.crypto

import fr.acinq.secp256k1.Secp256k1
import io.nostr.ndk.models.NDKEvent

/**
 * Verifies Schnorr signatures on Nostr events.
 *
 * This object handles signature verification using secp256k1 Schnorr signatures
 * as specified in NIP-01.
 */
object NDKSignatureVerifier {
    private val secp256k1 = Secp256k1.get()

    /**
     * Verifies the signature on a Nostr event.
     *
     * This performs two checks:
     * 1. Validates that the event ID matches the calculated ID
     * 2. Verifies the Schnorr signature
     *
     * @param event The event to verify
     * @return true if the event has a valid ID and signature, false otherwise
     */
    fun verify(event: NDKEvent): Boolean {
        // Events without signatures cannot be verified
        val signature = event.sig ?: return false

        try {
            // First verify the event ID is correct
            if (!event.isIdValid()) {
                return false
            }

            // Parse signature and public key from hex
            val signatureBytes = signature.hexToBytes()
            // Nostr uses x-only public keys (32 bytes)
            val publicKeyBytes = event.pubkey.hexToBytes()
            require(publicKeyBytes.size == 32) { "Public key must be 32 bytes (x-only)" }

            val messageBytes = event.id.hexToBytes()

            // Verify Schnorr signature using x-only public key
            return secp256k1.verifySchnorr(signatureBytes, messageBytes, publicKeyBytes)
        } catch (e: Exception) {
            // Any parsing or verification error means invalid signature
            return false
        }
    }
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
