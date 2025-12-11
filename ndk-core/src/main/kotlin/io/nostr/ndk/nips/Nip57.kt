package io.nostr.ndk.nips

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.utils.Bech32

/**
 * NIP-57: Lightning Zaps
 *
 * Zaps are Lightning payments with attached Nostr events for attribution.
 *
 * Kind 9734: Zap Request (sent by client)
 * Kind 9735: Zap Receipt (sent by LNURL provider)
 *
 * Zap Request tags:
 * - relays: list of relays to publish receipt to
 * - amount: requested amount in millisatoshis
 * - lnurl: LNURL of the recipient
 * - p: recipient pubkey
 * - e: (optional) event being zapped
 *
 * Zap Receipt tags:
 * - p: recipient pubkey
 * - P: sender pubkey (from zap request)
 * - e: (optional) event being zapped
 * - bolt11: the Lightning invoice
 * - description: JSON-encoded zap request
 * - preimage: (optional) payment preimage
 */

/**
 * Kind constant for zap requests.
 */
const val KIND_ZAP_REQUEST = 9734

/**
 * Kind constant for zap receipts.
 */
const val KIND_ZAP_RECEIPT = 9735

private val objectMapper = jacksonObjectMapper()

/**
 * Returns true if this event is a zap request (kind 9734).
 */
val NDKEvent.isZapRequest: Boolean
    get() = kind == KIND_ZAP_REQUEST

/**
 * Returns true if this event is a zap receipt (kind 9735).
 */
val NDKEvent.isZapReceipt: Boolean
    get() = kind == KIND_ZAP_RECEIPT

/**
 * Gets the zap request from a zap receipt's description tag.
 * Returns null if parsing fails or not a zap receipt.
 */
val NDKEvent.zapRequest: NDKEvent?
    get() {
        if (!isZapReceipt) return null
        val description = tagValue("description") ?: return null
        return try {
            NDKEvent.fromJson(description)
        } catch (e: Exception) {
            null
        }
    }

/**
 * Gets the bolt11 invoice from a zap receipt.
 */
val NDKEvent.zapBolt11: String?
    get() = tagValue("bolt11")

/**
 * Gets the payment preimage from a zap receipt.
 */
val NDKEvent.zapPreimage: String?
    get() = tagValue("preimage")

/**
 * Gets the zap recipient pubkey (p tag).
 */
val NDKEvent.zapRecipient: PublicKey?
    get() = tagValue("p")

/**
 * Gets the zap sender pubkey.
 * For zap receipts, this is from the embedded zap request.
 * For zap requests, this is the event author.
 */
val NDKEvent.zapSender: PublicKey?
    get() = when {
        isZapReceipt -> zapRequest?.pubkey
        isZapRequest -> pubkey
        else -> null
    }

/**
 * Gets the event being zapped (e tag).
 */
val NDKEvent.zappedEventId: EventId?
    get() = tagValue("e")

/**
 * Gets the requested amount in millisatoshis from a zap request.
 */
val NDKEvent.zapAmountMillisats: Long?
    get() {
        if (!isZapRequest) return null
        return tagValue("amount")?.toLongOrNull()
    }

/**
 * Gets the LNURL from a zap request.
 */
val NDKEvent.zapLnurl: String?
    get() {
        if (!isZapRequest) return null
        return tagValue("lnurl")
    }

/**
 * Gets the relays from a zap request (relays tag).
 */
val NDKEvent.zapRelays: List<String>
    get() {
        if (!isZapRequest) return emptyList()
        return tagsWithName("relays").firstOrNull()?.values ?: emptyList()
    }

/**
 * Parses the amount in millisatoshis from a BOLT11 invoice.
 *
 * BOLT11 amounts are encoded in the human-readable part:
 * - lnbc1... = no amount (just the separator)
 * - lnbc100n1... = 100 millisatoshis (n = nano)
 * - lnbc1u1... = 1000 millisatoshis (u = micro, 1 microsatoshi = 1000 millisatoshis)
 * - lnbc1m1... = 1,000,000 millisatoshis (m = milli)
 * - lnbc11... = 1 BTC in millisatoshis
 *
 * The separator '1' follows the amount portion. The amount can have an optional
 * multiplier suffix (p, n, u, m).
 *
 * Returns null if parsing fails.
 */
