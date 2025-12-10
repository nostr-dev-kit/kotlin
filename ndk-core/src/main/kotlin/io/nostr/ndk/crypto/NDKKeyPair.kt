package io.nostr.ndk.crypto

import fr.acinq.secp256k1.Secp256k1

/**
 * Represents a Nostr keypair with private and public keys.
 *
 * @property privateKey The private key bytes (32 bytes), null for read-only keypairs
 * @property publicKey The public key bytes (32 bytes)
 */
data class NDKKeyPair(
    val privateKey: ByteArray?,
    val publicKey: ByteArray
) {
    /**
     * Returns the public key as a 64-character hex string.
     */
    val pubkeyHex: String
        get() = publicKey.toHex()

    /**
     * Returns the private key as a 64-character hex string, or null if no private key.
     */
    val privateKeyHex: String?
        get() = privateKey?.toHex()

    /**
     * Returns the npub (Bech32-encoded public key).
     * For now, returns hex until Bech32 is implemented in Task 5.
     */
    val npub: String
        get() = pubkeyHex  // TODO: Task 5 - Implement Bech32 encoding

    companion object {
        private val secp256k1 = Secp256k1.get()

        /**
         * Generates a new random keypair.
         *
         * @return A new NDKKeyPair with random private and public keys
         */
        fun generate(): NDKKeyPair {
            // Generate 32 random bytes for private key
            val privateKey = ByteArray(32).apply {
                java.security.SecureRandom().nextBytes(this)
            }

            // Verify the private key is valid (required for secp256k1)
            require(secp256k1.secKeyVerify(privateKey)) { "Generated invalid private key" }

            // Derive public key from private key (returns uncompressed 65-byte key)
            val uncompressedPubkey = secp256k1.pubkeyCreate(privateKey)
            // Extract x-only public key (32 bytes) - skip first byte (0x04 marker) and last 32 bytes (y coordinate)
            val publicKey = uncompressedPubkey.copyOfRange(1, 33)
            return NDKKeyPair(privateKey, publicKey)
        }

        /**
         * Creates a keypair from a private key hex string.
         *
         * @param hex 64-character hex string representing the private key
         * @return NDKKeyPair derived from the private key
         * @throws IllegalArgumentException if hex is invalid or wrong length
         */
        fun fromPrivateKey(hex: String): NDKKeyPair {
            val privateKey = hex.hexToBytes()
            require(privateKey.size == 32) { "Private key must be 32 bytes (64 hex characters)" }

            // Verify the private key is valid
            require(secp256k1.secKeyVerify(privateKey)) { "Invalid private key" }

            // Derive public key from private key (returns uncompressed 65-byte key)
            val uncompressedPubkey = secp256k1.pubkeyCreate(privateKey)
            // Extract x-only public key (32 bytes) - skip first byte (0x04 marker) and last 32 bytes (y coordinate)
            val publicKey = uncompressedPubkey.copyOfRange(1, 33)
            return NDKKeyPair(privateKey, publicKey)
        }

        /**
         * Creates a read-only keypair from a public key hex string.
         * This keypair cannot be used for signing.
         *
         * @param hex 64-character hex string representing the public key
         * @return NDKKeyPair with only public key (privateKey is null)
         * @throws IllegalArgumentException if hex is invalid or wrong length
         */
        fun fromPublicKey(hex: String): NDKKeyPair {
            val publicKey = hex.hexToBytes()
            require(publicKey.size == 32) { "Public key must be 32 bytes (64 hex characters)" }
            return NDKKeyPair(null, publicKey)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NDKKeyPair

        if (privateKey != null) {
            if (other.privateKey == null) return false
            if (!privateKey.contentEquals(other.privateKey)) return false
        } else if (other.privateKey != null) return false
        if (!publicKey.contentEquals(other.publicKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = privateKey?.contentHashCode() ?: 0
        result = 31 * result + publicKey.contentHashCode()
        return result
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
 *
 * @throws IllegalArgumentException if the string contains invalid hex characters
 */
private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Hex string must have even length" }

    return try {
        chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    } catch (e: NumberFormatException) {
        throw IllegalArgumentException("Invalid hex string: $this", e)
    }
}
