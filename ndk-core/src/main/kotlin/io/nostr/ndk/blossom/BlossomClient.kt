package io.nostr.ndk.blossom

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64

/**
 * Client for uploading files to Blossom servers (BUD-01 protocol).
 *
 * @param serverUrl Base URL of the Blossom server (e.g., "https://blossom.primal.net")
 * @param httpClient Ktor HTTP client instance
 */
class BlossomClient(
    private val serverUrl: String,
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Upload a file to the Blossom server.
     *
     * @param file File to upload
     * @param signer Signer for creating BUD-01 auth event
     * @param mimeType MIME type of the file
     * @return Upload result with URL and metadata
     */
    suspend fun upload(
        file: File,
        signer: NDKSigner,
        mimeType: String
    ): Result<BlossomUploadResult> = withContext(Dispatchers.IO) {
        try {
            // Calculate SHA-256 hash
            val hash = file.inputStream().use { calculateSHA256(it) }

            // Create BUD-01 auth event (kind 24242)
            val authEvent = createUploadAuthEvent(
                hash = hash,
                size = file.length(),
                mimeType = mimeType,
                signer = signer
            )

            // Upload with auth header (base64-encoded as per BUD-01)
            val authJson = authEvent.toJson()
            val authBase64 = Base64.getEncoder().encodeToString(authJson.toByteArray())

            val response: HttpResponse = httpClient.put("$serverUrl/upload") {
                headers {
                    append("Authorization", "Nostr $authBase64")
                }
                setBody(file.readBytes())
                contentType(ContentType.parse(mimeType))
            }

            if (response.status.isSuccess()) {
                val result = json.decodeFromString<BlossomUploadResult>(response.bodyAsText())
                Result.success(result)
            } else {
                Result.failure(Exception("Upload failed: ${response.status} - ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun createUploadAuthEvent(
        hash: String,
        size: Long,
        mimeType: String,
        signer: NDKSigner
    ): NDKEvent {
        val now = System.currentTimeMillis() / 1000
        val expiration = now + 600 // 10 minutes

        val unsignedEvent = io.nostr.ndk.crypto.UnsignedEvent(
            pubkey = signer.pubkey,
            createdAt = now,
            kind = 24242,
            tags = listOf(
                NDKTag("t", listOf("upload")),
                NDKTag("x", listOf(hash)),
                NDKTag("size", listOf(size.toString())),
                NDKTag("m", listOf(mimeType)),
                NDKTag("expiration", listOf(expiration.toString()))
            ),
            content = ""
        )

        return signer.sign(unsignedEvent)
    }

    private fun calculateSHA256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } > 0) {
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
