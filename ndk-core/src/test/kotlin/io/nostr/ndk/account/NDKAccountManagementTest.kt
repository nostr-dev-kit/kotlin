package io.nostr.ndk.account

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NDKAccountManagementTest {

    @Test
    fun `login creates current user`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        val currentUser = ndk.login(signer)

        assertNotNull(currentUser)
        assertEquals(keyPair.pubkeyHex, currentUser.pubkey)
        assertEquals(currentUser, ndk.currentUser.value)
    }

    @Test
    fun `login adds to accounts list`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        ndk.login(signer)

        assertEquals(1, ndk.accounts.value.size)
        assertEquals(keyPair.pubkeyHex, ndk.accounts.value.first().pubkey)
    }

    @Test
    fun `login with storage persists account`() = runTest {
        val storage = InMemoryAccountStorage()
        val ndk = NDK(accountStorage = storage)
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        ndk.login(signer)

        val savedAccounts = storage.listAccounts()
        assertEquals(1, savedAccounts.size)
        assertEquals(keyPair.pubkeyHex, savedAccounts.first())
    }

    @Test
    fun `logout clears current user`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        ndk.login(signer)
        ndk.logout()

        assertNull(ndk.currentUser.value)
    }

    @Test
    fun `logout with storage removes account`() = runTest {
        val storage = InMemoryAccountStorage()
        val ndk = NDK(accountStorage = storage)
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        ndk.login(signer)
        ndk.logout()

        assertEquals(0, storage.listAccounts().size)
    }

    @Test
    fun `login multiple accounts`() = runTest {
        val ndk = NDK()
        val keyPair1 = NDKKeyPair.generate()
        val keyPair2 = NDKKeyPair.generate()
        val signer1 = NDKPrivateKeySigner(keyPair1)
        val signer2 = NDKPrivateKeySigner(keyPair2)

        ndk.login(signer1)
        ndk.login(signer2)

        // Current user should be the last logged in
        assertEquals(keyPair2.pubkeyHex, ndk.currentUser.value?.pubkey)
        // Both accounts should be in the list
        assertEquals(2, ndk.accounts.value.size)
    }

    @Test
    fun `switchAccount changes current user`() = runTest {
        val ndk = NDK()
        val keyPair1 = NDKKeyPair.generate()
        val keyPair2 = NDKKeyPair.generate()
        val signer1 = NDKPrivateKeySigner(keyPair1)
        val signer2 = NDKPrivateKeySigner(keyPair2)

        ndk.login(signer1)
        ndk.login(signer2)

        // Switch back to first account
        val switched = ndk.switchAccount(keyPair1.pubkeyHex)

        assertTrue(switched)
        assertEquals(keyPair1.pubkeyHex, ndk.currentUser.value?.pubkey)
    }

    @Test
    fun `switchAccount returns false for unknown pubkey`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        ndk.login(signer)

        val switched = ndk.switchAccount("unknown-pubkey")

        assertFalse(switched)
    }

    @Test
    fun `restoreAccounts loads from storage`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        // First NDK: login and persist
        val storage = InMemoryAccountStorage()
        val ndk1 = NDK(accountStorage = storage)
        ndk1.login(signer)

        // Second NDK: restore from storage
        val ndk2 = NDK(accountStorage = storage)
        val restored = ndk2.restoreAccounts()

        assertEquals(1, restored.size)
        assertEquals(keyPair.pubkeyHex, restored.first().pubkey)
    }

    @Test
    fun `logout specific account by pubkey`() = runTest {
        val ndk = NDK()
        val keyPair1 = NDKKeyPair.generate()
        val keyPair2 = NDKKeyPair.generate()
        val signer1 = NDKPrivateKeySigner(keyPair1)
        val signer2 = NDKPrivateKeySigner(keyPair2)

        ndk.login(signer1)
        ndk.login(signer2)

        ndk.logout(keyPair1.pubkeyHex)

        assertEquals(1, ndk.accounts.value.size)
        assertEquals(keyPair2.pubkeyHex, ndk.accounts.value.first().pubkey)
        // Current user unchanged since we logged out the other one
        assertEquals(keyPair2.pubkeyHex, ndk.currentUser.value?.pubkey)
    }
}
