package io.nostr.ndk.account

import io.nostr.ndk.models.PublicKey

/**
 * Interface for persisting account data (signers).
 *
 * Implementations should securely store signer data. The default Android
 * implementation uses Android Keystore for encryption.
 */
interface NDKAccountStorage {
    /**
     * Saves signer data for an account.
     *
     * @param pubkey The public key identifying the account
     * @param signerPayload Serialized signer data (may contain private keys)
     */
    suspend fun saveSigner(pubkey: PublicKey, signerPayload: ByteArray)

    /**
     * Loads signer data for an account.
     *
     * @param pubkey The public key identifying the account
     * @return The serialized signer data, or null if not found
     */
    suspend fun loadSigner(pubkey: PublicKey): ByteArray?

    /**
     * Lists all saved account pubkeys.
     *
     * @return List of pubkeys with saved signer data
     */
    suspend fun listAccounts(): List<PublicKey>

    /**
     * Deletes an account's signer data.
     *
     * @param pubkey The public key identifying the account to delete
     */
    suspend fun deleteAccount(pubkey: PublicKey)
}

/**
 * In-memory storage for testing. Does not persist across app restarts.
 */
class InMemoryAccountStorage : NDKAccountStorage {
    private val accounts = mutableMapOf<PublicKey, ByteArray>()

    override suspend fun saveSigner(pubkey: PublicKey, signerPayload: ByteArray) {
        accounts[pubkey] = signerPayload
    }

    override suspend fun loadSigner(pubkey: PublicKey): ByteArray? {
        return accounts[pubkey]
    }

    override suspend fun listAccounts(): List<PublicKey> {
        return accounts.keys.toList()
    }

    override suspend fun deleteAccount(pubkey: PublicKey) {
        accounts.remove(pubkey)
    }
}
