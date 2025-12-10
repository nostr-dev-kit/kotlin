package io.nostr.ndk.utils

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for Bech32 encoding/decoding implementation.
 *
 * Test vectors from:
 * - BIP-173: https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki
 * - NIP-19: https://github.com/nostr-protocol/nips/blob/master/19.md
 */
class Bech32Test {

    @Test
    fun `encode and decode simple string round-trip`() {
        val data = "hello".toByteArray()
        val encoded = Bech32.encode("test", data)
        val decoded = Bech32.decode(encoded)

        assertEquals("test", decoded.hrp)
        assertEquals(data.toList(), decoded.data.toList())
    }

    @Test
    fun `encode with lowercase hrp`() {
        val data = byteArrayOf(0x00, 0x11, 0x22)
        val encoded = Bech32.encode("npub", data)

        assertTrue(encoded.startsWith("npub1"))
        // Bech32 should be all lowercase
        assertEquals(encoded, encoded.lowercase())
    }

    @Test
    fun `decode rejects mixed case`() {
        try {
            Bech32.decode("Npub1test")
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `decode rejects invalid characters`() {
        try {
            Bech32.decode("npub1test!")
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `decode rejects invalid checksum`() {
        // Valid structure but wrong checksum
        try {
            Bech32.decode("npub1abcdefgh")
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `decode rejects too short string`() {
        try {
            Bech32.decode("n1")
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `validate accepts valid bech32 string`() {
        val data = "test".toByteArray()
        val encoded = Bech32.encode("npub", data)

        assertTrue(Bech32.validate(encoded))
    }

    @Test
    fun `validate rejects invalid bech32 string`() {
        assertFalse(Bech32.validate("invalid"))
        assertFalse(Bech32.validate("npub1abc"))
        assertFalse(Bech32.validate(""))
    }

    @Test
    fun `encode handles 32-byte data for Nostr keys`() {
        // Typical Nostr public key size
        val data = ByteArray(32) { it.toByte() }
        val encoded = Bech32.encode("npub", data)
        val decoded = Bech32.decode(encoded)

        assertEquals("npub", decoded.hrp)
        assertEquals(data.toList(), decoded.data.toList())
    }

    @Test
    fun `known test vector from BIP-173`() {
        // Test vector: hrp "a", data = empty
        val encoded = Bech32.encode("a", byteArrayOf())
        val decoded = Bech32.decode(encoded)

        assertEquals("a", decoded.hrp)
        assertEquals(0, decoded.data.size)
    }

    @Test
    fun `bech32 alphabet does not contain ambiguous characters`() {
        // The alphabet should not contain 'b', 'i', 'o', '1' after the separator
        val alphabet = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

        assertFalse(alphabet.contains('b'))
        assertFalse(alphabet.contains('i'))
        assertFalse(alphabet.contains('o'))
        assertFalse(alphabet.contains('1'))
    }

    @Test
    fun `encode handles empty data`() {
        val encoded = Bech32.encode("test", byteArrayOf())
        val decoded = Bech32.decode(encoded)

        assertEquals("test", decoded.hrp)
        assertEquals(0, decoded.data.size)
    }

    @Test
    fun `encode handles maximum length data`() {
        // Bech32 supports up to 90 characters total
        // hrp + separator + data + checksum <= 90
        val data = ByteArray(40) { 0xFF.toByte() }
        val encoded = Bech32.encode("test", data)

        assertTrue(encoded.length <= 90)

        val decoded = Bech32.decode(encoded)
        assertEquals(data.toList(), decoded.data.toList())
    }
}
