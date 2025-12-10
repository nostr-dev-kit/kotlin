package io.nostr.ndk.crypto

import io.nostr.ndk.NDK
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NDKRemoteSignerSerializationTest {

    @Test
    fun `NDKRemoteSigner serializes and deserializes configuration`() = runTest {
        val ndk = NDK(explicitRelayUrls = setOf("wss://relay.example.com"))
        val localKeyPair = NDKKeyPair.generate()

        val originalSigner = NDKRemoteSigner(
            ndk = ndk,
            remotePubkey = "abc123" + "0".repeat(58), // 64 hex chars
            relayUrls = listOf("wss://relay1.com", "wss://relay2.com"),
            localKeyPair = localKeyPair,
            timeoutMs = 30000L
        )

        // Serialize
        val serialized = originalSigner.serialize()
        assertNotNull(serialized)
        assertTrue(serialized.isNotEmpty())

        // Deserialize
        val deserialized = NDKSigner.deserialize(serialized)
        assertNotNull(deserialized)
        assertTrue(deserialized is NDKDeferredRemoteSigner)

        // Initialize deferred signer
        val deferredSigner = deserialized as NDKDeferredRemoteSigner
        val initializedSigner = deferredSigner.initialize(ndk)

        assertNotNull(initializedSigner)
        assertTrue(initializedSigner is NDKRemoteSigner)
    }

    @Test
    fun `NDKDeferredRemoteSigner can be serialized again`() = runTest {
        val ndk = NDK(explicitRelayUrls = setOf("wss://relay.example.com"))
        val localKeyPair = NDKKeyPair.generate()

        val originalSigner = NDKRemoteSigner(
            ndk = ndk,
            remotePubkey = "abc123" + "0".repeat(58),
            relayUrls = listOf("wss://relay1.com"),
            localKeyPair = localKeyPair,
            timeoutMs = 30000L
        )

        // Serialize -> Deserialize -> Serialize again
        val serialized1 = originalSigner.serialize()
        val deferred = NDKSigner.deserialize(serialized1) as NDKDeferredRemoteSigner
        val serialized2 = deferred.serialize()

        assertNotNull(serialized2)
        assertTrue(serialized2.isNotEmpty())

        // Should be able to deserialize the second serialization
        val deferred2 = NDKSigner.deserialize(serialized2)
        assertNotNull(deferred2)
        assertTrue(deferred2 is NDKDeferredRemoteSigner)
    }

    @Test
    fun `fromBunkerUrl creates signer that can be serialized`() = runTest {
        val ndk = NDK(explicitRelayUrls = setOf("wss://relay.example.com"))
        val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        val bunkerUrl = "bunker://$pubkey?relay=wss://relay.nsec.app&secret=test123"

        val signer = NDKRemoteSigner.fromBunkerUrl(
            ndk = ndk,
            bunkerUrl = bunkerUrl
        )

        // Serialize
        val serialized = signer.serialize()
        assertNotNull(serialized)

        // Deserialize
        val deserialized = NDKSigner.deserialize(serialized) as? NDKDeferredRemoteSigner
        assertNotNull(deserialized)
    }

    @Test(expected = IllegalStateException::class)
    fun `NDKDeferredRemoteSigner throws when accessing pubkey before initialization`() {
        val localKeyPair = NDKKeyPair.generate()

        val deferredSigner = NDKDeferredRemoteSigner(
            remotePubkey = "abc123" + "0".repeat(58),
            relayUrls = listOf("wss://relay.com"),
            localKeyPair = localKeyPair,
            secret = null,
            timeoutMs = 30000L,
            userPubkey = null
        )

        // Should throw because not initialized
        deferredSigner.pubkey
    }

    @Test(expected = IllegalStateException::class)
    fun `NDKDeferredRemoteSigner throws when signing before initialization`() = runTest {
        val localKeyPair = NDKKeyPair.generate()

        val deferredSigner = NDKDeferredRemoteSigner(
            remotePubkey = "abc123" + "0".repeat(58),
            relayUrls = listOf("wss://relay.com"),
            localKeyPair = localKeyPair,
            secret = null,
            timeoutMs = 30000L,
            userPubkey = null
        )

        val unsignedEvent = UnsignedEvent(
            pubkey = localKeyPair.pubkeyHex,
            createdAt = System.currentTimeMillis() / 1000,
            kind = 1,
            tags = emptyList(),
            content = "test"
        )

        // Should throw because not initialized
        deferredSigner.sign(unsignedEvent)
    }
}
