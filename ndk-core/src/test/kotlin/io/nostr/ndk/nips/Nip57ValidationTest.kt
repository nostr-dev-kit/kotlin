package io.nostr.ndk.nips

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for NIP-57 zap receipt validation.
 */
class Nip57ValidationTest {

    private fun createZapRequest(
        pubkey: String = "sender_pubkey",
        recipient: String = "recipient_pubkey",
        amount: Long = 10000,
        lnurl: String = "lnurl1test",
        eventId: String? = null
    ): NDKEvent {
        val tags = mutableListOf(
            NDKTag("p", listOf(recipient)),
            NDKTag("amount", listOf(amount.toString())),
            NDKTag("lnurl", listOf(lnurl)),
            NDKTag("relays", listOf("wss://relay.example.com"))
        )

        if (eventId != null) {
            tags.add(NDKTag("e", listOf(eventId)))
        }

        return NDKEvent(
            id = "zap_request_id",
            pubkey = pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_ZAP_REQUEST,
            tags = tags,
            content = "Test zap",
            sig = "zap_request_sig"
        )
    }

    private fun createZapReceipt(
        pubkey: String = "lnurl_provider_pubkey",
        recipient: String = "recipient_pubkey",
        sender: String = "sender_pubkey",
        bolt11: String = "lnbc100n1test_invoice",
        zapRequest: NDKEvent,
        eventId: String? = null
    ): NDKEvent {
        val tags = mutableListOf(
            NDKTag("p", listOf(recipient)),
            NDKTag("P", listOf(sender)),
            NDKTag("bolt11", listOf(bolt11)),
            NDKTag("description", listOf(zapRequest.toJson()))
        )

        if (eventId != null) {
            tags.add(NDKTag("e", listOf(eventId)))
        }

        return NDKEvent(
            id = "zap_receipt_id",
            pubkey = pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_ZAP_RECEIPT,
            tags = tags,
            content = "",
            sig = "zap_receipt_sig"
        )
    }

    @Test
    fun `valid zap receipt passes validation`() {
        val zapRequest = createZapRequest(amount = 10000)
        val zapReceipt = createZapReceipt(
            pubkey = "lnurl_provider_pubkey",
            bolt11 = "lnbc10000n1test", // 10000n = 10000 millisats
            zapRequest = zapRequest
        )

        val result = zapReceipt.validateZapReceipt()

        assertTrue("Valid receipt should pass", result is ZapValidationResult.Valid)
    }

    @Test
    fun `valid zap receipt with expected pubkey passes validation`() {
        val zapRequest = createZapRequest(amount = 10000)
        val zapReceipt = createZapReceipt(
            pubkey = "lnurl_provider_pubkey",
            bolt11 = "lnbc10000n1test", // 10000n = 10000 millisats
            zapRequest = zapRequest
        )

        val result = zapReceipt.validateZapReceipt(expectedNostrPubkey = "lnurl_provider_pubkey")

        assertTrue("Valid receipt with matching pubkey should pass", result is ZapValidationResult.Valid)
    }

    @Test
    fun `invalid kind fails validation`() {
        val zapRequest = createZapRequest()
        val invalidReceipt = NDKEvent(
            id = "test_id",
            pubkey = "lnurl_provider_pubkey",
            createdAt = System.currentTimeMillis() / 1000,
            kind = 1, // Wrong kind (should be 9735)
            tags = listOf(
                NDKTag("p", listOf("recipient_pubkey")),
                NDKTag("bolt11", listOf("lnbc100n1test")),
                NDKTag("description", listOf(zapRequest.toJson()))
            ),
            content = "",
            sig = "test_sig"
        )

        val result = invalidReceipt.validateZapReceipt()

        assertTrue("Invalid kind should fail", result is ZapValidationResult.Invalid)
        val error = (result as ZapValidationResult.Invalid).reason
        assertTrue("Error should mention kind", error.contains("kind", ignoreCase = true))
    }

