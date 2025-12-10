package io.nostr.ndk.crypto

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NDKCryptoTest {

    @Test
    fun `generate random keypair creates valid keys`() {
        val keyPair = NDKKeyPair.generate()

        assertNotNull("Private key should not be null", keyPair.privateKey)
        assertNotNull("Public key should not be null", keyPair.publicKey)
        assertEquals("Private key should be 32 bytes", 32, keyPair.privateKey?.size)
        assertEquals("Public key should be 32 bytes", 32, keyPair.publicKey.size)
        assertEquals("Public key hex should be 64 characters", 64, keyPair.pubkeyHex.length)
    }

    @Test
    fun `derive keypair from private key hex`() {
        val privateKeyHex = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        // Expected x-only public key (32 bytes) derived from the private key
        val expectedPubkeyHex = "718d756f60cf5179ef35b39dc6db3ff58f04c0734f81f6d4410f0b047ddf9029"

        val keyPair = NDKKeyPair.fromPrivateKey(privateKeyHex)

        assertEquals("Public key should match expected", expectedPubkeyHex, keyPair.pubkeyHex)
        assertNotNull("Private key should not be null", keyPair.privateKey)
        assertEquals("Private key hex should match input", privateKeyHex, keyPair.privateKeyHex)
    }

    @Test
    fun `create read-only keypair from public key`() {
        val publicKeyHex = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"

        val keyPair = NDKKeyPair.fromPublicKey(publicKeyHex)

        assertNull("Private key should be null for read-only keypair", keyPair.privateKey)
        assertEquals("Public key should match input", publicKeyHex, keyPair.pubkeyHex)
        assertNull("Private key hex should be null", keyPair.privateKeyHex)
    }

    @Test
    fun `sign event and verify signature`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        // Create unsigned event
        val unsignedEvent = UnsignedEvent(
            pubkey = keyPair.pubkeyHex,
            createdAt = 1234567890L,
            kind = 1,
            tags = listOf(NDKTag("t", listOf("nostr"))),
            content = "Hello Nostr!"
        )

        // Sign event
        val signedEvent = signer.sign(unsignedEvent)

        // Verify signature
        assertNotNull("Event should have signature", signedEvent.sig)
        assertTrue("Signature should be valid", NDKSignatureVerifier.verify(signedEvent))
        assertTrue("Event ID should be valid", signedEvent.isIdValid())
    }

    @Test
    fun `reject invalid signature`() = runTest {
        val keyPair1 = NDKKeyPair.generate()
        val keyPair2 = NDKKeyPair.generate()
        val signer1 = NDKPrivateKeySigner(keyPair1)

        // Create unsigned event
        val unsignedEvent = UnsignedEvent(
            pubkey = keyPair1.pubkeyHex,
            createdAt = 1234567890L,
            kind = 1,
            tags = emptyList(),
            content = "Test"
        )

        // Sign with signer1
        val signedEvent = signer1.sign(unsignedEvent)

        // Tamper with event by changing pubkey to different key
        val tamperedEvent = NDKEvent(
            id = signedEvent.id,
            pubkey = keyPair2.pubkeyHex,  // Different pubkey
            createdAt = signedEvent.createdAt,
            kind = signedEvent.kind,
            tags = signedEvent.tags,
            content = signedEvent.content,
            sig = signedEvent.sig  // Keep original signature
        )

        // Verification should fail
        assertFalse("Tampered event signature should be invalid", NDKSignatureVerifier.verify(tamperedEvent))
    }

    @Test
    fun `verify event with tags`() = runTest {
        // Test that signing and verification works with events containing tags
        val keyPair = NDKKeyPair.fromPrivateKey("3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d")
        val signer = NDKPrivateKeySigner(keyPair)

        val unsignedEvent = UnsignedEvent(
            pubkey = keyPair.pubkeyHex,
            createdAt = 1673347337L,
            kind = 1,
            tags = listOf(
                NDKTag("e", listOf("5c83da77af1dec6d7289834998ad7aafbd9e2191396d75ec3cc27f5a77226f36")),
                NDKTag("p", listOf("f7234bd4c1394dda46d09f35bd384dd30cc552ad5541990f98844fb06676e9ca"))
            ),
            content = "Hello Nostr! This is my first note."
        )

        val signedEvent = signer.sign(unsignedEvent)

        // Verify the signed event
        assertTrue("Event ID should be valid", signedEvent.isIdValid())
        assertTrue("Signature should verify", NDKSignatureVerifier.verify(signedEvent))
        assertNotNull("Signature should not be null", signedEvent.sig)
    }

    @Test
    fun `npub encoding returns hex for now`() {
        // Per requirements, npub should return hex until Bech32 is implemented in Task 5
        val publicKeyHex = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
        val keyPair = NDKKeyPair.fromPublicKey(publicKeyHex)

        assertEquals("npub should return hex until Bech32 implemented", publicKeyHex, keyPair.npub)
    }

    @Test
    fun `signer pubkey matches keypair pubkey`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        assertEquals("Signer pubkey should match keypair pubkey", keyPair.pubkeyHex, signer.pubkey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `read-only keypair cannot be used for signing`() = runTest {
        val publicKeyHex = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
        val keyPair = NDKKeyPair.fromPublicKey(publicKeyHex)
        val signer = NDKPrivateKeySigner(keyPair)

        val unsignedEvent = UnsignedEvent(
            pubkey = keyPair.pubkeyHex,
            createdAt = 1234567890L,
            kind = 1,
            tags = emptyList(),
            content = "Test"
        )

        // Should throw IllegalArgumentException
        signer.sign(unsignedEvent)
    }

    @Test
    fun `signatures are deterministic for same input`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        val unsignedEvent = UnsignedEvent(
            pubkey = keyPair.pubkeyHex,
            createdAt = 1234567890L,
            kind = 1,
            tags = emptyList(),
            content = "Same content"
        )

        // Sign twice
        val event1 = signer.sign(unsignedEvent)
        val event2 = signer.sign(unsignedEvent)

        // Signatures should be identical (deterministic signing per BIP-340)
        assertEquals("Signatures should be deterministic", event1.sig, event2.sig)

        // Both should verify
        assertTrue("First signature should verify", NDKSignatureVerifier.verify(event1))
        assertTrue("Second signature should verify", NDKSignatureVerifier.verify(event2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromPrivateKey rejects invalid hex`() {
        NDKKeyPair.fromPrivateKey("not-valid-hex")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromPrivateKey rejects wrong length hex`() {
        NDKKeyPair.fromPrivateKey("aabbcc")  // Too short
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromPublicKey rejects invalid hex`() {
        NDKKeyPair.fromPublicKey("zzzzz")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromPublicKey rejects wrong length hex`() {
        NDKKeyPair.fromPublicKey("aabbcc")  // Too short
    }
}
