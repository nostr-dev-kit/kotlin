package io.nostr.ndk.account

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NDKAccountStorageTest {

    @Test
    fun `InMemoryAccountStorage saves and loads signer data`() = runTest {
        val storage = InMemoryAccountStorage()
        val pubkey = "abc123"
        val signerData = "test-signer-payload".toByteArray()

        storage.saveSigner(pubkey, signerData)
        val loaded = storage.loadSigner(pubkey)

        assertNotNull(loaded)
        assertArrayEquals(signerData, loaded)
    }

    @Test
    fun `InMemoryAccountStorage returns null for unknown pubkey`() = runTest {
        val storage = InMemoryAccountStorage()

        val loaded = storage.loadSigner("unknown")

        assertNull(loaded)
    }

    @Test
    fun `InMemoryAccountStorage lists saved accounts`() = runTest {
        val storage = InMemoryAccountStorage()
        storage.saveSigner("pubkey1", "data1".toByteArray())
        storage.saveSigner("pubkey2", "data2".toByteArray())

        val accounts = storage.listAccounts()

        assertEquals(2, accounts.size)
        assertTrue(accounts.contains("pubkey1"))
        assertTrue(accounts.contains("pubkey2"))
    }

    @Test
    fun `InMemoryAccountStorage deletes account`() = runTest {
        val storage = InMemoryAccountStorage()
        storage.saveSigner("pubkey1", "data1".toByteArray())

        storage.deleteAccount("pubkey1")

        assertNull(storage.loadSigner("pubkey1"))
        assertEquals(0, storage.listAccounts().size)
    }

    @Test
    fun `InMemoryAccountStorage overwrites existing account`() = runTest {
        val storage = InMemoryAccountStorage()
        storage.saveSigner("pubkey1", "old-data".toByteArray())
        storage.saveSigner("pubkey1", "new-data".toByteArray())

        val loaded = storage.loadSigner("pubkey1")

        assertArrayEquals("new-data".toByteArray(), loaded)
        assertEquals(1, storage.listAccounts().size)
    }
}