    @Test
    fun `missing description tag fails validation`() {
        val zapReceipt = NDKEvent(
            id = "test_id",
            pubkey = "lnurl_provider_pubkey",
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_ZAP_RECEIPT,
            tags = listOf(
                NDKTag("p", listOf("recipient_pubkey")),
                NDKTag("bolt11", listOf("lnbc100n1test"))
                // Missing description tag
            ),
            content = "",
            sig = "test_sig"
        )

        val result = zapReceipt.validateZapReceipt()

        assertTrue("Missing description should fail", result is ZapValidationResult.Invalid)
        val error = (result as ZapValidationResult.Invalid).reason
        assertTrue("Error should mention description", error.contains("description", ignoreCase = true))
    }

    @Test
    fun `invalid JSON in description tag fails validation`() {
        val zapReceipt = NDKEvent(
            id = "test_id",
            pubkey = "lnurl_provider_pubkey",
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_ZAP_RECEIPT,
            tags = listOf(
                NDKTag("p", listOf("recipient_pubkey")),
                NDKTag("bolt11", listOf("lnbc100n1test")),
                NDKTag("description", listOf("not valid json"))
            ),
            content = "",
            sig = "test_sig"
        )

        val result = zapReceipt.validateZapReceipt()

        assertTrue("Invalid JSON should fail", result is ZapValidationResult.Invalid)
        val error = (result as ZapValidationResult.Invalid).reason
        assertTrue("Error should mention JSON or parsing",
            error.contains("json", ignoreCase = true) || error.contains("parse", ignoreCase = true))
    }

    @Test
    fun `embedded request with wrong kind fails validation`() {
        val invalidRequest = NDKEvent(
            id = "test_id",
            pubkey = "sender_pubkey",
            createdAt = System.currentTimeMillis() / 1000,
            kind = 1, // Wrong kind (should be 9734)
            tags = listOf(
                NDKTag("p", listOf("recipient_pubkey")),
                NDKTag("amount", listOf("10000"))
            ),
            content = "Not a zap request",
            sig = "test_sig"
        )

        val zapReceipt = NDKEvent(
            id = "receipt_id",
            pubkey = "lnurl_provider_pubkey",
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_ZAP_RECEIPT,
            tags = listOf(
                NDKTag("p", listOf("recipient_pubkey")),
                NDKTag("bolt11", listOf("lnbc100n1test")),
                NDKTag("description", listOf(invalidRequest.toJson()))
            ),
            content = "",
            sig = "receipt_sig"
        )

        val result = zapReceipt.validateZapReceipt()

        assertTrue("Invalid request kind should fail", result is ZapValidationResult.Invalid)
        val error = (result as ZapValidationResult.Invalid).reason
        assertTrue("Error should mention zap request kind",
            error.contains("9734", ignoreCase = true) || error.contains("zap request", ignoreCase = true))
    }

    @Test
    fun `amount mismatch fails validation`() {
        // Request asks for 10000 millisats
        val zapRequest = createZapRequest(amount = 10000)

        // Receipt has bolt11 with different amount (50000 millisats)
        val zapReceipt = createZapReceipt(
            bolt11 = "lnbc500n1test", // 500 nanosats = 500 millisats
            zapRequest = zapRequest
        )

        val result = zapReceipt.validateZapReceipt()

        assertTrue("Amount mismatch should fail", result is ZapValidationResult.Invalid)
        val error = (result as ZapValidationResult.Invalid).reason
        assertTrue("Error should mention amount", error.contains("amount", ignoreCase = true))
    }

    @Test
    fun `missing amount in zap request fails validation`() {
        val requestWithoutAmount = NDKEvent(
            id = "test_id",
            pubkey = "sender_pubkey",
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_ZAP_REQUEST,
            tags = listOf(
                NDKTag("p", listOf("recipient_pubkey")),
                NDKTag("lnurl", listOf("lnurl1test"))
                // Missing amount tag
            ),
            content = "Test zap",
            sig = "test_sig"
        )

        val zapReceipt = createZapReceipt(
            bolt11 = "lnbc100n1test",
            zapRequest = requestWithoutAmount
        )

        val result = zapReceipt.validateZapReceipt()

        assertTrue("Missing amount in request should fail", result is ZapValidationResult.Invalid)
        val error = (result as ZapValidationResult.Invalid).reason
        assertTrue("Error should mention amount", error.contains("amount", ignoreCase = true))
    }

