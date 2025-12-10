package io.nostr.ndk.nips

import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKTag
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Tests for NIP-59 Gift Wrap functionality.
 *
 * Note: Tests that involve actual encryption/decryption are marked as @Ignore
 * because they require Android runtime (LazySodium). These should be run as
 * instrumented tests on an Android device/emulator.
 */
class Nip59Test {

    @Test
    fun `toRumor removes signature from event`() = runTest {
        // Create a signed event
        val senderKeyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(senderKeyPair)

        val unsignedEvent = UnsignedEvent(
            pubkey = signer.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_TEXT_NOTE,
            tags = listOf(NDKTag.hashtag("test")),
            content = "Hello, Nostr!"
        )

        val signedEvent = signer.sign(unsignedEvent)

        // Convert to rumor
        val rumor = signedEvent.toRumor()

        // Verify signature is removed
        assertNull("Rumor should have no signature", rumor.sig)
        assertEquals("Rumor should have empty ID", "", rumor.id)
        assertEquals("Content should be preserved", signedEvent.content, rumor.content)
        assertEquals("Pubkey should be preserved", signedEvent.pubkey, rumor.pubkey)
        assertEquals("Kind should be preserved", signedEvent.kind, rumor.kind)
        assertEquals("Tags should be preserved", signedEvent.tags, rumor.tags)
    }

    @Ignore("Requires Android runtime for LazySodium")
    @Test
    fun `seal creates encrypted kind 13 event`() = runTest {
        // Create sender and recipient
        val senderKeyPair = NDKKeyPair.generate()
        val senderSigner = NDKPrivateKeySigner(senderKeyPair)
        val recipientKeyPair = NDKKeyPair.generate()

        // Create a rumor
        val unsignedEvent = UnsignedEvent(
            pubkey = senderSigner.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_TEXT_NOTE,
            tags = emptyList(),
            content = "Secret message"
        )
        val signedEvent = senderSigner.sign(unsignedEvent)
        val rumor = signedEvent.toRumor()

        // Seal the rumor
        val seal = rumor.seal(senderSigner, recipientKeyPair.pubkeyHex)

        // Verify seal structure
        assertEquals("Seal should be kind 13", KIND_SEAL, seal.kind)
        assertNotNull("Seal should be signed", seal.sig)
        assertNotEquals("Seal content should be encrypted", rumor.content, seal.content)
        assertTrue("Seal should have encrypted content", seal.content.isNotEmpty())
        assertEquals("Seal should be from sender", senderKeyPair.pubkeyHex, seal.pubkey)
    }

    @Ignore("Requires Android runtime for LazySodium")
    @Test
    fun `giftWrap creates encrypted kind 1059 event with random key`() = runTest {
        // Create sender and recipient
        val senderKeyPair = NDKKeyPair.generate()
        val senderSigner = NDKPrivateKeySigner(senderKeyPair)
        val recipientKeyPair = NDKKeyPair.generate()

        // Create and seal a rumor
        val unsignedEvent = UnsignedEvent(
            pubkey = senderSigner.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_TEXT_NOTE,
            tags = emptyList(),
            content = "Secret message"
        )
        val signedEvent = senderSigner.sign(unsignedEvent)
        val rumor = signedEvent.toRumor()
        val seal = rumor.seal(senderSigner, recipientKeyPair.pubkeyHex)

        // Gift wrap the seal
        val giftWrap = seal.giftWrap(recipientKeyPair.pubkeyHex)

        // Verify gift wrap structure
        assertEquals("Gift wrap should be kind 1059", KIND_GIFT_WRAP, giftWrap.kind)
        assertNotNull("Gift wrap should be signed", giftWrap.sig)
        assertNotEquals("Gift wrap should be from random key", senderKeyPair.pubkeyHex, giftWrap.pubkey)
        assertTrue("Gift wrap should have encrypted content", giftWrap.content.isNotEmpty())

        // Verify p tag with recipient
        val pTags = giftWrap.tagsWithName("p")
        assertEquals("Gift wrap should have exactly one p tag", 1, pTags.size)
        assertEquals("p tag should contain recipient pubkey", recipientKeyPair.pubkeyHex, pTags[0].values[0])
    }

