package io.nostr.ndk.nips

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.nostr.ndk.models.PublicKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * NIP-05: DNS-Based Verification
 *
 * NIP-05 provides a way to verify a Nostr identity using DNS.
 * A NIP-05 identifier looks like: user@domain.com
 *
 * Verification works by:
 * 1. Parse identifier into name and domain
 * 2. Fetch https://domain.com/.well-known/nostr.json?name=user
 * 3. Check if response contains the expected pubkey for that name
 *
 * Optional: The response can also include relay recommendations for the user.
 */

/**
 * Result of a NIP-05 lookup/verification.
 */
data class Nip05Result(
    /**
     * The NIP-05 identifier that was looked up.
     */
    val identifier: String,

    /**
     * The pubkey associated with this identifier, or null if not found.
     */
    val pubkey: PublicKey?,

    /**
     * Recommended relays for this user, if any.
     */
    val relays: List<String>,

    /**
     * Whether verification succeeded (pubkey was found for the identifier).
     */
    val verified: Boolean,

    /**
     * Error message if verification failed, null otherwise.
     */
    val error: String?
)

/**
 * Verifies and looks up NIP-05 identifiers.
 */
class Nip05Verifier(
    private val httpClient: OkHttpClient = defaultHttpClient
) {
    companion object {
        private val defaultHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        private val objectMapper = jacksonObjectMapper()
    }

    /**
     * Parses a NIP-05 identifier into name and domain.
     *
     * @param identifier NIP-05 identifier (e.g., "user@domain.com" or "_@domain.com")
     * @return Pair of (name, domain) or null if invalid
     */
    fun parseIdentifier(identifier: String): Pair<String, String>? {
        val normalized = identifier.lowercase().trim()
        val parts = normalized.split("@")
        if (parts.size != 2) return null

        val name = parts[0]
        val domain = parts[1]

        // Validate domain (basic check)
        if (domain.isBlank() || !domain.contains(".")) return null

        // Name can be empty or underscore for root identifier
        val finalName = if (name.isBlank()) "_" else name

        return Pair(finalName, domain)
    }

    /**
     * Looks up a NIP-05 identifier and returns the associated pubkey and relays.
     *
     * @param identifier NIP-05 identifier (e.g., "user@domain.com")
     * @return Nip05Result with lookup results
     */
    suspend fun lookup(identifier: String): Nip05Result = withContext(Dispatchers.IO) {
        val parsed = parseIdentifier(identifier)
            ?: return@withContext Nip05Result(
                identifier = identifier,
                pubkey = null,
                relays = emptyList(),
                verified = false,
                error = "Invalid NIP-05 identifier format"
            )

        val (name, domain) = parsed
        val url = "https://$domain/.well-known/nostr.json?name=$name"

        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Nip05Result(
                        identifier = identifier,
                        pubkey = null,
                        relays = emptyList(),
                        verified = false,
                        error = "HTTP ${response.code}: ${response.message}"
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext Nip05Result(
                        identifier = identifier,
                        pubkey = null,
                        relays = emptyList(),
                        verified = false,
                        error = "Empty response body"
                    )

                val json: Map<String, Any?> = objectMapper.readValue(body)

                @Suppress("UNCHECKED_CAST")
                val names = json["names"] as? Map<String, String>
                val pubkey = names?.get(name)

                @Suppress("UNCHECKED_CAST")
                val relaysMap = json["relays"] as? Map<String, List<String>>
                val relays = pubkey?.let { relaysMap?.get(it) } ?: emptyList()

                Nip05Result(
                    identifier = identifier,
                    pubkey = pubkey,
                    relays = relays,
                    verified = pubkey != null,
                    error = null
                )
            }
        } catch (e: Exception) {
            Nip05Result(
                identifier = identifier,
                pubkey = null,
                relays = emptyList(),
                verified = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Verifies that a NIP-05 identifier resolves to the expected pubkey.
     *
     * @param identifier NIP-05 identifier (e.g., "user@domain.com")
     * @param expectedPubkey The pubkey to verify against
     * @return True if the identifier resolves to the expected pubkey
     */
    suspend fun verify(identifier: String, expectedPubkey: PublicKey): Boolean {
        val result = lookup(identifier)
        return result.verified && result.pubkey == expectedPubkey.lowercase()
    }
}

/**
 * Extracts the NIP-05 identifier from a kind 0 metadata event.
 */
val io.nostr.ndk.models.NDKEvent.nip05: String?
    get() {
        if (kind != 0) return null
        return try {
            val json: Map<String, Any?> = jacksonObjectMapper().readValue(content)
            json["nip05"] as? String
        } catch (e: Exception) {
            null
        }
    }
