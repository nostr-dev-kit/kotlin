package io.nostr.ndk.nips

import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for NIP-17 Private Direct Messages functionality.
 */
class Nip17Test {

    @Test
    fun `createPrivateMessage creates kind 14 event`() = runTest {
        val recipientKeyPair = NDKKeyPair.generate()
        val content = "Hello, this is a private message!"

        val unsigned = createPrivateMessage(
            recipientPubkey = recipientKeyPair.pubkeyHex,
            content = content
        )

        assertEquals("Should create kind 14", KIND_PRIVATE_MESSAGE, unsigned.kind)
        assertEquals("Content should match", content, unsigned.content)
    }

    @Test
    fun `createPrivateMessage includes recipient p tag`() = runTest {
        val recipientKeyPair = NDKKeyPair.generate()
        val content = "Test message"

        val unsigned = createPrivateMessage(
            recipientPubkey = recipientKeyPair.pubkeyHex,
            content = content
        )

        val pTags = unsigned.tags.filter { it.name == "p" }
        assertEquals("Should have one p tag", 1, pTags.size)
        assertEquals("p tag should contain recipient pubkey", recipientKeyPair.pubkeyHex, pTags[0].values[0])
    }

    @Test
    fun `createPrivateMessage includes subject tag when provided`() = runTest {
        val recipientKeyPair = NDKKeyPair.generate()
        val content = "Test message"
        val subject = "Important Discussion"

        val unsigned = createPrivateMessage(
            recipientPubkey = recipientKeyPair.pubkeyHex,
            content = content,
            subject = subject
        )

        val subjectTags = unsigned.tags.filter { it.name == "subject" }
        assertEquals("Should have one subject tag", 1, subjectTags.size)
        assertEquals("Subject tag should contain subject", subject, subjectTags[0].values[0])
    }

    @Test
    fun `createPrivateMessage excludes subject tag when null`() = runTest {
        val recipientKeyPair = NDKKeyPair.generate()
        val content = "Test message"

        val unsigned = createPrivateMessage(
            recipientPubkey = recipientKeyPair.pubkeyHex,
            content = content,
            subject = null
        )

        val subjectTags = unsigned.tags.filter { it.name == "subject" }
        assertEquals("Should have no subject tag", 0, subjectTags.size)
    }

    @Test
    fun `createPrivateMessage includes reply tags when provided`() = runTest {
        val recipientKeyPair = NDKKeyPair.generate()
        val replyToKeyPair = NDKKeyPair.generate()
        val content = "This is a reply"
        val replyToEventId = "abc123"

        val unsigned = createPrivateMessage(
            recipientPubkey = recipientKeyPair.pubkeyHex,
            content = content,
            replyTo = replyToEventId,
            replyToPubkey = replyToKeyPair.pubkeyHex
        )

        // Should have e tag with reply marker
        val eTags = unsigned.tags.filter { it.name == "e" }
        assertEquals("Should have one e tag", 1, eTags.size)
        assertEquals("e tag should contain event ID", replyToEventId, eTags[0].values[0])
        assertEquals("e tag should have reply marker", "reply", eTags[0].values.getOrNull(2))

        // Should have p tag for recipient and replyTo author
        val pTags = unsigned.tags.filter { it.name == "p" }
        assertEquals("Should have two p tags", 2, pTags.size)
        assertTrue("Should include recipient", pTags.any { it.values[0] == recipientKeyPair.pubkeyHex })
        assertTrue("Should include reply author", pTags.any { it.values[0] == replyToKeyPair.pubkeyHex })
    }

    @Test
    fun `createPrivateMessage excludes reply tags when null`() = runTest {
        val recipientKeyPair = NDKKeyPair.generate()
        val content = "Test message"

        val unsigned = createPrivateMessage(
            recipientPubkey = recipientKeyPair.pubkeyHex,
            content = content,
            replyTo = null,
            replyToPubkey = null
        )

        val eTags = unsigned.tags.filter { it.name == "e" }
        assertEquals("Should have no e tags", 0, eTags.size)

        val pTags = unsigned.tags.filter { it.name == "p" }
        assertEquals("Should have only recipient p tag", 1, pTags.size)
    }

