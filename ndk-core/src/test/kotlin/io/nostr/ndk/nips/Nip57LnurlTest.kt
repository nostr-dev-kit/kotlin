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
}
