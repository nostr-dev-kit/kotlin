package io.nostr.ndk.account.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidAccountStorageTest {

    private lateinit var storage: AndroidAccountStorage

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        storage = AndroidAccountStorage.getInstance(context)
    }

    @After
    fun cleanup() = runTest {
        // Clean up all test accounts
        val accounts = storage.listAccounts()
        accounts.forEach { pubkey ->
            storage.deleteAccount(pubkey)
        }
    }

    @Test
    fun saveSigner_and_loadSigner_roundtrip() = runTest {
        val pubkey = "test_pubkey_123"
        val payload = "test_payload_data".toByteArray()

        storage.saveSigner(pubkey, payload)
        val loaded = storage.loadSigner(pubkey)

        assertNotNull(loaded)
        assertArrayEquals(payload, loaded)
    }

    @Test
    fun loadSigner_returns_null_for_unknown_pubkey() = runTest {
        val unknownPubkey = "nonexistent_pubkey_xyz"

        val loaded = storage.loadSigner(unknownPubkey)

        assertNull(loaded)
    }

    @Test
    fun listAccounts_returns_saved_accounts() = runTest {
        val pubkey1 = "pubkey_1"
        val pubkey2 = "pubkey_2"
        val pubkey3 = "pubkey_3"
        val payload = "payload".toByteArray()

        storage.saveSigner(pubkey1, payload)
        storage.saveSigner(pubkey2, payload)
        storage.saveSigner(pubkey3, payload)

        val accounts = storage.listAccounts()

        assertEquals(3, accounts.size)
        assertTrue(accounts.contains(pubkey1))
        assertTrue(accounts.contains(pubkey2))
        assertTrue(accounts.contains(pubkey3))
    }

    @Test
    fun deleteAccount_removes_account() = runTest {
        val pubkey = "pubkey_to_delete"
        val payload = "some_payload".toByteArray()

        // Save account
        storage.saveSigner(pubkey, payload)
        assertTrue(storage.listAccounts().contains(pubkey))
        assertNotNull(storage.loadSigner(pubkey))

        // Delete account
        storage.deleteAccount(pubkey)

        // Verify removed from both file and list
        assertNull(storage.loadSigner(pubkey))
        assertTrue(!storage.listAccounts().contains(pubkey))
    }

    @Test
    fun saveSigner_overwrites_existing_account() = runTest {
        val pubkey = "pubkey_overwrite"
        val payload1 = "original_payload".toByteArray()
        val payload2 = "updated_payload".toByteArray()

        // Save original
        storage.saveSigner(pubkey, payload1)
        val loaded1 = storage.loadSigner(pubkey)
        assertArrayEquals(payload1, loaded1)

        // Overwrite with new payload
        storage.saveSigner(pubkey, payload2)
        val loaded2 = storage.loadSigner(pubkey)

        // Verify new payload is loaded
        assertArrayEquals(payload2, loaded2)
        // Verify still only one account in list
        assertEquals(1, storage.listAccounts().filter { it == pubkey }.size)
    }

    @Test
    fun data_persists_across_storage_instances() = runTest {
        val pubkey = "pubkey_persist"
        val payload = "persistent_data".toByteArray()

        // Save with first instance
        storage.saveSigner(pubkey, payload)

        // Create new instance
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage2 = AndroidAccountStorage.getInstance(context)

        // Load with new instance
        val loaded = storage2.loadSigner(pubkey)

        assertNotNull(loaded)
        assertArrayEquals(payload, loaded)
        assertTrue(storage2.listAccounts().contains(pubkey))
    }

    @Test
    fun handles_large_signer_payload() = runTest {
        val pubkey = "pubkey_large"
        // Create 10KB+ payload
        val payload = ByteArray(10 * 1024) { (it % 256).toByte() }

        storage.saveSigner(pubkey, payload)
        val loaded = storage.loadSigner(pubkey)

        assertNotNull(loaded)
        assertArrayEquals(payload, loaded)
        assertEquals(10 * 1024, loaded?.size)
    }

    @Test
    fun handles_real_hex_pubkey() = runTest {
        // Real 64-char hex pubkey
        val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        val payload = "signer_data_for_real_key".toByteArray()

        storage.saveSigner(pubkey, payload)
        val loaded = storage.loadSigner(pubkey)

        assertNotNull(loaded)
        assertArrayEquals(payload, loaded)
        assertTrue(storage.listAccounts().contains(pubkey))
    }
}