    @Ignore("Requires Android runtime for LazySodium")
    @Test
    fun `wrapAsGift performs complete gift wrap flow`() = runTest {
        // Create sender and recipient
        val senderKeyPair = NDKKeyPair.generate()
        val senderSigner = NDKPrivateKeySigner(senderKeyPair)
        val recipientKeyPair = NDKKeyPair.generate()

        // Create an event
        val unsignedEvent = UnsignedEvent(
            pubkey = senderSigner.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_TEXT_NOTE,
            tags = listOf(NDKTag.hashtag("nostr")),
            content = "This is a private message"
        )
        val signedEvent = senderSigner.sign(unsignedEvent)

        // Wrap as gift
        val giftWrap = signedEvent.wrapAsGift(senderSigner, recipientKeyPair.pubkeyHex)

        // Verify structure
        assertEquals("Should create gift wrap", KIND_GIFT_WRAP, giftWrap.kind)
        assertNotEquals("Should use random key", senderKeyPair.pubkeyHex, giftWrap.pubkey)
        assertTrue("Should have encrypted content", giftWrap.content.isNotEmpty())
    }

    @Ignore("Requires Android runtime for LazySodium")
    @Test
    fun `unwrapGift successfully decrypts and returns inner event`() = runTest {
        // Create sender and recipient
        val senderKeyPair = NDKKeyPair.generate()
        val senderSigner = NDKPrivateKeySigner(senderKeyPair)
        val recipientKeyPair = NDKKeyPair.generate()
        val recipientSigner = NDKPrivateKeySigner(recipientKeyPair)

        // Create and wrap an event
        val originalContent = "Secret message for testing"
        val unsignedEvent = UnsignedEvent(
            pubkey = senderSigner.pubkey,
            createdAt = 1234567890L,
            kind = KIND_TEXT_NOTE,
            tags = listOf(NDKTag.hashtag("test"), NDKTag.reference("https://example.com")),
            content = originalContent
        )
        val signedEvent = senderSigner.sign(unsignedEvent)
        val giftWrap = signedEvent.wrapAsGift(senderSigner, recipientKeyPair.pubkeyHex)

        // Unwrap the gift
        val unwrapped = giftWrap.unwrapGift(recipientSigner)

        // Verify unwrapped event
        assertNotNull("Should successfully unwrap", unwrapped)
        assertEquals("Content should match", originalContent, unwrapped!!.content)
        assertEquals("Pubkey should match sender", senderKeyPair.pubkeyHex, unwrapped.pubkey)
        assertEquals("Kind should match", KIND_TEXT_NOTE, unwrapped.kind)
        assertEquals("Tags should match", 2, unwrapped.tags.size)
        assertEquals("Should have hashtag", "test", unwrapped.tagValue("t"))
    }

    @Ignore("Requires Android runtime for LazySodium")
    @Test
    fun `unwrapGift returns null when decryption fails`() = runTest {
        // Create sender, recipient, and wrong recipient
        val senderKeyPair = NDKKeyPair.generate()
        val senderSigner = NDKPrivateKeySigner(senderKeyPair)
        val recipientKeyPair = NDKKeyPair.generate()
        val wrongRecipientKeyPair = NDKKeyPair.generate()
        val wrongRecipientSigner = NDKPrivateKeySigner(wrongRecipientKeyPair)

        // Create and wrap an event
        val unsignedEvent = UnsignedEvent(
            pubkey = senderSigner.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_TEXT_NOTE,
            tags = emptyList(),
            content = "Secret message"
        )
        val signedEvent = senderSigner.sign(unsignedEvent)
        val giftWrap = signedEvent.wrapAsGift(senderSigner, recipientKeyPair.pubkeyHex)

        // Try to unwrap with wrong recipient
        val unwrapped = giftWrap.unwrapGift(wrongRecipientSigner)

        // Should fail to decrypt
        assertNull("Should return null when decryption fails", unwrapped)
    }

