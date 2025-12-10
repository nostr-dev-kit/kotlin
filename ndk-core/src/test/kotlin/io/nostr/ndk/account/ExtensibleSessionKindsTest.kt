package io.nostr.ndk.account

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ExtensibleSessionKindsTest {

    @Test
    fun `can register additional session kinds`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        // Register kind 30078 (app-specific data) before login
        ndk.registerSessionKind(30078)

        val currentUser = ndk.login(signer)
        val filters = currentUser.sessionSubscription?.filters ?: emptyList()

        val kinds = filters.flatMap { it.kinds ?: emptySet() }.toSet()
        assertTrue(kinds.contains(30078))
    }

    @Test
    fun `custom kind events stored in sessionEvents`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        ndk.registerSessionKind(30078)
        val currentUser = ndk.login(signer)

        // Simulate receiving custom kind event
        val customEvent = NDKEvent(
            id = "abc",
            pubkey = keyPair.pubkeyHex,
            createdAt = 1000L,
            kind = 30078,
            tags = listOf(NDKTag("d", listOf("my-app-data"))),
            content = """{"preference":"dark-mode"}""",
            sig = "sig"
        )

        currentUser.handleEvent(customEvent)

        val storedEvent = currentUser.sessionEvents.value[30078]
        assertNotNull(storedEvent)
        assertEquals(customEvent.content, storedEvent?.content)
    }

    @Test
    fun `sessionEvents updated with newer event only`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        ndk.registerSessionKind(30078)
        val currentUser = ndk.login(signer)

        // Add newer event first
        val newerEvent = NDKEvent(
            id = "newer",
            pubkey = keyPair.pubkeyHex,
            createdAt = 2000L,
            kind = 30078,
            tags = emptyList(),
            content = "newer",
            sig = "sig"
        )
        currentUser.handleEvent(newerEvent)

        // Try to add older event
        val olderEvent = NDKEvent(
            id = "older",
            pubkey = keyPair.pubkeyHex,
            createdAt = 1000L,
            kind = 30078,
            tags = emptyList(),
            content = "older",
            sig = "sig"
        )
        currentUser.handleEvent(olderEvent)

        val storedEvent = currentUser.sessionEvents.value[30078]
        assertEquals("newer", storedEvent?.content)
    }
}
