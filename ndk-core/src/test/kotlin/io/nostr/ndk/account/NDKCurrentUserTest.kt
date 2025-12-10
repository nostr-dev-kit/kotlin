package io.nostr.ndk.account

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NDKCurrentUserTest {

    @Test
    fun `NDKCurrentUser has pubkey from signer`() {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)
        val ndk = NDK()

        val currentUser = NDKCurrentUser(signer, ndk)

        assertEquals(keyPair.pubkeyHex, currentUser.pubkey)
    }

    @Test
    fun `NDKCurrentUser can sign events`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)
        val ndk = NDK()

        val currentUser = NDKCurrentUser(signer, ndk)

        val unsigned = UnsignedEvent(
            pubkey = currentUser.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = 1,
            tags = emptyList(),
            content = "Hello Nostr!"
        )

        val signed = currentUser.sign(unsigned)

        assertNotNull(signed.id)
        assertNotNull(signed.sig)
        assertEquals(currentUser.pubkey, signed.pubkey)
        assertEquals("Hello Nostr!", signed.content)
    }

    @Test
    fun `NDKCurrentUser has empty follows initially`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)
        val ndk = NDK()

        val currentUser = NDKCurrentUser(signer, ndk)

        assertTrue(currentUser.follows.value.isEmpty())
    }

    @Test
    fun `NDKCurrentUser updates follows from contact list event`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)
        val ndk = NDK()

        val currentUser = NDKCurrentUser(signer, ndk)

        // Simulate receiving a contact list event
        val contactListEvent = NDKEvent(
            id = "abc",
            pubkey = keyPair.pubkeyHex,
            createdAt = 1000L,
            kind = 3,
            tags = listOf(
                NDKTag("p", listOf("followed1")),
                NDKTag("p", listOf("followed2")),
            ),
            content = "",
            sig = "sig"
        )

        currentUser.handleEvent(contactListEvent)

        assertEquals(2, currentUser.follows.value.size)
        assertTrue(currentUser.follows.value.contains("followed1"))
        assertTrue(currentUser.follows.value.contains("followed2"))
    }

    @Test
    fun `NDKCurrentUser updates mutes from mute list event`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)
        val ndk = NDK()

        val currentUser = NDKCurrentUser(signer, ndk)

        val muteListEvent = NDKEvent(
            id = "abc",
            pubkey = keyPair.pubkeyHex,
            createdAt = 1000L,
            kind = 10000,
            tags = listOf(
                NDKTag("p", listOf("muted1")),
                NDKTag("word", listOf("spam")),
            ),
            content = "",
            sig = "sig"
        )

        currentUser.handleEvent(muteListEvent)

        val muteList = currentUser.mutes.value
        assertTrue(muteList.isMuted("muted1"))
        assertTrue(muteList.words.contains("spam"))
    }

    @Test
    fun `NDKCurrentUser ignores older events`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)
        val ndk = NDK()

        val currentUser = NDKCurrentUser(signer, ndk)

        // First, add newer event
        val newerEvent = NDKEvent(
            id = "newer",
            pubkey = keyPair.pubkeyHex,
            createdAt = 2000L,
            kind = 3,
            tags = listOf(NDKTag("p", listOf("newer-follow"))),
            content = "",
            sig = "sig"
        )
        currentUser.handleEvent(newerEvent)

        // Then try to add older event
        val olderEvent = NDKEvent(
            id = "older",
            pubkey = keyPair.pubkeyHex,
            createdAt = 1000L,
            kind = 3,
            tags = listOf(NDKTag("p", listOf("older-follow"))),
            content = "",
            sig = "sig"
        )
        currentUser.handleEvent(olderEvent)

        // Should still have newer data
        assertEquals(1, currentUser.follows.value.size)
        assertTrue(currentUser.follows.value.contains("newer-follow"))
        assertFalse(currentUser.follows.value.contains("older-follow"))
    }
}
