package io.nostr.ndk.crypto

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.interfaces.Hash
import fr.acinq.secp256k1.Secp256k1

/**
 * NIP-44 encryption implementation using XChaCha20-Poly1305.
 *
 * NIP-44 provides encrypted communication between Nostr users using
 * ECDH key agreement and XChaCha20-Poly1305 authenticated encryption.
 */
object Nip44 {
    private val secp256k1 = Secp256k1.get()
    private val sodium = LazySodiumAndroid(SodiumAndroid())

    /**
     * Encrypts a message using NIP-44 encryption.
     *
     * @param plaintext The message to encrypt
     * @param senderPrivateKey The sender's 32-byte private key
     * @param recipientPublicKey The recipient's 32-byte public key (hex string)
     * @return Base64-encoded encrypted payload with version prefix
     * @throws IllegalStateException if encryption fails
     */
    fun encrypt(
        plaintext: String,
        senderPrivateKey: ByteArray,
        recipientPublicKey: String
    ): String {
        require(senderPrivateKey.size == 32) { "Private key must be 32 bytes" }

        val recipientPubkeyBytes = recipientPublicKey.hexToBytes()
        require(recipientPubkeyBytes.size == 32) { "Public key must be 32 bytes" }

        // Perform ECDH to get shared secret
        val sharedSecret = computeSharedSecret(senderPrivateKey, recipientPubkeyBytes)

        // Generate random 24-byte nonce for XChaCha20
        val nonce = sodium.randomBytesBuf(24)

        // Encrypt using XChaCha20-Poly1305
        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        val ciphertext = ByteArray(plaintextBytes.size + AEAD.XCHACHA20POLY1305_IETF_ABYTES)

        val success = sodium.cryptoAeadXChaCha20Poly1305IetfEncrypt(
            ciphertext,
            null,
            plaintextBytes,
            plaintextBytes.size.toLong(),
            null,
            0,
            null,
            nonce,
            sharedSecret
        )

        require(success) { "Encryption failed" }

        // Format: version (1 byte) + nonce (24 bytes) + ciphertext
        val version = byteArrayOf(0x02) // NIP-44 version 2
        val payload = version + nonce + ciphertext

        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /**
     * Decrypts a NIP-44 encrypted message.
     *
     * @param encryptedPayload Base64-encoded encrypted payload
     * @param recipientPrivateKey The recipient's 32-byte private key
     * @param senderPublicKey The sender's 32-byte public key (hex string)
     * @return The decrypted plaintext message
     * @throws IllegalArgumentException if payload is invalid
     * @throws IllegalStateException if decryption fails
     */
    fun decrypt(
        encryptedPayload: String,
        recipientPrivateKey: ByteArray,
        senderPublicKey: String
    ): String {
        require(recipientPrivateKey.size == 32) { "Private key must be 32 bytes" }

        val senderPubkeyBytes = senderPublicKey.hexToBytes()
        require(senderPubkeyBytes.size == 32) { "Public key must be 32 bytes" }

        // Decode base64 payload
        val payload = try {
            Base64.decode(encryptedPayload, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid base64 encoding", e)
        }

        require(payload.size >= 26) { "Payload too short" } // 1 + 24 + at least 1 + MAC

        // Parse payload: version + nonce + ciphertext
        val version = payload[0]
        require(version == 0x02.toByte()) { "Unsupported NIP-44 version: $version" }

        val nonce = payload.copyOfRange(1, 25)
        val ciphertext = payload.copyOfRange(25, payload.size)

        // Perform ECDH to get shared secret
        val sharedSecret = computeSharedSecret(recipientPrivateKey, senderPubkeyBytes)

        // Decrypt using XChaCha20-Poly1305
        val plaintext = ByteArray(ciphertext.size - AEAD.XCHACHA20POLY1305_IETF_ABYTES)

        val success = sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(
            plaintext,
            null,
            null,
            ciphertext,
            ciphertext.size.toLong(),
            null,
            0,
            nonce,
            sharedSecret
        )

        require(success) { "Decryption failed - invalid MAC or corrupted data" }

        return plaintext.toString(Charsets.UTF_8)
    }

    /**
     * Computes the shared secret using ECDH.
     *
     * @param privateKey The private key (32 bytes)
     * @param publicKey The public key (32 bytes, x-only)
     * @return The shared secret (32 bytes)
     */
    private fun computeSharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        // Convert x-only pubkey to full pubkey for ECDH
        // We need to add the 0x02 prefix for even y-coordinate
        val fullPubkey = byteArrayOf(0x02) + publicKey

        // Perform ECDH multiplication
        val sharedPoint = secp256k1.pubKeyTweakMul(fullPubkey, privateKey)

        // Extract x-coordinate (skip first byte which is the prefix)
        val sharedX = sharedPoint.copyOfRange(1, 33)

        // Hash the shared x-coordinate using SHA-256 to get final shared secret
        val sharedSecret = ByteArray(Hash.SHA256_BYTES)
        sodium.cryptoHashSha256(sharedSecret, sharedX, sharedX.size.toLong())

        return sharedSecret
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

/**
 * Converts a byte array to a hex string.
 */
private fun ByteArray.toHex(): String {
    return joinToString("") { "%02x".format(it) }
}
