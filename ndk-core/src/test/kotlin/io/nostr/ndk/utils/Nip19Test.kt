package io.nostr.ndk.utils

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for NIP-19 encoding/decoding.
 *
 * Test vectors from: https://github.com/nostr-protocol/nips/blob/master/19.md
 */
class Nip19Test {

    // Known test vectors from NIP-19
    companion object {
        // Public key: 3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d
        const val TEST_PUBKEY_HEX = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        const val TEST_PUBKEY_NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"

        // Private key: 67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa
        const val TEST_PRIVKEY_HEX = "67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa"
        const val TEST_PRIVKEY_NSEC = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"

        // Event ID (32-byte hex)
        const val TEST_EVENT_ID_HEX = "4376c65d2f232afbe9b882a35baa4f6fe8667c4e684749af565f981833ed6a65"
    }

    // npub tests
    @Test
    fun `encode npub from hex public key`() {
        val encoded = Nip19.encodeNpub(TEST_PUBKEY_HEX)
        assertEquals(TEST_PUBKEY_NPUB, encoded)
    }

    @Test
    fun `decode npub to hex public key`() {
        val decoded = Nip19.decode(TEST_PUBKEY_NPUB)
        assertTrue(decoded is Nip19.Decoded.Npub)
        assertEquals(TEST_PUBKEY_HEX, (decoded as Nip19.Decoded.Npub).pubkey)
    }

    @Test
    fun `npub encode and decode round-trip`() {
        val encoded = Nip19.encodeNpub(TEST_PUBKEY_HEX)
        val decoded = Nip19.decode(encoded)
        assertTrue(decoded is Nip19.Decoded.Npub)
        assertEquals(TEST_PUBKEY_HEX, (decoded as Nip19.Decoded.Npub).pubkey)
    }

    // nsec tests
    @Test
    fun `encode nsec from hex private key`() {
        val encoded = Nip19.encodeNsec(TEST_PRIVKEY_HEX)
        assertEquals(TEST_PRIVKEY_NSEC, encoded)
    }

    @Test
    fun `decode nsec to hex private key`() {
        val decoded = Nip19.decode(TEST_PRIVKEY_NSEC)
        assertTrue(decoded is Nip19.Decoded.Nsec)
        assertEquals(TEST_PRIVKEY_HEX, (decoded as Nip19.Decoded.Nsec).privateKey)
    }

    @Test
    fun `nsec encode and decode round-trip`() {
        val encoded = Nip19.encodeNsec(TEST_PRIVKEY_HEX)
        val decoded = Nip19.decode(encoded)
        assertTrue(decoded is Nip19.Decoded.Nsec)
        assertEquals(TEST_PRIVKEY_HEX, (decoded as Nip19.Decoded.Nsec).privateKey)
    }

    // note tests
    @Test
    fun `encode note from hex event id`() {
        val encoded = Nip19.encodeNote(TEST_EVENT_ID_HEX)
        assertTrue(encoded.startsWith("note1"))
    }

    @Test
    fun `decode note to hex event id`() {
        // First encode to get a valid note
        val encoded = Nip19.encodeNote(TEST_EVENT_ID_HEX)
        val decoded = Nip19.decode(encoded)
        assertTrue(decoded is Nip19.Decoded.Note)
        assertEquals(TEST_EVENT_ID_HEX, (decoded as Nip19.Decoded.Note).eventId)
    }

    @Test
    fun `note encode and decode round-trip`() {
        val encoded = Nip19.encodeNote(TEST_EVENT_ID_HEX)
        val decoded = Nip19.decode(encoded)
        assertTrue(decoded is Nip19.Decoded.Note)
        assertEquals(TEST_EVENT_ID_HEX, (decoded as Nip19.Decoded.Note).eventId)
    }

    // nevent tests
    @Test
    fun `encode nevent with event id and relays`() {
        val relays = listOf("wss://relay.nostr.example", "wss://relay2.nostr.example")
        val encoded = Nip19.encodeNevent(TEST_EVENT_ID_HEX, relays = relays)

        assertTrue(encoded.startsWith("nevent1"))

        val decoded = Nip19.decode(encoded)
        assertTrue(decoded is Nip19.Decoded.Nevent)
        val nevent = decoded as Nip19.Decoded.Nevent
        assertEquals(TEST_EVENT_ID_HEX, nevent.eventId)
        assertEquals(relays, nevent.relays)
    }

    @Test
    fun `encode nevent with event id, relays, and author`() {
        val relays = listOf("wss://relay.nostr.example")
        val author = TEST_PUBKEY_HEX
        val encoded = Nip19.encodeNevent(TEST_EVENT_ID_HEX, relays = relays, author = author)

        val decoded = Nip19.decode(encoded)
        assertTrue(decoded is Nip19.Decoded.Nevent)
        val nevent = decoded as Nip19.Decoded.Nevent
        assertEquals(TEST_EVENT_ID_HEX, nevent.eventId)
        assertEquals(relays, nevent.relays)
        assertEquals(author, nevent.author)
    }