    @Test
    fun `missing bolt11 fails validation`() {
        val zapRequest = createZapRequest(amount = 10000)
        val zapReceipt = NDKEvent(
            id = "receipt_id",
            pubkey = "lnurl_provider_pubkey",
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_ZAP_RECEIPT,
            tags = listOf(
                NDKTag("p", listOf("recipient_pubkey")),
                NDKTag("description", listOf(zapRequest.toJson()))
                // Missing bolt11 tag
            ),
            content = "",
            sig = "receipt_sig"
        )

        val result = zapReceipt.validateZapReceipt()

        assertTrue("Missing bolt11 should fail", result is ZapValidationResult.Invalid)
        val error = (result as ZapValidationResult.Invalid).reason
        assertTrue("Error should mention bolt11", error.contains("bolt11", ignoreCase = true))
    }

    @Test
    fun `invalid bolt11 format fails validation`() {
        val zapRequest = createZapRequest(amount = 10000)
        val zapReceipt = createZapReceipt(
            bolt11 = "not_a_valid_bolt11",
            zapRequest = zapRequest
        )

        val result = zapReceipt.validateZapReceipt()

        assertTrue("Invalid bolt11 should fail", result is ZapValidationResult.Invalid)
        val error = (result as ZapValidationResult.Invalid).reason
        assertTrue("Error should mention bolt11", error.contains("bolt11", ignoreCase = true))
    }

    @Test
    fun `pubkey mismatch fails validation when expectedNostrPubkey is provided`() {
        val zapRequest = createZapRequest(amount = 10000)
        val zapReceipt = createZapReceipt(
            pubkey = "actual_provider_pubkey",
            bolt11 = "lnbc100n1test",
            zapRequest = zapRequest
        )

        val result = zapReceipt.validateZapReceipt(expectedNostrPubkey = "different_pubkey")

        assertTrue("Pubkey mismatch should fail", result is ZapValidationResult.Invalid)
        val error = (result as ZapValidationResult.Invalid).reason
        assertTrue("Error should mention pubkey", error.contains("pubkey", ignoreCase = true))
    }

    @Test
    fun `validation succeeds when expectedNostrPubkey is null`() {
        val zapRequest = createZapRequest(amount = 10000)
        val zapReceipt = createZapReceipt(
            pubkey = "any_pubkey",
            bolt11 = "lnbc10000n1test", // 10000n = 10000 millisats
            zapRequest = zapRequest
        )

        val result = zapReceipt.validateZapReceipt(expectedNostrPubkey = null)

        assertTrue("Should pass when expectedNostrPubkey is null", result is ZapValidationResult.Valid)
    }

    @Test
    fun `validation with matching amounts in different formats succeeds`() {
        // 10000 millisats = 10 microsats
        val zapRequest = createZapRequest(amount = 10000)
        val zapReceipt = createZapReceipt(
            bolt11 = "lnbc10u1test", // 10 microsats = 10000 millisats
            zapRequest = zapRequest
        )

        val result = zapReceipt.validateZapReceipt()

        assertTrue("Matching amounts in different formats should pass", result is ZapValidationResult.Valid)
    }

    @Test
    fun `validation with large amounts succeeds`() {
        // 1000000 millisats = 1000 microsats = 1 millisat
        val largeAmount = 1000000L
        val zapRequest = createZapRequest(amount = largeAmount)
        val zapReceipt = createZapReceipt(
            bolt11 = "lnbc1m1test", // 1 milli = 1000000 millisats
            zapRequest = zapRequest
        )

        val result = zapReceipt.validateZapReceipt()

        assertTrue("Large amounts should be validated correctly", result is ZapValidationResult.Valid)
    }
}
