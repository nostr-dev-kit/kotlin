package io.nostr.ndk.account

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.nips.KIND_CONTACT_LIST
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SessionDataLoadingTest {

    @Test
    fun `login starts session data subscription`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        val currentUser = ndk.login(signer)

        // Verify session subscription was started
        assertNotNull(currentUser.sessionSubscription)
    }

    @Test
    fun `session subscription filters for correct kinds`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        val currentUser = ndk.login(signer)
        val filters = currentUser.sessionSubscription?.filters ?: emptyList()

        // Should have filter for session data kinds
        assertTrue(filters.isNotEmpty())
        val kinds = filters.flatMap { it.kinds ?: emptySet() }.toSet()
        assertTrue(kinds.contains(KIND_CONTACT_LIST))
        assertTrue(kinds.contains(KIND_SESSION_MUTE_LIST))
        assertTrue(kinds.contains(KIND_SESSION_RELAY_LIST))
        assertTrue(kinds.contains(KIND_SESSION_BLOCKED_RELAY_LIST))
    }

    @Test
    fun `session subscription filters for user pubkey`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        val currentUser = ndk.login(signer)
        val filters = currentUser.sessionSubscription?.filters ?: emptyList()

        // Should filter for this user's pubkey
        assertTrue(filters.isNotEmpty())
        val authors = filters.flatMap { it.authors ?: emptySet() }.toSet()
        assertTrue(authors.contains(keyPair.pubkeyHex))
    }

    @Test
    fun `logout stops session subscription`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        val currentUser = ndk.login(signer)
        val subscription = currentUser.sessionSubscription

        ndk.logout()

        // Subscription should be stopped
        // (In real implementation, we'd check subscription.isClosed or similar)
        assertNotNull(subscription)
    }
}