fun parseBolt11Amount(bolt11: String): Long? {
    val lowerBolt11 = bolt11.lowercase()
    if (!lowerBolt11.startsWith("lnbc") && !lowerBolt11.startsWith("lntb") && !lowerBolt11.startsWith("lnbcrt")) {
        return null
    }

    // Extract the amount portion (after lnbc/lntb/lnbcrt, before '1' separator)
    val prefix = when {
        lowerBolt11.startsWith("lnbcrt") -> "lnbcrt"
        lowerBolt11.startsWith("lnbc") -> "lnbc"
        lowerBolt11.startsWith("lntb") -> "lntb"
        else -> return null
    }

    val afterPrefix = lowerBolt11.removePrefix(prefix)

    // The separator is '1' but we need to find the one that follows the amount
    // Amount format: [0-9]+[pnum]? followed by '1' separator
    // We scan for digits optionally followed by a multiplier (p,n,u,m)
    var amountEndIndex = 0
    while (amountEndIndex < afterPrefix.length && afterPrefix[amountEndIndex].isDigit()) {
        amountEndIndex++
    }

    // Check for optional multiplier suffix
    if (amountEndIndex < afterPrefix.length) {
        val possibleMultiplier = afterPrefix[amountEndIndex]
        if (possibleMultiplier in listOf('p', 'n', 'u', 'm')) {
            amountEndIndex++
        }
    }

    // Now afterPrefix[amountEndIndex] should be '1' (the separator)
    if (amountEndIndex >= afterPrefix.length || afterPrefix[amountEndIndex] != '1') {
        return null
    }

    val amountPart = afterPrefix.substring(0, amountEndIndex)
    if (amountPart.isEmpty()) return null // No amount specified (just "lnbc1...")

    // Parse the numeric part and multiplier
    val lastChar = amountPart.last()
    val multiplier: Any = when (lastChar) {
        'p' -> 0.1 // pico = 0.001 millisats (not common)
        'n' -> 1L // nano = 1 millisat
        'u' -> 1_000L // micro = 1000 millisats
        'm' -> 1_000_000L // milli = 1,000,000 millisats
        else -> {
            // No suffix means BTC
            if (lastChar.isDigit()) {
                return amountPart.toLongOrNull()?.times(100_000_000_000L) // BTC to millisats
            }
            return null
        }
    }

    val numericPart = amountPart.dropLast(1)
    val amount = numericPart.toLongOrNull() ?: return null

    return if (multiplier is Double) {
        (amount * multiplier).toLong()
    } else {
        amount * (multiplier as Long)
    }
}

/**
 * Gets the amount in millisatoshis from a zap receipt.
 * Parses from the bolt11 invoice.
 */
val NDKEvent.zapReceiptAmountMillisats: Long?
    get() {
        if (!isZapReceipt) return null
        val bolt11 = zapBolt11 ?: return null
        return parseBolt11Amount(bolt11)
    }

/**
 * Gets the amount in satoshis from a zap receipt (rounded down).
 */
val NDKEvent.zapReceiptAmountSats: Long?
    get() = zapReceiptAmountMillisats?.div(1000)

/**
 * Converts a Lightning Address (LUD-16) to a LNURL-pay endpoint URL.
 *
 * Example: "user@domain.com" -> "https://domain.com/.well-known/lnurlp/user"
 *
 * @param lud16 Lightning address in the format "user@domain.com"
 * @return LNURL-pay endpoint URL, or null if the format is invalid
 */
fun lud16ToUrl(lud16: String): String? {
    if (lud16.isEmpty()) return null

    val parts = lud16.lowercase().split("@")
    if (parts.size != 2) return null

    val user = parts[0]
    val domain = parts[1]

    if (user.isEmpty() || domain.isEmpty()) return null

    return "https://$domain/.well-known/lnurlp/$user"
}

/**
 * Decodes a bech32-encoded LNURL to its underlying URL.
 *
 * LNURL uses bech32 encoding with hrp "lnurl".
 *
 * @param lnurl Bech32-encoded LNURL string
 * @return Decoded URL, or null if decoding fails
 */
fun decodeLnurl(lnurl: String): String? {
    if (lnurl.isEmpty()) return null

    return try {
        val decoded = Bech32.decode(lnurl.lowercase())
        if (decoded.hrp != "lnurl") return null

        String(decoded.data, Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}

/**
 * Resolves a LNURL endpoint from LUD-16 (Lightning Address) or LUD-06 (bech32 LNURL).
 *
 * Prefers lud16 over lud06 if both are provided.
 *
 * @param lud16 Lightning address (e.g., "user@domain.com")
 * @param lud06 Bech32-encoded LNURL
 * @return Resolved LNURL endpoint URL, or null if both are null or invalid
 */
fun resolveLnurlEndpoint(lud16: String?, lud06: String?): String? {
    lud16?.let { lud16ToUrl(it) }?.let { return it }
    lud06?.let { decodeLnurl(it) }?.let { return it }
    return null
}

/**
 * Gets the LNURL endpoint from an event's zap tag.
 *
 * The zap tag can contain either a Lightning Address (LUD-16) or bech32 LNURL (LUD-06).
 * This extension automatically handles both formats.
 *
 * @return Resolved LNURL endpoint URL, or null if no zap tag or invalid format
 */
val NDKEvent.zapEndpoint: String?
    get() {
        val zapValue = tagValue("zap") ?: return null
        return resolveLnurlEndpoint(lud16 = zapValue, lud06 = zapValue)
    }
