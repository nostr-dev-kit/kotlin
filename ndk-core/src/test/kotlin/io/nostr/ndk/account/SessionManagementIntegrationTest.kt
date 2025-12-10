package io.nostr.ndk.account

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.nips.KIND_CONTACT_LIST
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration tests for the full session management flow.
 */
class SessionManagementIntegrationTest {

    @Test
    fun `full session lifecycle - login, use, logout`() = runTest {
        // Setup
        val storage = InMemoryAccountStorage()
        val ndk = NDK(accountStorage = storage)
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        // Login
        val me = ndk.login(signer)
        assertEquals(keyPair.pubkeyHex, me.pubkey)
        assertEquals(me, ndk.currentUser.value)
        assertEquals(1, ndk.accounts.value.size)

        // Simulate receiving session data
        val contactList = NDKEvent(
            id = "contacts",
            pubkey = keyPair.pubkeyHex,
            createdAt = 1000L,
            kind = KIND_CONTACT_LIST,
            tags = listOf(
                NDKTag("p", listOf("friend1")),
                NDKTag("p", listOf("friend2"))
            ),
            content = "",
            sig = "sig"
        )
        me.handleEvent(contactList)

        // Verify session data
        assertEquals(2, me.follows.value.size)
        assertTrue(me.follows.value.contains("friend1"))

        // Sign an event
        val unsigned = io.nostr.ndk.crypto.UnsignedEvent(
            pubkey = me.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = 1,
            tags = emptyList(),
            content = "Hello from NDK!"
        )
        val signed = me.sign(unsigned)
        assertNotNull(signed.sig)

        // Logout
        ndk.logout()
        assertNull(ndk.currentUser.value)
        assertEquals(0, ndk.accounts.value.size)
    }

    @Test
    fun `multi-account workflow`() = runTest {
        val ndk = NDK()
        val keyPair1 = NDKKeyPair.generate()
        val keyPair2 = NDKKeyPair.generate()
        val signer1 = NDKPrivateKeySigner(keyPair1)
        val signer2 = NDKPrivateKeySigner(keyPair2)

        // Login first account
        val user1 = ndk.login(signer1)
        assertEquals(user1, ndk.currentUser.value)

        // Add session data for user1
        user1.handleEvent(NDKEvent(
            id = "u1-contacts",
            pubkey = keyPair1.pubkeyHex,
            createdAt = 1000L,
            kind = KIND_CONTACT_LIST,
            tags = listOf(NDKTag("p", listOf("user1-friend"))),
            content = "",
            sig = "sig"
        ))

        // Login second account (becomes current)
        val user2 = ndk.login(signer2)
        assertEquals(user2, ndk.currentUser.value)
        assertEquals(2, ndk.accounts.value.size)

        // Add session data for user2
        user2.handleEvent(NDKEvent(
            id = "u2-contacts",
            pubkey = keyPair2.pubkeyHex,
            createdAt = 1000L,
            kind = KIND_CONTACT_LIST,
            tags = listOf(NDKTag("p", listOf("user2-friend"))),
            content = "",
            sig = "sig"
        ))

        // Verify each user has their own data
        assertTrue(user1.follows.value.contains("user1-friend"))
        assertFalse(user1.follows.value.contains("user2-friend"))
        assertTrue(user2.follows.value.contains("user2-friend"))
        assertFalse(user2.follows.value.contains("user1-friend"))

        // Switch accounts
        ndk.switchAccount(keyPair1.pubkeyHex)
        assertEquals(user1, ndk.currentUser.value)

        // Logout user1 only
        ndk.logout(keyPair1.pubkeyHex)
        assertEquals(1, ndk.accounts.value.size)
        assertEquals(user2, ndk.currentUser.value)
    }

    @Test
    fun `account restoration from storage`() = runTest {
        val storage = InMemoryAccountStorage()

        // First session: login and persist
        run {
            val ndk = NDK(accountStorage = storage)
            val keyPair = NDKKeyPair.generate()
            val signer = NDKPrivateKeySigner(keyPair)
            ndk.login(signer)
        }

        // Verify data was persisted
        assertEquals(1, storage.listAccounts().size)

        // Second session: restore
        run {
            val ndk = NDK(accountStorage = storage)
            val restored = ndk.restoreAccounts()

            assertEquals(1, restored.size)
            assertNotNull(ndk.currentUser.value)
            assertEquals(restored.first().pubkey, ndk.currentUser.value?.pubkey)
        }
    }
}
