package io.nostr.ndk.nips

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for NIP-57 LNURL-pay metadata fetching.
 */
class Nip57LnurlTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var httpClient: OkHttpClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        httpClient = OkHttpClient.Builder().build()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetchLnurlPayMetadata parses valid response with all fields`() = runTest {
        val json = """
            {
                "callback": "https://example.com/lnurlp/callback",
                "minSendable": 1000,
                "maxSendable": 100000000,
                "metadata": "[[\"text/plain\",\"Payment to user@example.com\"]]",
                "tag": "payRequest",
                "allowsNostr": true,
                "nostrPubkey": "fa984bd7dbb282f07e16e7ae87b26a2a7b9b90b7246a44771f0cf5ae58018f52",
                "commentAllowed": 255
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val url = mockWebServer.url("/lnurlp/user").toString()
        val result = fetchLnurlPayMetadata(url, httpClient)

        assertTrue("Should be success", result is LnurlPayResult.Success)
        val response = (result as LnurlPayResult.Success).response
        assertEquals("https://example.com/lnurlp/callback", response.callback)
        assertEquals(1000L, response.minSendable)
        assertEquals(100000000L, response.maxSendable)
        assertEquals("[[\"text/plain\",\"Payment to user@example.com\"]]", response.metadata)
        assertEquals("payRequest", response.tag)
        assertEquals(true, response.allowsNostr)
        assertEquals("fa984bd7dbb282f07e16e7ae87b26a2a7b9b90b7246a44771f0cf5ae58018f52", response.nostrPubkey)
        assertEquals(255, response.commentAllowed)
    }

    @Test
    fun `fetchLnurlPayMetadata parses response with missing optional fields`() = runTest {
        val json = """
            {
                "callback": "https://example.com/lnurlp/callback",
                "minSendable": 1000,
                "maxSendable": 100000000,
                "metadata": "[[\"text/plain\",\"Payment\"]]",
                "tag": "payRequest"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val url = mockWebServer.url("/lnurlp/user").toString()
        val result = fetchLnurlPayMetadata(url, httpClient)

        assertTrue("Should be success", result is LnurlPayResult.Success)
        val response = (result as LnurlPayResult.Success).response
        assertEquals("https://example.com/lnurlp/callback", response.callback)
        assertEquals(1000L, response.minSendable)
        assertEquals(100000000L, response.maxSendable)
        assertEquals("[[\"text/plain\",\"Payment\"]]", response.metadata)
        assertEquals("payRequest", response.tag)
        assertEquals(null, response.allowsNostr)
        assertEquals(null, response.nostrPubkey)
        assertEquals(null, response.commentAllowed)
    }

    @Test
    fun `fetchLnurlPayMetadata handles HTTP errors`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val url = mockWebServer.url("/lnurlp/user").toString()
        val result = fetchLnurlPayMetadata(url, httpClient)

        assertTrue("Should be error", result is LnurlPayResult.Error)
        val error = (result as LnurlPayResult.Error).message
        assertTrue("Error should mention HTTP code", error.contains("404"))
    }

    @Test
    fun `fetchLnurlPayMetadata handles invalid JSON`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("not json").setResponseCode(200))

        val url = mockWebServer.url("/lnurlp/user").toString()
        val result = fetchLnurlPayMetadata(url, httpClient)

        assertTrue("Should be error", result is LnurlPayResult.Error)
    }

    @Test
    fun `fetchLnurlPayMetadata handles empty response body`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("").setResponseCode(200))

        val url = mockWebServer.url("/lnurlp/user").toString()
        val result = fetchLnurlPayMetadata(url, httpClient)

        assertTrue("Should be error", result is LnurlPayResult.Error)
    }

    @Test
    fun `fetchLnurlPayMetadata handles network errors`() = runTest {
        // Use an invalid URL to trigger network error
        val result = fetchLnurlPayMetadata("http://invalid.localhost.test:99999", httpClient)

        assertTrue("Should be error", result is LnurlPayResult.Error)
        assertNotNull("Error message should not be null", (result as LnurlPayResult.Error).message)
    }

    @Test
    fun `fetchLnurlPayMetadata handles missing required fields`() = runTest {
        val json = """
            {
                "callback": "https://example.com/lnurlp/callback",
                "metadata": "[[\"text/plain\",\"Payment\"]]"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val url = mockWebServer.url("/lnurlp/user").toString()
        val result = fetchLnurlPayMetadata(url, httpClient)

        assertTrue("Should be error for missing required fields", result is LnurlPayResult.Error)
    }

    // Tests for fetchLnurlInvoice

    private fun createTestZapRequest(
        pubkey: String = "sender_pubkey",
        recipient: String = "recipient_pubkey",
        amount: Long = 10000,
        lnurl: String = "lnurl1test"
    ): io.nostr.ndk.models.NDKEvent {
        return io.nostr.ndk.models.NDKEvent(
            id = "test_id",
            pubkey = pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_ZAP_REQUEST,
            tags = listOf(
                io.nostr.ndk.models.NDKTag("p", listOf(recipient)),
                io.nostr.ndk.models.NDKTag("amount", listOf(amount.toString())),
                io.nostr.ndk.models.NDKTag("lnurl", listOf(lnurl)),
                io.nostr.ndk.models.NDKTag("relays", listOf("wss://relay.example.com"))
            ),
            content = "Test zap",
            sig = "test_sig"
        )
    }

    @Test
    fun `fetchLnurlInvoice successfully fetches invoice with proper URL encoding`() = runTest {
        val zapRequest = createTestZapRequest(amount = 21000, lnurl = "lnurl1test")
        val invoiceJson = """
            {
                "pr": "lnbc210n1test_invoice_here",
                "routes": []
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(invoiceJson).setResponseCode(200))

        val callbackUrl = mockWebServer.url("/callback").toString()
        val result = fetchLnurlInvoice(
            callback = callbackUrl,
            amountMillisats = 21000,
            zapRequest = zapRequest,
            lnurl = "lnurl1test",
            httpClient = httpClient
        )

        assertTrue("Should be success", result is LnurlInvoiceResult.Success)
        val invoice = (result as LnurlInvoiceResult.Success).invoice
        assertEquals("lnbc210n1test_invoice_here", invoice.pr)
        assertEquals(emptyList<Any>(), invoice.routes)

        // Verify the request was made with proper URL parameters
        val request = mockWebServer.takeRequest()
        val requestUrl = request.requestUrl!!
        assertEquals("21000", requestUrl.queryParameter("amount"))
        assertEquals("lnurl1test", requestUrl.queryParameter("lnurl"))

        // Verify nostr parameter is present and URI-encoded
        val nostrParam = requestUrl.queryParameter("nostr")
        assertNotNull("nostr parameter should be present", nostrParam)
        assertTrue("nostr parameter should be non-empty", nostrParam!!.isNotEmpty())
        // The nostr param should be JSON that's been URI encoded, so it shouldn't contain raw { or }
        // After decoding it should be valid JSON
        val decodedJson = java.net.URLDecoder.decode(nostrParam, "UTF-8")
        assertTrue("decoded nostr should contain JSON", decodedJson.contains("{"))
        assertTrue("decoded nostr should contain event id", decodedJson.contains("test_id"))
    }

    @Test
    fun `fetchLnurlInvoice handles HTTP error responses`() = runTest {
        val zapRequest = createTestZapRequest()
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val callbackUrl = mockWebServer.url("/callback").toString()
        val result = fetchLnurlInvoice(
            callback = callbackUrl,
            amountMillisats = 10000,
            zapRequest = zapRequest,
            lnurl = "lnurl1test",
            httpClient = httpClient
        )

        assertTrue("Should be error", result is LnurlInvoiceResult.Error)
        val error = (result as LnurlInvoiceResult.Error).message
        assertTrue("Error should mention HTTP code", error.contains("500"))
    }

    @Test
    fun `fetchLnurlInvoice handles invalid JSON response`() = runTest {
        val zapRequest = createTestZapRequest()
        mockWebServer.enqueue(MockResponse().setBody("not json at all").setResponseCode(200))

        val callbackUrl = mockWebServer.url("/callback").toString()
        val result = fetchLnurlInvoice(
            callback = callbackUrl,
            amountMillisats = 10000,
            zapRequest = zapRequest,
            lnurl = "lnurl1test",
            httpClient = httpClient
        )

        assertTrue("Should be error", result is LnurlInvoiceResult.Error)
        val error = (result as LnurlInvoiceResult.Error).message
        assertTrue("Error should mention JSON parsing", error.contains("JSON") || error.contains("parse"))
    }

    @Test
    fun `fetchLnurlInvoice handles missing pr field in response`() = runTest {
        val zapRequest = createTestZapRequest()
        val incompleteJson = """
            {
                "routes": []
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(incompleteJson).setResponseCode(200))

        val callbackUrl = mockWebServer.url("/callback").toString()
        val result = fetchLnurlInvoice(
            callback = callbackUrl,
            amountMillisats = 10000,
            zapRequest = zapRequest,
            lnurl = "lnurl1test",
            httpClient = httpClient
        )

        assertTrue("Should be error", result is LnurlInvoiceResult.Error)
        val error = (result as LnurlInvoiceResult.Error).message
        assertTrue("Error should mention missing pr field", error.contains("pr"))
    }

    @Test
    fun `fetchLnurlInvoice handles network errors`() = runTest {
        val zapRequest = createTestZapRequest()

        // Use an invalid URL to trigger network error
        val result = fetchLnurlInvoice(
            callback = "http://invalid.localhost.test:99999/callback",
            amountMillisats = 10000,
            zapRequest = zapRequest,
            lnurl = "lnurl1test",
            httpClient = httpClient
        )

        assertTrue("Should be error", result is LnurlInvoiceResult.Error)
        assertNotNull("Error message should not be null", (result as LnurlInvoiceResult.Error).message)
    }

    @Test
    fun `fetchLnurlInvoice encodes special characters in zap request correctly`() = runTest {
        // Create a zap request with content that has special characters
        val zapRequest = io.nostr.ndk.models.NDKEvent(
            id = "test_id_123",
            pubkey = "sender_pubkey",
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_ZAP_REQUEST,
            tags = listOf(
                io.nostr.ndk.models.NDKTag("p", listOf("recipient_pubkey")),
                io.nostr.ndk.models.NDKTag("amount", listOf("50000")),
                io.nostr.ndk.models.NDKTag("lnurl", listOf("lnurl1test"))
            ),
            content = "Zap with special chars: @#$%&",
            sig = "test_sig"
        )

        val invoiceJson = """
            {
                "pr": "lnbc500n1test_invoice",
                "routes": []
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(invoiceJson).setResponseCode(200))

        val callbackUrl = mockWebServer.url("/callback").toString()
        val result = fetchLnurlInvoice(
            callback = callbackUrl,
            amountMillisats = 50000,
            zapRequest = zapRequest,
            lnurl = "lnurl1test",
            httpClient = httpClient
        )

        assertTrue("Should be success", result is LnurlInvoiceResult.Success)

        // Verify the request was properly encoded
        val request = mockWebServer.takeRequest()
        val requestUrl = request.requestUrl!!
        val nostrParam = requestUrl.queryParameter("nostr")
        assertNotNull("nostr parameter should be present", nostrParam)

        // Decode and verify it's valid JSON with our special content
        val decodedJson = java.net.URLDecoder.decode(nostrParam!!, "UTF-8")
        assertTrue("decoded JSON should contain special chars content",
            decodedJson.contains("Zap with special chars: @#\$%&"))
    }
}
