package io.nostr.ndk.crypto

import io.nostr.ndk.models.PublicKey
import java.net.URLEncoder
import java.security.SecureRandom

/**
 * Generates nostrconnect:// URIs for client-initiated NIP-46 connections.
 *
 * URI format: nostrconnect://<client-pubkey>?relay=<url>&secret=<string>&perms=<list>&name=<app>&url=<url>&image=<url>
 *
 * The client generates this URI and displays it as a QR code or deeplink.
 * When the remote signer scans/opens it, they send a `connect` response
 * back to the client via the specified relays.
 *
 * @see <a href="https://github.com/nostr-protocol/nips/blob/master/46.md">NIP-46</a>
 */
data class NostrConnectUrl(
    val clientPubkey: PublicKey,
    val relays: List<String>,
    val secret: String,
    val permissions: List<String>? = null,
    val name: String? = null,
    val url: String? = null,
    val image: String? = null
) {
    /**
     * Generates the nostrconnect:// URI string.
     */
    fun toUri(): String {
        val params = buildList {
            relays.forEach { relay ->
                add("relay=${URLEncoder.encode(relay, "UTF-8")}")
            }
            add("secret=${URLEncoder.encode(secret, "UTF-8")}")
            permissions?.let { perms ->
                if (perms.isNotEmpty()) {
                    add("perms=${URLEncoder.encode(perms.joinToString(","), "UTF-8")}")
                }
            }
            name?.let { add("name=${URLEncoder.encode(it, "UTF-8")}") }
            url?.let { add("url=${URLEncoder.encode(it, "UTF-8")}") }
            image?.let { add("image=${URLEncoder.encode(it, "UTF-8")}") }
        }

        return "nostrconnect://$clientPubkey?${params.joinToString("&")}"
    }

    companion object {
        /**
         * Generates a cryptographically secure random secret.
         */
        fun generateSecret(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        /**
         * Creates a NostrConnectUrl with a generated secret.
         *
         * @param clientKeyPair The client's keypair (pubkey will be used in URI)
         * @param relays Relay URLs where client listens for responses
         * @param permissions Optional permissions to request
         * @param name Optional app name
         * @param url Optional app URL
         * @param image Optional app icon URL
         */
        fun create(
            clientKeyPair: NDKKeyPair,
            relays: List<String>,
            permissions: List<String>? = null,
            name: String? = null,
            url: String? = null,
            image: String? = null
        ): NostrConnectUrl {
            return NostrConnectUrl(
                clientPubkey = clientKeyPair.pubkeyHex,
                relays = relays,
                secret = generateSecret(),
                permissions = permissions,
                name = name,
                url = url,
                image = image
            )
        }
    }
}
