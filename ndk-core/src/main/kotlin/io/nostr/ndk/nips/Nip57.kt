package io.nostr.ndk.nips

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.utils.Bech32
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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

/**
 * LNURL-pay response data structure.
 *
 * Represents the response from a LNURL-pay endpoint as defined in LUD-06.
 */
data class LnurlPayResponse(
    val callback: String,
    val minSendable: Long,
    val maxSendable: Long,
    val metadata: String,
    val tag: String,
    val allowsNostr: Boolean?,
    val nostrPubkey: String?,
    val commentAllowed: Int?
)

/**
 * Result of a LNURL-pay metadata fetch operation.
 */
sealed class LnurlPayResult {
    data class Success(val response: LnurlPayResponse) : LnurlPayResult()
    data class Error(val message: String) : LnurlPayResult()
}

private val defaultHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

/**
 * Fetches LNURL-pay metadata from a LNURL endpoint.
 *
 * @param url The LNURL-pay endpoint URL
 * @param httpClient Optional HTTP client (defaults to a basic client with 10s timeouts)
 * @return LnurlPayResult containing the response or error
 */
suspend fun fetchLnurlPayMetadata(url: String, httpClient: OkHttpClient = defaultHttpClient): LnurlPayResult = withContext(Dispatchers.IO) {
    try {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext LnurlPayResult.Error("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body?.string()
                ?: return@withContext LnurlPayResult.Error("Empty response body")

            if (body.isBlank()) {
                return@withContext LnurlPayResult.Error("Empty response body")
            }

            try {
                val json: Map<String, Any?> = objectMapper.readValue(body)

                val callback = json["callback"] as? String
                    ?: return@withContext LnurlPayResult.Error("Missing required field: callback")
                val minSendable = (json["minSendable"] as? Number)?.toLong()
                    ?: return@withContext LnurlPayResult.Error("Missing required field: minSendable")
                val maxSendable = (json["maxSendable"] as? Number)?.toLong()
                    ?: return@withContext LnurlPayResult.Error("Missing required field: maxSendable")
                val metadata = json["metadata"] as? String
                    ?: return@withContext LnurlPayResult.Error("Missing required field: metadata")
                val tag = json["tag"] as? String
                    ?: return@withContext LnurlPayResult.Error("Missing required field: tag")

                val allowsNostr = json["allowsNostr"] as? Boolean
                val nostrPubkey = json["nostrPubkey"] as? String
                val commentAllowed = (json["commentAllowed"] as? Number)?.toInt()

                LnurlPayResult.Success(
                    LnurlPayResponse(
                        callback = callback,
                        minSendable = minSendable,
                        maxSendable = maxSendable,
                        metadata = metadata,
                        tag = tag,
                        allowsNostr = allowsNostr,
                        nostrPubkey = nostrPubkey,
                        commentAllowed = commentAllowed
                    )
                )
            } catch (e: Exception) {
                LnurlPayResult.Error("Failed to parse JSON: ${e.message}")
            }
        }
    } catch (e: Exception) {
        LnurlPayResult.Error(e.message ?: "Unknown error")
    }
}

/**
 * LNURL invoice response data structure.
 *
 * Represents the response from a LNURL callback when requesting an invoice.
 */
data class LnurlInvoiceResponse(
    val pr: String,
    val routes: List<Any>?
)

/**
 * Result of a LNURL invoice fetch operation.
 */
sealed class LnurlInvoiceResult {
    data class Success(val invoice: LnurlInvoiceResponse) : LnurlInvoiceResult()
    data class Error(val message: String) : LnurlInvoiceResult()
}

/**
 * Result of zap receipt validation.
 */
sealed class ZapValidationResult {
    object Valid : ZapValidationResult()
    data class Invalid(val reason: String) : ZapValidationResult()
}

/**
 * Fetches a Lightning invoice from a LNURL callback endpoint.
 *
 * Per NIP-57, the callback URL must include:
 * - `amount`: Amount in millisatoshis
 * - `nostr`: JSON-encoded zap request (kind 9734), then URI-encoded
 * - `lnurl`: The recipient's encoded LNURL
 *
 * @param callback The LNURL callback URL
 * @param amountMillisats Amount to pay in millisatoshis
 * @param zapRequest The zap request event (kind 9734)
 * @param lnurl The recipient's encoded LNURL
 * @param httpClient Optional HTTP client (defaults to a basic client with 10s timeouts)
 * @return LnurlInvoiceResult containing the invoice or error
 */
