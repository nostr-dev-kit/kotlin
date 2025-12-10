package io.nostr.ndk.crypto

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for NDKRemoteSigner NIP-46 implementation.
 *
 * Note: These tests cover URL parsing and basic validation.
 * Integration tests with actual remote signers require network access
 * and a running NIP-46 signer application.
 */
class NDKRemoteSignerTest {

    @Test
    fun `BunkerUrl parse extracts pubkey from valid URL`() {
        val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        val url = "bunker://$pubkey?relay=wss://relay.example.com"

        val result = BunkerUrl.parse(url)

        assertEquals(pubkey, result.pubkey)
    }

    @Test
    fun `BunkerUrl parse extracts single relay`() {
        val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        val url = "bunker://$pubkey?relay=wss://relay.example.com"

        val result = BunkerUrl.parse(url)

        assertEquals(1, result.relays.size)
        assertEquals("wss://relay.example.com", result.relays[0])
    }

    @Test
    fun `BunkerUrl parse extracts multiple relays`() {
        val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        val url = "bunker://$pubkey?relay=wss://relay1.example.com&relay=wss://relay2.example.com"

        val result = BunkerUrl.parse(url)

        assertEquals(2, result.relays.size)
        assertTrue(result.relays.contains("wss://relay1.example.com"))
        assertTrue(result.relays.contains("wss://relay2.example.com"))
    }

    @Test
    fun `BunkerUrl parse extracts optional secret`() {
        val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        val secret = "mysecret123"
        val url = "bunker://$pubkey?relay=wss://relay.example.com&secret=$secret"

        val result = BunkerUrl.parse(url)

        assertEquals(secret, result.secret)
    }

    @Test
    fun `BunkerUrl parse handles missing secret`() {
        val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        val url = "bunker://$pubkey?relay=wss://relay.example.com"

        val result = BunkerUrl.parse(url)

        assertNull(result.secret)
    }

    @Test
    fun `BunkerUrl parse handles URL-encoded relay`() {
        val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        val encodedRelay = "wss%3A%2F%2Frelay.example.com%2Fpath"
        val url = "bunker://$pubkey?relay=$encodedRelay"

        val result = BunkerUrl.parse(url)

        assertEquals("wss://relay.example.com/path", result.relays[0])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `BunkerUrl parse rejects non-bunker URL`() {
        BunkerUrl.parse("https://example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `BunkerUrl parse rejects URL without pubkey`() {
        BunkerUrl.parse("bunker://?relay=wss://relay.example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `BunkerUrl parse rejects invalid pubkey length`() {
        val shortPubkey = "abc123"
        BunkerUrl.parse("bunker://$shortPubkey?relay=wss://relay.example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `BunkerUrl parse rejects invalid pubkey characters`() {
        val invalidPubkey = "ZZZZ" + "0".repeat(60) // 64 chars but invalid hex
        BunkerUrl.parse("bunker://$invalidPubkey?relay=wss://relay.example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `BunkerUrl parse rejects URL without relay`() {
        val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        BunkerUrl.parse("bunker://$pubkey")
    }

    @Test
    fun `BunkerUrl parse handles complex URL with all parameters`() {
        val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        val url = "bunker://$pubkey?relay=wss://relay1.com&secret=abc&relay=wss://relay2.com"

        val result = BunkerUrl.parse(url)

        assertEquals(pubkey, result.pubkey)
        assertEquals(2, result.relays.size)
        assertEquals("abc", result.secret)
    }

    @Test
    fun `NDKKeyPair generates valid keypair`() {
        val keyPair = NDKKeyPair.generate()

        assertNotNull(keyPair.privateKey)
        assertNotNull(keyPair.publicKey)
        assertEquals(32, keyPair.privateKey?.size)
        assertEquals(32, keyPair.publicKey.size)
        assertEquals(64, keyPair.pubkeyHex.length)
    }
}
