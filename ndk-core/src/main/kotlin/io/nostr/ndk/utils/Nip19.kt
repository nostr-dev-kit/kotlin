package io.nostr.ndk.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * NIP-19 encoding/decoding utilities for Nostr identifiers.
 *
 * Supports:
 * - npub: Public key (32 bytes)
 * - nsec: Private key (32 bytes)
 * - note: Event ID (32 bytes)
 * - nevent: Event with optional relays, author, kind
 * - nprofile: Profile with optional relays
 * - naddr: Parameterized replaceable event coordinate
 *
 * Reference: https://github.com/nostr-protocol/nips/blob/master/19.md
 */
object Nip19 {

    /**
     * Decoded NIP-19 identifier types.
     */
    sealed class Decoded {
        data class Npub(val pubkey: String) : Decoded()
        data class Nsec(val privateKey: String) : Decoded()
        data class Note(val eventId: String) : Decoded()
        data class Nevent(
            val eventId: String,
            val relays: List<String> = emptyList(),
            val author: String? = null,
            val kind: Int? = null
        ) : Decoded()
        data class Nprofile(
            val pubkey: String,
            val relays: List<String> = emptyList()
        ) : Decoded()
        data class Naddr(
            val identifier: String,
            val pubkey: String,
            val kind: Int,
            val relays: List<String> = emptyList()
        ) : Decoded()
    }

    // TLV types for complex entities
    private object TlvType {
        const val SPECIAL = 0
        const val RELAY = 1
        const val AUTHOR = 2
        const val KIND = 3
    }

    /**
     * Encodes a public key as npub.
     */
    fun encodeNpub(pubkeyHex: String): String {
        val bytes = pubkeyHex.hexToBytes()
        require(bytes.size == 32) { "Public key must be 32 bytes" }
        return Bech32.encode("npub", bytes)
    }

    /**
     * Encodes a private key as nsec.
     */
    fun encodeNsec(privateKeyHex: String): String {
        val bytes = privateKeyHex.hexToBytes()
        require(bytes.size == 32) { "Private key must be 32 bytes" }
        return Bech32.encode("nsec", bytes)
    }

    /**
     * Encodes an event ID as note.
     */
    fun encodeNote(eventIdHex: String): String {
        val bytes = eventIdHex.hexToBytes()
        require(bytes.size == 32) { "Event ID must be 32 bytes" }
        return Bech32.encode("note", bytes)
    }

    /**
     * Encodes an event reference with optional metadata.
     */
    fun encodeNevent(
        eventIdHex: String,
        relays: List<String> = emptyList(),
        author: String? = null,
        kind: Int? = null
    ): String {
        val eventIdBytes = eventIdHex.hexToBytes()
        require(eventIdBytes.size == 32) { "Event ID must be 32 bytes" }

        val tlvData = buildTlvData {
            // Special (event ID)
            add(TlvType.SPECIAL, eventIdBytes)

            // Relays
            relays.forEach { relay ->
                add(TlvType.RELAY, relay.toByteArray())
            }

            // Author
            author?.let {
                val authorBytes = it.hexToBytes()
                require(authorBytes.size == 32) { "Author pubkey must be 32 bytes" }
                add(TlvType.AUTHOR, authorBytes)
            }

            // Kind
            kind?.let {
                add(TlvType.KIND, it.toByteArray())
            }
        }

        return Bech32.encode("nevent", tlvData)
    }

    /**
     * Encodes a profile reference with optional relays.
     */
    fun encodeNprofile(
        pubkeyHex: String,
        relays: List<String> = emptyList()
    ): String {
        val pubkeyBytes = pubkeyHex.hexToBytes()
        require(pubkeyBytes.size == 32) { "Public key must be 32 bytes" }

        val tlvData = buildTlvData {
            // Special (pubkey)
            add(TlvType.SPECIAL, pubkeyBytes)

            // Relays
            relays.forEach { relay ->
                add(TlvType.RELAY, relay.toByteArray())
            }
        }

        return Bech32.encode("nprofile", tlvData)
    }

    /**
     * Encodes a parameterized replaceable event coordinate.
     */
    fun encodeNaddr(
        identifier: String,
        pubkeyHex: String,
        kind: Int,
        relays: List<String> = emptyList()
    ): String {
        val pubkeyBytes = pubkeyHex.hexToBytes()
        require(pubkeyBytes.size == 32) { "Public key must be 32 bytes" }

        val tlvData = buildTlvData {
            // Special (identifier)
            add(TlvType.SPECIAL, identifier.toByteArray())

            // Relays
            relays.forEach { relay ->
                add(TlvType.RELAY, relay.toByteArray())
            }

            // Author
            add(TlvType.AUTHOR, pubkeyBytes)

            // Kind
            add(TlvType.KIND, kind.toByteArray())
        }

        return Bech32.encode("naddr", tlvData)
    }