    @Ignore("Requires Android runtime for LazySodium")
    @Test
    fun `gift wrap hides sender metadata`() = runTest {
        // Create sender and recipient
        val senderKeyPair = NDKKeyPair.generate()
        val senderSigner = NDKPrivateKeySigner(senderKeyPair)
        val recipientKeyPair = NDKKeyPair.generate()

        // Create an event
        val unsignedEvent = UnsignedEvent(
            pubkey = senderSigner.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_TEXT_NOTE,
            tags = emptyList(),
            content = "Hidden message"
        )
        val signedEvent = senderSigner.sign(unsignedEvent)

        // Wrap as gift
        val giftWrap = signedEvent.wrapAsGift(senderSigner, recipientKeyPair.pubkeyHex)

        // Verify sender is hidden
        assertNotEquals("Gift wrap should not reveal sender", senderKeyPair.pubkeyHex, giftWrap.pubkey)
        assertFalse("Content should not contain original message", giftWrap.content.contains("Hidden message"))
    }

    @Ignore("Requires Android runtime for LazySodium")
    @Test
    fun `gift wrap uses randomized timestamp`() = runTest {
        // Create sender and recipient
        val senderKeyPair = NDKKeyPair.generate()
        val senderSigner = NDKPrivateKeySigner(senderKeyPair)
        val recipientKeyPair = NDKKeyPair.generate()

        // Create an event with known timestamp
        val originalTimestamp = System.currentTimeMillis() / 1000
        val unsignedEvent = UnsignedEvent(
            pubkey = senderSigner.pubkey,
            createdAt = originalTimestamp,
            kind = KIND_TEXT_NOTE,
            tags = emptyList(),
            content = "Timestamped message"
        )
        val signedEvent = senderSigner.sign(unsignedEvent)

        // Wrap as gift
        val giftWrap = signedEvent.wrapAsGift(senderSigner, recipientKeyPair.pubkeyHex)

        // Verify timestamp is randomized (should be within +/- 2 days)
        val twoDaysInSeconds = 2 * 24 * 60 * 60L
        val timestampDiff = Math.abs(giftWrap.createdAt - originalTimestamp)
        assertTrue("Timestamp should be randomized", timestampDiff <= twoDaysInSeconds)
    }

    @Ignore("Requires Android runtime for LazySodium")
    @Test
    fun `multiple gift wraps use different random keys`() = runTest {
        // Create sender and recipient
        val senderKeyPair = NDKKeyPair.generate()
        val senderSigner = NDKPrivateKeySigner(senderKeyPair)
        val recipientKeyPair = NDKKeyPair.generate()

        // Create an event
        val unsignedEvent = UnsignedEvent(
            pubkey = senderSigner.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_TEXT_NOTE,
            tags = emptyList(),
            content = "Message"
        )
        val signedEvent = senderSigner.sign(unsignedEvent)

        // Wrap same event twice
        val giftWrap1 = signedEvent.wrapAsGift(senderSigner, recipientKeyPair.pubkeyHex)
        val giftWrap2 = signedEvent.wrapAsGift(senderSigner, recipientKeyPair.pubkeyHex)

        // Verify different random keys are used
        assertNotEquals("Each gift wrap should use different random key", giftWrap1.pubkey, giftWrap2.pubkey)
        assertNotEquals("Encrypted content should differ", giftWrap1.content, giftWrap2.content)
    }

    @Ignore("Requires Android runtime for LazySodium")
    @Test
    fun `isGiftWrap and isSeal extension properties work correctly`() = runTest {
        // Create sender and recipient
        val senderKeyPair = NDKKeyPair.generate()
        val senderSigner = NDKPrivateKeySigner(senderKeyPair)
        val recipientKeyPair = NDKKeyPair.generate()

        // Create events
        val textNote = senderSigner.sign(UnsignedEvent(
            pubkey = senderSigner.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_TEXT_NOTE,
            tags = emptyList(),
            content = "Normal message"
        ))

        val rumor = textNote.toRumor()
        val seal = rumor.seal(senderSigner, recipientKeyPair.pubkeyHex)
        val giftWrap = seal.giftWrap(recipientKeyPair.pubkeyHex)

        // Test extension properties
        assertFalse("Text note should not be a seal", textNote.isSeal)
        assertFalse("Text note should not be a gift wrap", textNote.isGiftWrap)
        assertTrue("Seal should be identified as seal", seal.isSeal)
        assertFalse("Seal should not be a gift wrap", seal.isGiftWrap)
        assertFalse("Gift wrap should not be a seal", giftWrap.isSeal)
        assertTrue("Gift wrap should be identified as gift wrap", giftWrap.isGiftWrap)
    }
}
