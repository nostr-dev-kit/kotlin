package io.nostr.ndk.account

import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.crypto.NDKSigner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SignerSerializationTest {

    @Test
    fun `NDKPrivateKeySigner serializes and deserializes`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        val serialized = signer.serialize()
        val deserialized = NDKSigner.deserialize(serialized)

        assertNotNull(deserialized)
        assertTrue(deserialized is NDKPrivateKeySigner)
        assertEquals(signer.pubkey, deserialized!!.pubkey)
    }

    @Test
    fun `serialized signer can sign events`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val original = NDKPrivateKeySigner(keyPair)

        val serialized = original.serialize()
        val restored = NDKSigner.deserialize(serialized) as NDKPrivateKeySigner

        // Create unsigned event
        val unsigned = io.nostr.ndk.crypto.UnsignedEvent(
            pubkey = restored.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = 1,
            tags = emptyList(),
            content = "test"
        )

        val signed = restored.sign(unsigned)

        assertNotNull(signed.id)
        assertNotNull(signed.sig)
        assertEquals(restored.pubkey, signed.pubkey)
    }

    @Test
    fun `deserialize returns null for invalid data`() = runTest {
        val invalid = "not-valid-json".toByteArray()

        val result = NDKSigner.deserialize(invalid)

        assertNull(result)
    }

    @Test
    fun `deserialize returns null for unknown signer type`() = runTest {
        val unknown = """{"type":"UnknownSigner","data":{}}""".toByteArray()

        val result = NDKSigner.deserialize(unknown)

        assertNull(result)
    }
}