    @Test
    fun `encode nevent with event id, relays, author, and kind`() {
        val relays = listOf("wss://relay.nostr.example")
        val author = TEST_PUBKEY_HEX
        val kind = 1
        val encoded = Nip19.encodeNevent(TEST_EVENT_ID_HEX, relays = relays, author = author, kind = kind)

        val decoded = Nip19.decode(encoded)
        assertTrue(decoded is Nip19.Decoded.Nevent)
        val nevent = decoded as Nip19.Decoded.Nevent
        assertEquals(TEST_EVENT_ID_HEX, nevent.eventId)
        assertEquals(relays, nevent.relays)
        assertEquals(author, nevent.author)
        assertEquals(kind, nevent.kind)
    }

    @Test
    fun `nevent encode and decode round-trip with all fields`() {
        val relays = listOf("wss://relay.nostr.example", "wss://relay2.nostr.example")
        val author = TEST_PUBKEY_HEX
        val kind = 1

        val encoded = Nip19.encodeNevent(TEST_EVENT_ID_HEX, relays = relays, author = author, kind = kind)
        val decoded = Nip19.decode(encoded)

        assertTrue(decoded is Nip19.Decoded.Nevent)
        val nevent = decoded as Nip19.Decoded.Nevent
        assertEquals(TEST_EVENT_ID_HEX, nevent.eventId)
        assertEquals(relays, nevent.relays)
        assertEquals(author, nevent.author)
        assertEquals(kind, nevent.kind)
    }

    // nprofile tests
    @Test
    fun `encode nprofile with pubkey and relays`() {
        val relays = listOf("wss://relay.nostr.example")
        val encoded = Nip19.encodeNprofile(TEST_PUBKEY_HEX, relays = relays)

        assertTrue(encoded.startsWith("nprofile1"))

        val decoded = Nip19.decode(encoded)
        assertTrue(decoded is Nip19.Decoded.Nprofile)
        val nprofile = decoded as Nip19.Decoded.Nprofile
        assertEquals(TEST_PUBKEY_HEX, nprofile.pubkey)
        assertEquals(relays, nprofile.relays)
    }

    @Test
    fun `nprofile encode and decode round-trip`() {
        val relays = listOf("wss://relay.nostr.example", "wss://relay2.nostr.example")

        val encoded = Nip19.encodeNprofile(TEST_PUBKEY_HEX, relays = relays)
        val decoded = Nip19.decode(encoded)

        assertTrue(decoded is Nip19.Decoded.Nprofile)
        val nprofile = decoded as Nip19.Decoded.Nprofile
        assertEquals(TEST_PUBKEY_HEX, nprofile.pubkey)
        assertEquals(relays, nprofile.relays)
    }

    // naddr tests
    @Test
    fun `encode naddr with identifier, pubkey, kind, and relays`() {
        val identifier = "test-article"
        val kind = 30023
        val relays = listOf("wss://relay.nostr.example")

        val encoded = Nip19.encodeNaddr(identifier, TEST_PUBKEY_HEX, kind, relays = relays)

        assertTrue(encoded.startsWith("naddr1"))

        val decoded = Nip19.decode(encoded)
        assertTrue(decoded is Nip19.Decoded.Naddr)
        val naddr = decoded as Nip19.Decoded.Naddr
        assertEquals(identifier, naddr.identifier)
        assertEquals(TEST_PUBKEY_HEX, naddr.pubkey)
        assertEquals(kind, naddr.kind)
        assertEquals(relays, naddr.relays)
    }

    @Test
    fun `naddr encode and decode round-trip`() {
        val identifier = "my-article"
        val kind = 30023
        val relays = listOf("wss://relay.nostr.example", "wss://relay2.nostr.example")

        val encoded = Nip19.encodeNaddr(identifier, TEST_PUBKEY_HEX, kind, relays = relays)
        val decoded = Nip19.decode(encoded)

        assertTrue(decoded is Nip19.Decoded.Naddr)
        val naddr = decoded as Nip19.Decoded.Naddr
        assertEquals(identifier, naddr.identifier)
        assertEquals(TEST_PUBKEY_HEX, naddr.pubkey)
        assertEquals(kind, naddr.kind)
        assertEquals(relays, naddr.relays)
    }

    // Error cases
    @Test
    fun `decode throws on invalid prefix`() {
        try {
            Nip19.decode("invalid1abcdef")
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `decode throws on malformed bech32`() {
        try {
            Nip19.decode("npub1invalid!")
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `encode npub throws on invalid hex length`() {
        try {
            Nip19.encodeNpub("abcd") // Too short
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `encode nsec throws on invalid hex length`() {
        try {
            Nip19.encodeNsec("abcd") // Too short
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `encode note throws on invalid hex length`() {
        try {
            Nip19.encodeNote("abcd") // Too short
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }
}
