package io.nostr.ndk.crypto

import io.nostr.ndk.models.PublicKey
import java.net.URI
import java.net.URLDecoder

/**
 * Parsed components from a bunker:// URL.
 *
 * URL format: bunker://<remote-signer-pubkey>?relay=<wss://relay>&relay=<wss://relay2>&secret=<optional-secret>
 */
data class BunkerUrl(
    val pubkey: PublicKey,
    val relays: List<String>,
    val secret: String?
) {
    companion object {
        /**
         * Parses a bunker:// URL into its components.
         *
         * @param url The bunker URL (e.g., "bunker://abc123...?relay=wss://relay.example.com")
         * @return Parsed BunkerUrl components
         * @throws IllegalArgumentException if the URL is invalid
         */
        fun parse(url: String): BunkerUrl {
            require(url.startsWith("bunker://")) { "URL must start with bunker://" }

            val uri = URI(url)
            val pubkey = uri.host
                ?: throw IllegalArgumentException("Bunker URL must contain a pubkey")

            require(pubkey.length == 64) { "Pubkey must be 64 hex characters" }
            require(pubkey.all { it in '0'..'9' || it in 'a'..'f' }) {
                "Pubkey must be valid hex"
            }

            val params = parseQueryParams(uri.query ?: "")
            val relays = params.filter { it.first == "relay" }.map { it.second }
            val secret = params.firstOrNull { it.first == "secret" }?.second

            require(relays.isNotEmpty()) { "Bunker URL must contain at least one relay" }

            return BunkerUrl(pubkey, relays, secret)
        }

        private fun parseQueryParams(query: String): List<Pair<String, String>> {
            if (query.isBlank()) return emptyList()

            return query.split("&").mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    URLDecoder.decode(parts[0], "UTF-8") to
                        URLDecoder.decode(parts[1], "UTF-8")
                } else null
            }
        }
    }
}
