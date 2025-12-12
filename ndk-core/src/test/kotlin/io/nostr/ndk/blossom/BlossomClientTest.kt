package io.nostr.ndk.blossom

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BlossomClientTest {

    @Test
    fun `upload file successfully returns result`() = runTest {
        // Mock HTTP client that returns success
        val mockEngine = MockEngine { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/upload", request.url.encodedPath)
            assertTrue(request.headers.contains("Authorization"))

            respond(
                content = """{"url":"https://cdn.example.com/abc123.jpg","sha256":"abc123","size":1024,"type":"image/jpeg"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val client = BlossomClient("https://blossom.example.com", httpClient)
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        // Create temporary test file
        val testFile = File.createTempFile("test", ".jpg")
        testFile.writeBytes(ByteArray(1024) { it.toByte() })

        try {
            val result = client.upload(testFile, signer, "image/jpeg").getOrThrow()

            assertEquals("https://cdn.example.com/abc123.jpg", result.url)
            assertEquals("abc123", result.sha256)
            assertEquals(1024L, result.size)
            assertEquals("image/jpeg", result.mimeType)
        } finally {
            testFile.delete()
        }
    }

    @Test
    fun `upload returns failure for non-2xx response`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"error":"Upload failed"}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val client = BlossomClient("https://blossom.example.com", httpClient)
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val testFile = File.createTempFile("test", ".jpg")
        testFile.writeBytes(ByteArray(1024))

        try {
            val result = client.upload(testFile, signer, "image/jpeg")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Upload failed") == true)
        } finally {
            testFile.delete()
        }
    }

    @Test
    fun `upload includes Authorization header with kind 24242 event`() = runTest {
        var capturedAuthHeader: String? = null

        val mockEngine = MockEngine { request ->
            capturedAuthHeader = request.headers["Authorization"]

            respond(
                content = """{"url":"https://cdn.example.com/abc123.jpg","sha256":"abc123","size":1024,"type":"image/jpeg"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val client = BlossomClient("https://blossom.example.com", httpClient)
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val testFile = File.createTempFile("test", ".jpg")
        testFile.writeBytes(ByteArray(1024))

        try {
            client.upload(testFile, signer, "image/jpeg").getOrThrow()

            assertNotNull(capturedAuthHeader)
            assertTrue(capturedAuthHeader?.startsWith("Nostr ") == true)
        } finally {
            testFile.delete()
        }
    }
}
