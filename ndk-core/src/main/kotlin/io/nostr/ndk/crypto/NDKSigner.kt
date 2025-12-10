package io.nostr.ndk.crypto

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey

/**
 * Interface for signing Nostr events.
 *
 * Implementations include:
 * - NDKPrivateKeySigner: Signs with secp256k1 private key
 * - NDKAmberSigner: Signs using Android Amber app (NIP-55)
 * - NDKBunkerSigner: Remote signing via Nostr Connect (NIP-46)
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
}
