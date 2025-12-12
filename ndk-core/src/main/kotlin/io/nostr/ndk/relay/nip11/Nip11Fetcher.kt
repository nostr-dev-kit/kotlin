package io.nostr.ndk.relay.nip11

import io.nostr.ndk.logging.NDKLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "Nip11Fetcher"

/**
 * Fetches NIP-11 relay information documents over HTTP.
 *
 * Converts WebSocket URLs (wss://relay.url) to HTTPS URLs (https://relay.url)
 * and fetches the relay information document with proper headers.
 *
 * @param okHttpClient The OkHttpClient to use for HTTP requests
 */
class Nip11Fetcher(
    private val okHttpClient: OkHttpClient = defaultClient()
) {
    companion object {
        private const val DEFAULT_TIMEOUT_MS = 5000L

        /**
         * Creates a default OkHttpClient with appropriate timeouts for NIP-11 fetching.
         */
        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

        /**
         * Convert WebSocket URL to HTTP URL for NIP-11 fetching.
         * - wss://relay.url -> https://relay.url
         * - ws://relay.url -> http://relay.url
         */
        fun toHttpUrl(relayUrl: String): String {
            return relayUrl
                .replace("wss://", "https://")
                .replace("ws://", "http://")
        }
    }

    /**
     * Fetch NIP-11 relay information.
     *
     * @param relayUrl The WebSocket URL of the relay (wss://...)
     * @param timeoutMs Timeout in milliseconds (default: 5000)
     * @return Result containing Nip11RelayInformation or Nip11Error
     */
    suspend fun fetch(
        relayUrl: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Result<Nip11RelayInformation> = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMs) {
                val httpUrl = toHttpUrl(relayUrl)
                NDKLogging.d(TAG, "Fetching NIP-11 info from $httpUrl")

                val request = Request.Builder()
                    .url(httpUrl)
                    .header("Accept", "application/nostr+json")
                    .build()

                val response = okHttpClient.newCall(request).execute()

                response.use {
                    when {
                        !it.isSuccessful -> {
                            NDKLogging.w(TAG, "Failed to fetch NIP-11 from $httpUrl: HTTP ${it.code}")
                            Result.failure(Nip11Error.HttpError(it.code, it.message))
                        }
                        else -> {
                            val body = it.body?.string()
                            if (body.isNullOrBlank()) {
                                NDKLogging.w(TAG, "Empty response from $httpUrl")
                                Result.failure(Nip11Error.EmptyResponse)
                            } else {
                                try {
                                    val info = Nip11RelayInformation.fromJson(body)
                                    NDKLogging.d(TAG, "Successfully fetched NIP-11 from $httpUrl: ${info.name ?: "unnamed"}")
                                    Result.success(info)
                                } catch (e: Exception) {
                                    NDKLogging.e(TAG, "Failed to parse NIP-11 from $httpUrl: ${e.message}")
                                    Result.failure(Nip11Error.ParseError(e.message ?: "Unknown parse error"))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            NDKLogging.e(TAG, "Failed to fetch NIP-11 from $relayUrl: ${e.message}")
            Result.failure(Nip11Error.NetworkError(e.message ?: "Unknown network error"))
        }
    }
}

/**
 * Errors that can occur during NIP-11 fetching.
 */
sealed class Nip11Error : Exception() {
    /**
     * HTTP error with status code and message.
     */
    data class HttpError(val code: Int, override val message: String) : Nip11Error() {
        override fun toString(): String = "HTTP $code: $message"
    }

    /**
     * JSON parsing error.
     */
    data class ParseError(override val message: String) : Nip11Error()

    /**
     * Network error (timeout, connection refused, etc.).
     */
    data class NetworkError(override val message: String) : Nip11Error()

    /**
     * Empty response body.
     */
    data object EmptyResponse : Nip11Error() {
        override val message: String get() = "Empty response body"
    }
}
