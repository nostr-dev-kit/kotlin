package io.nostr.ndk.nips

import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.crypto.Nip44
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.PublicKey
import kotlin.random.Random

/**
 * NIP-59: Gift Wrap
 *
 * Gift wrapping provides metadata protection for private messages by:
 * 1. Creating a "rumor" (unsigned inner event)
 * 2. Wrapping it in a "seal" (kind 13, encrypted to recipient, signed by sender)
 * 3. Wrapping the seal in a "gift wrap" (kind 1059, encrypted with random throwaway key)
 *
 * This hides sender/recipient metadata from relays and provides stronger privacy.
 */

/**
 * Kind constant for seal events (encrypted inner events).
 */
const val KIND_SEAL = 13

/**
 * Kind constant for gift wrap events (encrypted seal events).
 */
const val KIND_GIFT_WRAP = 1059

/**
 * Returns true if this event is a seal (kind 13).
 */
val NDKEvent.isSeal: Boolean
    get() = kind == KIND_SEAL

/**
 * Returns true if this event is a gift wrap (kind 1059).
 */
val NDKEvent.isGiftWrap: Boolean
    get() = kind == KIND_GIFT_WRAP

/**
 * Converts a signed event to a rumor (unsigned event).
 *
 * A rumor is an event without an ID or signature, used as the innermost
 * layer in gift wrapping. This removes the signature to prepare the event
 * for encryption.
 *
 * @return A new NDKEvent without ID and signature
 */
fun NDKEvent.toRumor(): NDKEvent {
    return NDKEvent(
        id = "",
        pubkey = pubkey,
        createdAt = createdAt,
        kind = kind,
        tags = tags,
        content = content,
        sig = null
    )
}

/**
 * Seals a rumor by encrypting it to the recipient's public key.
 *
 * The seal (kind 13) contains the encrypted rumor and is signed by the sender.
 * The createdAt timestamp is randomized for privacy protection.
 *
 * @param signer The sender's signer (must be NDKPrivateKeySigner)
 * @param recipientPubkey The recipient's public key
 * @return The sealed event (kind 13) with encrypted content
 * @throws IllegalArgumentException if the signer doesn't have a private key
 */
suspend fun NDKEvent.seal(
    signer: NDKPrivateKeySigner,
    recipientPubkey: PublicKey
): NDKEvent {
    // Ensure this is a rumor (no signature)
    require(sig == null) { "Cannot seal a signed event. Use toRumor() first." }

    // Get sender's private key
    val privateKey = signer.keyPair.privateKey
        ?: throw IllegalArgumentException("Signer must have a private key")

    // Serialize the rumor to JSON
    val rumorJson = this.toJson()

    // Encrypt the rumor using NIP-44
    val encryptedContent = Nip44.encrypt(rumorJson, privateKey, recipientPubkey)

    // Create and sign the seal with randomized timestamp
    val randomizedTimestamp = randomizeTimestamp(System.currentTimeMillis() / 1000)
    val unsignedSeal = UnsignedEvent(
        pubkey = signer.pubkey,
        createdAt = randomizedTimestamp,
        kind = KIND_SEAL,
        tags = emptyList(),
        content = encryptedContent
    )

    return signer.sign(unsignedSeal)
}

/**
 * Gift wraps a sealed event using a random throwaway key.
 *
 * The gift wrap (kind 1059) hides the sender by using a random key pair
 * that is discarded after creation. The timestamp is also randomized.
 *
 * @param recipientPubkey The recipient's public key
 * @param randomSigner Optional random signer (generates one if not provided)
 * @return The gift wrapped event (kind 1059)
 */
suspend fun NDKEvent.giftWrap(
    recipientPubkey: PublicKey,
    randomSigner: NDKPrivateKeySigner = NDKPrivateKeySigner(NDKKeyPair.generate())
): NDKEvent {
    // Ensure this is a seal
    require(kind == KIND_SEAL) { "Can only gift wrap seal events (kind 13)" }

    // Get random signer's private key
    val privateKey = randomSigner.keyPair.privateKey
        ?: throw IllegalArgumentException("Random signer must have a private key")

    // Serialize the seal to JSON
    val sealJson = this.toJson()

    // Encrypt the seal using NIP-44 with the random key
    val encryptedContent = Nip44.encrypt(sealJson, privateKey, recipientPubkey)

    // Create and sign the gift wrap with random key and randomized timestamp
    val randomizedTimestamp = randomizeTimestamp(System.currentTimeMillis() / 1000)
    val unsignedGiftWrap = UnsignedEvent(
        pubkey = randomSigner.pubkey,
        createdAt = randomizedTimestamp,
        kind = KIND_GIFT_WRAP,
        tags = listOf(NDKTag.pubkey(recipientPubkey)),
        content = encryptedContent
    )

    return randomSigner.sign(unsignedGiftWrap)
}

/**
 * Complete gift wrap flow: converts an event to a rumor, seals it, and gift wraps it.
 *
 * This is a convenience function that performs all three steps:
 * 1. Convert to rumor (remove signature)
 * 2. Seal (encrypt to recipient, sign with sender)
 * 3. Gift wrap (encrypt with random key)
 *
 * @param signer The sender's signer
 * @param recipientPubkey The recipient's public key
 * @return The gift wrapped event ready for publishing
 */
suspend fun NDKEvent.wrapAsGift(
    signer: NDKPrivateKeySigner,
    recipientPubkey: PublicKey
): NDKEvent {
    return this.toRumor()
        .seal(signer, recipientPubkey)
        .giftWrap(recipientPubkey)
}

/**
 * Unwraps a gift wrapped event to reveal the inner event.
 *
 * This performs the full unwrapping:
 * 1. Decrypt the gift wrap (kind 1059) to get the seal
 * 2. Decrypt the seal (kind 13) to get the rumor
 * 3. Return the inner event
 *
 * @param signer The recipient's signer (must have private key)
 * @return The unwrapped inner event, or null if decryption fails
 */
suspend fun NDKEvent.unwrapGift(
    signer: NDKPrivateKeySigner
): NDKEvent? {
    require(kind == KIND_GIFT_WRAP) { "Can only unwrap gift wrap events (kind 1059)" }

    // Get recipient's private key
    val recipientPrivateKey = signer.keyPair.privateKey
        ?: throw IllegalArgumentException("Signer must have a private key for unwrapping")

    return try {
        // Step 1: Decrypt the gift wrap to get the seal
        // The sender pubkey is the random throwaway key used for the gift wrap
        val sealJson = Nip44.decrypt(content, recipientPrivateKey, pubkey)
        val seal = NDKEvent.fromJson(sealJson)

        require(seal.kind == KIND_SEAL) { "Expected seal (kind 13), got kind ${seal.kind}" }

        // Step 2: Decrypt the seal to get the rumor
        // The seal's pubkey is the actual sender
        val rumorJson = Nip44.decrypt(seal.content, recipientPrivateKey, seal.pubkey)
        val rumor = NDKEvent.fromJson(rumorJson)

        rumor
    } catch (e: Exception) {
        // Decryption failed - not intended for this recipient or corrupted
        null
    }
}

/**
 * Randomizes a timestamp for privacy protection.
 *
 * Adds random jitter of +/- 2 days to hide the exact creation time.
 *
 * @param baseTimestamp The base timestamp in seconds
 * @return Randomized timestamp in seconds
 */
private fun randomizeTimestamp(baseTimestamp: Long): Long {
    val twoDaysInSeconds = 2 * 24 * 60 * 60L
    val jitter = Random.nextLong(-twoDaysInSeconds, twoDaysInSeconds)
    return baseTimestamp + jitter
}