    @Test
    fun `isPrivateMessage returns true for kind 14`() = runTest {
        val senderKeyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(senderKeyPair)

        val unsigned = createPrivateMessage(
            recipientPubkey = "recipient123",
            content = "Test"
        )
        val event = signer.sign(unsigned)

        assertTrue("Kind 14 should be private message", event.isPrivateMessage)
    }

    @Test
    fun `isPrivateMessage returns false for other kinds`() = runTest {
        val senderKeyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(senderKeyPair)

        val textNote = signer.sign(io.nostr.ndk.crypto.UnsignedEvent(
            pubkey = signer.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_TEXT_NOTE,
            tags = emptyList(),
            content = "Test"
        ))

        assertFalse("Kind 1 should not be private message", textNote.isPrivateMessage)
    }

    @Test
    fun `isPrivateFileMessage returns true for kind 15`() = runTest {
        val senderKeyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(senderKeyPair)

        val fileMessage = signer.sign(io.nostr.ndk.crypto.UnsignedEvent(
            pubkey = signer.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_PRIVATE_FILE_MESSAGE,
            tags = emptyList(),
            content = "File content"
        ))

        assertTrue("Kind 15 should be private file message", fileMessage.isPrivateFileMessage)
    }

    @Test
    fun `dmRecipients extracts pubkeys from p tags`() = runTest {
        val senderKeyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(senderKeyPair)
        val recipientKeyPair1 = NDKKeyPair.generate()
        val recipientKeyPair2 = NDKKeyPair.generate()

        val unsigned = createPrivateMessage(
            recipientPubkey = recipientKeyPair1.pubkeyHex,
            content = "Test",
            replyTo = "event123",
            replyToPubkey = recipientKeyPair2.pubkeyHex
        )
        val event = signer.sign(unsigned)

        val recipients = event.dmRecipients
        assertEquals("Should have two recipients", 2, recipients.size)
        assertTrue("Should include first recipient", recipients.contains(recipientKeyPair1.pubkeyHex))
        assertTrue("Should include second recipient", recipients.contains(recipientKeyPair2.pubkeyHex))
    }

    @Test
    fun `dmSubject extracts subject tag value`() = runTest {
        val senderKeyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(senderKeyPair)
        val recipientKeyPair = NDKKeyPair.generate()
        val subject = "Meeting Tomorrow"

        val unsigned = createPrivateMessage(
            recipientPubkey = recipientKeyPair.pubkeyHex,
            content = "Test",
            subject = subject
        )
        val event = signer.sign(unsigned)

        assertEquals("Should extract subject", subject, event.dmSubject)
    }

    @Test
    fun `dmSubject returns null when no subject tag`() = runTest {
        val senderKeyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(senderKeyPair)
        val recipientKeyPair = NDKKeyPair.generate()

        val unsigned = createPrivateMessage(
            recipientPubkey = recipientKeyPair.pubkeyHex,
            content = "Test"
        )
        val event = signer.sign(unsigned)

        assertNull("Should return null when no subject", event.dmSubject)
    }

    @Test
    fun `dmReplyTo extracts event ID from e tag with reply marker`() = runTest {
        val senderKeyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(senderKeyPair)
        val recipientKeyPair = NDKKeyPair.generate()
        val replyToEventId = "event456"

        val unsigned = createPrivateMessage(
            recipientPubkey = recipientKeyPair.pubkeyHex,
            content = "Test",
            replyTo = replyToEventId,
            replyToPubkey = "author123"
        )
        val event = signer.sign(unsigned)

        assertEquals("Should extract reply event ID", replyToEventId, event.dmReplyTo)
    }

    @Test
    fun `dmReplyTo returns null when no e tag`() = runTest {
        val senderKeyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(senderKeyPair)
        val recipientKeyPair = NDKKeyPair.generate()

        val unsigned = createPrivateMessage(
            recipientPubkey = recipientKeyPair.pubkeyHex,
            content = "Test"
        )
        val event = signer.sign(unsigned)

        assertNull("Should return null when no e tag", event.dmReplyTo)
    }
}