suspend fun fetchLnurlInvoice(
    callback: String,
    amountMillisats: Long,
    zapRequest: NDKEvent,
    lnurl: String,
    httpClient: OkHttpClient = defaultHttpClient
): LnurlInvoiceResult = withContext(Dispatchers.IO) {
    try {
        // Serialize the zap request to JSON
        val zapRequestJson = objectMapper.writeValueAsString(zapRequest)

        // URI-encode the JSON string
        val encodedZapRequest = java.net.URLEncoder.encode(zapRequestJson, "UTF-8")

        // Build the callback URL with query parameters
        val finalUrl = try {
            callback.toHttpUrl().newBuilder()
                .addQueryParameter("amount", amountMillisats.toString())
                .addQueryParameter("nostr", encodedZapRequest)
                .addQueryParameter("lnurl", lnurl)
                .build()
        } catch (e: Exception) {
            return@withContext LnurlInvoiceResult.Error("Invalid callback URL: ${e.message}")
        }

        val request = Request.Builder()
            .url(finalUrl)
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext LnurlInvoiceResult.Error("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body?.string()
                ?: return@withContext LnurlInvoiceResult.Error("Empty response body")

            if (body.isBlank()) {
                return@withContext LnurlInvoiceResult.Error("Empty response body")
            }

            try {
                val json: Map<String, Any?> = objectMapper.readValue(body)

                val pr = json["pr"] as? String
                    ?: return@withContext LnurlInvoiceResult.Error("Missing required field: pr")

                val routes = json["routes"] as? List<Any>

                LnurlInvoiceResult.Success(
                    LnurlInvoiceResponse(
                        pr = pr,
                        routes = routes
                    )
                )
            } catch (e: Exception) {
                LnurlInvoiceResult.Error("Failed to parse JSON: ${e.message}")
            }
        }
    } catch (e: Exception) {
        LnurlInvoiceResult.Error(e.message ?: "Unknown error")
    }
}

/**
 * Validates a zap receipt according to NIP-57 requirements.
 *
 * NIP-57 validation requirements:
 * 1. Receipt is kind 9735
 * 2. Has `description` tag containing JSON-encoded zap request
 * 3. Embedded request is valid kind 9734
 * 4. Receipt `pubkey` matches recipient's LNURL provider's `nostrPubkey` (if expectedNostrPubkey is provided)
 * 5. BOLT11 invoice amount equals zap request's `amount` tag
 *
 * @param expectedNostrPubkey Optional expected pubkey of the LNURL provider
 * @return ZapValidationResult.Valid if valid, or ZapValidationResult.Invalid with reason if invalid
 */
fun NDKEvent.validateZapReceipt(expectedNostrPubkey: String? = null): ZapValidationResult {
    // 1. Verify receipt is kind 9735
    if (kind != KIND_ZAP_RECEIPT) {
        return ZapValidationResult.Invalid("Event is not a zap receipt (kind $kind, expected $KIND_ZAP_RECEIPT)")
    }

    // 2. Get and verify description tag
    val descriptionJson = tagValue("description")
        ?: return ZapValidationResult.Invalid("Missing description tag")

    // 3. Parse embedded zap request
    val zapRequest = try {
        NDKEvent.fromJson(descriptionJson)
    } catch (e: Exception) {
        return ZapValidationResult.Invalid("Failed to parse zap request from description: ${e.message}")
    }

    // 4. Verify embedded request is kind 9734
    if (zapRequest.kind != KIND_ZAP_REQUEST) {
        return ZapValidationResult.Invalid("Embedded request is not a zap request (kind ${zapRequest.kind}, expected $KIND_ZAP_REQUEST)")
    }

    // 5. Verify pubkey matches if expectedNostrPubkey is provided
    if (expectedNostrPubkey != null && pubkey != expectedNostrPubkey) {
        return ZapValidationResult.Invalid("Receipt pubkey ($pubkey) does not match expected LNURL provider pubkey ($expectedNostrPubkey)")
    }

    // 6. Get and verify bolt11 invoice
    val bolt11 = tagValue("bolt11")
        ?: return ZapValidationResult.Invalid("Missing bolt11 tag")

    // 7. Parse bolt11 amount
    val bolt11Amount = parseBolt11Amount(bolt11)
        ?: return ZapValidationResult.Invalid("Failed to parse amount from bolt11 invoice")

    // 8. Get zap request amount
    val requestAmount = zapRequest.tagValue("amount")?.toLongOrNull()
        ?: return ZapValidationResult.Invalid("Missing or invalid amount in zap request")

    // 9. Verify amounts match
    if (bolt11Amount != requestAmount) {
        return ZapValidationResult.Invalid("Amount mismatch: bolt11 has $bolt11Amount millisats, request has $requestAmount millisats")
    }

    return ZapValidationResult.Valid
}