    /**
     * Decodes a NIP-19 identifier.
     */
    fun decode(nip19: String): Decoded {
        val decoded = Bech32.decode(nip19)

        return when (decoded.hrp) {
            "npub" -> {
                require(decoded.data.size == 32) { "Invalid npub length" }
                Decoded.Npub(decoded.data.toHex())
            }
            "nsec" -> {
                require(decoded.data.size == 32) { "Invalid nsec length" }
                Decoded.Nsec(decoded.data.toHex())
            }
            "note" -> {
                require(decoded.data.size == 32) { "Invalid note length" }
                Decoded.Note(decoded.data.toHex())
            }
            "nevent" -> {
                val tlv = parseTlv(decoded.data)
                val eventId = tlv[TlvType.SPECIAL]?.firstOrNull()?.toHex()
                    ?: throw IllegalArgumentException("Missing event ID in nevent")
                val relays = tlv[TlvType.RELAY]?.map { String(it) } ?: emptyList()
                val author = tlv[TlvType.AUTHOR]?.firstOrNull()?.toHex()
                val kind = tlv[TlvType.KIND]?.firstOrNull()?.toInt()

                Decoded.Nevent(eventId, relays, author, kind)
            }
            "nprofile" -> {
                val tlv = parseTlv(decoded.data)
                val pubkey = tlv[TlvType.SPECIAL]?.firstOrNull()?.toHex()
                    ?: throw IllegalArgumentException("Missing pubkey in nprofile")
                val relays = tlv[TlvType.RELAY]?.map { String(it) } ?: emptyList()

                Decoded.Nprofile(pubkey, relays)
            }
            "naddr" -> {
                val tlv = parseTlv(decoded.data)
                val identifier = tlv[TlvType.SPECIAL]?.firstOrNull()?.let { String(it) }
                    ?: throw IllegalArgumentException("Missing identifier in naddr")
                val pubkey = tlv[TlvType.AUTHOR]?.firstOrNull()?.toHex()
                    ?: throw IllegalArgumentException("Missing author in naddr")
                val kind = tlv[TlvType.KIND]?.firstOrNull()?.toInt()
                    ?: throw IllegalArgumentException("Missing kind in naddr")
                val relays = tlv[TlvType.RELAY]?.map { String(it) } ?: emptyList()

                Decoded.Naddr(identifier, pubkey, kind, relays)
            }
            else -> throw IllegalArgumentException("Unknown NIP-19 prefix: ${decoded.hrp}")
        }
    }

    /**
     * Builder for TLV data.
     */
    private class TlvBuilder {
        private val items = mutableListOf<Pair<Int, ByteArray>>()

        fun add(type: Int, value: ByteArray) {
            items.add(type to value)
        }

        fun build(): ByteArray {
            val buffer = ByteBuffer.allocate(items.sumOf { 1 + 1 + it.second.size })
            buffer.order(ByteOrder.BIG_ENDIAN)

            for ((type, value) in items) {
                buffer.put(type.toByte())
                buffer.put(value.size.toByte())
                buffer.put(value)
            }

            return buffer.array()
        }
    }

    private inline fun buildTlvData(block: TlvBuilder.() -> Unit): ByteArray {
        return TlvBuilder().apply(block).build()
    }

    /**
     * Parses TLV-encoded data.
     */
    private fun parseTlv(data: ByteArray): Map<Int, List<ByteArray>> {
        val result = mutableMapOf<Int, MutableList<ByteArray>>()
        var offset = 0

        while (offset < data.size) {
            require(offset + 2 <= data.size) { "Invalid TLV: incomplete entry" }

            val type = data[offset].toInt() and 0xff
            val length = data[offset + 1].toInt() and 0xff
            offset += 2

            require(offset + length <= data.size) { "Invalid TLV: data overflow" }

            val value = data.copyOfRange(offset, offset + length)
            result.getOrPut(type) { mutableListOf() }.add(value)

            offset += length
        }

        return result
    }

    /**
     * Converts a hex string to bytes.
     */
    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Hex string must have even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    /**
     * Converts bytes to hex string.
     */
    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }

    /**
     * Converts an Int to bytes (4 bytes, big-endian).
     */
    private fun Int.toByteArray(): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(this).array()
    }

    /**
     * Converts bytes to Int (big-endian).
     */
    private fun ByteArray.toInt(): Int {
        require(size == 4) { "ByteArray must be 4 bytes to convert to Int" }
        return ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN).int
    }
}
