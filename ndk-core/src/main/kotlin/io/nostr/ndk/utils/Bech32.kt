package io.nostr.ndk.utils

/**
 * Bech32 encoding/decoding implementation for Nostr.
 *
 * Based on BIP-173: https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki
 *
 * Nostr uses lowercase-only Bech32 encoding for human-readable identifiers.
 */
object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val SEPARATOR = '1'

    /**
     * Result of decoding a Bech32 string.
     *
     * @property hrp Human-readable part (e.g., "npub", "nsec", "note")
     * @property data Decoded data bytes
     */
    data class Decoded(
        val hrp: String,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Decoded

            if (hrp != other.hrp) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = hrp.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /**
     * Encodes data with a human-readable prefix using Bech32.
     *
     * @param hrp Human-readable part (will be lowercased)
     * @param data Data bytes to encode
     * @return Bech32-encoded string (all lowercase)
     */
    fun encode(hrp: String, data: ByteArray): String {
        val hrpLower = hrp.lowercase()
        require(hrpLower.isNotEmpty()) { "HRP cannot be empty" }
        require(hrpLower.all { it in 'a'..'z' || it in '0'..'9' }) { "HRP contains invalid characters" }

        // Convert 8-bit data to 5-bit groups
        val fiveBitData = convertBits(data, 8, 5, pad = true)

        // Create checksum
        val checksum = createChecksum(hrpLower, fiveBitData)

        // Combine data and checksum
        val combined = fiveBitData + checksum

        // Encode to string
        return hrpLower + SEPARATOR + combined.map { CHARSET[it.toInt()] }.joinToString("")
    }

    /**
     * Decodes a Bech32 string.
     *
     * @param str Bech32-encoded string
     * @return Decoded result with HRP and data
     * @throws IllegalArgumentException if the string is invalid
     */
    fun decode(str: String): Decoded {
        require(str.isNotEmpty()) { "Bech32 string cannot be empty" }
        // Note: While BIP-173 suggests 90 chars, Nostr implementations often exceed this for TLV data
        require(str.length <= 1000) { "Bech32 string too long (max 1000 characters)" }

        // Must be all lowercase or all uppercase (but we only support lowercase for Nostr)
        require(str == str.lowercase()) { "Bech32 string must be lowercase" }

        // Find separator
        val separatorPos = str.lastIndexOf(SEPARATOR)
        require(separatorPos >= 1) { "Missing separator '1'" }
        require(separatorPos < str.length - 6) { "Data part too short (must be at least 6 characters for checksum)" }

        val hrp = str.substring(0, separatorPos)
        val dataString = str.substring(separatorPos + 1)

        // Decode data part
        val dataValues = dataString.map { char ->
            val index = CHARSET.indexOf(char)
            require(index >= 0) { "Invalid character in data part: $char" }
            index.toByte()
        }.toByteArray()

        // Verify checksum
        require(verifyChecksum(hrp, dataValues)) { "Invalid checksum" }

        // Remove checksum (last 6 bytes)
        val dataWithoutChecksum = dataValues.copyOfRange(0, dataValues.size - 6)

        // Convert from 5-bit to 8-bit
        val data = convertBits(dataWithoutChecksum, 5, 8, pad = false)

        return Decoded(hrp, data)
    }

    /**
     * Validates a Bech32 string without decoding it.
     *
     * @param str String to validate
     * @return true if valid Bech32, false otherwise
     */
    fun validate(str: String): Boolean {
        return try {
            decode(str)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Converts between different bit group sizes.
     *
     * @param data Input data
     * @param fromBits Input bit group size
     * @param toBits Output bit group size
     * @param pad Whether to pad the output
     * @return Converted data
     */
    private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray {
        var acc = 0
        var bits = 0
        val result = mutableListOf<Byte>()
        val maxv = (1 shl toBits) - 1

        for (value in data) {
            val v = value.toInt() and 0xff
            require(v ushr fromBits == 0) { "Invalid data" }

            acc = (acc shl fromBits) or v
            bits += fromBits

            while (bits >= toBits) {
                bits -= toBits
                result.add(((acc ushr bits) and maxv).toByte())
            }
        }

        if (pad) {
            if (bits > 0) {
                result.add(((acc shl (toBits - bits)) and maxv).toByte())
            }
        } else {
            require(bits < fromBits) { "Invalid padding" }
            require(((acc shl (toBits - bits)) and maxv) == 0) { "Non-zero padding" }
        }

        return result.toByteArray()
    }

    /**
     * Creates a checksum for the given HRP and data.
     */
    private fun createChecksum(hrp: String, data: ByteArray): ByteArray {
        val values = hrpExpand(hrp) + data.map { it.toInt() }.toIntArray() + intArrayOf(0, 0, 0, 0, 0, 0)
        val polymod = polymod(values) xor 1
        return (0 until 6).map { ((polymod shr (5 * (5 - it))) and 31).toByte() }.toByteArray()
    }

    /**
     * Verifies the checksum of the given HRP and data.
     */
    private fun verifyChecksum(hrp: String, data: ByteArray): Boolean {
        val values = hrpExpand(hrp) + data.map { it.toInt() }.toIntArray()
        return polymod(values) == 1
    }

    /**
     * Expands the HRP for checksum calculation.
     */
    private fun hrpExpand(hrp: String): IntArray {
        val result = IntArray(hrp.length * 2 + 1)
        for (i in hrp.indices) {
            result[i] = hrp[i].code shr 5
            result[i + hrp.length + 1] = hrp[i].code and 31
        }
        result[hrp.length] = 0
        return result
    }

    /**
     * Computes the Bech32 polymod checksum.
     */
    private fun polymod(values: IntArray): Int {
        val GEN = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (value in values) {
            val b = chk shr 25
            chk = ((chk and 0x1ffffff) shl 5) xor value
            for (i in 0 until 5) {
                if (((b shr i) and 1) != 0) {
                    chk = chk xor GEN[i]
                }
            }
        }
        return chk
    }
}
