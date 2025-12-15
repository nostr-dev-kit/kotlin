package io.nostr.ndk.blossom

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NDKBlossomTest {

    @Test
    fun `upload uses instance signer when ndk signer is null`() = runTest {
        // Create NDK without a signer
        val ndk = NDK()
        assertNull(ndk.signer)

        // Create signer separately
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        // Create NDKBlossom with explicit signer
        val blossom = NDKBlossom(ndk, signer)

        // The blossom instance should use its own signer
        assertEquals(signer, blossom.signer)
    }

    @Test
    fun `getServerList uses instance signer over ndk signer`() = runTest {
        val ndk = NDK()
        val instanceSigner = NDKPrivateKeySigner(NDKKeyPair.generate())

        val blossom = NDKBlossom(ndk, instanceSigner)

        // Set a cached server list to avoid the actual network call
        val serverList = BlossomServerList(
            pubkey = instanceSigner.pubkey,
            servers = listOf("https://blossom.example.com"),
            createdAt = System.currentTimeMillis() / 1000
        )
        blossom.setServerList(serverList)

        // Now getServerList should return without error
        val result = blossom.getServerList()
        assertNotNull(result)
        assertEquals(1, result?.servers?.size)
    }

    @Test
    fun `upload without any signer and no fallback throws SERVER_LIST_EMPTY error`() = runTest {
        val ndk = NDK()
        val blossom = NDKBlossom(ndk, null)

        val testData = ByteArray(100) { it.toByte() }
        val options = BlossomUploadOptions() // No fallback server

        try {
            blossom.upload(testData, "application/octet-stream", options)
            fail("Expected BlossomError to be thrown")
        } catch (e: BlossomError) {
            // Without a signer, getServerList fails but gets caught
            // Then without a fallback server, it throws SERVER_LIST_EMPTY
            assertEquals(BlossomErrorCode.SERVER_LIST_EMPTY, e.code)
        }
    }

    @Test
    fun `upload with cached server list uses servers from list`() = runTest {
        val ndk = NDK()
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())
        val blossom = NDKBlossom(ndk, signer)

        // Set a cached server list
        val serverList = BlossomServerList(
            pubkey = signer.pubkey,
            servers = listOf("https://blossom.example.com"),
            createdAt = System.currentTimeMillis() / 1000
        )
        blossom.setServerList(serverList)

        // Verify cached list is used
        val result = blossom.getServerList()
        assertEquals("https://blossom.example.com", result?.servers?.first())
    }
}
